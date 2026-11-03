package com.houseaffairs.app.core.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

private fun i(name: String, amount: Double, unit: String) = Ingredient(name, amount, unit)
private fun r(title: String, servings: Int, mood: List<String>, vararg ingredients: Ingredient) =
    Recipe(id = "lib:" + title.lowercase().replace(' ', '-'), title = title, ingredients = ingredients.toList(), servings = servings, mood = mood, link = null)

private val BREAKFAST = setOf("Poha", "Upma", "Masala omelette", "French toast", "Banana smoothie", "Moong dal chilla", "Aloo paratha", "Egg bhurji", "Veg sandwich")
private val SWEET = setOf("Kheer", "Fruit salad")

/** Everyday dishes so suggestions work from day one, before the family has saved any recipes. */
val RECIPE_LIBRARY: List<Recipe> = listOf(
    r("Paneer bhurji", 2, listOf("Spicy", "Savoury"), i("Paneer", 200.0, "g"), i("Onion", 1.0, "pcs"), i("Tomato", 1.0, "pcs"), i("Capsicum", 1.0, "pcs")),
    r("Palak paneer", 3, listOf("Healthy"), i("Paneer", 200.0, "g"), i("Spinach", 250.0, "g"), i("Onion", 1.0, "pcs"), i("Cream", 50.0, "ml")),
    r("Paneer tikka", 2, listOf("Spicy", "Crispy"), i("Paneer", 250.0, "g"), i("Curd", 100.0, "g"), i("Capsicum", 1.0, "pcs"), i("Onion", 1.0, "pcs")),
    r("Matar paneer", 3, listOf("Savoury"), i("Paneer", 200.0, "g"), i("Peas", 150.0, "g"), i("Tomato", 2.0, "pcs"), i("Onion", 1.0, "pcs")),
    r("Aloo matar", 3, listOf("Savoury"), i("Potato", 3.0, "pcs"), i("Peas", 150.0, "g"), i("Tomato", 2.0, "pcs")),
    r("Dal tadka", 3, listOf("Healthy", "Savoury"), i("Toor dal", 150.0, "g"), i("Tomato", 1.0, "pcs"), i("Onion", 1.0, "pcs")),
    r("Veg pulao", 3, listOf("Savoury"), i("Rice", 200.0, "g"), i("Peas", 100.0, "g"), i("Carrot", 1.0, "pcs"), i("Beans", 100.0, "g")),
    r("Jeera rice", 2, listOf("Savoury"), i("Rice", 200.0, "g")),
    r("Curd rice", 2, listOf("Tangy", "Healthy"), i("Rice", 150.0, "g"), i("Curd", 200.0, "g")),
    r("Lemon rice", 2, listOf("Tangy"), i("Rice", 200.0, "g"), i("Lemon", 1.0, "pcs"), i("Peanuts", 30.0, "g")),
    r("Egg bhurji", 2, listOf("Spicy"), i("Eggs", 4.0, "pcs"), i("Onion", 1.0, "pcs"), i("Tomato", 1.0, "pcs")),
    r("Masala omelette", 1, listOf("Savoury"), i("Eggs", 3.0, "pcs"), i("Onion", 1.0, "pcs"), i("Tomato", 1.0, "pcs")),
    r("French toast", 2, listOf("Sweet"), i("Bread", 4.0, "pcs"), i("Eggs", 2.0, "pcs"), i("Milk", 100.0, "ml")),
    r("Veg sandwich", 2, listOf("Crispy"), i("Bread", 4.0, "pcs"), i("Tomato", 1.0, "pcs"), i("Cucumber", 1.0, "pcs"), i("Cheese", 2.0, "pcs")),
    r("Banana smoothie", 2, listOf("Sweet", "Healthy"), i("Banana", 2.0, "pcs"), i("Milk", 300.0, "ml")),
    r("Kheer", 4, listOf("Sweet"), i("Milk", 1000.0, "ml"), i("Rice", 50.0, "g"), i("Sugar", 100.0, "g")),
    r("Raita", 3, listOf("Tangy", "Healthy"), i("Curd", 300.0, "g"), i("Cucumber", 1.0, "pcs")),
    r("Tomato soup", 2, listOf("Tangy", "Healthy"), i("Tomato", 5.0, "pcs"), i("Onion", 1.0, "pcs"), i("Cream", 30.0, "ml")),
    r("Pasta arrabbiata", 2, listOf("Spicy", "Tangy"), i("Pasta", 200.0, "g"), i("Tomato", 4.0, "pcs"), i("Garlic", 4.0, "pcs")),
    r("Poha", 2, listOf("Tangy", "Healthy"), i("Poha", 200.0, "g"), i("Onion", 1.0, "pcs"), i("Peanuts", 30.0, "g"), i("Lemon", 1.0, "pcs")),
    r("Upma", 2, listOf("Savoury"), i("Rava", 150.0, "g"), i("Onion", 1.0, "pcs")),
    r("Aloo paratha", 3, listOf("Crispy"), i("Atta", 300.0, "g"), i("Potato", 3.0, "pcs")),
    r("Moong dal chilla", 2, listOf("Healthy", "Crispy"), i("Moong dal", 150.0, "g"), i("Onion", 1.0, "pcs")),
    r("Fruit salad", 2, listOf("Sweet", "Healthy"), i("Banana", 1.0, "pcs"), i("Apple", 1.0, "pcs"), i("Papaya", 200.0, "g")),
    r("Chicken curry", 4, listOf("Spicy"), i("Chicken", 500.0, "g"), i("Onion", 2.0, "pcs"), i("Tomato", 2.0, "pcs"), i("Curd", 100.0, "g")),
).map { it.copy(meal = when (it.title) { in BREAKFAST -> "Breakfast"; in SWEET -> "Dessert"; else -> "Lunch or dinner" }) }

