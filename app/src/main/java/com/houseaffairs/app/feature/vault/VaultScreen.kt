package com.houseaffairs.app.feature.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.ElectricalServices
import com.houseaffairs.app.core.data.KEPT_KINDS
import com.houseaffairs.app.core.data.ListRow
import androidx.compose.foundation.layout.size
import com.houseaffairs.app.core.data.stockFlag
import com.houseaffairs.app.core.ui.CompactRow
import com.houseaffairs.app.core.ui.itemList
import com.houseaffairs.app.core.ui.ListGroupsViewModel
import com.houseaffairs.app.core.ui.ListToolbar
import com.houseaffairs.app.core.ui.organizedSections
import com.houseaffairs.app.core.ui.rememberListControls
import com.houseaffairs.app.core.ui.rememberOrganized
import com.houseaffairs.app.feature.stock.stockDetail
import java.time.LocalDate
import com.houseaffairs.app.core.data.SUPPLY_TYPES
import com.houseaffairs.app.core.data.grouped
import com.houseaffairs.app.core.data.keptCategory
import com.houseaffairs.app.core.data.keptKindLabel
import com.houseaffairs.app.core.data.medicineCategory
import com.houseaffairs.app.core.data.supplyCategory
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.houseaffairs.app.core.data.KeptItem
import com.houseaffairs.app.core.data.SessionManager
import com.houseaffairs.app.core.data.SessionState
import com.houseaffairs.app.core.data.ThingsRepository
import com.houseaffairs.app.core.data.findKept
import com.houseaffairs.app.core.designsystem.LocalHouseColors
import com.houseaffairs.app.core.ui.HaCard
import com.houseaffairs.app.core.ui.IconBlob
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.LaunchedEffect
import com.houseaffairs.app.core.model.AppModule
import com.houseaffairs.app.feature.add.EntryType
import com.houseaffairs.app.feature.stock.StockViewModel
import com.houseaffairs.app.core.ui.opensEdit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

enum class ThingsTab(val label: String) { MEDICINES("Medicines"), SUPPLIES("Supplies"), KEPT("Where it's kept"), DOCUMENTS("Documents") }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VaultViewModel @Inject constructor(session: SessionManager, things: ThingsRepository) : ViewModel() {
    private val ready = session.state.filterIsInstance<SessionState.Ready>()

