package com.houseaffairs.app.feature.vault

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.Flag
import com.houseaffairs.app.core.data.KEPT_KINDS
import com.houseaffairs.app.core.data.KeptItem
import com.houseaffairs.app.core.data.ListRow
import com.houseaffairs.app.core.data.PantryStock
import com.houseaffairs.app.core.data.SUPPLY_TYPES
import com.houseaffairs.app.core.data.findKept
import com.houseaffairs.app.core.data.keptCategory
import com.houseaffairs.app.core.data.keptKindLabel
import com.houseaffairs.app.core.data.medicineCategory
import com.houseaffairs.app.core.data.stockDetail
import com.houseaffairs.app.core.data.stockFlag
import com.houseaffairs.app.core.data.supplyCategory
import com.houseaffairs.app.core.designsystem.LocalHouseColors
import com.houseaffairs.app.core.time.LocalDate
import com.houseaffairs.app.core.ui.CompactRow
import com.houseaffairs.app.core.ui.LoadingRow
import com.houseaffairs.app.core.ui.SubTabs
import com.houseaffairs.app.core.ui.WasteDialog
import com.houseaffairs.app.core.ui.itemList
import com.houseaffairs.app.core.ui.rememberListControls
import com.houseaffairs.app.core.ui.rememberOrganized
import com.houseaffairs.app.core.ui.rememberTopListState

enum class ThingsTab(val label: String) { MEDICINES("Medicines"), SUPPLIES("Supplies"), KEPT("Where it's kept"), DOCUMENTS("Documents") }

/** What the Things tab shows. A null list is still loading, so it never says "No medicines" too early. */
class ThingsLists(val medicines: List<PantryStock>?, val supplies: List<PantryStock>?, val kept: List<KeptItem>?)

/** What the Things tab asks its phone to do; each phone saves through its own wiring. */
class ThingsActions(
    val add: (ThingsTab) -> Unit,
    /** Opens the form for an item; the tab says which kind (medicine, supply, or where it's kept). */
    val edit: (ThingsTab, String) -> Unit,
    /** [from] is the collection: "medicines" or "stock". */
    val usedUp: (PantryStock, String) -> Unit,
    val useOne: (PantryStock) -> Unit,
    val skipToday: (PantryStock) -> Unit,
    /** [share] of it (25…100) went to waste. */
    val wastePart: (PantryStock, Int, String) -> Unit,
    val toggleGroup: (String) -> Unit,
    /** Only where the phone can share a Drive link. */
    val shareLink: ((KeptItem) -> Unit)? = null,
    val noteDocument: () -> Unit = {},
)

/**
 * Things in the house, the same on both phones: the medicine cabinet, supplies, where valuables
 * are kept and documents. Slots hold what only one phone has (Photos, the month card).
 */
