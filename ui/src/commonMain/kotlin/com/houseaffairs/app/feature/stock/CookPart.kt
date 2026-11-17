package com.houseaffairs.app.feature.stock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.DishIdea
import com.houseaffairs.app.core.data.MEAL_PEEK
import com.houseaffairs.app.core.data.PantryStock
import com.houseaffairs.app.core.data.Recipe
import com.houseaffairs.app.core.data.RecipeFit
import com.houseaffairs.app.core.data.byMeal
import com.houseaffairs.app.core.data.lastMadeText
import com.houseaffairs.app.core.data.mealAt
import com.houseaffairs.app.core.data.mealEmoji
import com.houseaffairs.app.core.data.planCooking
import com.houseaffairs.app.core.data.qty
import com.houseaffairs.app.core.data.recipeFit
import com.houseaffairs.app.core.data.searchDishes
import com.houseaffairs.app.core.data.servingsPossible
import com.houseaffairs.app.core.data.useItUp
import com.houseaffairs.app.core.data.withHistory
import com.houseaffairs.app.core.designsystem.Accent
import com.houseaffairs.app.core.designsystem.LocalHouseColors
import com.houseaffairs.app.core.model.Member
import com.houseaffairs.app.core.time.LocalDate
import com.houseaffairs.app.core.time.systemToday
import com.houseaffairs.app.core.ui.AddRow
import com.houseaffairs.app.core.ui.DishCard
import com.houseaffairs.app.core.ui.DishSheet
import com.houseaffairs.app.core.ui.Empty
import com.houseaffairs.app.core.ui.HaCard
import com.houseaffairs.app.core.ui.IconBlob
import com.houseaffairs.app.core.ui.LocalHouseImages
import com.houseaffairs.app.core.ui.WhoIsEating
import kotlinx.coroutines.launch

val MOODS = listOf("Sweet", "Spicy", "Tangy", "Crispy", "Savoury", "Healthy")

/** A saved dish with how well the pantry covers it. */
data class RecipeView(val recipe: Recipe, val fit: RecipeFit)

/** What Cook shows, from the kitchen's lists: the family's recipes ranked for now, and what to use up. */
fun recipeViews(recipes: List<Recipe>, pantry: List<PantryStock>, mood: String?, today: LocalDate): List<RecipeView> =
    recipes.filter { mood == null || mood in it.mood }.map { RecipeView(it, recipeFit(it.ingredients, pantry, today)) }
        .sortedWith(
            // "What can I cook?": cookable first, then the ones that rescue expiring food.
            compareByDescending<RecipeView> { it.fit.canCook }
                .thenByDescending { it.fit.usesExpiring.size }
                .thenByDescending { if (it.fit.total == 0) 0.0 else it.fit.have.toDouble() / it.fit.total },
        )

/** Cook's own choices on screen: the search, the mood, the meal chip, and the dish open or being cooked. */
class CookState {
    var query by mutableStateOf("")
    var mood by mutableStateOf<String?>(null)
    var mealFilter by mutableStateOf<String?>(null)
    var viewing by mutableStateOf<DishIdea?>(null)
    var cooking by mutableStateOf<Recipe?>(null)
}

@Composable
fun rememberCookState() = remember { CookState() }

/**
 * Kitchen → Cook, the same on both phones: search a dish or ingredient, pick a mood, dishes to
 * use up what's expiring, then the family's recipes as a menu by meal (the coming meal first),
 * two tiles across with Cook on each. [CookSheets] draws the dish and cooking sheets.
 */
