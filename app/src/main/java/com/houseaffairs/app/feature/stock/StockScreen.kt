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
import com.houseaffairs.app.core.data.qty
import com.houseaffairs.app.core.data.stockDetail
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
    val cook = rememberCookState()
    var showUsage by remember { mutableStateOf(false) }
    val groups: ListGroupsViewModel = hiltViewModel()
    val toggled by groups.toggled.collectAsStateWithLifecycle()
    val actions = remember(viewModel, groups) {
        KitchenActions(
            add = { t -> if (t == StockTab.PANTRY) onAddGrocery() else onAddShopping() },
            edit = { t, id -> onEdit(if (t == StockTab.PANTRY) EntryType.GROCERY else EntryType.SHOPPING, id) },
            bought = { viewModel.markBought(it.id, onCooked) },
            usedUp = { viewModel.usedUp(it) },
            wastePart = { item, share -> viewModel.wastePart(item, share) },
            toggleGroup = { groups.toggle(it) },
        )
    }

    // To buy and Pantry are the shared KitchenScreen (in :ui, also the iPhone's); Cook is Android's own for now.
    KitchenScreen(
        tabs = state.tabs,
        tab = state.tab,
        onTab = viewModel::selectTab,
        lists = KitchenLists(
            pantry = state.pantry.takeIf { state.loaded },
            shopping = state.shopping.takeIf { state.loaded },
            canCook = state.recipes.count { it.fit.canCook },
            lastTime = state.lastTime,
        ),
        toggledGroups = toggled,
        actions = actions,
        today = LocalDate.now(),
        modifier = Modifier.statusBarsPadding(),
        monthCard = {
            com.houseaffairs.app.core.ui.StockMonthCard(
                com.houseaffairs.app.core.data.StockKind.FOOD,
                onOpen = { showUsage = true },
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
            )
        },
        photos = { ids -> com.houseaffairs.app.core.photo.RowPhotos(ids) },
    ) {
        if (!state.loaded) {
            item { com.houseaffairs.app.core.ui.LoadingRow() }
            return@KitchenScreen
        }
        // Cook is shared too (:ui CookPart.kt, also the iPhone's).
        cookPart(cook, state.pantry, state.recipes.map { it.recipe }, state.lastCooked, java.time.LocalTime.now().hour, onAddRecipe)
    }

    if (showUsage) com.houseaffairs.app.core.ui.StockYearSheet(com.houseaffairs.app.core.data.StockKind.FOOD, onDismiss = { showUsage = false })
    CookSheets(
        cook, state.pantry, state.members,
        onCook = { r, eaters, guests -> viewModel.cook(r, eaters, guests)?.let(onCooked) },
        onAddMissing = { names -> viewModel.addMissing(names, onCooked) },
        onEdit = { onEdit(EntryType.RECIPE, it.id) },
        onDelete = { viewModel.deleteRecipe(it, onSaved) },
        onSaveIdea = onSaveIdea,
    )
}
