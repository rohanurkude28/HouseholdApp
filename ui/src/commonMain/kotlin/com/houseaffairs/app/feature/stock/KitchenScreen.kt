package com.houseaffairs.app.feature.stock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.Aisle
import com.houseaffairs.app.core.data.Flag
import com.houseaffairs.app.core.data.ListRow
import com.houseaffairs.app.core.data.PantryStock
import com.houseaffairs.app.core.data.ShoppingEntry
import com.houseaffairs.app.core.data.pantryCategory
import com.houseaffairs.app.core.data.qty
import com.houseaffairs.app.core.data.stockDetail
import com.houseaffairs.app.core.data.stockFlag
import com.houseaffairs.app.core.designsystem.LocalHouseColors
import com.houseaffairs.app.core.time.LocalDate
import com.houseaffairs.app.core.ui.CompactRow
import com.houseaffairs.app.core.ui.LoadingRow
import com.houseaffairs.app.core.ui.SubTabs
import com.houseaffairs.app.core.ui.TickCircle
import com.houseaffairs.app.core.ui.WasteDialog
import com.houseaffairs.app.core.ui.itemList
import com.houseaffairs.app.core.ui.rememberListControls
import com.houseaffairs.app.core.ui.rememberOrganized
import com.houseaffairs.app.core.ui.rememberTopListState

/** To buy first: the most everyday kitchen job. */
enum class StockTab(val label: String) { SHOPPING("To buy"), PANTRY("Pantry"), RECIPES("Cook") }

/** What the Kitchen tab lists. Null lists are still loading, so "Nothing to buy" is never said too early. */
class KitchenLists(
    val pantry: List<PantryStock>?,
    val shopping: List<ShoppingEntry>?,
    /** How many saved dishes can be cooked now, when the phone knows. */
    val canCook: Int? = null,
    /** "Last time: Amul 👍" for shopping items, by their id. */
    val lastTime: Map<String, String> = emptyMap(),
)

/** What the Kitchen tab asks its phone to do. */
class KitchenActions(
    val add: (StockTab) -> Unit,
    /** Opens a pantry item (PANTRY) or a shopping item (SHOPPING) to edit. */
    val edit: (StockTab, String) -> Unit,
    /** In the cart: off the list and into the pantry. */
    val bought: (ShoppingEntry) -> Unit,
    val usedUp: (PantryStock) -> Unit,
    /** [share] of it (25…100) went to waste. */
    val wastePart: (PantryStock, Int) -> Unit,
    val toggleGroup: (String) -> Unit,
)

/**
 * The Kitchen tab, the same on both phones: To buy and Pantry are drawn here; Cook is each phone's
 * own for now ([cook]), as are the month card and photos.
 */
@Composable
fun KitchenScreen(
    tabs: List<StockTab>,
    tab: StockTab,
    onTab: (StockTab) -> Unit,
    lists: KitchenLists,
    toggledGroups: Set<String>,
    actions: KitchenActions,
    today: LocalDate,
    modifier: Modifier = Modifier,
    monthCard: (@Composable () -> Unit)? = null,
    photos: (@Composable (List<String>) -> Unit)? = null,
    cook: LazyListScope.() -> Unit,
) {
    val accent = LocalHouseColors.current.stock
    var wasting by remember { mutableStateOf<PantryStock?>(null) }
    wasting?.let { item -> WasteDialog(item.title, onPick = { actions.wastePart(item, it); wasting = null }, onDismiss = { wasting = null }) }
    val pantryItems = lists.pantry.orEmpty()
    val shoppingItems = lists.shopping.orEmpty()
    val pantryControls = rememberListControls()
    val pantry = rememberOrganized(
        "pantry",
        remember(pantryItems, today) { pantryItems.map { ListRow(it, it.id, it.title, pantryCategory(it).label, stockFlag(it, today), it.keptAt.orEmpty()) } },
        pantryControls, toggledGroups,
        order = Aisle.FOOD.map { it.label },
    )
    val shoppingControls = rememberListControls()
    // While shopping you want to see everything, so aisles never start closed.
    val shopping = rememberOrganized(
        "shopping",
        remember(shoppingItems) { shoppingItems.map { ListRow(it, it.id, it.title, it.aisle.label) } },
        shoppingControls, toggledGroups,
        order = Aisle.entries.map { it.label },
        openUpTo = Int.MAX_VALUE,
    )
    val loaded = lists.pantry != null && lists.shopping != null

    LazyColumn(modifier.fillMaxSize(), state = rememberTopListState(tab), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
                Text("Kitchen", style = MaterialTheme.typography.headlineLarge)
                Text(
                    if (!loaded) "Getting your kitchen…"
                    else listOfNotNull("${pantryItems.size} in the pantry", "${shoppingItems.size} to buy", lists.canCook?.let { "$it you can cook now" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { SubTabs(tabs, tab, { it.label }, onTab) }
        if (tab == StockTab.RECIPES) {
            cook()
            return@LazyColumn
        }
        // Still loading: no "Nothing to buy" or "Your pantry is empty" that isn't true.
        if (!loaded) {
            item { LoadingRow() }
            return@LazyColumn
        }
        // This month's groceries, kinds and waste where the shopping happens; tap for every month.
        if (monthCard != null) item(key = "kitchen-month") { monthCard() }
        when (tab) {
            StockTab.PANTRY -> itemList(
                "pantry", pantry, pantryControls, accent,
                addLabel = "Add groceries", onAdd = { actions.add(tab) }, searchHint = "Find in the pantry…",
                emptyTitle = "Your pantry is empty", emptyBody = "Add what you buy, or tick items off the shopping list and they land here.",
                onToggle = { actions.toggleGroup("pantry:$it") },
            ) { row ->
                CompactRow(
                    row.title, stockDetail(row.item, row.flag, today), row.flag,
                    onClick = { actions.edit(tab, row.item.id) },
                    actions = listOf("Used up" to { actions.usedUp(row.item) }, "Threw it away…" to { wasting = row.item }),
                    // Gone off: binning it is the likely next step, so it's right there.
                    quick = if (row.flag == Flag.EXPIRED) "🗑️ Bin it" to { wasting = row.item } else null,
                    leading = if (row.item.photos.isEmpty() || photos == null) null else ({ photos(row.item.photos) }),
                )
            }
            StockTab.SHOPPING -> itemList(
                "shopping", shopping, shoppingControls, accent,
                addLabel = "Add to shopping list", onAdd = { actions.add(tab) }, searchHint = "Find on the list…",
                emptyTitle = "Nothing to buy", emptyBody = "Things that run low or out after cooking are added here for you.",
                onToggle = { actions.toggleGroup("shopping:$it") },
                flags = false,
                footer = "Tap the circle when it's in the cart: it moves to your pantry. Tap the name to edit.".takeIf { shoppingItems.isNotEmpty() },
            ) { row ->
                val item = row.item
                // The circle ticks it off (into the pantry, with Undo); the row opens it for editing.
                CompactRow(
                    item.title, listOfNotNull(qty(item.amount, item.unit ?: "").takeIf { it.isNotEmpty() }, lists.lastTime[item.id]).joinToString(" · "), null,
                    onClick = { actions.edit(tab, item.id) },
                    leading = { TickCircle(Color(item.aisle.argb), onTick = { actions.bought(item) }, size = 22.dp, contentDescription = "Bought ${item.title}") },
                )
            }
            StockTab.RECIPES -> Unit
        }
    }
}
