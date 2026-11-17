package com.houseaffairs.app.feature.vault

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.houseaffairs.app.core.data.DOC_TYPES
import com.houseaffairs.app.core.data.KeptItem
import com.houseaffairs.app.core.data.SessionManager
import com.houseaffairs.app.core.data.SessionState
import com.houseaffairs.app.core.data.StockKind
import com.houseaffairs.app.core.data.ThingsRepository
import com.houseaffairs.app.core.model.AppModule
import com.houseaffairs.app.core.ui.ListGroupsViewModel
import com.houseaffairs.app.core.ui.StockMonthCard
import com.houseaffairs.app.core.ui.StockYearSheet
import com.houseaffairs.app.feature.add.EntryType
import com.houseaffairs.app.feature.stock.StockViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

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

/**
 * Android's Things tab: the shared [ThingsScreen] (in :ui, also the iPhone's), fed from Hilt, plus
 * what only Android has: Photos, Drive uploads and the month and year views.
 */
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
    val context = androidx.compose.ui.platform.LocalContext.current
    // Documents: pick a file, then name it and choose its Drive folder.
    val drive: com.houseaffairs.app.core.drive.DriveViewModel = hiltViewModel()
    var pickedFile by remember { mutableStateOf<android.net.Uri?>(null) }
    var showGallery by remember { mutableStateOf(false) }
    if (showGallery) com.houseaffairs.app.core.photo.GallerySheet(onDismiss = { showGallery = false }, onOpenEntry = { type, id -> onEdit(EntryType.valueOf(type), id) })
    val pickFile = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { pickedFile = it }
    // The year for medicines or supplies, whichever tab it was opened from.
    var showUsage by remember { mutableStateOf<StockKind?>(null) }
    showUsage?.let { kind -> StockYearSheet(kind, onDismiss = { showUsage = null }) }
    pickedFile?.let { uri -> com.houseaffairs.app.core.drive.UploadDocumentSheet(uri, drive) { pickedFile = null; drive.clearMessage() } }
    LaunchedEffect(requestedTab) {
        ThingsTab.entries.firstOrNull { it.name == requestedTab }?.let { viewModel.tab.value = it; onTabShown() }
    }
    val groups: ListGroupsViewModel = hiltViewModel()
    val toggled by groups.toggled.collectAsStateWithLifecycle()
    val actions = remember(stock, groups) {
        ThingsActions(
            add = { t ->
                when (t) {
                    ThingsTab.MEDICINES -> onAddMedicine()
                    ThingsTab.SUPPLIES -> onAddSupply()
                    ThingsTab.KEPT -> onAddKept()
                    ThingsTab.DOCUMENTS -> pickFile.launch(arrayOf("*/*"))
                }
            },
            edit = { t, id -> onEdit(when (t) { ThingsTab.MEDICINES -> EntryType.MEDICINE; ThingsTab.SUPPLIES -> EntryType.HOUSEHOLD; else -> EntryType.KEPT }, id) },
            usedUp = { item, from -> stock.usedUp(item, from = from) },
            useOne = { stock.useOne(it) },
            skipToday = { stock.skipToday(it) },
            wastePart = { item, share, from -> stock.wastePart(item, share, from) },
            toggleGroup = { groups.toggle(it) },
            shareLink = { item: KeptItem ->
                context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, "${item.title}: ${item.link}"), null))
            },
            noteDocument = onAddDocument,
        )
    }
    ThingsScreen(
        tabs = tabs,
        tab = picked?.takeIf { it in tabs } ?: tabs.firstOrNull(),
        onTab = { viewModel.tab.value = it },
        lists = ThingsLists(
            medicines = stockState.medicines.takeIf { stockState.loaded },
            supplies = stockState.supplies.takeIf { stockState.loaded },
            kept = kept,
        ),
        toggledGroups = toggled,
        actions = actions,
        today = LocalDate.now(),
        modifier = Modifier.statusBarsPadding(),
        docOrder = DOC_TYPES,
        // Every picture the family added, from every list.
        headerAction = { TextButton(onClick = { showGallery = true }) { Text("📷 Photos") } },
        monthCard = { t ->
            val kind = if (t == ThingsTab.MEDICINES) StockKind.MEDICINES else StockKind.SUPPLIES
            StockMonthCard(kind, onOpen = { showUsage = kind }, modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp))
        },
        photos = { ids -> com.houseaffairs.app.core.photo.RowPhotos(ids) },
    )
}

/** A small photo at the start of a row when the item has one; tapping it opens it full screen. */
internal fun photoLead(ids: List<String>): (@Composable () -> Unit)? =
    if (ids.isEmpty()) null else ({ com.houseaffairs.app.core.photo.RowPhotos(ids) })
