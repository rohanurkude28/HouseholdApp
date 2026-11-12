package com.houseaffairs.app.core.data

import java.time.LocalDate

/** Sub-categories offered under each expense category (custom ones are allowed too). */
val SUBCATEGORIES: Map<String, List<String>> = linkedMapOf(
    "Dining" to listOf("Breakfast", "Lunch", "Dinner", "Snacks", "Coffee", "Drinks"),
    "Groceries" to listOf("Vegetables", "Fruits", "Dairy", "Staples", "Snacks", "Household"),
    "Transport" to listOf("Cab", "Auto", "Fuel", "Parking", "Toll", "Metro", "Train", "Flight"),
    "Shopping" to listOf("Clothes", "Shoes", "Accessories", "Electronics", "Gifts", "Souvenirs"),
    "Travel" to listOf("Hotel", "Flights", "Activities", "Tickets", "Visa"),
    "Entertainment" to listOf("Movies", "Events", "Games", "Streaming"),
    "Bills" to listOf("Electricity", "Water", "Gas", "Internet", "Mobile"),
    "Health" to listOf("Medicines", "Doctor", "Tests", "Gym"),
    "Home" to listOf("Repairs", "Furniture", "Cleaning", "Help"),
    "Gifts" to listOf("Birthday", "Wedding", "Festival"),
)

data class Trip(
    val id: String,
    val title: String,
    val start: LocalDate,
    val end: LocalDate?,
    val budgetMinor: Long?,
    /** The vision-board dream this trip is part of, by title. */
    val dream: String? = null,
    /** Marked done: it moves to past trips and new expenses stop joining it. */
    val done: Boolean = false,
    /** The plan: places to go, by day, in order. */
    val stops: List<Stop> = emptyList(),
)

/**
 * One place on a trip day, in the order it's visited. [day] counts from 1 (the start date).
 * [about] and [photo] come from looking the place up, so nobody has to Google it again;
 * [note] is what to do there, in the family's words.
 */
data class Stop(val day: Int, val title: String, val time: String? = null, val about: String? = null, val photo: String? = null, val note: String? = null)

/** How many days the plan has: one for an outing, start to end for a trip (up to two weeks). */
fun Trip.dayCount(): Int = ((end ?: start).toEpochDay() - start.toEpochDay() + 1).toInt().coerceIn(1, 14)

/** "Cubbon Park, Lalbagh and MTR" → stops on [day], in that order. */
fun stopsFrom(text: String?, day: Int = 1): List<Stop> =
    text.orEmpty().split(Regex(",|\\band\\b|→|->|\\bthen\\b", RegexOption.IGNORE_CASE)).map { it.trim().trim('.') }.filter { it.isNotEmpty() }.map { Stop(day, it) }

/** Moves the stop at [index] (in [stops]) one place earlier or later within its day. */
fun moveStop(stops: List<Stop>, index: Int, later: Boolean): List<Stop> {
    val day = stops.getOrNull(index)?.day ?: return stops
    val sameDay = stops.indices.filter { stops[it].day == day }
    val pos = sameDay.indexOf(index)
    val other = sameDay.getOrNull(if (later) pos + 1 else pos - 1) ?: return stops
    return stops.toMutableList().also { val t = it[index]; it[index] = it[other]; it[other] = t }
}

/**
 * Google Maps directions through [stops] in order: from the first, via the middle ones, to the
 * last. One stop just finds it on the map. Null with none.
 */
fun routeUrl(stops: List<Stop>, area: String? = null): String? {
    if (stops.isEmpty()) return null
    fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
    // "Baga Beach, Goa": the trip's name narrows the search to the right place.
    fun q(stop: Stop) = if (area.isNullOrBlank() || stop.title.contains(area, ignoreCase = true)) stop.title else "${stop.title}, $area"
    if (stops.size == 1) return "https://www.google.com/maps/search/?api=1&query=" + enc(q(stops[0]))
    val via = stops.drop(1).dropLast(1).joinToString("|") { q(it) }
    return "https://www.google.com/maps/dir/?api=1&origin=" + enc(q(stops.first())) + "&destination=" + enc(q(stops.last())) +
        (if (via.isNotEmpty()) "&waypoints=" + enc(via) else "")
}

