package com.houseaffairs.app.feature.stock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.houseaffairs.app.core.data.pantryCategory
import com.houseaffairs.app.core.data.Aisle
import com.houseaffairs.app.core.data.Flag
import com.houseaffairs.app.core.data.ListRow
import com.houseaffairs.app.core.data.stockFlag
import com.houseaffairs.app.core.ui.CompactRow
import com.houseaffairs.app.core.ui.AddRow
import com.houseaffairs.app.core.ui.Empty
import com.houseaffairs.app.core.ui.itemList
import com.houseaffairs.app.core.ui.ListGroupsViewModel
import com.houseaffairs.app.core.ui.ListToolbar
import com.houseaffairs.app.core.ui.organizedSections
import com.houseaffairs.app.core.ui.rememberListControls
import com.houseaffairs.app.core.ui.rememberOrganized
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.houseaffairs.app.core.data.CookResult
import com.houseaffairs.app.core.data.PantryStock
import com.houseaffairs.app.core.data.Recipe
import com.houseaffairs.app.core.designsystem.Accent
import com.houseaffairs.app.core.designsystem.LocalHouseColors
import com.houseaffairs.app.core.ui.HaCard
import com.houseaffairs.app.core.ui.IconBlob
import com.houseaffairs.app.core.ui.DishCard
import com.houseaffairs.app.feature.add.EntryType
import androidx.compose.runtime.LaunchedEffect
import com.houseaffairs.app.core.ui.WhoIsEating
import com.houseaffairs.app.core.ui.TickCircle
import com.houseaffairs.app.core.ui.opensEdit
import com.houseaffairs.app.feature.add.formatDate
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val MOODS = listOf("Sweet", "Spicy", "Tangy", "Crispy", "Savoury", "Healthy")

/** "200 g · use by Thu 5 Nov", "50 g · 20% left · 📍 spice box", "1 L · expired". */
internal fun stockDetail(item: PantryStock, flag: Flag?): String = listOfNotNull(
    qty(item.amount, item.unit).takeIf { it.isNotEmpty() },
    when (flag) {
        Flag.EXPIRED -> "expired"
        Flag.EXPIRING -> item.expiry?.let { "use by ${formatDate(it)}" }
        Flag.LOW -> item.initialAmount?.takeIf { it > 0 }?.let { "${(((item.amount ?: it) / it) * 100).toInt()}% left" }
        // Not urgent, but the date still matters (medicines especially).
        null -> item.expiry?.let { "use by ${formatDate(it)}" }
    },
    item.keptAt?.let { "📍 $it" },
).joinToString(" · ").ifEmpty { "In stock" }

internal fun qty(amount: Double?, unit: String): String = when {
    amount == null -> ""
    amount % 1.0 == 0.0 -> "${amount.toLong()} $unit"
    else -> "${"%.1f".format(amount)} $unit"
}

