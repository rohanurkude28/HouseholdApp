package com.houseaffairs.app.feature.home

import com.houseaffairs.app.core.data.HomeData
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A gentle push on Home: something to tidy up, or something worth adding. [route] opens where. */
data class HomeNudge(val emoji: String, val text: String, val action: String, val route: String, val tidy: Boolean)

/**
 * What to tidy and what to add, a couple at a time: expired things, long-overdue tasks, a
 * shopping list grown stale; then empty corners of the app worth filling. Cleaning up comes
 * first. Pure, so it's tested.
 */
fun homeNudges(
    data: HomeData,
    today: LocalDate,
    canMoney: Boolean,
    canStock: Boolean,
    canMedicine: Boolean,
    canPlans: Boolean,
    hasBudget: Boolean,
    limit: Int = 3,
): List<HomeNudge> = buildList {
    val expired = (if (canStock) data.pantry else emptyList()) + (if (canMedicine) data.medicines else emptyList())
    val gone = expired.filter { s -> s.expiry?.isBefore(today) == true }
    if (gone.isNotEmpty()) add(HomeNudge("🗑️", if (gone.size == 1) "${gone.first().title} has expired" else "${gone.size} things have expired", "Review", "stock/PANTRY", tidy = true))
    if (canPlans) {
        val late = data.tasks.filter { t -> t.dueDate?.let { ChronoUnit.DAYS.between(it, today) > 7 } == true }
        if (late.isNotEmpty()) add(HomeNudge("🧹", "${late.size} task${if (late.size == 1) " is" else "s are"} over a week late. Still needed?", "Clear up", "plans/TASKS", tidy = true))
    }
    if (canStock && data.shopping.size >= 15) add(HomeNudge("🛒", "${data.shopping.size} things on the shopping list. Still need them all?", "Tidy", "stock/SHOPPING", tidy = true))
    if (canMedicine) {
        val soon = data.medicines.filter { s -> s.expiry?.let { !it.isBefore(today) && !it.isAfter(today.plusDays(30)) } == true }
        if (soon.isNotEmpty()) add(HomeNudge("💊", "${soon.size} medicine${if (soon.size == 1) "" else "s"} expire${if (soon.size == 1) "s" else ""} this month", "See", "vault/MEDICINES", tidy = true))
    }
    // Then what's worth adding: the empty corners.
    if (canMoney && !hasBudget) add(HomeNudge("💰", "Set a monthly budget to see what's left each day", "Set it", "money", tidy = false))
    if (canStock && data.pantry.isEmpty()) add(HomeNudge("🥫", "Add what's in your kitchen, so cooking and shopping can help", "Add", "stock/PANTRY", tidy = false))
    if (canPlans && data.events.isEmpty()) add(HomeNudge("🎂", "Add the family's birthdays, so none is missed", "Add", "plans/EVENTS", tidy = false))
    if (canMedicine && data.medicines.isEmpty()) add(HomeNudge("💊", "Note the medicines at home and when they expire", "Add", "vault/MEDICINES", tidy = false))
}.take(limit)
