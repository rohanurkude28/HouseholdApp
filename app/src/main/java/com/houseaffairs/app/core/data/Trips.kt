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

/** One place on a trip day, in the order it's visited. [day] counts from 1 (the start date). */
data class Stop(val day: Int, val title: String, val time: String? = null)

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
fun routeUrl(stops: List<Stop>): String? {
    if (stops.isEmpty()) return null
    fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
    if (stops.size == 1) return "https://www.google.com/maps/search/?api=1&query=" + enc(stops[0].title)
    val via = stops.drop(1).dropLast(1).joinToString("|") { it.title }
    return "https://www.google.com/maps/dir/?api=1&origin=" + enc(stops.first().title) + "&destination=" + enc(stops.last().title) +
        (if (via.isNotEmpty()) "&waypoints=" + enc(via) else "")
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
