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

/** Rough grams per piece, so "200 g onion" can come off "3 onions" and the other way round. */
private val PIECE_GRAMS = mapOf(
    "onion" to 150.0, "tomato" to 100.0, "potato" to 150.0, "egg" to 50.0, "lemon" to 50.0, "lime" to 40.0,
    "garlic" to 5.0, "green chilli" to 5.0, "chilli" to 5.0, "carrot" to 80.0, "capsicum" to 150.0, "cucumber" to 200.0,
    "banana" to 120.0, "apple" to 180.0, "orange" to 150.0, "brinjal" to 200.0, "beetroot" to 150.0, "radish" to 150.0,
    "coriander" to 100.0, "mint" to 50.0, "spinach" to 250.0, "methi" to 150.0, "ginger" to 30.0, "drumstick" to 60.0,
    "coconut" to 400.0, "bread" to 400.0, "paneer block" to 200.0,
)

private val LIQUIDS = listOf("milk", "oil", "juice", "buttermilk", "chaas", "water", "vinegar", "soy sauce", "syrup", "lassi", "cream", "coconut milk")
private val COUNTED = listOf("egg", "bread", "banana", "lemon", "lime", "coconut", "papad", "bun", "pav", "tortilla", "roti", "avocado", "kiwi")

/** How an item is usually measured: "ml" for liquids, "pcs" for things you count, "g" for everything else. */
fun naturalUnit(title: String): String {
    val t = title.lowercase()
    fun has(words: List<String>) = words.any { Regex("\\b${Regex.escape(it)}(?:s|es)?\\b").containsMatchIn(t) }
    return when {
        has(LIQUIDS) -> "ml"
        has(COUNTED) -> "pcs"
        else -> "g"
    }
}

/** Grams in one piece of [name], when known. */
fun pieceGrams(name: String): Double? {
    val n = normalize(name)
    return PIECE_GRAMS.entries.firstOrNull { (k, _) -> n == k || n.contains(k) }?.value
}

/**
 * Like [convert], but also between pieces and weight for things with a typical piece weight:
 * 200 g of onion is about 1.3 onions. Null when they still don't mix.
 */
fun convertFor(name: String, amount: Double, from: String, to: String): Double? {
    convert(amount, from, to)?.let { return it }
    val grams = pieceGrams(name) ?: return null
    val a = UNITS[from.lowercase()] ?: return null
    val b = UNITS[to.lowercase()] ?: return null
    return when {
        a.dimension == Dimension.COUNT && b.dimension == Dimension.MASS -> amount * a.toBase * grams / b.toBase
        a.dimension == Dimension.MASS && b.dimension == Dimension.COUNT -> amount * a.toBase / grams / b.toBase
        else -> null
    }
}

private fun normalize(name: String) = name.lowercase().trim().removeSuffix("es").removeSuffix("s")

/**
 * A saved recipe that [title] with [ingredients] would repeat: the same dish name and the same
 * ingredients, whatever the quantities or order. A different take on a dish (other ingredients)
 * is its own recipe. [exceptId] is the recipe being edited.
 */
fun duplicateRecipe(title: String, ingredients: List<String>, saved: List<Recipe>, exceptId: String? = null): Recipe? {
    fun dish(t: String) = normalize(t.replace(Regex("\\s+"), " "))
    fun names(list: List<String>) = list.map(::normalize).filter { it.isNotEmpty() }.toSet()
    val mine = names(ingredients)
    return saved.firstOrNull { r -> r.id != exceptId && dish(r.title) == dish(title) && names(r.ingredients.map { it.name }) == mine }
}

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
        // Across units too: "200 g onion" against "3 pcs" in the pantry.
        val need = ingredient.amount?.times(scale)?.let { amount -> convertFor(ingredient.name, amount, ingredient.unit, stock.unit) }
        val after = if (need != null && stock.amount != null) (stock.amount - need).coerceAtLeast(0.0) else null
        deductions += Deduction(stock, need, after)
    }
    return CookPlan(deductions, missing)
}

data class RecipeFit(
    val have: Int,
    val total: Int,
    val usesExpiring: List<String>,
    /** Ingredients not in the pantry, or not enough of them. */
    val missing: List<String> = emptyList(),
    /** The ones in [missing] that are in the pantry, just not enough. */
    val short: List<String> = emptyList(),
) {
    val canCook: Boolean get() = total > 0 && have == total

    /** "You have everything", or "Missing: peas, cream" (never "2 of 4": that isn't a dish you can make). */
    val summary: String get() = when {
        total == 0 -> "Add ingredients to check the pantry"
        canCook -> "You have everything"
        else -> {
            val absent = missing - short.toSet()
            listOfNotNull(
                absent.takeIf { it.isNotEmpty() }?.let { "Missing: " + it.take(3).joinToString().lowercase() + if (it.size > 3) " +${it.size - 3}" else "" },
                short.takeIf { it.isNotEmpty() }?.let { "need more " + it.joinToString().lowercase() },
            ).joinToString(" · ").replaceFirstChar { it.uppercaseChar() }
        }
    }
}

/** One ingredient of a dish against the pantry: have it (enough), or not. */
data class IngredientCheck(val name: String, val need: String?, val have: Boolean, val inPantry: String?)

/** Every ingredient of a dish with ✓ or ✗, for the dish's detail. Pure, so it's tested. */
fun checkIngredients(ingredients: List<Ingredient>, pantry: List<PantryStock>): List<IngredientCheck> =
    ingredients.filter { it.name.isNotBlank() }.map { ing ->
        val plan = planCooking(listOf(ing), 1.0, pantry)
        val d = plan.deductions.firstOrNull()
        val enough = d != null && (d.need == null || d.stock.amount == null || d.stock.amount >= d.need)
        val needText = ing.amount?.let { a -> (if (a % 1.0 == 0.0) a.toLong().toString() else a.toString()) + " " + ing.unit }
        val haveText = d?.stock?.let { s -> s.amount?.let { a -> (if (a % 1.0 == 0.0) a.toLong().toString() else "%.1f".format(a)) + " " + s.unit } ?: "some" }
        IngredientCheck(ing.name, needText, enough, haveText)
    }

/** How well the pantry covers a recipe, and which soon-to-expire items it would use up. */
fun recipeFit(ingredients: List<Ingredient>, pantry: List<PantryStock>, today: LocalDate): RecipeFit {
    val named = ingredients.filter { it.name.isNotBlank() }
    val plan = planCooking(named, 1.0, pantry)
    val enough = plan.deductions.count { d -> d.need == null || d.stock.amount == null || d.stock.amount >= d.need }
    val expiring = plan.deductions
        .filter { d -> d.stock.expiry?.let { ChronoUnit.DAYS.between(today, it) in 0..3 } == true }
        .map { it.stock.title }
    val short = plan.deductions.filter { d -> !(d.need == null || d.stock.amount == null || d.stock.amount >= d.need) }
    val missingNames = plan.missing.map { it.name } + short.map { d -> named.firstOrNull { sameIngredient(d.stock.title, it.name) }?.name ?: d.stock.title }
    val shortNames = short.map { d -> named.firstOrNull { sameIngredient(d.stock.title, it.name) }?.name ?: d.stock.title }
    return RecipeFit(enough, named.size, expiring, missingNames, shortNames)
}
