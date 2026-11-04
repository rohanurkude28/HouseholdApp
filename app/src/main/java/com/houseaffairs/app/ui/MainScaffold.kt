package com.houseaffairs.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.houseaffairs.app.core.data.SessionState
import com.houseaffairs.app.core.designsystem.LocalHouseColors
import com.houseaffairs.app.core.model.AppModule
import com.houseaffairs.app.core.model.Member
import com.houseaffairs.app.core.ui.asRecipePreset
import com.houseaffairs.app.feature.home.HomeScreen
import com.houseaffairs.app.feature.add.AddRequest
import com.houseaffairs.app.feature.add.AddSheet
import com.houseaffairs.app.feature.add.EntryType
import com.houseaffairs.app.feature.money.MoneyScreen
import com.houseaffairs.app.feature.plans.PlansScreen
import com.houseaffairs.app.feature.vault.VaultScreen
import com.houseaffairs.app.feature.review.WeeklyReviewScreen
import com.houseaffairs.app.feature.stock.StockScreen
import java.time.LocalDate
import com.houseaffairs.app.feature.settings.SettingsScreen
import kotlinx.coroutines.launch

internal enum class Tab(val route: String, val label: String, val icon: ImageVector, val module: AppModule?) {
    HOME("home", "Home", Icons.Outlined.Home, null),
    MONEY("money", "Money", Icons.Outlined.AccountBalanceWallet, AppModule.MONEY),
    STOCK("stock", "Kitchen", Icons.Outlined.Kitchen, AppModule.STOCK),
    PLANS("plans", "Plans", Icons.Outlined.TaskAlt, AppModule.PLANS),
    VAULT("vault", "Things", Icons.Outlined.Inventory2, AppModule.VAULT),
    ;

    fun visibleTo(member: Member): Boolean = when (module) {
        null -> true
        // Things holds medicines and supplies too, so it shows for anyone with one of them.
        AppModule.VAULT -> member.canAccess(AppModule.VAULT) || member.canAccess(AppModule.MEDICINE) || member.canAccess(AppModule.STOCK)
        else -> member.canAccess(module)
    }
}

private const val SETTINGS_ROUTE = "settings"
private const val REVIEW_ROUTE = "review"

