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
) : ViewModel() {
    private data class Scope(val householdId: String, val uid: String, val canMedicine: Boolean)

    private val scope = session.state.filterIsInstance<SessionState.Ready>()
        .map { Scope(it.household.id, it.user.uid, it.me.canAccess(AppModule.MEDICINE)) }
        .distinctUntilChanged()
    private val tab = MutableStateFlow(StockTab.RECIPES)
    private val mood = MutableStateFlow<String?>(null)

    private val data = scope.flatMapLatest { s ->
        combine(
            pantryRepo.observePantry(s.householdId, s.uid),
            pantryRepo.observeRecipes(s.householdId, s.uid),
            pantryRepo.observeShopping(s.householdId, s.uid),
            if (s.canMedicine) pantryRepo.observeMedicines(s.householdId, s.uid) else flowOf(emptyList()),
            combine(pantryRepo.observeSupplies(s.householdId, s.uid), pantryRepo.observeLastCooked(s.householdId, s.uid)) { a, b -> a to b },
        ) { pantry, recipes, shopping, medicines, (supplies, cooked) -> Data(s.canMedicine, pantry, recipes, shopping, medicines, supplies, cooked) }
    }

    private data class Data(
        val canMedicine: Boolean,
        val pantry: List<PantryStock>,
        val recipes: List<Recipe>,
        val shopping: List<ShoppingEntry>,
        val medicines: List<PantryStock>,
        val supplies: List<PantryStock>,
        val lastCooked: Map<String, LocalDate>,
    )

    private val members = scope.map { it.householdId }.distinctUntilChanged().flatMapLatest { households.observeMembers(it) }

    val state: StateFlow<StockUiState> = combine(data, tab, mood, members) { d, t, m, people ->
        val today = LocalDate.now()
        StockUiState(
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
        return pantryRepo.cook(r.household.id, r.user.uid, recipe.title, planFor(recipe, people), state.value.shopping.map { it.title }, eaters, people)
    }

    /** Ticks a shopping item into the pantry and hands back an Undo. */
    fun markBought(id: String, onDone: (CookResult) -> Unit) = withIds { hid, uid ->
        val title = state.value.shopping.firstOrNull { it.id == id }?.title ?: "Item"
        viewModelScope.launch {
            runCatching { pantryRepo.markBought(hid, uid, id) }.getOrNull()?.let { undo -> onDone(CookResult("$title · bought, now in the pantry") { undo() }) }
        }
    }

    fun useOne(stock: PantryStock) = withIds { hid, uid -> pantryRepo.useOne(hid, uid, stock, state.value.shopping.map { it.title }) }

    fun usedUp(stock: PantryStock) = withIds { hid, uid -> pantryRepo.usedUp(hid, uid, stock, state.value.shopping.map { it.title }) }

    private fun withIds(block: (String, String) -> Unit) {
        (session.state.value as? SessionState.Ready)?.let { block(it.household.id, it.user.uid) }
    }
}