fun LazyListScope.cookPart(
    state: CookState,
    pantry: List<PantryStock>,
    recipes: List<Recipe>,
    lastCooked: Map<String, LocalDate>,
    hour: Int,
    onAddRecipe: () -> Unit,
) {
    val today = systemToday()
    item {
        OutlinedTextField(
            value = state.query, onValueChange = { state.query = it },
            placeholder = { Text("Search a dish or ingredient…") }, singleLine = true, shape = CircleShape,
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
        )
    }
    if (state.query.trim().length >= 2) {
        val found = searchDishes(state.query, pantry, recipes, today)
        if (found.isEmpty()) item { Empty("No dish matches “${state.query}”", "Save it as a recipe to see what you have for it.") }
        items(found, key = { "search-" + it.recipe.id }) { idea ->
            val stock = LocalHouseColors.current.stock
            Row(Modifier.fillMaxWidth().clickable { state.viewing = idea }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (idea.fit.canCook) "✅" else "🛒", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(idea.recipe.title + if (idea.saved) "" else " · idea", style = MaterialTheme.typography.titleSmall)
                    Text(idea.fit.summary, style = MaterialTheme.typography.bodySmall, color = if (idea.fit.canCook) stock.color else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    item {
        val stock = LocalHouseColors.current.stock
        LazyRow(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MOODS) { mood ->
                FilterChip(
                    selected = state.mood == mood, onClick = { state.mood = if (state.mood == mood) null else mood }, label = { Text(mood) }, shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = stock.container, selectedLabelColor = stock.color),
                )
            }
        }
    }
    val rescue = withHistory(useItUp(pantry, recipes, today), lastCooked, today).filter { state.mood == null || state.mood in it.recipe.mood }
    if (rescue.isNotEmpty()) {
        // Your recipes, ranked by what's about to expire.
        item { Label("Use up what's expiring", "Your recipes that use it, soonest first") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(rescue.take(6)) { idea -> DishCard(idea, onClick = { state.viewing = idea }) }
            }
        }
    }
    val views = recipeViews(recipes, pantry, state.mood, today)
    item { Label("Your recipes", "Your family's dishes. Cooking ideas come from these.") }
    item { AddRow("Save a recipe", LocalHouseColors.current.stock, onAddRecipe) }
    if (views.isEmpty()) item { Empty("No recipes yet", "Save a dish with its ingredients. When you cook it, the pantry updates itself.") }
    // A menu, not one long list: a chip per meal, and dishes as tiles two across.
    val menu = byMeal(views, { it.recipe.meal }, { it.recipe.title }, hour)
    val shown = menu.firstOrNull { it.first == state.mealFilter }
    if (menu.size > 1) item(key = "meal-chips") {
        val stock = LocalHouseColors.current.stock
        val chip = FilterChipDefaults.filterChipColors(selectedContainerColor = stock.container, selectedLabelColor = stock.color)
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = shown == null, onClick = { state.mealFilter = null }, label = { Text("All  ${views.size}") }, shape = CircleShape, colors = chip) }
            items(menu, key = { it.first }) { (meal, list) ->
                FilterChip(
                    selected = shown?.first == meal, onClick = { state.mealFilter = if (state.mealFilter == meal) null else meal },
                    label = { Text("${mealEmoji(meal)} $meal  ${list.size}") }, shape = CircleShape, colors = chip,
                )
            }
        }
    }
    (shown?.let { listOf(it) } ?: menu).forEach { (meal, list) ->
        val peek = if (shown == null && menu.size > 1) MEAL_PEEK else Int.MAX_VALUE
        if (menu.size > 1) item(key = "meal-$meal") {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(mealEmoji(meal), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(8.dp))
                Text(meal, style = MaterialTheme.typography.titleMedium)
                if (meal == mealAt(hour)) { Spacer(Modifier.width(8.dp)); Tag("Up next", LocalHouseColors.current.stock.color) }
                Spacer(Modifier.weight(1f))
                if (list.size > peek) TextButton(onClick = { state.mealFilter = meal }) { Text("See all ${list.size}") } else Spacer(Modifier.height(48.dp))
            }
        }
        // A dish on both the lunch and dinner menus needs a key per menu.
        items(list.take(peek).chunked(2), key = { pair -> "$meal-" + pair.joinToString("-") { it.recipe.id } }) { pair ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { view ->
                    // A tap shows the dish (what you have, Cook, Edit, Delete).
                    RecipeTile(
                        view, meal, LocalHouseColors.current.stock, lastCooked[view.recipe.title.lowercase()], Modifier.weight(1f),
                        onOpen = { state.viewing = DishIdea(view.recipe, saved = true, fit = view.fit, rescues = emptyList()) },
                    ) { state.cooking = view.recipe }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * The dish up close and the cooking sheet. [onCook] takes from the pantry for the people eating
 * and adds what runs out to the shopping list; each phone saves it its own way.
 */
@Composable
fun CookSheets(
    state: CookState,
    pantry: List<PantryStock>,
    members: List<Member>,
    onCook: suspend (Recipe, List<String>, Int) -> Unit,
    onAddMissing: (List<String>) -> Unit,
    onEdit: (Recipe) -> Unit,
    onDelete: (Recipe) -> Unit,
    onSaveIdea: (Recipe) -> Unit = {},
) {
    state.viewing?.let { idea ->
        DishSheet(
            idea, pantry,
            onCook = if (idea.saved) ({ state.viewing = null; state.cooking = idea.recipe }) else null,
            onSave = if (idea.saved) null else ({ onSaveIdea(idea.recipe) }),
            onAddMissing = onAddMissing,
            onDismiss = { state.viewing = null },
            onEdit = if (idea.saved) ({ state.viewing = null; onEdit(idea.recipe) }) else null,
            onDelete = if (idea.saved) ({ state.viewing = null; onDelete(idea.recipe) }) else null,
        )
    }
    state.cooking?.let { recipe ->
        CookSheet(recipe, pantry, members, onCook = onCook, onAddMissing = { onAddMissing(it); state.cooking = null }) { state.cooking = null }
    }
}

@Composable
private fun Label(text: String, detail: String? = null) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium)
        detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** One dish on the menu: its photo (or the meal's emoji), whether it can be cooked now, and Cook. */
@Composable
private fun RecipeTile(view: RecipeView, meal: String, accent: Accent, lastMade: LocalDate?, modifier: Modifier = Modifier, onOpen: () -> Unit, onCook: () -> Unit) {
    val fit = view.fit
    val images = LocalHouseImages.current
    HaCard(modifier, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The dish's own photo when this phone has it: food is chosen by eye.
            val photo = view.recipe.photos.firstOrNull()?.takeIf(images::hasPhoto)
            if (photo != null) Box(Modifier.size(44.dp).clip(CircleShape)) { images.Photo(photo, Modifier.size(44.dp)) }
            else Box(Modifier.size(44.dp).clip(CircleShape).background(accent.container), contentAlignment = Alignment.Center) {
                Text(mealEmoji(meal), style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.weight(1f))
            if (fit.canCook && fit.total > 0) Tag("Ready", accent.color)
        }
        Spacer(Modifier.height(10.dp))
        Text(view.recipe.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            when {
                fit.usesExpiring.isNotEmpty() -> "Uses ${fit.usesExpiring.first()} before it expires"
                fit.total == 0 -> "No ingredients yet"
                fit.canCook -> lastMadeText(lastMade, systemToday()) ?: "You have everything"
                else -> fit.summary
            },
            style = MaterialTheme.typography.bodySmall, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis,
            color = if (fit.usesExpiring.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = onCook, shape = CircleShape, modifier = Modifier.fillMaxWidth()) { Text("Cook") }
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(
        text, style = MaterialTheme.typography.labelMedium, color = color,
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Shows exactly what cooking takes from the pantry before anything changes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CookSheet(recipe: Recipe, pantry: List<PantryStock>, members: List<Member>, onCook: suspend (Recipe, List<String>, Int) -> Unit, onAddMissing: (List<String>) -> Unit, onDone: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var eaters by remember { mutableStateOf<List<String>>(emptyList()) }
    var guests by remember { mutableIntStateOf(0) }
    var tried by remember { mutableStateOf(false) }
    val people = eaters.size + guests
    // Recipe quantities are for its servings; scaled to the people eating.
    val plan = planCooking(recipe.ingredients, people.coerceAtLeast(1).toDouble() / recipe.servings.coerceAtLeast(1), pantry)
    val accent = LocalHouseColors.current.stock

    ModalBottomSheet(onDismissRequest = onDone, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Cook ${recipe.title}", style = MaterialTheme.typography.headlineSmall)
            // The pantry sets the ceiling: no more people than it has ingredients for.
            val most = remember(recipe, pantry) { servingsPossible(recipe, pantry) }
            val full = most != null && most > 0 && people >= most
            WhoIsEating(members, eaters, guests, { if (most == null || most == 0 || it.size + guests <= most) eaters = it }, { if (most == null || most == 0 || eaters.size + it <= most) guests = it }, showError = tried && people == 0, canAddMore = !full)
            when {
                most == 0 -> Text("The pantry doesn't have enough for even one serving yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                most != null && full -> Text("That's all the pantry has: enough for $most. Buy more to cook for more.", style = MaterialTheme.typography.bodySmall, color = accent.color)
                most != null -> Text("The pantry has enough for up to $most", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (people == 0) "From your pantry (per person)" else "From your pantry, for $people", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (plan.deductions.isEmpty()) Text("None of the ingredients are in the pantry yet.", style = MaterialTheme.typography.bodyMedium)
            plan.deductions.forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBlob(Icons.Outlined.Kitchen, accent, size = 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(d.stock.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        when {
                            d.after == null -> "uses some"
                            d.usedUp -> "all used"
                            else -> "${qty(d.stock.amount, d.stock.unit ?: "")} → ${qty(d.after, d.stock.unit ?: "")}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (d.usedUp || d.runsLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (plan.toBuy.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(accent.container).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ShoppingCart, contentDescription = null, tint = accent.color)
                    Spacer(Modifier.width(10.dp))
                    Text("Will add to shopping: ${plan.toBuy.joinToString()}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            // Not enough at home: say what's short and don't take more out of the pantry than it has.
            if (!plan.enough) {
                Text(
                    listOfNotNull(
                        plan.missing.takeIf { it.isNotEmpty() }?.let { "Missing: " + it.joinToString { i -> i.name.lowercase() } },
                        plan.short.takeIf { it.isNotEmpty() }?.let { "Not enough " + it.joinToString().lowercase() + if (people > 0) " for $people" else "" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton(onClick = { onAddMissing(plan.missing.map { it.name } + plan.short) }, shape = CircleShape, modifier = Modifier.fillMaxWidth()) { Text("Add what's short to shopping") }
            }
            Button(
                onClick = {
                    tried = true
                    if (people > 0 && plan.enough) scope.launch { onCook(recipe, eaters, guests); onDone() }
                },
                enabled = plan.enough, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(if (plan.enough) "I cooked this" else "Not enough in the pantry", style = MaterialTheme.typography.titleMedium) }
        }
    }
}
