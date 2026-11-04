package com.houseaffairs.app.core.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.houseaffairs.app.core.model.Visibility
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/** Everything the Money tab and the vision board need, in one snapshot. */
data class MoneyState(
    val budget: Budget,
    val startedOn: LocalDate,
    val summary: BudgetSummary,
    val envelopes: EnvelopeState,
    val credits: List<Credit>,
    val dreams: List<DreamProgress>,
    val investments: List<InvestmentValue> = emptyList(),
    val trips: List<TripSummary> = emptyList(),
    /** Every expense since the budget started, for the monthly overview. */
    val txns: List<Txn> = emptyList(),
    /** Every move between envelopes, the gullak and dreams, for the list of moves. */
    val moves: List<Transfer> = emptyList(),
    /** Each card's closed statement (the bill) and open one (spends so far). */
    val cardBills: List<CardBill> = emptyList(),
)

/** Money that can be moved out of [pocket] right now. */
fun MoneyState.available(pocket: Pocket): Long = when (pocket) {
    Pocket.Pot -> envelopes.potMinor
    is Pocket.Category -> envelopes.envelopes.firstOrNull { it.category == pocket.name }?.availableMinor ?: 0
    is Pocket.Dream -> envelopes.dreamFunding[pocket.id] ?: 0
    is Pocket.Investment -> investments.firstOrNull { it.investment.id == pocket.id }?.currentMinor ?: 0
    Pocket.Growth -> Long.MAX_VALUE
}

