package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.time.LocalDate
import com.houseaffairs.app.core.time.isBefore
import com.houseaffairs.app.core.time.toEpochDay

/** What the budget needs to know about a trip; both phones read trips into this. */
data class TripMoney(val id: String, val title: String, val start: LocalDate, val end: LocalDate?, val budgetMinor: Long?, val done: Boolean)

/** The id prefix of the line a trip's unspent budget makes in Money's planned list. */
const val TRIP_RESERVE = "trip:"

/**
 * A planned spend the trip already stands for: filed under it, or a Travel plan named after it
 * ("Goa trip" for the Goa trip). The trip's budget reserves the money, so counting this as well
 * would take the same money out of the month twice.
 */
fun coveredByTrip(txn: Txn, trips: List<TripMoney>): Boolean =
    txn.planned && trips.any { t ->
        t.budgetMinor != null && (txn.trip == t.title || (txn.category == "Travel" && txn.title.contains(t.title, ignoreCase = true)))
    }

/**
 * What each trip still holds back from the budget: its budget less what's been spent on it,
 * dated the day it starts (or today, once it has). Trips that are done, past or have no budget
 * hold nothing. Pure, so it's tested.
 */
fun tripReserves(txns: List<Txn>, trips: List<TripMoney>, today: LocalDate): List<Txn> = trips.mapNotNull { t ->
    val budget = t.budgetMinor ?: return@mapNotNull null
    if (t.done || (t.end ?: t.start).isBefore(today)) return@mapNotNull null
    val left = budget - txns.filter { !it.planned && it.trip == t.title }.sumOf { it.amountMinor }
    if (left <= 0) return@mapNotNull null
    Txn(TRIP_RESERVE + t.id, "${t.title} trip", "Travel", left, if (t.start.toEpochDay() > today.toEpochDay()) t.start else today, planned = true, trip = t.title)
}
