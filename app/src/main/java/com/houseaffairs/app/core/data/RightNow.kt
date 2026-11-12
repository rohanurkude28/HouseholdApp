package com.houseaffairs.app.core.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

enum class SuggestionKind { FOOD, WATCH, EAT_OUT, SHOP }

data class NowSuggestion(
    val kind: SuggestionKind,
    val title: String,
    val detail: String,
    val image: String? = null,
    /** A family recipe (tap to cook) or a library dish (tap to save). */
    val dish: DishIdea? = null,
)

data class RightNow(val headline: String, val items: List<NowSuggestion>)

/** A wish-list item: eat-out spots and things to buy. */
data class WishItem(
    val id: String,
    val template: String,
    val title: String,
    val place: String?,
    val budgetText: String?,
    /** "IDEA" or "PLANNED"; done items aren't listed. */
    val status: String = "IDEA",
    /** Who ❤️'d it. */
    val votes: List<String> = emptyList(),
    /** For gift ideas: the event it's for ("Riya's birthday"). */
    val event: String? = null,
    /** A link the family saved (reel, website, shop). */
    val link: String? = null,
    /** Photos of it (PhotoStore ids): the sofa, the place, the gift. */
    val photos: List<String> = emptyList(),
)

/**
 * What's worth suggesting at this moment: breakfast/lunch/dinner from the pantry by time of day,
 * and on weekend days restaurants to try and (only if the Shopping envelope has money) things on
 * the buy list. The watch list has its own Home tab, so it isn't repeated here. Pure, so the timing
 * rules are tested.
 */
fun rightNow(
    now: LocalDateTime,
    pantry: List<PantryStock>,
    recipes: List<Recipe>,
    wishes: List<WishItem>,
    places: List<PlaceItem>,
    shoppingLeft: String?,
    lastCooked: Map<String, LocalDate> = emptyMap(),
): RightNow? {
    val today: LocalDate = now.toLocalDate()
    val hour = now.hour
    val weekend = now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY
    // Dishes that rescue expiring food already have their own Home section.
    // Only dishes you can make now; ones that rescue expiring food have their own Home tab.
    fun food(meal: String, n: Int) = withHistory(mealIdeas(meal, pantry, recipes, today), lastCooked, today).filter { it.rescues.isEmpty() && it.fit.canCook }.take(n).map { idea ->
        NowSuggestion(
            SuggestionKind.FOOD, idea.recipe.title,
            when {
                idea.rescues.isNotEmpty() -> "Uses ${idea.rescues.first()} before it expires"
                lastMadeText(idea.lastMade, today)?.startsWith("Not made") == true -> lastMadeText(idea.lastMade, today)!!
                idea.fit.canCook -> "You have everything"
                idea.fit.total > 0 -> idea.fit.summary
                else -> meal
            },
            dish = idea,
        )
    }
    return when {
        weekend && hour in 9..19 -> {
            val eatOut = wishes.filter { it.template == "Eat out" }.map { NowSuggestion(SuggestionKind.EAT_OUT, it.title, it.place ?: "On your eat-out list") } +
                places.filter { (it.kind == "Restaurant" || it.kind == "Café") && it.rating?.let { r -> "Want" in r || "Love" in r } == true }
                    .map { NowSuggestion(SuggestionKind.EAT_OUT, it.title, listOfNotNull(it.area, it.goodFor?.let { g -> "try $g" }).joinToString(" · ").ifEmpty { it.kind }) }
            val shop = if (shoppingLeft != null) {
                listOf(NowSuggestion(SuggestionKind.SHOP, "Shopping", "$shoppingLeft left this month")) +
                    wishes.filter { it.template == "Buy" }.take(2).map { NowSuggestion(SuggestionKind.SHOP, it.title, it.budgetText ?: "On your buy list") }
            } else {
                emptyList()
            }
            RightNow("This weekend", (eatOut.take(4) + shop).ifEmpty { food("Lunch", 3) })
        }
        hour in 5..10 -> RightNow("Breakfast ideas", food("Breakfast", 4))
        hour in 11..15 -> RightNow("For lunch", food("Lunch", 4))
        hour in 16..21 -> RightNow("For dinner", food("Dinner", 4))
        else -> null
    }?.takeIf { it.items.isNotEmpty() }
}
