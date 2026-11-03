package com.houseaffairs.app.core.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

data class Ingredient(val name: String, val amount: Double?, val unit: String)

data class PantryStock(
    val id: String,
    val title: String,
    val amount: Double?,
    val unit: String,
    /** Quantity when bought, so "how much is left" can be a percentage. */
    val initialAmount: Double?,
    val expiry: LocalDate? = null,
    /** Pantry type, supply type, or what a medicine is used for; null when not set. */
    val category: String? = null,
    /** Where it's kept in the house ("spice box", "medicine drawer"), if said. */
    val keptAt: String? = null,
)

private enum class Dimension { MASS, VOLUME, COUNT }

private data class UnitInfo(val dimension: Dimension, val toBase: Double)

private val UNITS = mapOf(
    "mg" to UnitInfo(Dimension.MASS, 0.001), "g" to UnitInfo(Dimension.MASS, 1.0), "gm" to UnitInfo(Dimension.MASS, 1.0),
    "kg" to UnitInfo(Dimension.MASS, 1000.0),
    "ml" to UnitInfo(Dimension.VOLUME, 1.0), "l" to UnitInfo(Dimension.VOLUME, 1000.0),
    "tsp" to UnitInfo(Dimension.VOLUME, 5.0), "tbsp" to UnitInfo(Dimension.VOLUME, 15.0), "cup" to UnitInfo(Dimension.VOLUME, 240.0),
    "pcs" to UnitInfo(Dimension.COUNT, 1.0), "pack" to UnitInfo(Dimension.COUNT, 1.0), "dozen" to UnitInfo(Dimension.COUNT, 12.0),
    "tablets" to UnitInfo(Dimension.COUNT, 1.0), "strips" to UnitInfo(Dimension.COUNT, 1.0), "bottles" to UnitInfo(Dimension.COUNT, 1.0),
)

/** Converts [amount] from one unit to another of the same kind (g↔kg, ml↔L, tbsp→ml); null if they don't mix. */
fun convert(amount: Double, from: String, to: String): Double? {
    val a = UNITS[from.lowercase()] ?: return if (from.equals(to, ignoreCase = true)) amount else null
    val b = UNITS[to.lowercase()] ?: return null
    if (a.dimension != b.dimension) return null
    return amount * a.toBase / b.toBase
}

private fun normalize(name: String) = name.lowercase().trim().removeSuffix("es").removeSuffix("s")

/** "Paneer" matches "Malai paneer"; "tomatoes" matches "Tomato". */
fun sameIngredient(a: String, b: String): Boolean {
    val x = normalize(a)
    val y = normalize(b)
    return x.isNotEmpty() && y.isNotEmpty() && (x == y || x.contains(y) || y.contains(x))
}

data class Deduction(
    val stock: PantryStock,
    val need: Double?,
    /** New amount in the stock's own unit; null when quantities couldn't be compared. */
    val after: Double?,
) {
    val usedUp: Boolean get() = after != null && after <= 0.0
    /** Percentage left of the original purchase. */
    val level: Int? get() = after?.let { left -> stock.initialAmount?.takeIf { it > 0 }?.let { (left / it * 100).roundToInt().coerceIn(0, 100) } }
    val runsLow: Boolean get() = !usedUp && (level ?: 100) <= 25
}

data class CookPlan(val deductions: List<Deduction>, val missing: List<Ingredient>) {
    /** Items that should go on the shopping list after cooking. */
    val toBuy: List<String> get() = missing.map { it.name } + deductions.filter { it.usedUp || it.runsLow }.map { it.stock.title }
}

/** What cooking a recipe ([scale] × its quantities) takes out of the pantry. */
fun planCooking(ingredients: List<Ingredient>, scale: Double, pantry: List<PantryStock>): CookPlan {
    val deductions = mutableListOf<Deduction>()
    val missing = mutableListOf<Ingredient>()
    ingredients.filter { it.name.isNotBlank() }.forEach { ingredient ->
        val stock = pantry
            .filter { sameIngredient(it.title, ingredient.name) && (it.amount ?: 1.0) > 0 }
            // Use what expires first.
            .minByOrNull { it.expiry ?: LocalDate.MAX }
        if (stock == null) {
            missing += ingredient
            return@forEach
        }
        val need = ingredient.amount?.times(scale)?.let { amount -> convert(amount, ingredient.unit, stock.unit) }
        val after = if (need != null && stock.amount != null) (stock.amount - need).coerceAtLeast(0.0) else null
        deductions += Deduction(stock, need, after)
    }
    return CookPlan(deductions, missing)
}

data class RecipeFit(val have: Int, val total: Int, val usesExpiring: List<String>) {
    val canCook: Boolean get() = total > 0 && have == total
}

/** How well the pantry covers a recipe, and which soon-to-expire items it would use up. */
fun recipeFit(ingredients: List<Ingredient>, pantry: List<PantryStock>, today: LocalDate): RecipeFit {
    val named = ingredients.filter { it.name.isNotBlank() }
    val plan = planCooking(named, 1.0, pantry)
    val enough = plan.deductions.count { d -> d.need == null || d.stock.amount == null || d.stock.amount >= d.need }
    val expiring = plan.deductions
        .filter { d -> d.stock.expiry?.let { ChronoUnit.DAYS.between(today, it) in 0..3 } == true }
        .map { it.stock.title }
    return RecipeFit(enough, named.size, expiring)
}