/** The budget, this cycle's spending and what's still due. Callers check Money access first. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class MoneyRepository @Inject constructor(
    private val db: FirebaseFirestore,
) {
    private fun household(id: String) = db.collection("households").document(id)
    private fun budgetDoc(householdId: String) = household(householdId).collection("budgets").document("main")

    private data class BudgetDoc(val budget: Budget, val startedOn: LocalDate)

    fun observeBudget(householdId: String): Flow<Budget?> = observeBudgetDoc(householdId).map { it?.budget }

    private fun observeBudgetDoc(householdId: String): Flow<BudgetDoc?> =
        budgetDoc(householdId).observeSnapshots()
            .map { doc ->
                if (!doc.exists()) return@map null
                val budget = Budget(
                    limitMinor = doc.getLong("limitMinor") ?: 0,
                    currency = doc.getString("currency") ?: "USD",
                    cycleStartDay = doc.getLong("cycleStartDay")?.toInt() ?: 1,
                    savingsGoalMinor = doc.getLong("savingsGoalMinor") ?: 0,
                    categories = (doc.get("categories") as? Map<*, *>).orEmpty()
                        .mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? Number)?.toLong()?.let { key to it } } }
                        .toMap(),
                    cards = (doc.get("cards") as? List<*>).orEmpty().mapNotNull { raw ->
                        (raw as? Map<*, *>)?.let { m ->
                            Card(
                                name = (m["name"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                                statementDay = (m["statementDay"] as? Number)?.toInt() ?: 1,
                                dueDay = (m["dueDay"] as? Number)?.toInt() ?: 20,
                            )
                        }
                    },
                    paidBills = (doc.get("paidBills") as? List<*>).orEmpty().filterIsInstance<String>().toSet(),
                    incomeMinor = doc.getLong("incomeMinor") ?: 0,
                    customCategories = (doc.get("customCategories") as? List<*>).orEmpty().filterIsInstance<String>(),
                )
                // Rollover counts from the budget month the family started tracking.
                val started = doc.localDate("startedOn") ?: doc.localDate("createdAt") ?: LocalDate.now()
                BudgetDoc(budget, cycleFor(started, budget.cycleStartDay).start)
            }
            // One failed read (offline, a permission race) shouldn't leave the screen stuck.
            .retryWhen { _, attempt -> (attempt < 3).also { if (it) delay(1_000L * (attempt + 1)) } }
            .catch { emit(null) }

    suspend fun saveBudget(householdId: String, uid: String, budget: Budget) {
        val fields = mapOf(
            "limitMinor" to budget.limitMinor,
            "currency" to budget.currency,
            "cycleStartDay" to budget.cycleStartDay,
            "savingsGoalMinor" to budget.savingsGoalMinor,
            "incomeMinor" to budget.incomeMinor,
            "categories" to budget.categories,
            "customCategories" to budget.customCategories,
            "cards" to budget.cards.map { mapOf("name" to it.name.trim(), "statementDay" to it.statementDay, "dueDay" to it.dueDay) },
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        val ref = budgetDoc(householdId)
        // Rules keep `createdBy` fixed, so only the first save sets it.
        if (ref.get().await().exists()) {
            ref.update(fields).await()
        } else {
            ref.set(
                fields + mapOf(
                    "createdBy" to uid,
                    "visibleTo" to Visibility.everyone,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "startedOn" to cycleFor(LocalDate.now(), budget.cycleStartDay).start.toTimestamp(),
                ),
            ).await()
        }
    }

    /** The budget summary for today, or null until a budget is set. */
    fun observeSummary(householdId: String, uid: String): Flow<BudgetSummary?> =
        observeMoney(householdId, uid).map { it?.summary }

    /** Budget, rollover envelopes, gullak, credits and dreams; null until a budget is set. */
    fun observeMoney(householdId: String, uid: String): Flow<MoneyState?> =
        observeBudgetDoc(householdId).flatMapLatest { doc ->
            if (doc == null) return@flatMapLatest flowOf(null)
            val (budget, startedOn) = doc
            val inputs = combine(
                // Back far enough for the last card statement too, even in a new budget's first month.
                transactions(householdId, uid, minOf(startedOn, LocalDate.now().minusDays(70)), budget.currency),
                duePayments(householdId, uid, budget.currency),
                transfers(householdId, uid),
                observeCredits(householdId, uid),
                observeDreams(householdId, uid),
            ) { txns, due, moves, credits, dreams -> Inputs(txns, due, moves, credits, dreams) }
            combine(inputs, observeInvestments(householdId, uid), observeTrips(householdId, uid)) { i, investments, trips ->
                val today = LocalDate.now()
                val state = envelopes(budget, startedOn, i.txns, i.moves, i.credits, today)
                val values = investmentValues(investments, i.moves, today)
                MoneyState(
                    budget = budget,
                    startedOn = startedOn,
                    summary = summarize(budget, i.txns, i.due, today),
                    envelopes = state,
                    credits = i.credits.filter { !it.used },
                    dreams = dreamProgress(i.dreams, state, i.credits, today, values),
                    investments = values,
                    trips = tripSummaries(trips, i.txns, today),
                    txns = i.txns,
                    moves = i.moves,
                    cardBills = cardBills(budget.cards, i.txns, budget.paidBills, today),
                )
            }
        }

    private data class Inputs(
        val txns: List<Txn>,
        val due: List<DuePayment>,
        val moves: List<Transfer>,
        val credits: List<Credit>,
        val dreams: List<Dream>,
    )

    fun observeInvestments(householdId: String, uid: String): Flow<List<Investment>> =
        visible(householdId, "investments", uid).observeSnapshots()
            .map { snap ->
                snap.documents.filter { it.getBoolean("closed") != true }.map { doc ->
                    Investment(
                        id = doc.id,
                        title = doc.getString("title").orEmpty(),
                        kind = doc.getString("kind") ?: "Other",
                        ratePercent = doc.getString("rate")?.replace("%", "")?.trim()?.toDoubleOrNull(),
                        maturity = doc.localDate("maturity"),
                        forDream = doc.getString("forDream")?.takeIf { it != "Nothing specific" },
                    )
                }
            }
            .catch { emit(emptyList()) }

    fun observeTrips(householdId: String, uid: String): Flow<List<Trip>> =
        visible(householdId, "trips", uid).observeSnapshots()
            .map { snap ->
                snap.documents.mapNotNull { d ->
                    Trip(
                        id = d.id,
                        title = d.getString("title") ?: return@mapNotNull null,
                        start = d.localDate("start") ?: return@mapNotNull null,
                        end = d.localDate("end"),
                        budgetMinor = d.getLong("budgetMinor"),
                        dream = d.getString("forDream")?.takeIf { it != "Not linked" },
                        done = d.getBoolean("done") == true,
                    )
                }
            }
            .catch { emit(emptyList()) }

    /** Marks a trip done (ending it today if it had no end yet); the returned undo reverses it. */
    fun setTripDone(householdId: String, trip: Trip, today: LocalDate = LocalDate.now()): () -> Unit {
        val ref = household(householdId).collection("trips").document(trip.id)
        val end = trip.end?.takeIf { !it.isAfter(today) } ?: today
        ref.update(mapOf("done" to true, "end" to Timestamp(Date.from(end.atStartOfDay(ZoneId.systemDefault()).toInstant()))))
        return {
            ref.update(
                mapOf(
                    "done" to false,
                    "end" to (trip.end?.let { Timestamp(Date.from(it.atStartOfDay(ZoneId.systemDefault()).toInstant())) } ?: com.google.firebase.firestore.FieldValue.delete()),
                ),
            )
        }
    }

    fun reopenTrip(householdId: String, id: String) {
        household(householdId).collection("trips").document(id).update("done", false)
    }

    fun closeInvestment(householdId: String, id: String) {
        household(householdId).collection("investments").document(id).update("closed", true)
    }

    /** Dreams on the vision board (visible to anyone with Plans access, even without Money). */
    fun observeDreams(householdId: String, uid: String): Flow<List<Dream>> =
        visible(householdId, "dreams", uid).observeSnapshots()
            .map { snap ->
                snap.documents.map { doc ->
                    Dream(
                        id = doc.id,
                        title = doc.getString("title").orEmpty(),
                        kind = doc.getString("kind") ?: "Other",
                        emoji = doc.getString("emoji") ?: "⭐",
                        targetMinor = doc.getLong("amountMinor") ?: 0,
                        targetDate = doc.localDate("targetDate"),
                        achieved = doc.getBoolean("achieved") == true,
                        horizonChoice = doc.getString("horizon"),
                    )
                }
            }
            .catch { emit(emptyList()) }

    fun observeCredits(householdId: String, uid: String): Flow<List<Credit>> =
        visible(householdId, "credits", uid).observeSnapshots()
            .map { snap ->
                snap.documents.map { doc ->
                    Credit(
                        id = doc.id,
                        title = doc.getString("title").orEmpty(),
                        kind = doc.getString("kind") ?: "Credit",
                        valueMinor = doc.getLong("amountMinor") ?: 0,
                        expiry = doc.localDate("expiry"),
                        earmark = doc.getString("earmark")?.takeIf { it != "Anything" },
                        used = doc.getBoolean("used") == true,
                    )
                }.sortedBy { it.expiry ?: LocalDate.MAX }
            }
            .catch { emit(emptyList()) }

    /** Moves money between envelopes, the gullak and dreams ("2000 from Gifts to Travel"). */
    fun transfer(householdId: String, uid: String, from: Pocket, to: Pocket, amountMinor: Long, note: String? = null): com.google.firebase.firestore.DocumentReference {
        val ref = household(householdId).collection("transfers").document()
        ref.set(
            mapOf(
                "from" to from.key,
                "to" to to.key,
                "amountMinor" to amountMinor,
                "note" to note,
                "date" to LocalDate.now().toTimestamp(),
                "createdBy" to uid,
                "visibleTo" to Visibility.everyone,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        )
        return ref
    }

    fun markCreditUsed(householdId: String, creditId: String) {
        household(householdId).collection("credits").document(creditId).update("used", true)
    }

    fun markDreamAchieved(householdId: String, dreamId: String, achieved: Boolean) {
        household(householdId).collection("dreams").document(dreamId).update("achieved", achieved)
    }

    private fun transfers(householdId: String, uid: String): Flow<List<Transfer>> =
        visible(householdId, "transfers", uid).observeSnapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    Transfer(
                        id = doc.id,
                        from = doc.getString("from") ?: return@mapNotNull null,
                        to = doc.getString("to") ?: return@mapNotNull null,
                        amountMinor = doc.getLong("amountMinor") ?: return@mapNotNull null,
                        date = doc.localDate("date") ?: LocalDate.now(),
                        note = doc.getString("note"),
                    )
                }
            }
            .catch { emit(emptyList()) }

    /** Deletes a ledger entry; the returned undo writes it back as it was. */
    fun deleteTransfer(householdId: String, id: String): () -> Unit {
        val ref = household(householdId).collection("transfers").document(id)
        var saved: Map<String, Any>? = null
        ref.get().addOnSuccessListener { doc ->
            saved = doc.data
            ref.delete()
        }
        return { saved?.let { ref.set(it) } }
    }

    /** Turns a planned spend into a real one dated today. */
    /** A category typed in a form ("Cook") joins the family's own, so it's offered next time. */
    fun addCustomCategory(householdId: String, name: String) {
        budgetDoc(householdId).update("customCategories", FieldValue.arrayUnion(name.trim()))
    }

    /** Marks a card bill paid (or not), remembered on the budget. */
    fun setBillPaid(householdId: String, key: String, paid: Boolean) {
        budgetDoc(householdId).update("paidBills", if (paid) FieldValue.arrayUnion(key) else FieldValue.arrayRemove(key))
    }

    /**
     * Logs a subscription's renewal on [date] as an expense and remembers it, so it isn't counted
     * twice. [exclusive] (automatic logging, maybe on several phones) uses a transaction and does
     * nothing if someone already logged it; a tap writes straight away, offline too.
     * Returns an undo, or null if there was nothing to log.
     */
    suspend fun logRenewal(householdId: String, uid: String, id: String, date: LocalDate, exclusive: Boolean = false): (() -> Unit)? {
        val sub = household(householdId).collection("recurring").document(id)
        val txn = household(householdId).collection("transactions").document()
        fun expenseFrom(doc: DocumentSnapshot) = mapOf(
            "type" to "EXPENSE",
            "title" to (doc.getString("title") ?: "Subscription"),
            "merchant" to (doc.getString("title") ?: "Subscription"),
            "amountMinor" to (doc.getLong("amountMinor") ?: 0L),
            "currency" to doc.getString("currency"),
            "category" to (doc.getString("category") ?: "Bills"),
            "paymentMethod" to doc.getString("paymentMethod"),
            "date" to date.toTimestamp(),
            "status" to "Paid",
            "fromSubscription" to id,
            "createdBy" to uid,
            "visibleTo" to (doc.get("visibleTo") ?: Visibility.everyone),
            "createdAt" to FieldValue.serverTimestamp(),
            "source" to "subscription",
        ).filterValues { it != null }
        val before: LocalDate?
        if (exclusive) {
            var previous: LocalDate? = null
            val logged = runCatching {
                db.runTransaction { tr ->
                    val doc = tr.get(sub)
                    previous = doc.localDate("lastLogged")
                    if (!doc.exists() || previous?.let { !date.isAfter(it) } == true) return@runTransaction false
                    tr.set(txn, expenseFrom(doc))
                    tr.update(sub, "lastLogged", date.toTimestamp())
                    true
                }.await()
            }.getOrDefault(false)
            if (!logged) return null
            before = previous
        } else {
            val doc = runCatching { sub.get().await() }.getOrNull()?.takeIf { it.exists() } ?: return null
            before = doc.localDate("lastLogged")
            db.batch().set(txn, expenseFrom(doc)).update(sub, "lastLogged", date.toTimestamp()).commit()
        }
        return {
            txn.delete()
            sub.update("lastLogged", before?.toTimestamp() ?: FieldValue.delete())
        }
    }

    fun markPaid(householdId: String, txnId: String) {
        household(householdId).collection("transactions").document(txnId)
            .update(mapOf("status" to "Paid", "date" to Timestamp.now()))
    }

    private fun visible(householdId: String, name: String, uid: String): Query =
        household(householdId).collection(name).whereArrayContainsAny("visibleTo", listOf(Visibility.EVERYONE, uid))

    private fun transactions(householdId: String, uid: String, since: LocalDate, currency: String): Flow<List<Txn>> =
        visible(householdId, "transactions", uid)
            .whereGreaterThanOrEqualTo("date", since.toTimestamp())
            .observeSnapshots()
            .map { snap ->
                snap.documents
                    .filter { it.getString("currency") == currency }
                    .mapNotNull { doc ->
                        Txn(
                            id = doc.id,
                            title = doc.getString("title") ?: doc.getString("merchant") ?: doc.getString("category") ?: "Expense",
                            category = doc.getString("category") ?: "Other",
                            amountMinor = doc.getLong("amountMinor") ?: return@mapNotNull null,
                            date = doc.localDate("date") ?: return@mapNotNull null,
                            planned = doc.getString("status") == "Planned",
                            subcategory = doc.getString("subcategory"),
                            trip = doc.getString("trip"),
                            paidWith = doc.getString("paymentMethod"),
                            card = doc.getString("card"),
                        )
                    }
            }
            .catch { emit(emptyList()) }

    private fun duePayments(householdId: String, uid: String, currency: String): Flow<List<DuePayment>> =
        visible(householdId, "recurring", uid).observeSnapshots()
            .map { snap ->
                val today = LocalDate.now()
                snap.documents
                    .filter { it.getString("currency") == currency }
                    .mapNotNull { doc ->
                        val next = doc.localDate("nextDate") ?: return@mapNotNull null
                        // Once logged, a renewal is spending; until then it's still due (even if the day has passed).
                        val owed = nextRenewal(next, doc.getString("frequency"), doc.localDate("lastLogged"), today)
                        DuePayment(doc.id, doc.getString("title") ?: "Payment", owed, doc.getLong("amountMinor") ?: 0)
                    }
            }
            .catch { emit(emptyList()) }
}

internal fun LocalDate.toTimestamp() = Timestamp(Date.from(atStartOfDay(ZoneId.systemDefault()).toInstant()))

internal fun DocumentSnapshot.localDate(field: String): LocalDate? =
    getTimestamp(field)?.toDate()?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDate()
