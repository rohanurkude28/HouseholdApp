package com.houseaffairs.app.core.calendar

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.houseaffairs.app.core.data.HomeRepository
import com.houseaffairs.app.core.data.SessionManager
import com.houseaffairs.app.core.data.SessionState
import com.houseaffairs.app.core.model.AppModule
import com.houseaffairs.app.core.model.Visibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One round of two-way sync for the signed-in person: their dated tasks (assigned to them or to
 * nobody) and the family's events, against their own "HouseAffairs" Google calendar.
 */
@Singleton
class CalendarSyncer @Inject constructor(
    private val session: SessionManager,
    private val home: HomeRepository,
    private val db: FirebaseFirestore,
    private val auth: CalendarAuth,
    private val store: CalendarStore,
) {
    sealed interface Outcome {
        data class Done(val pushed: Int, val pulled: Int, val added: Int) : Outcome
        data object NeedsConsent : Outcome
        data class Failed(val message: String) : Outcome
    }

    suspend fun sync(): Outcome {
        val ready = withTimeoutOrNull(20_000) { session.state.first { it is SessionState.Ready || it == SessionState.SignedOut } } as? SessionState.Ready
            ?: return Outcome.Failed("Not signed in")
        if (!ready.me.canAccess(AppModule.PLANS)) return fail("Calendar sync covers tasks and events, which your role doesn't include")
        val token = when (val a = auth.authorize()) {
            is CalendarAuth.Result.Token -> a.value
            is CalendarAuth.Result.NeedsConsent -> return Outcome.NeedsConsent.also { store.setError("Allow calendar access again in Settings") }
            is CalendarAuth.Result.Failed -> return fail(a.message)
        }
        val data = withTimeoutOrNull(20_000) { home.observe(ready.household.id, ready.me, ready.household.baseCurrency).first { it.loaded } }
            ?: return fail("Couldn't read your tasks")
        val uid = ready.user.uid
        val app = data.tasks.filter { it.dueDate != null && (it.assignee == null || it.assignee == uid) }
            .map { CalItem("tasks/${it.id}", it.title, it.dueDate!!) } +
            data.events.map { CalItem("events/${it.id}", it.title, it.next, yearly = it.repeat == "Yearly") }

        return runCatching {
            withContext(Dispatchers.IO) {
                var saved = store.load()
                if (saved.owner != null && saved.owner != uid) {
                    store.reset()
                    saved = store.load()
                }
                val api = GoogleCalendar(token)
                var calendarId = saved.calendarId ?: api.createCalendar()
                val google = try {
                    api.events(calendarId)
                } catch (e: CalendarException) {
                    if (e.code != 404) throw e
                    // The calendar was deleted in Google: start again with a fresh one.
                    calendarId = api.createCalendar()
                    saved = saved.copy(synced = emptyMap(), skipped = emptySet())
                    emptyList()
                }
                val plan = planSync(app, google, saved.synced, saved.skipped)
                val state = plan.state.toMutableMap()
                plan.create.forEach { item -> state[item.haId] = Synced(api.insert(calendarId, item), item.title, item.date) }
                plan.update.forEach { (eventId, item) -> api.update(calendarId, eventId, item) }
                plan.delete.forEach { api.delete(calendarId, it) }
                plan.pull.forEach { (haId, title, date) -> pullIntoApp(ready.household.id, haId, title, date) }
                plan.adopt.forEach { g ->
                    val ref = db.collection("households").document(ready.household.id).collection("tasks").document()
                    ref.set(
                        mapOf(
                            "type" to "TASK", "title" to g.title, "due" to g.date!!.toTimestamp(), "assignee" to uid, "done" to false,
                            "createdBy" to uid, "visibleTo" to Visibility.everyone, "createdAt" to FieldValue.serverTimestamp(), "source" to "calendar",
                        ),
                    ).await()
                    api.link(calendarId, g.id, "tasks/${ref.id}")
                    state["tasks/${ref.id}"] = Synced(g.id, g.title, g.date)
                }
                store.save(uid, calendarId, state, saved.skipped + plan.forget, System.currentTimeMillis())
                Outcome.Done(plan.create.size + plan.update.size + plan.delete.size, plan.pull.size, plan.adopt.size)
            }
        }.getOrElse { e ->
            fail(
                when ((e as? CalendarException)?.code) {
                    401, 403 -> "Google said no: check Calendar access is allowed for this app"
                    else -> e.message?.take(120) ?: "Sync failed"
                },
            )
        }
    }

    /** A title or date changed in Google Calendar, written back to the task or event. */
    private suspend fun pullIntoApp(householdId: String, haId: String, title: String, date: LocalDate) {
        val (collection, id) = haId.split("/", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } ?: return
        val dateField = if (collection == "tasks") "due" else "date"
        db.collection("households").document(householdId).collection(collection).document(id)
            .update(mapOf("title" to title, dateField to date.toTimestamp(), "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    private suspend fun fail(message: String): Outcome = Outcome.Failed(message).also { store.setError(message) }

    private fun LocalDate.toTimestamp() = Timestamp(Date.from(atStartOfDay(ZoneId.systemDefault()).toInstant()))
}
