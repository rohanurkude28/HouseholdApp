package com.houseaffairs.app.core.data

/**
 * Daily nutrition from what the family cooked: each meal someone ate counts one serving of the
 * dish's macros (the recipe's own, or the library's rough values). Pure, so it's tested.
 */
data class Macros(val protein: Double = 0.0, val carbs: Double = 0.0, val fat: Double = 0.0, val fibre: Double = 0.0) {
    val kcal: Int get() = (protein * 4 + carbs * 4 + fat * 9).toInt()
    operator fun plus(o: Macros) = Macros(protein + o.protein, carbs + o.carbs, fat + o.fat, fibre + o.fibre)
}

/** Daily targets in grams; the defaults suit most adults and can be changed in the live-well plan. */
data class MacroTargets(val protein: Int = 50, val carbs: Int = 250, val fat: Int = 65, val fibre: Int = 30)

/** "25g · 30g · 12g · 4g" (protein · carbs · fat · fibre), or any four numbers in that order. */
fun parseMacros(text: String?): Macros? {
    val n = Regex("\\d+(?:\\.\\d+)?").findAll(text ?: return null).map { it.value.toDouble() }.toList()
    return if (n.size >= 4) Macros(n[0], n[1], n[2], n[3]) else null
}

/** One serving of [title]: the family's recipe if it has macros, else the library's. */
fun macrosFor(title: String, saved: List<Recipe>): Macros? {
    val key = title.trim().lowercase()
    return (saved.firstOrNull { it.title.lowercase() == key }?.macros ?: RECIPE_LIBRARY.firstOrNull { it.title.lowercase() == key }?.macros)
        .let(::parseMacros)
}

/** What [meals] (titles eaten today, one serving each) add up to; null when none have macros. */
fun dayMacros(meals: List<String>, saved: List<Recipe>): Macros? =
    meals.mapNotNull { macrosFor(it, saved) }.takeIf { it.isNotEmpty() }?.reduce(Macros::plus)