@Composable
fun ThingsScreen(
    tabs: List<ThingsTab>,
    tab: ThingsTab?,
    onTab: (ThingsTab) -> Unit,
    lists: ThingsLists,
    toggledGroups: Set<String>,
    actions: ThingsActions,
    today: LocalDate,
    modifier: Modifier = Modifier,
    /** Drive's folders, so documents are grouped in that order. */
    docOrder: List<String> = emptyList(),
    headerAction: (@Composable () -> Unit)? = null,
    monthCard: (@Composable (ThingsTab) -> Unit)? = null,
    photos: (@Composable (List<String>) -> Unit)? = null,
) {
    val vault = LocalHouseColors.current.vault
    val uri = LocalUriHandler.current
    var wasting by remember { mutableStateOf<Pair<PantryStock, String>?>(null) }
    wasting?.let { (item, from) -> WasteDialog(item.title, onPick = { actions.wastePart(item, it, from); wasting = null }, onDismiss = { wasting = null }) }
    val lead: (List<String>) -> (@Composable () -> Unit)? = { ids -> if (ids.isEmpty() || photos == null) null else ({ photos(ids) }) }

    val medicines = lists.medicines.orEmpty()
    val supplies = lists.supplies.orEmpty()
    val kept = lists.kept.orEmpty()
    val medControls = rememberListControls()
    val medList = rememberOrganized(
        "medicines",
        remember(medicines, today) { medicines.map { ListRow(it, it.id, it.title, medicineCategory(it), stockFlag(it, today, soonDays = 30), it.keptAt.orEmpty()) } },
        medControls, toggledGroups,
    )
    val supControls = rememberListControls()
    val supList = rememberOrganized(
        "supplies",
        remember(supplies, today) { supplies.map { ListRow(it, it.id, it.title, supplyCategory(it), stockFlag(it, today, soonDays = 30), it.keptAt.orEmpty()) } },
        supControls, toggledGroups, order = SUPPLY_TYPES,
    )
    val keptControls = rememberListControls()
    val keptList = rememberOrganized(
        "kept",
        remember(kept) { kept.map { ListRow(it, it.id, it.title, keptCategory(it), searchText = listOfNotNull(it.location, it.room, it.note).joinToString(" ")) } },
        keptControls, toggledGroups, order = KEPT_KINDS,
        search = { q -> findKept(kept, q).map { it.id }.toSet() },
    )
    val docControls = rememberListControls()
    val docs = rememberOrganized(
        "documents",
        // Grouped by Drive folder (IDs, Insurance…); papers noted without a file sit under "Kept at home".
        remember(kept) { kept.filter { keptCategory(it) == "Documents" }.map { ListRow(it, it.id, it.title, it.docType ?: "Kept at home", searchText = listOfNotNull(it.location, it.note, it.docType).joinToString(" ")) } },
        docControls, toggledGroups, order = docOrder,
        search = { q -> findKept(kept, q).map { it.id }.toSet() },
    )

    LazyColumn(modifier.fillMaxSize(), state = rememberTopListState(tab), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Things", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                    headerAction?.invoke()
                }
                Text(
                    "${medicines.size} medicines · ${supplies.size} supplies · ${kept.size} kept safe",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { SubTabs(tabs, tab, { it?.label.orEmpty() }, { it?.let(onTab) }) }
        val loading = when (tab) {
            ThingsTab.MEDICINES -> lists.medicines == null
            ThingsTab.SUPPLIES -> lists.supplies == null
            ThingsTab.KEPT, ThingsTab.DOCUMENTS -> lists.kept == null
            null -> false
        }
        if (loading) {
            item { LoadingRow() }
            return@LazyColumn
        }
        // This month for medicines or supplies: what came home, what ran out, what was thrown away.
        if (monthCard != null && (tab == ThingsTab.MEDICINES || tab == ThingsTab.SUPPLIES)) item(key = "month-$tab") { monthCard(tab) }
        when (tab) {
            null -> Unit
            ThingsTab.MEDICINES -> itemList(
                "medicines", medList, medControls, vault,
                addLabel = "Add medicine", onAdd = { actions.add(tab) }, searchHint = "Find a medicine…",
                emptyTitle = "No medicines", emptyBody = "Track what's in the cabinet, who it's for, and when it expires.",
                onToggle = { actions.toggleGroup("medicines:$it") },
            ) { row ->
                // Taken on a schedule: how much a day, what's left and when it runs out, instead of the expiry.
                val detail = stockDetail(row.item, row.flag, today)
                CompactRow(row.title, if (row.flag == null) row.item.schedule ?: detail else detail, row.flag, onClick = { actions.edit(tab, row.item.id) },
                    actions = listOfNotNull(
                        row.item.schedule?.let { "Skip today" to { actions.skipToday(row.item) } },
                        "Used up" to { actions.usedUp(row.item, "medicines") }, "Threw it away…" to { wasting = row.item to "medicines" },
                    ),
                    quick = if (row.flag == Flag.EXPIRED) "🗑️ Bin it" to { wasting = row.item to "medicines" } else null, leading = lead(row.item.photos))
            }
            ThingsTab.SUPPLIES -> itemList(
                "supplies", supList, supControls, vault,
                addLabel = "Add household supplies", onAdd = { actions.add(tab) }, searchHint = "Find a supply…",
                emptyTitle = "No supplies tracked", emptyBody = "Batteries, sunscreen, detergent… Say “AAA battery 5 units expiry 2028”.",
                onToggle = { actions.toggleGroup("supplies:$it") },
            ) { row ->
                CompactRow(
                    row.title, stockDetail(row.item, row.flag, today), row.flag,
                    onClick = { actions.edit(tab, row.item.id) },
                    actions = listOf("Used 1" to { actions.useOne(row.item) }, "Used up" to { actions.usedUp(row.item, "stock") }, "Threw it away…" to { wasting = row.item to "stock" }),
                    quick = if (row.flag == Flag.EXPIRED) "🗑️ Bin it" to { wasting = row.item to "stock" } else null,
                    leading = lead(row.item.photos),
                )
            }
            ThingsTab.KEPT -> itemList(
                "kept", keptList, keptControls, vault,
                addLabel = "Remember where something is kept", onAdd = { actions.add(tab) }, searchHint = "Where is…  e.g. car keys",
                emptyTitle = "Nothing kept yet", emptyBody = "Say “gold necklace is in the bank locker”, then ask “where is the gold necklace?” any time.",
                onToggle = { actions.toggleGroup("kept:$it") },
                flags = false,
                label = ::keptKindLabel,
                footer = "Only adults in your home see this.",
            ) { row ->
                CompactRow(row.title, "📍 ${row.item.location}", null, onClick = { actions.edit(tab, row.item.id) }, leading = lead(row.item.photos))
            }
            ThingsTab.DOCUMENTS -> itemList(
                "documents", docs, docControls, vault,
                addLabel = "Upload a document to Google Drive", onAdd = { actions.add(tab) }, searchHint = "Find a document…",
                emptyTitle = "No documents yet", emptyBody = "Upload passports, policies and bills into Drive › HouseAffairs, or note where a paper is kept: “passports are in the blue file”.",
                onToggle = { actions.toggleGroup("documents:$it") },
                flags = false,
                footer = "Files go to Google Drive › HouseAffairs › Documents, one folder per type. Tap one to open it.",
            ) { row ->
                val link = row.item.link
                CompactRow(
                    row.title, if (link != null) "📄 Drive · ${row.item.docType ?: "Documents"}" else "📍 ${row.item.location}", null,
                    // A Drive file opens in Drive; a paper note opens its form.
                    onClick = { if (link != null) uri.openUri(link) else actions.edit(tab, row.item.id) },
                    actions = listOfNotNull(
                        if (link != null) actions.shareLink?.let { share -> "Share link" to { share(row.item) } } else null,
                        "Edit" to { actions.edit(tab, row.item.id) },
                        "Note a paper copy" to actions.noteDocument,
                    ),
                )
            }
        }
    }
}