/** The food loop: what to cook, what's in the pantry, what to buy. Cooking updates the pantry. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StockScreen(
    onAddGrocery: () -> Unit,
    onAddRecipe: () -> Unit,
    onAddShopping: () -> Unit,
    onCooked: (CookResult) -> Unit,
    onSaveIdea: (Recipe) -> Unit,
    onEdit: (EntryType, String) -> Unit,
    requestedTab: String? = null,
    onTabShown: () -> Unit = {},
    viewModel: StockViewModel = hiltViewModel(),
) {
    LaunchedEffect(requestedTab) {
        StockTab.entries.firstOrNull { it.name == requestedTab }?.let { viewModel.selectTab(it); onTabShown() }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalHouseColors.current
    var cooking by remember { mutableStateOf<Recipe?>(null) }
    val groups: ListGroupsViewModel = hiltViewModel()
    val today = LocalDate.now()
    val pantryControls = rememberListControls()
    val pantry = rememberOrganized(
        "pantry",
        remember(state.pantry) { state.pantry.map { ListRow(it, it.id, it.title, pantryCategory(it).label, stockFlag(it, today), it.keptAt.orEmpty()) } },
        pantryControls,
        order = Aisle.FOOD.map { it.label },
        groups = groups,
    )
    val shoppingControls = rememberListControls()
    // While shopping you want to see everything, so aisles never start closed.
    val shopping = rememberOrganized(
        "shopping",
        remember(state.shopping) { state.shopping.map { ListRow(it, it.id, it.title, it.aisle.label) } },
        shoppingControls,
        order = Aisle.entries.map { it.label },
        openUpTo = Int.MAX_VALUE,
        groups = groups,
    )

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
                Text("Kitchen", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "${state.pantry.size} in the pantry · ${state.shopping.size} to buy · ${state.recipes.count { it.fit.canCook }} you can cook now",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.tabs) { tab ->
                    FilterChip(
                        selected = state.tab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        label = { Text(tab.label) },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
        }
        when (state.tab) {
            StockTab.PANTRY -> itemList(
                "pantry", pantry, pantryControls, colors.stock,
                addLabel = "Add groceries", onAdd = onAddGrocery, searchHint = "Find in the pantry…",
                emptyTitle = "Your pantry is empty", emptyBody = "Add what you buy, or tick items off the shopping list and they land here.",
                onToggle = { groups.toggle("pantry:$it") },
            ) { row ->
                CompactRow(row.title, stockDetail(row.item, row.flag), row.flag, onClick = { onEdit(EntryType.GROCERY, row.item.id) }, actions = listOf("Finished" to { viewModel.usedUp(row.item) }))
            }
            StockTab.RECIPES -> {
                item {
                    LazyRow(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(MOODS) { mood ->
                            FilterChip(
                                selected = state.mood == mood,
                                onClick = { viewModel.selectMood(mood) },
                                label = { Text(mood) },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.stock.container, selectedLabelColor = colors.stock.color),
                            )
                        }
                    }
                }
                if (state.useItUp.isNotEmpty()) {
                    item { Label("Cook before it expires") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(state.useItUp.take(6)) { idea ->
                                DishCard(idea, onClick = {
                                    if (idea.saved) cooking = idea.recipe else onSaveIdea(idea.recipe)
                                })
                            }
                        }
                    }
                }
                item { AddRow("Save a recipe", colors.stock, onAddRecipe) }
                if (state.recipes.isEmpty()) {
                    item { Empty("No recipes yet", "Save a dish with its ingredients. When you cook it, the pantry updates itself.") }
                }
                items(state.recipes, key = { it.recipe.id }) { view ->
                    RecipeCard(view, colors.stock, state.lastCooked[view.recipe.title.lowercase()], onEdit = { onEdit(EntryType.RECIPE, view.recipe.id) }) { cooking = view.recipe }
                }
                if (state.ideas.isNotEmpty()) {
                    item { Label("Ideas from what you have") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(state.ideas) { idea -> DishCard(idea, onClick = { onSaveIdea(idea.recipe) }) }
                        }
                    }
                }
            }
            StockTab.SHOPPING -> itemList(
                "shopping", shopping, shoppingControls, colors.stock,
                addLabel = "Add to shopping list", onAdd = onAddShopping, searchHint = "Find on the list…",
                emptyTitle = "Nothing to buy", emptyBody = "Things that run low or out after cooking are added here for you.",
                onToggle = { groups.toggle("shopping:$it") },
                flags = false,
                footer = "Tap the circle when it's in the cart: it moves to your pantry. Tap the name to edit.".takeIf { state.shopping.isNotEmpty() },
            ) { row ->
                val item = row.item
                // The circle ticks it off (into the pantry, with Undo); the row opens it for editing.
                CompactRow(
                    item.title, listOfNotNull(qty(item.amount, item.unit ?: "").takeIf { it.isNotEmpty() }, state.lastTime[item.id]).joinToString(" · "), null,
                    onClick = { onEdit(EntryType.SHOPPING, item.id) },
                    leading = { TickCircle(androidx.compose.ui.graphics.Color(item.aisle.argb), onTick = { viewModel.markBought(item.id, onCooked) }, size = 22.dp, contentDescription = "Bought ${item.title}") },
                )
            }
        }
    }

    cooking?.let { recipe ->
        CookSheet(recipe, viewModel, onDone = { result ->
            cooking = null
            result?.let(onCooked)
        })
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeCard(view: RecipeView, accent: Accent, lastMade: LocalDate?, onEdit: () -> Unit, onCook: () -> Unit) {
    val fit = view.fit
    HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBlob(Icons.Outlined.RestaurantMenu, accent, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(view.recipe.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        fit.total == 0 -> "Add ingredients to track the pantry"
                        fit.canCook -> "You have everything"
                        else -> "You have ${fit.have} of ${fit.total} ingredients"
                    } + (com.houseaffairs.app.core.data.lastMadeText(lastMade, LocalDate.now())?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (fit.canCook) accent.color else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onCook, shape = CircleShape) { Text("Cook") }
        }
        if (fit.usesExpiring.isNotEmpty() || view.recipe.mood.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                fit.usesExpiring.forEach { Tag("Uses $it before it expires", MaterialTheme.colorScheme.error) }
                view.recipe.mood.forEach { Tag(it, accent.color) }
            }
        }
    }
}

@Composable
private fun Tag(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Shows exactly what cooking takes from the pantry before anything changes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CookSheet(recipe: Recipe, viewModel: StockViewModel, onDone: (CookResult?) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var eaters by remember { mutableStateOf<List<String>>(emptyList()) }
    var guests by remember { mutableIntStateOf(0) }
    var tried by remember { mutableStateOf(false) }
    val people = eaters.size + guests
    val plan = viewModel.planFor(recipe, people.coerceAtLeast(1))
    val members = viewModel.state.collectAsStateWithLifecycle().value.members
    val accent = LocalHouseColors.current.stock

    ModalBottomSheet(onDismissRequest = { onDone(null) }, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Cook ${recipe.title}", style = MaterialTheme.typography.headlineSmall)
            WhoIsEating(members, eaters, guests, { eaters = it }, { guests = it }, showError = tried && people == 0)
            Text(if (people == 0) "From your pantry (per person)" else "From your pantry, for $people", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (plan.deductions.isEmpty()) {
                Text("None of the ingredients are in the pantry yet.", style = MaterialTheme.typography.bodyMedium)
            }
            plan.deductions.forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBlob(Icons.Outlined.Kitchen, accent, size = 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(d.stock.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        when {
                            d.after == null -> "uses some"
                            d.usedUp -> "all used"
                            else -> "${qty(d.stock.amount, d.stock.unit)} → ${qty(d.after, d.stock.unit)}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (d.usedUp || d.runsLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (plan.toBuy.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(accent.container).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.ShoppingCart, contentDescription = null, tint = accent.color)
                    Spacer(Modifier.width(10.dp))
                    Text("Will add to shopping: ${plan.toBuy.joinToString()}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Button(
                onClick = {
                    tried = true
                    if (people > 0) scope.launch { onDone(viewModel.cook(recipe, eaters, guests)) }
                },
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("I cooked this", style = MaterialTheme.typography.titleMedium) }
        }
    }
}