    /** Tabs this member's role can open. */
    val tabs: StateFlow<List<ThingsTab>> = ready.map { r ->
        ThingsTab.entries.filter {
            when (it) {
                ThingsTab.MEDICINES -> r.me.canAccess(AppModule.MEDICINE)
                ThingsTab.SUPPLIES -> r.me.canAccess(AppModule.STOCK)
                ThingsTab.KEPT, ThingsTab.DOCUMENTS -> r.me.canAccess(AppModule.VAULT)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tab = MutableStateFlow<ThingsTab?>(null)

    val kept: StateFlow<List<KeptItem>> = ready
        .map { Triple(it.household.id, it.user.uid, it.me.canAccess(AppModule.VAULT)) }
        .distinctUntilChanged()
        .flatMapLatest { (householdId, uid, can) -> if (can) things.observeKept(householdId, uid) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** Things in the house: the medicine cabinet, supplies, where valuables are kept, documents. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VaultScreen(
    onAddKept: () -> Unit,
    onAddMedicine: () -> Unit,
    onAddSupply: () -> Unit,
    onAddDocument: () -> Unit,
    onEdit: (EntryType, String) -> Unit,
    requestedTab: String? = null,
    onTabShown: () -> Unit = {},
    viewModel: VaultViewModel = hiltViewModel(),
    stock: StockViewModel = hiltViewModel(),
) {
    val kept by viewModel.kept.collectAsStateWithLifecycle()
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val picked by viewModel.tab.collectAsStateWithLifecycle()
    val stockState by stock.state.collectAsStateWithLifecycle()
    LaunchedEffect(requestedTab) {
        ThingsTab.entries.firstOrNull { it.name == requestedTab }?.let { viewModel.tab.value = it; onTabShown() }
    }
    val tab = picked?.takeIf { it in tabs } ?: tabs.firstOrNull()
    val colors = LocalHouseColors.current
    val vault = colors.vault
    val groups: ListGroupsViewModel = hiltViewModel()
    val today = LocalDate.now()
    val medControls = rememberListControls()
    val medicines = rememberOrganized(
        "medicines",
        remember(stockState.medicines) { stockState.medicines.map { ListRow(it, it.id, it.title, medicineCategory(it), stockFlag(it, today, soonDays = 30), it.keptAt.orEmpty()) } },
        medControls,
        groups = groups,
    )
    val supControls = rememberListControls()
    val supplies = rememberOrganized(
        "supplies",
        remember(stockState.supplies) { stockState.supplies.map { ListRow(it, it.id, it.title, supplyCategory(it), stockFlag(it, today, soonDays = 30), it.keptAt.orEmpty()) } },
        supControls,
        order = SUPPLY_TYPES,
        groups = groups,
    )
    val keptControls = rememberListControls()
    val keptList = rememberOrganized(
        "kept",
        remember(kept) { kept.map { ListRow(it, it.id, it.title, keptCategory(it), searchText = listOfNotNull(it.location, it.room, it.note).joinToString(" ")) } },
        keptControls,
        order = KEPT_KINDS,
        groups = groups,
        search = { q -> findKept(kept, q).map { it.id }.toSet() },
    )
    val docControls = rememberListControls()
    val docs = rememberOrganized(
        "documents",
        remember(kept) { kept.filter { keptCategory(it) == "Documents" }.map { ListRow(it, it.id, it.title, "Documents", searchText = it.location) } },
        docControls,
        groups = groups,
        search = { q -> findKept(kept, q).map { it.id }.toSet() },
    )

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
                Text("Things", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "${stockState.medicines.size} medicines · ${stockState.supplies.size} supplies · ${kept.size} kept safe",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tabs) { t ->
                    FilterChip(
                        selected = tab == t,
                        onClick = { viewModel.tab.value = t },
                        label = { Text(t.label) },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
                    )
                }
            }
        }
        when (tab) {
            null -> Unit
            ThingsTab.MEDICINES -> itemList(
                "medicines", medicines, medControls, vault,
                addLabel = "Add medicine", onAdd = onAddMedicine, searchHint = "Find a medicine…",
                emptyTitle = "No medicines", emptyBody = "Track what's in the cabinet, who it's for, and when it expires.",
                onToggle = { groups.toggle("medicines:$it") },
            ) { row ->
                CompactRow(row.title, stockDetail(row.item, row.flag), row.flag, onClick = { onEdit(EntryType.MEDICINE, row.item.id) }, actions = listOf("Finished" to { stock.usedUp(row.item, from = "medicines") }))
            }
            ThingsTab.SUPPLIES -> itemList(
                "supplies", supplies, supControls, vault,
                addLabel = "Add household supplies", onAdd = onAddSupply, searchHint = "Find a supply…",
                emptyTitle = "No supplies tracked", emptyBody = "Batteries, sunscreen, detergent… Say “AAA battery 5 units expiry 2028”.",
                onToggle = { groups.toggle("supplies:$it") },
            ) { row ->
                CompactRow(
                    row.title, stockDetail(row.item, row.flag), row.flag,
                    onClick = { onEdit(EntryType.HOUSEHOLD, row.item.id) },
                    actions = listOf("Used 1" to { stock.useOne(row.item) }, "Finished" to { stock.usedUp(row.item) }),
                )
            }
            ThingsTab.KEPT -> itemList(
                "kept", keptList, keptControls, vault,
                addLabel = "Remember where something is kept", onAdd = onAddKept, searchHint = "Where is…  e.g. car keys",
                emptyTitle = "Nothing kept yet", emptyBody = "Say “gold necklace is in the bank locker”, then ask “where is the gold necklace?” any time.",
                onToggle = { groups.toggle("kept:$it") },
                flags = false,
                label = ::keptKindLabel,
                footer = "Only adults in your home see this.",
            ) { row ->
                CompactRow(row.title, "📍 ${row.item.location}", null, onClick = { onEdit(EntryType.KEPT, row.item.id) })
            }
            // Where each paper document is kept, until scans can be filed into Google Drive.
            ThingsTab.DOCUMENTS -> itemList(
                "documents", docs, docControls, vault,
                addLabel = "Remember where a document is kept", onAdd = onAddDocument, searchHint = "Find a document…",
                emptyTitle = "No documents yet", emptyBody = "Say “passports are in the blue file in the wardrobe”, then ask “where are the passports?”.",
                onToggle = { groups.toggle("documents:$it") },
                flags = false,
                footer = "Only adults in your home see this. Coming soon: scan bills, warranties and prescriptions into your Google Drive.",
            ) { row ->
                CompactRow(row.title, "📍 ${row.item.location}", null, onClick = { onEdit(EntryType.KEPT, row.item.id) })
            }
        }
    }
}