@Composable
fun MainScaffold(session: SessionState.Ready) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val tabs = Tab.entries.filter { it.visibleTo(session.me) }
    val currentTab = tabs.firstOrNull { it.route == currentRoute }
    val colors = LocalHouseColors.current

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var addRequest by remember { mutableStateOf<AddRequest?>(null) }
    val openAdd = { type: EntryType?, preset: Map<String, Any?> -> addRequest = AddRequest(type, preset) }
    val openSettings = { navController.navigate(SETTINGS_ROUTE) }
    // "plans/TASKS" opens Plans on its Tasks tab; the screen clears the request once shown.
    val subTabs = remember { mutableStateMapOf<String, String>() }
    val openEdit = { type: EntryType, id: String -> addRequest = AddRequest(type, editId = id) }
    val openTab = { target: String ->
        val route = target.substringBefore('/')
        target.substringAfter('/', "").takeIf { it.isNotEmpty() }?.let { subTabs[route] = it }
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    // Bumped on every save, so screens that read once (the weekly story) can read again.
    var savedCount by remember { mutableStateOf(0) }
    val showUndo = { message: String, undo: suspend () -> Unit ->
        savedCount++
        // A new tick replaces the last Undo instead of queueing behind it, so its Undo shows at once.
        snackbarHostState.currentSnackbarData?.dismiss()
        scope.launch {
            val result = snackbarHostState.showSnackbar(message = message, actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) undo()
        }
        Unit
    }

    Scaffold(
        // Screens handle the status bar themselves so the Home hero can sit behind it.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (currentTab != null) {
                HaBottomBar(
                    tabs = tabs,
                    selected = currentTab,
                    onSelect = { tab -> openTab(tab.route) },
                    onAdd = { openAdd(null, emptyMap()) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        NavHost(navController, startDestination = Tab.HOME.route, modifier = Modifier.padding(padding)) {
            composable(Tab.HOME.route) {
                HomeScreen(
                    onQuickAdd = { openAdd(null, emptyMap()) },
                    onOpenSettings = openSettings,
                    onOpenTab = { target -> if (tabs.any { it.route == target.substringBefore('/') }) openTab(target) },
                    onAddRecipe = { openAdd(EntryType.RECIPE, it.asRecipePreset()) },
                    onEdit = openEdit,
                    onOpenReview = { navController.navigate(REVIEW_ROUTE) },
                    onSaved = { saved -> showUndo(saved.message) { saved.undo() } },
                )
            }
            composable(REVIEW_ROUTE) {
                WeeklyReviewScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSettings = openSettings,
                    onLogActivity = { openAdd(EntryType.ACTIVITY, emptyMap()) },
                    onEditEntry = openEdit,
                    refreshKey = savedCount,
                )
            }
            composable(Tab.MONEY.route) {
                MoneyScreen(
                    onAddExpense = { openAdd(EntryType.EXPENSE, emptyMap()) },
                    onPlanSpend = { openAdd(EntryType.EXPENSE, mapOf("status" to "Planned", "date" to LocalDate.now().plusWeeks(1))) },
                    onAddSubscription = { openAdd(EntryType.SUBSCRIPTION, emptyMap()) },
                    onAddCredit = { openAdd(EntryType.CREDIT, emptyMap()) },
                    onNewGullak = { openAdd(EntryType.DREAM, mapOf("kind" to "Savings")) },
                    onSaved = { saved -> showUndo(saved.message) { saved.undo() } },
                    onEdit = openEdit,
                    requestedTab = subTabs["money"],
                    onTabShown = { subTabs.remove("money") },
                )
            }
            composable(Tab.STOCK.route) {
                StockScreen(
                    onAddGrocery = { openAdd(EntryType.GROCERY, emptyMap()) },
                    onAddRecipe = { openAdd(EntryType.RECIPE, emptyMap()) },
                    onAddShopping = { openAdd(EntryType.SHOPPING, emptyMap()) },
                    onCooked = { showUndo(it.message, it.undo) },
                    onSaveIdea = { openAdd(EntryType.RECIPE, it.asRecipePreset()) },
                    onEdit = openEdit,
                    requestedTab = subTabs["stock"],
                    onTabShown = { subTabs.remove("stock") },
                )
            }
            composable(Tab.PLANS.route) {
                PlansScreen(
                    onAddDream = { horizon -> openAdd(EntryType.DREAM, horizon?.let { mapOf("horizon" to it) } ?: emptyMap()) },
                    onAddTask = { openAdd(EntryType.TASK, emptyMap()) },
                    onAddEvent = { openAdd(EntryType.EVENT, emptyMap()) },
                    onAddGoal = { openAdd(EntryType.GOAL, emptyMap()) },
                    onAddPlace = { openAdd(EntryType.PLACE, emptyMap()) },
                    // Dated inside the trip, so it counts even when added afterwards.
                    onAddTripExpense = { trip ->
                        val today = LocalDate.now()
                        val date = if (trip.end != null && today.isAfter(trip.end)) trip.end else if (today.isBefore(trip.start)) trip.start else today
                        openAdd(EntryType.EXPENSE, mapOf("trip" to trip.title, "date" to date))
                    },
                    onAddTrip = { dream -> openAdd(EntryType.TRIP, dream?.let { mapOf("forDream" to it, "title" to it) } ?: emptyMap()) },
                    onAddWish = { list -> openAdd(EntryType.WISH, list?.let { mapOf("template" to it.label) } ?: emptyMap()) },
                    onSaved = { saved -> showUndo(saved.message) { saved.undo() } },
                    onEdit = openEdit,
                    requestedTab = subTabs["plans"],
                    onTabShown = { subTabs.remove("plans") },
                )
            }
            composable(Tab.VAULT.route) {
                VaultScreen(
                    onAddKept = { openAdd(EntryType.KEPT, emptyMap()) },
                    onAddMedicine = { openAdd(EntryType.MEDICINE, emptyMap()) },
                    onAddSupply = { openAdd(EntryType.HOUSEHOLD, emptyMap()) },
                    onAddDocument = { openAdd(EntryType.KEPT, mapOf("kind" to "Documents")) },
                    onEdit = openEdit,
                    requestedTab = subTabs["vault"],
                    onTabShown = { subTabs.remove("vault") },
                )
            }
            composable(SETTINGS_ROUTE) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }

    // A link or text shared in from another app opens the right form.
    val shared by com.houseaffairs.app.feature.add.SharedInbox.pending.collectAsState()
    LaunchedEffect(shared) {
        shared?.let { addRequest = it; com.houseaffairs.app.feature.add.SharedInbox.pending.value = null }
    }

    addRequest?.let { request ->
        AddSheet(
            request = request,
            onDismiss = { addRequest = null },
            onSaved = { saved -> showUndo(saved.message) { saved.undo() } },
        )
    }
}