/** The kinds of trip spending offered as one-tap buttons inside a trip: emoji, label, category and type. */
data class TripSpend(val emoji: String, val label: String, val category: String, val subcategory: String?)

val TRIP_SPENDS = listOf(
    TripSpend("🎟️", "Tickets", "Travel", "Tickets"),
    TripSpend("🏨", "Stay", "Travel", "Hotel"),
    TripSpend("🚕", "Getting around", "Transport", "Cab"),
    TripSpend("🍽️", "Food", "Dining", null),
    TripSpend("☕", "Cafés", "Dining", "Coffee"),
    TripSpend("🛍️", "Shopping", "Shopping", null),
    TripSpend("🎡", "Activities", "Travel", "Activities"),
    TripSpend("➕", "Other", "Other", null),
)

enum class BudgetLevel { FINE, NEAR, OVER }

/** Where a trip stands against its budget: how full (0..1, past 1 when over), how worried to be, and a line. */
data class TripBudget(val share: Float, val level: BudgetLevel, val line: String)

/** From 80% spent a trip is "nearing" its budget; going over is allowed and shown, never blocked. */
fun tripBudget(spentMinor: Long, budgetMinor: Long?, money: (Long) -> String): TripBudget? {
    val budget = budgetMinor?.takeIf { it > 0 } ?: return null
    val share = spentMinor.toFloat() / budget
    val left = budget - spentMinor
    return when {
        left < 0 -> TripBudget(share, BudgetLevel.OVER, "${money(-left)} over the ${money(budget)} budget")
        share >= 0.8f -> TripBudget(share, BudgetLevel.NEAR, "Nearing the budget · ${money(left)} left of ${money(budget)}")
        else -> TripBudget(share, BudgetLevel.FINE, "${money(left)} left of ${money(budget)}")
    }
}

/** Said when an expense is saved into a trip, so the family knows where the trip stands. */
fun tripSavedNote(trip: String, spentMinor: Long, budgetMinor: Long?, money: (Long) -> String): String {
    val b = tripBudget(spentMinor, budgetMinor, money) ?: return "$trip so far: ${money(spentMinor)}"
    return when (b.level) {
        BudgetLevel.OVER -> "$trip is ${money(spentMinor - budgetMinor!!)} over budget"
        BudgetLevel.NEAR -> "$trip is nearing its budget: ${money(budgetMinor!! - spentMinor)} left"
        BudgetLevel.FINE -> "${money(budgetMinor!! - spentMinor)} left of $trip's budget"
    }
}


fun Trip.isOngoing(today: LocalDate) = !done && !today.isBefore(start) && !today.isAfter(end ?: start)

/** Still worth showing up front: not marked done, and not long over. */
fun Trip.isCurrent(today: LocalDate) = !done && !(end ?: start).isBefore(today.minusDays(14))

data class TripCategory(val name: String, val totalMinor: Long, val subcategories: List<Pair<String, Long>>)

data class TripSummary(
    val trip: Trip,
    val totalMinor: Long,
    val categories: List<TripCategory>,
    val count: Int,
) {
    val leftMinor: Long? get() = trip.budgetMinor?.let { it - totalMinor }
}

/**
 * Clubs a trip's expenses (those tagged with its name) by category and sub-category, biggest
 * first. Untagged sub-categories show as "Other". Pure, so it's tested.
 */
fun tripSummaries(trips: List<Trip>, txns: List<Txn>, today: LocalDate): List<TripSummary> =
    trips.map { trip ->
        val mine = txns.filter { !it.planned && it.trip.equals(trip.title, ignoreCase = true) }
        TripSummary(
            trip = trip,
            totalMinor = mine.sumOf { it.amountMinor },
            count = mine.size,
            categories = mine.groupBy { it.category }.map { (cat, list) ->
                TripCategory(
                    name = cat,
                    totalMinor = list.sumOf { it.amountMinor },
                    subcategories = list.groupBy { it.subcategory ?: "Other" }
                        .map { (sub, l) -> sub to l.sumOf { it.amountMinor } }
                        .sortedByDescending { it.second },
                )
            }.sortedByDescending { it.totalMinor },
        )
    }.sortedWith(compareByDescending<TripSummary> { it.trip.isOngoing(today) }.thenByDescending { it.trip.start })
