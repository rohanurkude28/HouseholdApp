package com.houseaffairs.ios

import com.houseaffairs.app.feature.stock.cookPart

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.houseaffairs.app.core.data.PantryRepository
import com.houseaffairs.app.core.data.PantryStock
import com.houseaffairs.app.core.data.lastTimeNote
import com.houseaffairs.app.core.time.systemToday
import com.houseaffairs.app.feature.stock.KitchenActions
import com.houseaffairs.app.feature.stock.KitchenLists
import com.houseaffairs.app.feature.stock.KitchenScreen
import com.houseaffairs.app.feature.stock.StockTab

private val kitchen = PantryRepository()

/** The iPhone's Kitchen tab: the same To buy, Pantry and Cook as Android's (from :ui). */
@Composable
internal fun KitchenTab(s: SessionState.Ready, actions: Actions, members: List<MemberInfo>, onAdd: () -> Unit, onAddRecipe: () -> Unit, onEdit: (String, String) -> Unit) {
    val hid = s.household.id
    val uid = s.uid
    // "-openTab KITCHEN/RECIPES" (test runs only) opens on that part.
    var tab by remember { mutableStateOf(StockTab.entries.firstOrNull { it.name == startTab?.substringAfter("KITCHEN/", "") } ?: StockTab.SHOPPING) }
    val pantry by remember(hid, uid) { kitchen.observePantry(hid, uid) }.collectAsState(null)
    val shopping by remember(hid, uid) { kitchen.observeShopping(hid, uid) }.collectAsState(null)
    val history by remember(hid, uid) { kitchen.observeHistory(hid, uid) }.collectAsState(null)
    val recipes by remember(hid, uid) { kitchen.observeRecipes(hid, uid) }.collectAsState(emptyList())
    val lastCooked by remember(hid, uid) { kitchen.observeLastCooked(hid, uid) }.collectAsState(emptyMap())
    val cook = com.houseaffairs.app.feature.stock.rememberCookState()
    val people = members.map { com.houseaffairs.app.core.model.Member(it.uid, it.displayName, role = it.role, moduleAccess = it.moduleAccess) }
    // Kept for this visit; Android saves them per phone.
    var toggled by remember { mutableStateOf(emptySet<String>()) }
    val onList = shopping.orEmpty().map { it.title }
    val memory = history?.second.orEmpty()

    fun usedUp(item: PantryStock, thrown: Boolean = false) =
        actions.run(if (thrown) "${item.title} thrown away" else "${item.title} used up") {
            val added = kitchen.usedUp(hid, uid, item, onList, "stock", thrown, addToList = item.keptStocked)
            ({ kitchen.putBack(hid, item, "stock", added) })
        }

    // From the month view: part (or all) of something in stock went off.
    fun wasteFromMonth(item: PantryStock, share: Int) {
        if (share >= 100) usedUp(item, thrown = true)
        else actions.run("Part of ${item.title} thrown away") { val undo = kitchen.wastePart(hid, uid, item, onList, share, "stock"); ({ undo() }) }
    }

    KitchenScreen(
        tabs = StockTab.entries,
        tab = tab,
        onTab = { tab = it },
        lists = KitchenLists(
            pantry = pantry?.filter { it.id !in actions.hidden },
            shopping = shopping?.filter { it.id !in actions.hidden },
            canCook = remember(recipes, pantry) { com.houseaffairs.app.feature.stock.recipeViews(recipes, pantry.orEmpty(), null, systemToday()).count { it.fit.canCook } },
            lastTime = remember(shopping, memory) { shopping.orEmpty().mapNotNull { item -> lastTimeNote(item.title, memory)?.let { item.id to it } }.toMap() },
        ),
        toggledGroups = toggled,
        actions = KitchenActions(
            add = { onAdd() },
            edit = { t, id -> onEdit(if (t == StockTab.PANTRY) "stock" else "shopping", id) },
            bought = { item -> actions.run("${item.title} · bought, now in the pantry") { val b = kitchen.markBought(hid, uid, item.id); ({ b.undo() }) } },
            usedUp = { usedUp(it) },
            wastePart = { item, share -> wasteFromMonth(item, share) },
            toggleGroup = { key -> toggled = if (key in toggled) toggled - key else toggled + key },
        ),
        today = systemToday(),
        monthCard = { StockUsage(s, com.houseaffairs.app.core.data.StockKind.FOOD) { item, share -> wasteFromMonth(item, share) } },
    ) {
        // Android's Cook (from :ui): the menu by meal, use-it-up dishes, and cooking from the pantry.
        if (pantry == null) { item { com.houseaffairs.app.core.ui.LoadingRow() }; return@KitchenScreen }
        cookPart(cook, pantry.orEmpty(), recipes, lastCooked, nowMinute() / 60, onAddRecipe)
    }
    com.houseaffairs.app.feature.stock.CookSheets(
        cook, pantry.orEmpty(), people,
        onCook = { recipe, eaters, guests ->
            val n = eaters.size + guests
            val plan = com.houseaffairs.app.core.data.planCooking(recipe.ingredients, n.coerceAtLeast(1).toDouble() / recipe.servings.coerceAtLeast(1), pantry.orEmpty())
            if (n > 0 && plan.enough) actions.run("${recipe.title} · cooked") { val r = kitchen.cook(hid, uid, recipe.title, plan, onList, eaters, n); ({ r.undo() }) }
        },
        onAddMissing = { names ->
            val adding = names.filter { n -> onList.none { com.houseaffairs.app.core.data.sameIngredient(it, n) } }
            if (adding.isNotEmpty()) actions.run("${adding.joinToString()} · added to the shopping list") { val undos = adding.map { kitchen.addToShopping(hid, uid, it) }; ({ undos.forEach { it() } }) }
        },
        onEdit = { onEdit("recipes", it.id) },
        onDelete = { r -> actions.run("${r.title} · deleted") { val undo = kitchenEntries.remove(hid, com.houseaffairs.app.feature.add.EntryType.RECIPE, r.id) ?: error("gone"); ({ undo() }) } },
    )
}

private val kitchenEntries = com.houseaffairs.app.feature.add.EntryStore()
