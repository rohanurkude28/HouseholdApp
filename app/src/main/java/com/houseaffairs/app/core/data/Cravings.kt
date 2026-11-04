package com.houseaffairs.app.core.data

import java.time.LocalDate

/**
 * "I want to eat something crispy with a savoury curry": each part of the craving ("crispy",
 * "savoury curry") becomes a short list of dishes from the family's recipes and the library,
 * best match with what's in the kitchen first. Pure, so the matching is tested.
 */

private val TASTE_WORDS = mapOf(
    "crispy" to "Crispy", "crunchy" to "Crispy", "fried" to "Crispy",
    "savoury" to "Savoury", "savory" to "Savoury",
    "spicy" to "Spicy", "hot" to "Spicy", "masaledar" to "Spicy",
    "tangy" to "Tangy", "sour" to "Tangy", "khatta" to "Tangy",
    "sweet" to "Sweet", "dessert" to "Sweet", "meetha" to "Sweet",
    "healthy" to "Healthy", "light" to "Healthy",
)

// Dishes with a gravy. "Masala omelette" and "paneer bhurji" aren't curries, so plain "masala" or "paneer" don't count.
private val CURRY_WORDS = listOf("curry", "dal", "chole", "rajma", "kadhi", "sambar", "butter masala", "makhani", "matar", "palak", "sabzi", "korma", "gravy")
private val FILLER = setOf("i", "want", "to", "eat", "have", "something", "some", "a", "an", "the", "for", "dinner", "lunch", "like", "feel", "craving", "eating", "having", "today", "tonight", "in", "mood", "of", "dish", "food")

/** One part of a craving: the tastes it asks for, whether it's a curry, and any dish or ingredient named. */
data class CravingPart(val label: String, val tastes: Set<String>, val curry: Boolean, val words: List<String>)

/** Splits a craving on "with" / "and" / "plus" into its parts. */
fun cravingParts(text: String): List<CravingPart> =
    text.lowercase().split(Regex("\\s+(?:with|and|plus|along with|&)\\s+|,")).mapNotNull { raw ->
        val words = raw.split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() && it !in FILLER }
        if (words.isEmpty()) return@mapNotNull null
        val tastes = words.mapNotNull { TASTE_WORDS[it] }.toSet()
        val curry = words.any { it in setOf("curry", "gravy", "sabzi") }
        val named = words.filter { it !in TASTE_WORDS && it !in setOf("curry", "gravy", "sabzi") }
        CravingPart(words.joinToString(" ").replaceFirstChar { it.uppercaseChar() }, tastes, curry, named)
    }

fun isCurry(recipe: Recipe): Boolean = CURRY_WORDS.any { recipe.title.lowercase().contains(it) }

/** Dishes for each part of [text], at most [perPart] each, best fit with the pantry first. */
fun cravingIdeas(text: String, pantry: List<PantryStock>, saved: List<Recipe>, today: LocalDate, perPart: Int = 3): List<Pair<String, List<DishIdea>>> {
    val savedTitles = saved.map { it.title.lowercase() }.toSet()
    val all = saved.map { it to true } + RECIPE_LIBRARY.filter { it.title.lowercase() !in savedTitles }.map { it to false }
    return cravingParts(text).map { part ->
        val matches = all.filter { (recipe, _) ->
            val about = (recipe.title + " " + recipe.ingredients.joinToString(" ") { it.name }).lowercase()
            part.tastes.all { it in recipe.mood } && (!part.curry || isCurry(recipe)) && part.words.all { w -> about.contains(w.removeSuffix("s")) }
        }.map { (recipe, isSaved) -> DishIdea(recipe, isSaved, recipeFit(recipe.ingredients, pantry, today), emptyList()) }
            .sortedWith(
                compareByDescending<DishIdea> { if (it.fit.total == 0) 0.0 else it.fit.have.toDouble() / it.fit.total }
                    .thenByDescending { it.saved },
            )
        part.label to matches.take(perPart)
    }.filter { it.second.isNotEmpty() }
}
