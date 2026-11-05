package com.houseaffairs.app.feature.stock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.houseaffairs.app.core.data.CookPlan
import com.houseaffairs.app.core.data.CookResult
import com.houseaffairs.app.core.data.PantryRepository
import com.houseaffairs.app.core.data.PantryStock
import com.houseaffairs.app.core.data.Recipe
import com.houseaffairs.app.core.data.RecipeFit
import com.houseaffairs.app.core.data.SessionManager
import com.houseaffairs.app.core.data.SessionState
import com.houseaffairs.app.core.data.ShoppingEntry
import com.houseaffairs.app.core.data.planCooking
import com.houseaffairs.app.core.data.DishIdea
import com.houseaffairs.app.core.data.cookIdeas
import com.houseaffairs.app.core.data.useItUp
import com.houseaffairs.app.core.data.withHistory
import com.houseaffairs.app.core.data.recipeFit
import com.houseaffairs.app.core.model.AppModule
import com.houseaffairs.app.core.model.Member
import com.houseaffairs.app.core.data.HouseholdRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class StockTab(val label: String) { RECIPES("Cook"), PANTRY("Pantry"), SHOPPING("Shopping") }

data class RecipeView(val recipe: Recipe, val fit: RecipeFit)

data class StockUiState(
    val tab: StockTab = StockTab.RECIPES,
    val tabs: List<StockTab> = StockTab.entries,
    val pantry: List<PantryStock> = emptyList(),
    val medicines: List<PantryStock> = emptyList(),
    val supplies: List<PantryStock> = emptyList(),
    val recipes: List<RecipeView> = emptyList(),
    val shopping: List<ShoppingEntry> = emptyList(),
    val mood: String? = null,
    val members: List<Member> = emptyList(),
    val lastCooked: Map<String, LocalDate> = emptyMap(),
    /** Dishes matching the Cook tab's search, what you can make first. */
    val search: List<DishIdea> = emptyList(),
    /** "Last time: Amul 👍" for shopping items, by their id. */
    val lastTime: Map<String, String> = emptyMap(),
    /** Dishes that rescue food about to expire. */
    val useItUp: List<DishIdea> = emptyList(),
    /** Library dishes you can mostly make with what's in the pantry. */
    val ideas: List<DishIdea> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StockViewModel @Inject constructor(
    private val session: SessionManager,
    private val pantryRepo: PantryRepository,
    households: HouseholdRepository,
    private val entries: com.houseaffairs.app.core.data.EntryRepository,
) : ViewModel() {
    /** Deletes a saved recipe; the snackbar's Undo brings it back as it was. */
    fun deleteRecipe(recipe: Recipe, onSaved: (com.houseaffairs.app.feature.add.SavedEntry) -> Unit) {
        val r = session.state.value as? SessionState.Ready ?: return
        viewModelScope.launch {
            val undo = runCatching { entries.removeById(r.household.id, com.houseaffairs.app.feature.add.EntryType.RECIPE, recipe.id) }.getOrNull() ?: return@launch
            onSaved(com.houseaffairs.app.feature.add.SavedEntry("${recipe.title} · deleted") { undo() })
        }
    }
    private data class Scope(val householdId: String, val uid: String, val canMedicine: Boolean, val ownMedicines: Boolean)

    private val scope = session.state.filterIsInstance<SessionState.Ready>()
        .map { Scope(it.household.id, it.user.uid, it.me.canAccess(AppModule.MEDICINE), com.houseaffairs.app.core.model.RolePolicy.ownMedicinesOnly(it.me.role)) }
        .distinctUntilChanged()
    private val tab = MutableStateFlow(StockTab.RECIPES)
    private val mood = MutableStateFlow<String?>(null)
    /** Search on the Cook tab: the family's recipes and the library, by name or ingredient. */
    val query = MutableStateFlow("")

    private val data = scope.flatMapLatest { s ->
        combine(
            pantryRepo.observePantry(s.householdId, s.uid),
            pantryRepo.observeRecipes(s.householdId, s.uid),
            pantryRepo.observeShopping(s.householdId, s.uid),
            if (s.canMedicine) pantryRepo.observeMedicines(s.householdId, s.uid, s.ownMedicines) else flowOf(emptyList()),
            combine(pantryRepo.observeSupplies(s.householdId, s.uid), pantryRepo.observeLastCooked(s.householdId, s.uid), pantryRepo.observeHistory(s.householdId, s.uid)) { a, b, c -> Triple(a, b, c) },
        ) { pantry, recipes, shopping, medicines, (supplies, cooked, history) -> Data(s.canMedicine, pantry, recipes, shopping, medicines, supplies, cooked, history.second) }
    }

    private data class Data(
        val canMedicine: Boolean,
        val pantry: List<PantryStock>,
        val recipes: List<Recipe>,
        val shopping: List<ShoppingEntry>,
        val medicines: List<PantryStock>,
        val supplies: List<PantryStock>,
        val lastCooked: Map<String, LocalDate>,
        val memory: List<com.houseaffairs.app.core.data.StockMemory>,
    )

    private val members = scope.map { it.householdId }.distinctUntilChanged().flatMapLatest { households.observeMembers(it) }

    val state: StateFlow<StockUiState> = combine(data, tab, mood, members, query) { d, t, m, people, q ->
        val today = LocalDate.now()
        StockUiState(
            search = searchDishes(q, d.pantry, d.recipes, today),
            lastTime = d.shopping.mapNotNull { item -> com.houseaffairs.app.core.data.lastTimeNote(item.title, d.memory)?.let { item.id to it } }.toMap(),
            tab = t,
            tabs = StockTab.entries,
            pantry = d.pantry,
            medicines = d.medicines,
            supplies = d.supplies,
            // "What can I cook?": cookable first, then the ones that rescue expiring food.
            recipes = d.recipes
                .filter { m == null || m in it.mood }
                .map { RecipeView(it, recipeFit(it.ingredients, d.pantry, today)) }
                .sortedWith(
                    compareByDescending<RecipeView> { it.fit.canCook }
                        .thenByDescending { it.fit.usesExpiring.size }
                        .thenByDescending { if (it.fit.total == 0) 0.0 else it.fit.have.toDouble() / it.fit.total },
                ),
            shopping = d.shopping,
            mood = m,
            members = people,
            useItUp = withHistory(useItUp(d.pantry, d.recipes, today), d.lastCooked, today).filter { m == null || m in it.recipe.mood },
            ideas = withHistory(cookIdeas(d.pantry, d.recipes, today, m), d.lastCooked, today).filter { !it.saved }.take(8),
            lastCooked = d.lastCooked,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StockUiState())

    fun selectTab(value: StockTab) {
        tab.value = value
    }

    /** Puts a dish's missing ingredients on the shopping list (skipping ones already there). */
    fun addMissing(names: List<String>, onDone: (CookResult) -> Unit) = withIds { hid, uid ->
        val listed = state.value.shopping.map { it.title }
        val adding = names.filter { n -> listed.none { com.houseaffairs.app.core.data.sameIngredient(it, n) } }
        if (adding.isEmpty()) return@withIds
        val undos = adding.map { pantryRepo.addToShopping(hid, uid, it) }
        onDone(CookResult("${adding.joinToString()} · added to the shopping list") { undos.forEach { it() } })
    }

    fun selectMood(value: String?) {
        mood.value = if (mood.value == value) null else value
    }

    /** Recipe quantities are for [Recipe.servings] people; scale to [people]. */
    fun planFor(recipe: Recipe, people: Int): CookPlan =
        planCooking(recipe.ingredients, people.toDouble() / recipe.servings.coerceAtLeast(1), state.value.pantry)

    suspend fun cook(recipe: Recipe, eaters: List<String>, guests: Int): CookResult? {
        val r = session.state.value as? SessionState.Ready ?: return null
        val people = eaters.size + guests
        if (people == 0) return null
        val plan = planFor(recipe, people)
        // Never more out of the pantry than it holds.
        if (!plan.enough) return null
        return pantryRepo.cook(r.household.id, r.user.uid, recipe.title, plan, state.value.shopping.map { it.title }, eaters, people)
    }

    /** Ticks a shopping item into the pantry and hands back an Undo. */
    fun markBought(id: String, onDone: (CookResult) -> Unit) = withIds { hid, uid ->
        val title = state.value.shopping.firstOrNull { it.id == id }?.title ?: "Item"
        viewModelScope.launch {
            runCatching { pantryRepo.markBought(hid, uid, id) }.getOrNull()?.let { bought ->
                onDone(CookResult("$title · bought, now in the pantry") { bought() })
                com.houseaffairs.app.core.ui.FollowUps.show(
                    com.houseaffairs.app.core.data.boughtFollowUp(title, bought.unit, LocalDate.now()) { a, u, e -> pantryRepo.refineBought(hid, bought.stockId, a, u, e) },
                )
            }
        }
    }

    fun useOne(stock: PantryStock) = withIds { hid, uid -> pantryRepo.useOne(hid, uid, stock, state.value.shopping.map { it.title }) }

    /** Finished: off the list it's on ([from] is "stock" or "medicines"), onto the shopping list, with a "don't buy again". */
    fun usedUp(stock: PantryStock, from: String = "stock") = withIds { hid, uid ->
        val added = pantryRepo.usedUp(hid, uid, stock, state.value.shopping.map { it.title }, from) ?: return@withIds
        com.houseaffairs.app.core.ui.FollowUps.show(com.houseaffairs.app.core.data.finishedFollowUp(stock.title) { pantryRepo.removeFromShopping(hid, added) })
    }

    /** Threw it away: like Finished, but counted as waste in the month and year views. */
    fun threwAway(stock: PantryStock, from: String = "stock") = withIds { hid, uid ->
        val added = pantryRepo.usedUp(hid, uid, stock, state.value.shopping.map { it.title }, from, thrown = true)
        com.houseaffairs.app.core.ui.FollowUps.show(com.houseaffairs.app.core.data.thrownFollowUp(stock.title) { added?.let { pantryRepo.removeFromShopping(hid, it) } })
    }

    private fun withIds(block: (String, String) -> Unit) {
        (session.state.value as? SessionState.Ready)?.let { block(it.household.id, it.user.uid) }
    }
}

/** Dishes whose name or ingredients match [query], cookable first. Empty for a blank query. Pure, so it's tested. */
fun searchDishes(query: String, pantry: List<PantryStock>, saved: List<Recipe>, today: LocalDate): List<DishIdea> {
    val q = query.trim().lowercase()
    if (q.length < 2) return emptyList()
    return (saved.map { it to true })
        .filter { (r, _) -> r.title.lowercase().contains(q) || r.ingredients.any { it.name.lowercase().contains(q) } }
        .map { (r, isSaved) -> DishIdea(r, isSaved, recipeFit(r.ingredients, pantry, today), emptyList()) }
        .sortedWith(compareByDescending<DishIdea> { it.fit.canCook }.thenBy { it.fit.missing.size }.thenByDescending { it.saved })
        .take(10)
}
