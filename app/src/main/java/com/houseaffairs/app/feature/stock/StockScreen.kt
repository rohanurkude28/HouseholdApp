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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
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
import com.houseaffairs.app.core.ui.DishSheet
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
    onSaved: (com.houseaffairs.app.feature.add.SavedEntry) -> Unit = {},
    viewModel: StockViewModel = hiltViewModel(),
) {
    LaunchedEffect(requestedTab) {
        StockTab.entries.firstOrNull { it.name == requestedTab }?.let { viewModel.selectTab(it); onTabShown() }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalHouseColors.current
    var cooking by remember { mutableStateOf<Recipe?>(null) }
    // A dish opened from an idea or a search: ingredients with ✓/✗.
    var viewing by remember { mutableStateOf<com.houseaffairs.app.core.data.DishIdea?>(null) }
    var showUsage by remember { mutableStateOf(false) }
    // The meal chip picked on the recipe menu; null shows every meal with a few dishes each.
    var mealFilter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var wasting by remember { mutableStateOf<com.houseaffairs.app.core.data.PantryStock?>(null) }
    wasting?.let { item -> com.houseaffairs.app.core.ui.WasteDialog(item.title, onPick = { viewModel.wastePart(item, it); wasting = null }, onDismiss = { wasting = null }) }
    val query by viewModel.query.collectAsStateWithLifecycle()
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

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), state = com.houseaffairs.app.core.ui.rememberTopListState(state.tab), contentPadding = PaddingValues(bottom = 24.dp)) {
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
        // This month's groceries, kinds and waste where the shopping happens; tap for every month.
        if (state.tab == StockTab.PANTRY || state.tab == StockTab.SHOPPING) item {
            com.houseaffairs.app.core.ui.StockMonthCard(
                com.houseaffairs.app.core.data.StockKind.FOOD,
                onOpen = { showUsage = true },
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
            )
        }
        when (state.tab) {
            StockTab.PANTRY -> itemList(
                "pantry", pantry, pantryControls, colors.stock,
                addLabel = "Add groceries", onAdd = onAddGrocery, searchHint = "Find in the pantry…",
                emptyTitle = "Your pantry is empty", emptyBody = "Add what you buy, or tick items off the shopping list and they land here.",
                onToggle = { groups.toggle("pantry:$it") },
            ) { row ->
                CompactRow(row.title, stockDetail(row.item, row.flag), row.flag, onClick = { onEdit(EntryType.GROCERY, row.item.id) }, actions = listOf("Used up" to { viewModel.usedUp(row.item) }, "Threw it away…" to { wasting = row.item }),
                    // Gone off: binning it is the likely next step, so it's right there.
                    quick = if (row.flag == com.houseaffairs.app.core.data.Flag.EXPIRED) "🗑️ Bin it" to { wasting = row.item } else null, leading = com.houseaffairs.app.feature.vault.photoLead(row.item.photos))
            }
            StockTab.RECIPES -> {
                // Find any dish, yours or the library's, and see what you have for it.
                item {
                    androidx.compose.material3.OutlinedTextField(
                        value = query,
                        onValueChange = { viewModel.query.value = it },
                        placeholder = { Text("Search a dish or ingredient…") },
                        singleLine = true,
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
                    )
                }
                if (query.trim().length >= 2) {
                    if (state.search.isEmpty()) item { Empty("No dish matches “$query”", "Save it as a recipe to see what you have for it.") }
                    items(state.search, key = { "search-" + it.recipe.id }) { idea ->
                        Row(
                            Modifier.fillMaxWidth().clickable { viewing = idea }.padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (idea.fit.canCook) "✅" else "🛒", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(idea.recipe.title + if (idea.saved) "" else " · idea", style = MaterialTheme.typography.titleSmall)
                                Text(idea.fit.summary, style = MaterialTheme.typography.bodySmall, color = if (idea.fit.canCook) colors.stock.color else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
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
                    // Your recipes, ranked by what's about to expire.
                    item { Label("Use up what's expiring", "Your recipes that use it, soonest first") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(state.useItUp.take(6)) { idea ->
                                DishCard(idea, onClick = { viewing = idea })
                            }
                        }
                    }
                }
                item { Label("Your recipes", "Dishes your family makes. Ideas, “what can I cook” and cravings all come from these.") }
                item { AddRow("Save a recipe", colors.stock, onAddRecipe) }
                if (state.recipes.isEmpty()) {
                    item { Empty("No recipes yet", "Save a dish with its ingredients. When you cook it, the pantry updates itself.") }
                }
                // A menu, not one long list: a chip per meal, and dishes as tiles two across.
                val hour = java.time.LocalTime.now().hour
                val menu = com.houseaffairs.app.core.data.byMeal(state.recipes, { it.recipe.meal }, { it.recipe.title }, hour)
                val shown = menu.firstOrNull { it.first == mealFilter }
                if (menu.size > 1) item(key = "meal-chips") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = shown == null, onClick = { mealFilter = null }, label = { Text("All  ${state.recipes.size}") }, shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.stock.container, selectedLabelColor = colors.stock.color),
                            )
                        }
                        items(menu, key = { it.first }) { (meal, list) ->
                            FilterChip(
                                selected = shown?.first == meal, onClick = { mealFilter = if (mealFilter == meal) null else meal },
                                label = { Text("${com.houseaffairs.app.core.data.mealEmoji(meal)} $meal  ${list.size}") }, shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.stock.container, selectedLabelColor = colors.stock.color),
                            )
                        }
                    }
                }
                (shown?.let { listOf(it) } ?: menu).forEach { (meal, list) ->
                    val peek = if (shown == null && menu.size > 1) com.houseaffairs.app.core.data.MEAL_PEEK else Int.MAX_VALUE
                    if (menu.size > 1) item(key = "meal-$meal") {
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(com.houseaffairs.app.core.data.mealEmoji(meal), style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.width(8.dp))
                            Text(meal, style = MaterialTheme.typography.titleMedium)
                            if (meal == com.houseaffairs.app.core.data.mealAt(hour)) {
                                Spacer(Modifier.width(8.dp))
                                Tag("Up next", colors.stock.color)
                            }
                            Spacer(Modifier.weight(1f))
                            if (list.size > peek) TextButton(onClick = { mealFilter = meal }) { Text("See all ${list.size}") } else Spacer(Modifier.height(48.dp))
                        }
                    }
                    // A dish on both the lunch and dinner menus needs a key per menu.
                    items(list.take(peek).chunked(2), key = { pair -> "$meal-" + pair.joinToString("-") { it.recipe.id } }) { pair ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { view ->
                                // A tap shows the dish (what you have, Cook, Edit, Delete).
                                RecipeTile(
                                    view, meal, colors.stock, state.lastCooked[view.recipe.title.lowercase()], Modifier.weight(1f),
                                    onOpen = { viewing = com.houseaffairs.app.core.data.DishIdea(view.recipe, saved = true, fit = view.fit, rescues = emptyList()) },
                                ) { cooking = view.recipe }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
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

    if (showUsage) com.houseaffairs.app.core.ui.StockYearSheet(com.houseaffairs.app.core.data.StockKind.FOOD, onDismiss = { showUsage = false })
    viewing?.let { idea ->
        DishSheet(
            idea, state.pantry,
            onCook = if (idea.saved) ({ cooking = idea.recipe }) else null,
            onSave = if (idea.saved) null else ({ onSaveIdea(idea.recipe) }),
            onAddMissing = { names -> viewModel.addMissing(names, onCooked) },
            onDismiss = { viewing = null },
            onEdit = if (idea.saved) ({ onEdit(EntryType.RECIPE, idea.recipe.id) }) else null,
            onDelete = if (idea.saved) ({ viewModel.deleteRecipe(idea.recipe, onSaved) }) else null,
        )
    }

    cooking?.let { recipe ->
        CookSheet(recipe, viewModel, onDone = { result ->
            cooking = null
            result?.let(onCooked)
        })
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
    HaCard(modifier, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The dish's own photo when there is one: food is chosen by eye.
            view.recipe.photos.firstOrNull()?.let { com.houseaffairs.app.core.photo.PhotoThumb(it, 44.dp) }
                ?: Box(Modifier.size(44.dp).clip(CircleShape).background(accent.container), contentAlignment = Alignment.Center) {
                    Text(com.houseaffairs.app.core.data.mealEmoji(meal), style = MaterialTheme.typography.titleLarge)
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
                fit.canCook -> com.houseaffairs.app.core.data.lastMadeText(lastMade, LocalDate.now()) ?: "You have everything"
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
            // The pantry sets the ceiling: no more people than it has ingredients for.
            val pantryNow = viewModel.state.collectAsStateWithLifecycle().value.pantry
            val most = remember(recipe, pantryNow) { com.houseaffairs.app.core.data.servingsPossible(recipe, pantryNow) }
            val full = most != null && most > 0 && people >= most
            WhoIsEating(members, eaters, guests, { if (most == null || most == 0 || it.size + guests <= most) eaters = it }, { if (most == null || most == 0 || eaters.size + it <= most) guests = it }, showError = tried && people == 0, canAddMore = !full)
            when {
                most == 0 -> Text("The pantry doesn't have enough for even one serving yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                most != null && full -> Text("That's all the pantry has: enough for $most. Buy more to cook for more.", style = MaterialTheme.typography.bodySmall, color = accent.color)
                most != null -> Text("The pantry has enough for up to $most", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
            // Not enough at home: say what's short and don't take more out of the pantry than it has.
            if (!plan.enough) {
                Text(
                    listOfNotNull(
                        plan.missing.takeIf { it.isNotEmpty() }?.let { "Missing: " + it.joinToString { i -> i.name.lowercase() } },
                        plan.short.takeIf { it.isNotEmpty() }?.let { "Not enough " + it.joinToString().lowercase() + if (people > 0) " for $people" else "" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                )
                androidx.compose.material3.OutlinedButton(
                    onClick = { viewModel.addMissing(plan.missing.map { it.name } + plan.short) { onDone(it) } },
                    shape = CircleShape, modifier = Modifier.fillMaxWidth(),
                ) { Text("Add what's short to shopping") }
            }
            Button(
                onClick = {
                    tried = true
                    if (people > 0 && plan.enough) scope.launch { onDone(viewModel.cook(recipe, eaters, guests)) }
                },
                enabled = plan.enough,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(if (plan.enough) "I cooked this" else "Not enough in the pantry", style = MaterialTheme.typography.titleMedium) }
        }
    }
}
