package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.model.Visibility
import com.houseaffairs.app.core.time.*
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.where
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen

/** A saved entry's id, and how to take it back (a money move, say). */
class Saved(val id: String, private val undo: () -> Unit) {
    fun delete() = undo()
}

/** The budget, this cycle's spending and what's still due. Callers check Money access first. */
@OptIn(ExperimentalCoroutinesApi::class)
class MoneyRepository {
    private fun budgetDoc(householdId: String) = collection(householdId, "budgets").document("main")

    private data class BudgetDoc(val budget: Budget, val startedOn: LocalDate)

    fun observeBudget(householdId: String): Flow<Budget?> = observeBudgetDoc(householdId).map { it?.budget }

    private fun observeBudgetDoc(householdId: String): Flow<BudgetDoc?> =
        budgetDoc(householdId).snapshots
            .map { doc ->
                if (!doc.exists) return@map null
                val raw = doc.plainData()
                val budget = Budget(
                    limitMinor = doc.long("limitMinor") ?: 0,
                    currency = doc.str("currency") ?: "USD",
                    cycleStartDay = doc.long("cycleStartDay")?.toInt() ?: 1,
                    savingsGoalMinor = doc.long("savingsGoalMinor") ?: 0,
                    categories = (raw["categories"] as? Map<*, *>).orEmpty()
                        .mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? Number)?.toLong()?.let { key to it } } }
                        .toMap(),
                    cards = (raw["cards"] as? List<*>).orEmpty().mapNotNull { raw ->
                        (raw as? Map<*, *>)?.let { m ->
                            Card(
                                name = (m["name"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                                statementDay = (m["statementDay"] as? Number)?.toInt() ?: 1,
                                dueDay = (m["dueDay"] as? Number)?.toInt() ?: 20,
                            )
                        }
                    },
                    paidBills = (raw["paidBills"] as? List<*>).orEmpty().filterIsInstance<String>().toSet(),
                    incomeMinor = doc.long("incomeMinor") ?: 0,
                    customCategories = (raw["customCategories"] as? List<*>).orEmpty().filterIsInstance<String>(),
                    dreamAllocations = (raw["dreamAllocations"] as? List<*>).orEmpty().mapNotNull { raw ->
                        val m = raw as? Map<*, *> ?: return@mapNotNull null
                        val from = (m["from"] as? LocalDate) ?: return@mapNotNull null
                        DreamAllocation((m["dreamId"] as? String) ?: return@mapNotNull null, (m["monthlyMinor"] as? Number)?.toLong() ?: 0, from)
                    },
                    limitHistory = (raw["limitHistory"] as? List<*>).orEmpty().mapNotNull { raw ->
                        val m = raw as? Map<*, *> ?: return@mapNotNull null
                        val from = (m["from"] as? LocalDate) ?: return@mapNotNull null
                        LimitChange(from, (m["limitMinor"] as? Number)?.toLong() ?: return@mapNotNull null)
                    },
                )
                // Rollover counts from the budget month the family started tracking.
                val started = doc.date("startedOn") ?: doc.date("createdAt") ?: systemToday()
                BudgetDoc(budget, cycleFor(started, budget.cycleStartDay).start)
            }
            // One failed read (offline, a permission race) shouldn't leave the screen stuck.
            .retryWhen { _, attempt -> (attempt < 3).also { if (it) delay(1_000L * (attempt + 1)) } }
            .catch { emit(null) }

    /** Saves the budget; a new monthly amount applies [from] this month, next month or always. */
    suspend fun saveBudget(householdId: String, uid: String, budget: Budget, from: LimitFrom = LimitFrom.THIS_MONTH) {
        val old = observeBudgetDoc(householdId).first()
        val history = limitHistoryAfter(old?.budget, budget.limitMinor, from, old?.startedOn, systemToday(), budget.cycleStartDay)
        val fields = mapOf(
            "limitHistory" to history.map { mapOf("from" to it.from.stamp(), "limitMinor" to it.limitMinor) },
            "limitMinor" to budget.limitMinor,
            "currency" to budget.currency,
            "cycleStartDay" to budget.cycleStartDay,
            "savingsGoalMinor" to budget.savingsGoalMinor,
            "incomeMinor" to budget.incomeMinor,
            "categories" to budget.categories,
            "customCategories" to budget.customCategories,
            "dreamAllocations" to budget.dreamAllocations.map { mapOf("dreamId" to it.dreamId, "monthlyMinor" to it.monthlyMinor, "from" to it.from.stamp()) },
            "cards" to budget.cards.map { mapOf("name" to it.name.trim(), "statementDay" to it.statementDay, "dueDay" to it.dueDay) },
            "updatedAt" to FieldValue.serverTimestamp,
        )
        val ref = budgetDoc(householdId)
        // Rules keep `createdBy` fixed, so only the first save sets it.
        if (ref.get().exists) {
            ref.updateFields(fields)
        } else {
            ref.set(
                fields + mapOf(
                    "createdBy" to uid,
                    "visibleTo" to Visibility.everyone,
                    "createdAt" to FieldValue.serverTimestamp,
                    "startedOn" to cycleFor(systemToday(), budget.cycleStartDay).start.stamp(),
                ),
            )
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
                transactions(householdId, uid, minOf(startedOn, systemToday().minusDays(70)), budget.currency),
                duePayments(householdId, uid, budget.currency),
                transfers(householdId, uid),
                observeCredits(householdId, uid),
                observeDreams(householdId, uid),
            ) { txns, due, moves, credits, dreams -> Inputs(txns, due, moves, credits, dreams) }
            combine(inputs, observeInvestments(householdId, uid), observeTrips(householdId, uid)) { i, investments, trips ->
                val today = systemToday()
                // A trip with a budget is the one place its money is planned: its unspent budget
                // is reserved, and a separate planned spend for it isn't counted again.
                val real = i.txns.filterNot { coveredByTrip(it, trips) }
                val counted = real + tripReserves(real, trips, today)
                val state = envelopes(budget, startedOn, counted, i.moves, i.credits, today)
                val values = investmentValues(investments, i.moves, today)
                MoneyState(
                    budget = budget,
                    startedOn = startedOn,
                    summary = summarize(budget, counted, i.due, today),
                    envelopes = state,
                    credits = i.credits.filter { !it.used },
                    dreams = dreamProgress(i.dreams, state, i.credits, today, values),
                    investments = values,
                    trips = tripSummaries(trips, i.txns, today),
                    txns = real,
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
        visible(householdId, "investments", uid).docs()
            .map { snap ->
                snap.filter { it.bool("closed") != true }.map { doc ->
                    Investment(
                        id = doc.id,
                        title = doc.str("title").orEmpty(),
                        kind = doc.str("kind") ?: "Other",
                        ratePercent = doc.str("rate")?.replace("%", "")?.trim()?.toDoubleOrNull(),
                        maturity = doc.date("maturity"),
                        forDream = doc.str("forDream")?.takeIf { it != "Nothing specific" },
                    )
                }
            }
            .catch { emit(emptyList()) }

    fun observeTrips(householdId: String, uid: String): Flow<List<Trip>> =
        visible(householdId, "trips", uid).docs()
            .map { snap ->
                snap.mapNotNull { d ->
                    Trip(
                        id = d.id,
                        title = d.str("title") ?: return@mapNotNull null,
                        start = d.date("start") ?: return@mapNotNull null,
                        end = d.date("end"),
                        budgetMinor = d.long("budgetMinor"),
                        dream = d.str("forDream")?.takeIf { it != "Not linked" },
                        done = d.bool("done") == true,
                        // The planned stops; a new trip's "Places, in order" line is day one until it's edited.
                        stops = (d.plainData()["stops"] as? List<*>)?.mapNotNull { raw ->
                            val m = raw as? Map<*, *> ?: return@mapNotNull null
                            Stop(
                                (m["day"] as? Number)?.toInt() ?: 1, m["title"] as? String ?: return@mapNotNull null, m["time"] as? String,
                                about = m["about"] as? String, photo = m["photo"] as? String, note = m["note"] as? String,
                            )
                        } ?: stopsFrom(d.str("places")),
                    )
                }
            }
            .catch { emit(emptyList()) }

    /** Marks a trip done (ending it today if it had no end yet); the returned undo reverses it. */
    fun setTripDone(householdId: String, trip: Trip, today: LocalDate = systemToday()): () -> Unit {
        val ref = collection(householdId, "trips").document(trip.id)
        val end = trip.end?.takeIf { !it.isAfter(today) } ?: today
        Writes.later { ref.update("done" to true, "end" to end.stamp()) }
        return { Writes.later { ref.update("done" to false, "end" to (trip.end?.stamp() ?: FieldValue.delete)) } }
    }

    /** Saves the plan's stops (and drops the first "places" line, which they now replace). */
    fun setStops(householdId: String, tripId: String, stops: List<Stop>) {
        Writes.later {
            collection(householdId, "trips").document(tripId).update(
                "stops" to stops.map { mapOf("day" to it.day, "title" to it.title, "time" to it.time, "about" to it.about, "photo" to it.photo, "note" to it.note) },
                "places" to FieldValue.delete,
            )
        }
    }

    fun reopenTrip(householdId: String, id: String) {
        Writes.later { collection(householdId, "trips").document(id).update("done" to false) }
    }

    fun closeInvestment(householdId: String, id: String) {
        Writes.later { collection(householdId, "investments").document(id).update("closed" to true) }
    }

    /** Dreams on the vision board (visible to anyone with Plans access, even without Money). */
    fun observeDreams(householdId: String, uid: String): Flow<List<Dream>> =
        visible(householdId, "dreams", uid).docs()
            .map { snap ->
                snap.map { doc ->
                    Dream(
                        id = doc.id,
                        title = doc.str("title").orEmpty(),
                        kind = doc.str("kind") ?: "Other",
                        emoji = doc.str("emoji") ?: "⭐",
                        targetMinor = doc.long("amountMinor") ?: 0,
                        targetDate = doc.date("targetDate"),
                        achieved = doc.bool("achieved") == true,
                        horizonChoice = doc.str("horizon"),
                        photos = doc.strings("photos"),
                        boardOrder = doc.dbl("boardOrder"),
                    )
                }
            }
            .catch { emit(emptyList()) }

    fun observeCredits(householdId: String, uid: String): Flow<List<Credit>> =
        visible(householdId, "credits", uid).docs()
            .map { snap ->
                snap.map { doc ->
                    Credit(
                        id = doc.id,
                        title = doc.str("title").orEmpty(),
                        kind = doc.str("kind") ?: "Credit",
                        valueMinor = doc.long("amountMinor") ?: 0,
                        expiry = doc.date("expiry"),
                        earmark = doc.str("earmark")?.takeIf { it != "Anything" },
                        used = doc.bool("used") == true,
                    )
                }.sortedBy { it.expiry ?: FAR_FUTURE }
            }
            .catch { emit(emptyList()) }

    /** Moves money between envelopes, the gullak and dreams ("2000 from Gifts to Travel"). */
    fun transfer(householdId: String, uid: String, from: Pocket, to: Pocket, amountMinor: Long, note: String? = null): Saved {
        val ref = collection(householdId, "transfers").document
        Writes.later { ref.set(
            mapOf(
                "from" to from.key,
                "to" to to.key,
                "amountMinor" to amountMinor,
                "note" to note,
                "date" to systemToday().stamp(),
                "createdBy" to uid,
                "visibleTo" to Visibility.everyone,
                "createdAt" to FieldValue.serverTimestamp,
            ),
        ) }
        return Saved(ref.id) { Writes.later { ref.delete() } }
    }

    fun markCreditUsed(householdId: String, creditId: String) {
        Writes.later { collection(householdId, "credits").document(creditId).update("used" to true) }
    }

    fun markDreamAchieved(householdId: String, dreamId: String, achieved: Boolean) {
        Writes.later { collection(householdId, "dreams").document(dreamId).update("achieved" to achieved) }
    }

    private fun transfers(householdId: String, uid: String): Flow<List<Transfer>> =
        visible(householdId, "transfers", uid).docs()
            .map { snap ->
                snap.mapNotNull { doc ->
                    Transfer(
                        id = doc.id,
                        from = doc.str("from") ?: return@mapNotNull null,
                        to = doc.str("to") ?: return@mapNotNull null,
                        amountMinor = doc.long("amountMinor") ?: return@mapNotNull null,
                        date = doc.date("date") ?: systemToday(),
                        note = doc.str("note"),
                    )
                }
            }
            .catch { emit(emptyList()) }

    /** Deletes a ledger entry; the returned undo writes it back as it was. */
    fun deleteTransfer(householdId: String, id: String): () -> Unit {
        val ref = collection(householdId, "transfers").document(id)
        var saved: Map<String, Any?>? = null
        Writes.later { saved = ref.get().plainData().forFirestore(); ref.delete() }
        return { saved?.let { Writes.later { ref.set(it) } } }
    }

    /** Turns a planned spend into a real one dated today. */
    /** How many expenses and subscriptions are filed under [name], all time. */
    suspend fun categoryUse(householdId: String, uid: String, name: String): Int =
        listOf("transactions", "recurring").sumOf { col ->
            visible(householdId, col, uid).get().documents.count { it.str("category") == name }
        }

    /**
     * Files everything under [from] as [to] instead (a rename, or a merge into an existing one),
     * or with [to] null removes the category. Returns how many entries moved.
     */
    suspend fun moveCategory(householdId: String, uid: String, from: String, to: String?): Int {
        var moved = 0
        if (to != null) {
            for (col in listOf("transactions", "recurring")) {
                visible(householdId, col, uid).get().documents.filter { it.str("category") == from }.chunked(400).forEach { chunk ->
                    val batch = store.batch()
                    chunk.forEach { batch.update(it.reference, "category" to to) }
                    batch.commit()
                    moved += chunk.size
                }
            }
        }
        val doc = budgetDoc(householdId).get()
        if (doc.exists) {
            val budget = observeBudget(householdId).first() ?: return moved
            val next = budgetWithCategory(budget, from, to)
            budgetDoc(householdId).update("categories" to next.categories, "customCategories" to next.customCategories)
        }
        return moved
    }

    /** Deletes the expenses filed under [name] and the category; returns an undo that puts them back. */
    suspend fun deleteCategoryWithHistory(householdId: String, uid: String, name: String): () -> Unit {
        val gone = listOf("transactions", "recurring").flatMap { col ->
            visible(householdId, col, uid).get().documents.filter { it.str("category") == name }
        }
        val saved = gone.map { it.reference to it.plainData().forFirestore() }
        saved.chunked(400).forEach { chunk -> store.batch().also { b -> chunk.forEach { (ref, _) -> b.delete(ref) } }.commit() }
        moveCategory(householdId, uid, name, null)
        return { Writes.later { saved.chunked(400).forEach { chunk -> store.batch().also { b -> chunk.forEach { (ref, data) -> b.set(ref, data) } }.commit() } } }
    }

    /** Saves where dreams were dragged to on the vision board, for the whole family. */
    fun setBoardOrder(householdId: String, orders: Map<String, Double>) {
        if (orders.isEmpty()) return
        val batch = store.batch()
        orders.forEach { (id, pos) -> batch.update(collection(householdId, "dreams").document(id), "boardOrder" to pos) }
        Writes.later { batch.commit() }
    }

    /** A category typed in a form ("Cook") joins the family's own, so it's offered next time. */
    fun addCustomCategory(householdId: String, name: String) {
        Writes.later { budgetDoc(householdId).update("customCategories" to FieldValue.arrayUnion(name.trim())) }
    }

    /** Marks a card bill paid (or not), remembered on the budget. */
    fun setBillPaid(householdId: String, key: String, paid: Boolean) {
        Writes.later { budgetDoc(householdId).update("paidBills" to if (paid) FieldValue.arrayUnion(key) else FieldValue.arrayRemove(key)) }
    }

    /** A subscription's logged payments, newest first: (expense id, date, amount, currency). */
    suspend fun pastRenewals(householdId: String, uid: String, subscriptionId: String): List<PastPayment> =
        runCatching { visible(householdId, "transactions", uid).get().documents }.getOrDefault(emptyList())
            .filter { it.str("fromSubscription") == subscriptionId }
            .mapNotNull { d -> d.date("date")?.let { PastPayment(d.id, it, d.long("amountMinor") ?: 0, d.str("currency") ?: "") } }
            .sortedByDescending { it.date }

    /**
     * A subscription's price changed in its form. [choice] says which renewals it's for: from the
     * next one, from [date], or every one (a mistake fixed, which also corrects logged payments).
     * Earlier renewals otherwise keep what they cost, through the price history.
     */
    suspend fun changeSubscriptionPrice(householdId: String, uid: String, id: String, oldMinor: Long, newMinor: Long, choice: PriceFrom, date: LocalDate?) {
        val ref = collection(householdId, "recurring").document(id)
        val doc = runCatching { ref.get() }.getOrNull() ?: return
        val from = when (choice) {
            PriceFrom.ALWAYS -> null
            PriceFrom.DATE -> date ?: systemToday()
            PriceFrom.NEXT -> doc.date("nextDate")?.let { nextRenewal(it, doc.str("frequency"), doc.date("lastLogged"), systemToday()) } ?: systemToday()
        }
        val history = changePrice(decodePrices(doc.plainData()["priceHistory"]), oldMinor, newMinor, from)
        ref.update("priceHistory" to if (history.isEmpty()) FieldValue.delete else encodePrices(history))
        if (choice == PriceFrom.ALWAYS) {
            pastRenewals(householdId, uid, id).forEach { collection(householdId, "transactions").document(it.id).update("amountMinor" to newMinor) }
        }
    }

    /**
     * Logs a subscription's renewal on [date] as an expense and remembers it, so it isn't counted
     * twice. [exclusive] (automatic logging, maybe on several phones) uses a transaction and does
     * nothing if someone already logged it; a tap writes straight away, offline too.
     * Returns an undo, or null if there was nothing to log.
     */
    suspend fun logRenewal(householdId: String, uid: String, id: String, date: LocalDate, exclusive: Boolean = false): (() -> Unit)? {
        val sub = collection(householdId, "recurring").document(id)
        val txn = collection(householdId, "transactions").document
        fun expenseFrom(doc: DocumentSnapshot) = mapOf(
            "type" to "EXPENSE",
            "title" to (doc.str("title") ?: "Subscription"),
            "merchant" to (doc.str("title") ?: "Subscription"),
            // What that renewal cost: a price changed from a later date doesn't touch earlier ones.
            "amountMinor" to priceOn(date, doc.long("amountMinor") ?: 0L, decodePrices(doc.plainData()["priceHistory"])),
            "currency" to doc.str("currency"),
            "category" to (doc.str("category") ?: "Bills"),
            "paymentMethod" to doc.str("paymentMethod"),
            "date" to date.stamp(),
            "status" to "Paid",
            "fromSubscription" to id,
            "createdBy" to uid,
            "visibleTo" to doc.strings("visibleTo").ifEmpty { Visibility.everyone },
            "createdAt" to FieldValue.serverTimestamp,
            "source" to "subscription",
        ).filterValues { it != null }
        val before: LocalDate?
        if (exclusive) {
            var previous: LocalDate? = null
            val logged = runCatching {
                store.runTransaction {
                    val doc = get(sub)
                    previous = doc.date("lastLogged")
                    if (!doc.exists || previous?.let { !date.isAfter(it) } == true) return@runTransaction false
                    set(txn, expenseFrom(doc))
                    update(sub, "lastLogged" to date.stamp())
                    true
                }
            }.getOrDefault(false)
            if (!logged) return null
            before = previous
        } else {
            val doc = runCatching { sub.get() }.getOrNull()?.takeIf { it.exists } ?: return null
            before = doc.date("lastLogged")
            store.batch().also { it.set(txn, expenseFrom(doc)); it.update(sub, "lastLogged" to date.stamp()) }.let { b -> Writes.later { b.commit() } }
        }
        return {
            Writes.later {
                txn.delete()
                sub.update("lastLogged" to (before?.stamp() ?: FieldValue.delete))
            }
        }
    }

    fun markPaid(householdId: String, txnId: String) {
        Writes.later { collection(householdId, "transactions").document(txnId).update("status" to "Paid", "date" to Timestamp.now()) }
    }

    /**
     * Only what [uid] spent, newest first: "My spends" for members without Money. Asked by
     * creator, which the rules allow without seeing anyone else's spending.
     */
    fun observeMine(householdId: String, uid: String): Flow<List<Txn>> =
        collection(householdId, "transactions").where { "createdBy" equalTo uid }.docs()
            .map { snap ->
                snap.mapNotNull { doc ->
                    Txn(
                        id = doc.id,
                        title = doc.str("title") ?: doc.str("merchant") ?: doc.str("category") ?: "Expense",
                        category = doc.str("category") ?: "Other",
                        amountMinor = doc.long("amountMinor") ?: return@mapNotNull null,
                        date = doc.date("date") ?: return@mapNotNull null,
                        planned = doc.str("status") == "Planned",
                    )
                }.sortedByDescending { it.date }
            }
            .catch { emit(emptyList()) }

    private fun transactions(householdId: String, uid: String, since: LocalDate, currency: String): Flow<List<Txn>> =
        visible(householdId, "transactions", uid)
            .where { "date" greaterThanOrEqualTo since.stamp() }
            .docs()
            .map { snap ->
                snap
                    .filter { it.str("currency") == currency }
                    .mapNotNull { doc ->
                        Txn(
                            id = doc.id,
                            title = doc.str("title") ?: doc.str("merchant") ?: doc.str("category") ?: "Expense",
                            category = doc.str("category") ?: "Other",
                            amountMinor = doc.long("amountMinor") ?: return@mapNotNull null,
                            date = doc.date("date") ?: return@mapNotNull null,
                            planned = doc.str("status") == "Planned",
                            subcategory = doc.str("subcategory"),
                            trip = doc.str("trip"),
                            paidWith = doc.str("paymentMethod"),
                            card = doc.str("card"),
                            paidBy = doc.str("paidBy"),
                        )
                    }
            }
            .catch { emit(emptyList()) }

    private fun duePayments(householdId: String, uid: String, currency: String): Flow<List<DuePayment>> =
        visible(householdId, "recurring", uid).docs()
            .map { snap ->
                val today = systemToday()
                snap
                    .filter { it.str("currency") == currency }
                    .mapNotNull { doc ->
                        val next = doc.date("nextDate") ?: return@mapNotNull null
                        // Once logged, a renewal is spending; until then it's still due (even if the day has passed).
                        val owed = nextRenewal(next, doc.str("frequency"), doc.date("lastLogged"), today)
                        DuePayment(doc.id, doc.str("title") ?: "Payment", owed, priceOn(owed, doc.long("amountMinor") ?: 0, decodePrices(doc.plainData()["priceHistory"])))
                    }
            }
            .catch { emit(emptyList()) }
}