/** Dishes for a meal, best fit with the pantry first; saved recipes win ties. Unlike [cookIdeas], nothing is filtered out. */
fun mealIdeas(meal: String, pantry: List<PantryStock>, saved: List<Recipe>, today: LocalDate): List<DishIdea> {
    val savedTitles = saved.map { it.title.lowercase() }.toSet()
    fun fits(r: Recipe) = r.meal == null || r.meal == meal || (r.meal == "Lunch or dinner" && meal in setOf("Lunch", "Dinner")) ||
        (meal in setOf("Lunch", "Dinner") && r.meal in setOf("Lunch", "Dinner"))
    val expiring = useItUp(pantry, saved, today).associateBy { it.recipe.title }
    return (saved.filter(::fits).map { it to true } + RECIPE_LIBRARY.filter { fits(it) && it.title.lowercase() !in savedTitles }.map { it to false })
        .map { (r, isSaved) -> DishIdea(r, isSaved, recipeFit(r.ingredients, pantry, today), expiring[r.title]?.rescues.orEmpty()) }
        .sortedWith(
            compareByDescending<DishIdea> { it.rescues.size }
                .thenByDescending { if (it.fit.total == 0) 0.0 else it.fit.have.toDouble() / it.fit.total }
                .thenByDescending { it.saved },
        )
}

data class DishIdea(
    val recipe: Recipe,
    /** True when it's one of the family's own recipes (cooking it updates the pantry). */
    val saved: Boolean,
    val fit: RecipeFit,
    /** Pantry items about to expire that this dish uses up. */
    val rescues: List<String>,
    /** When the family last cooked it, if ever. */
    val lastMade: LocalDate? = null,
)

/**
 * Applies cooking history: dishes made in the last 3 days drop to the end (no one wants dal three
 * nights running); ones not made for two weeks or more get a nudge up. Otherwise order is kept.
 */
fun withHistory(ideas: List<DishIdea>, lastCooked: Map<String, LocalDate>, today: LocalDate): List<DishIdea> {
    val tagged = ideas.map { idea -> idea.copy(lastMade = lastCooked.entries.firstOrNull { sameIngredient(it.key, idea.recipe.title) }?.value) }
    fun bucket(d: DishIdea): Int {
        val days = d.lastMade?.let { ChronoUnit.DAYS.between(it, today) } ?: return 1
        return when {
            days <= 3 -> 2
            days >= 14 -> 0
            else -> 1
        }
    }
    return tagged.sortedBy(::bucket)
}

/** "Made 2 days ago", "Not made in 3 weeks", or null if never made. */
fun lastMadeText(lastMade: LocalDate?, today: LocalDate): String? {
    val days = lastMade?.let { ChronoUnit.DAYS.between(it, today) } ?: return null
    return when {
        days <= 0 -> "Made today"
        days == 1L -> "Made yesterday"
        days < 14 -> "Made $days days ago"
        else -> "Not made in ${days / 7} weeks"
    }
}

/**
 * "What can we make before it goes off?" Dishes from saved recipes and the library that use
 * items expiring within [withinDays], best first: most rescued items, then most ingredients in
 * stock, then the family's own recipes.
 */
fun useItUp(pantry: List<PantryStock>, saved: List<Recipe>, today: LocalDate, withinDays: Long = 3): List<DishIdea> {
    val expiring = pantry.filter { s -> s.expiry?.let { ChronoUnit.DAYS.between(today, it) in 0..withinDays } == true }
    if (expiring.isEmpty()) return emptyList()
    val savedTitles = saved.map { it.title.lowercase() }.toSet()
    val candidates = saved.map { it to true } + RECIPE_LIBRARY.filter { it.title.lowercase() !in savedTitles }.map { it to false }
    return candidates.mapNotNull { (recipe, isSaved) ->
        val rescues = expiring.filter { stock -> recipe.ingredients.any { sameIngredient(it.name, stock.title) } }.map { it.title }.distinct()
        if (rescues.isEmpty()) return@mapNotNull null
        DishIdea(recipe, isSaved, recipeFit(recipe.ingredients, pantry, today), rescues)
    }.sortedWith(
        compareByDescending<DishIdea> { it.rescues.size }
            .thenByDescending { if (it.fit.total == 0) 0.0 else it.fit.have.toDouble() / it.fit.total }
            .thenByDescending { it.saved },
    )
}

/** General ideas (not tied to expiry): dishes you can mostly make with what's in the pantry. */
fun cookIdeas(pantry: List<PantryStock>, saved: List<Recipe>, today: LocalDate, mood: String? = null): List<DishIdea> {
    val savedTitles = saved.map { it.title.lowercase() }.toSet()
    val expiringSoon = useItUp(pantry, saved, today).associateBy { it.recipe.title }
    return (saved.map { it to true } + RECIPE_LIBRARY.filter { it.title.lowercase() !in savedTitles }.map { it to false })
        .filter { (recipe, _) -> mood == null || mood in recipe.mood }
        .map { (recipe, isSaved) -> DishIdea(recipe, isSaved, recipeFit(recipe.ingredients, pantry, today), expiringSoon[recipe.title]?.rescues.orEmpty()) }
        .filter { it.fit.total > 0 && it.fit.have * 2 >= it.fit.total }
        .sortedWith(
            compareByDescending<DishIdea> { it.rescues.size }
                .thenByDescending { it.fit.have.toDouble() / it.fit.total }
                .thenByDescending { it.saved },
        )
}
