package com.houseaffairs.app.core.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

private fun i(name: String, amount: Double, unit: String) = Ingredient(name, amount, unit)
private fun r(title: String, servings: Int, mood: List<String>, vararg ingredients: Ingredient) =
    Recipe(id = "lib:" + title.lowercase().replace(' ', '-'), title = title, ingredients = ingredients.toList(), servings = servings, mood = mood, link = null)

private val BREAKFAST = setOf("Poha", "Upma", "Masala omelette", "French toast", "Banana smoothie", "Moong dal chilla", "Aloo paratha", "Egg bhurji", "Veg sandwich")
private val SWEET = setOf("Kheer", "Fruit salad")

/** Rough per-serving protein · carbs · fat · fibre for the library dishes; families' own recipes carry their own. */
private val LIBRARY_MACROS = mapOf(
    "Paneer bhurji" to "18g · 8g · 20g · 2g", "Palak paneer" to "14g · 10g · 18g · 4g", "Paneer tikka" to "20g · 8g · 18g · 2g",
    "Matar paneer" to "14g · 16g · 16g · 4g", "Aloo matar" to "5g · 30g · 8g · 5g", "Dal tadka" to "10g · 28g · 6g · 6g",
    "Veg pulao" to "5g · 50g · 6g · 3g", "Jeera rice" to "4g · 50g · 5g · 1g", "Curd rice" to "7g · 40g · 6g · 1g",
    "Lemon rice" to "5g · 48g · 8g · 2g", "Egg bhurji" to "13g · 4g · 12g · 1g", "Masala omelette" to "18g · 5g · 15g · 1g",
    "French toast" to "10g · 30g · 10g · 1g", "Veg sandwich" to "8g · 32g · 9g · 3g", "Banana smoothie" to "7g · 40g · 5g · 3g",
    "Kheer" to "7g · 40g · 8g · 0g", "Raita" to "5g · 6g · 4g · 1g", "Tomato soup" to "3g · 14g · 4g · 3g",
    "Pasta arrabbiata" to "12g · 70g · 8g · 5g", "Poha" to "5g · 40g · 8g · 3g", "Upma" to "6g · 38g · 8g · 3g",
    "Aloo paratha" to "8g · 45g · 12g · 5g", "Moong dal chilla" to "12g · 22g · 5g · 5g", "Fruit salad" to "2g · 30g · 1g · 5g",
    "Chicken curry" to "28g · 8g · 16g · 2g", "Onion pakoda" to "6g · 24g · 14g · 4g", "Aloo tikki" to "4g · 28g · 9g · 3g",
    "Masala dosa" to "8g · 55g · 12g · 4g", "Bhindi fry" to "3g · 10g · 8g · 4g", "Papad" to "3g · 8g · 2g · 2g",
    "Paneer pakoda" to "16g · 14g · 20g · 2g", "Chole" to "12g · 40g · 8g · 10g", "Rajma" to "12g · 38g · 6g · 11g",
    "Kadhi" to "7g · 12g · 6g · 1g", "Egg curry" to "14g · 8g · 14g · 2g", "Paneer butter masala" to "16g · 12g · 26g · 2g",
    "Sambar" to "7g · 20g · 4g · 6g",
)

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
    r("Chicken curry", 4, listOf("Spicy", "Savoury"), i("Chicken", 500.0, "g"), i("Onion", 2.0, "pcs"), i("Tomato", 2.0, "pcs"), i("Curd", 100.0, "g")),
    // Crispy sides and snacks.
    r("Onion pakoda", 3, listOf("Crispy", "Spicy"), i("Besan", 150.0, "g"), i("Onion", 2.0, "pcs")),
    r("Aloo tikki", 3, listOf("Crispy", "Savoury"), i("Potato", 4.0, "pcs"), i("Peas", 50.0, "g")),
    r("Masala dosa", 2, listOf("Crispy", "Savoury"), i("Dosa batter", 400.0, "g"), i("Potato", 3.0, "pcs"), i("Onion", 1.0, "pcs")),
    r("Bhindi fry", 3, listOf("Crispy", "Savoury"), i("Okra", 300.0, "g"), i("Onion", 1.0, "pcs")),
    r("Papad", 4, listOf("Crispy"), i("Papad", 4.0, "pcs")),
    r("Paneer pakoda", 2, listOf("Crispy", "Savoury"), i("Paneer", 200.0, "g"), i("Besan", 100.0, "g")),
    // Curries.
    r("Chole", 4, listOf("Spicy", "Savoury"), i("Chana", 250.0, "g"), i("Onion", 2.0, "pcs"), i("Tomato", 2.0, "pcs")),
    r("Rajma", 4, listOf("Savoury"), i("Rajma", 250.0, "g"), i("Onion", 2.0, "pcs"), i("Tomato", 2.0, "pcs")),
    r("Kadhi", 4, listOf("Tangy", "Savoury"), i("Curd", 400.0, "g"), i("Besan", 60.0, "g")),
    r("Egg curry", 3, listOf("Spicy", "Savoury"), i("Eggs", 6.0, "pcs"), i("Onion", 2.0, "pcs"), i("Tomato", 2.0, "pcs")),
    r("Paneer butter masala", 3, listOf("Savoury"), i("Paneer", 250.0, "g"), i("Tomato", 3.0, "pcs"), i("Butter", 30.0, "g"), i("Cream", 50.0, "ml")),
    r("Sambar", 4, listOf("Tangy", "Healthy"), i("Toor dal", 150.0, "g"), i("Tomato", 2.0, "pcs"), i("Drumstick", 2.0, "pcs")),
).map {
    it.copy(
        meal = when (it.title) { in BREAKFAST -> "Breakfast"; in SWEET -> "Dessert"; else -> "Lunch or dinner" },
        macros = LIBRARY_MACROS[it.title],
    )
}


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
        // Only dishes you can actually make, or that use up something about to expire.
        .filter { it.fit.canCook || it.rescues.isNotEmpty() }
        .sortedWith(
            compareByDescending<DishIdea> { it.rescues.size }
                .thenByDescending { it.fit.have.toDouble() / it.fit.total }
                .thenByDescending { it.saved },
        )
}
