package com.houseaffairs.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.houseaffairs.app.core.data.PantryRepository
import com.houseaffairs.app.core.data.PantryStock
import com.houseaffairs.app.core.model.RolePolicy
import com.houseaffairs.app.core.data.ThingsRepository
import com.houseaffairs.app.core.model.AppModule
import com.houseaffairs.app.core.time.systemToday
import com.houseaffairs.app.feature.vault.ThingsActions
import com.houseaffairs.app.feature.vault.ThingsLists
import com.houseaffairs.app.feature.vault.ThingsScreen
import com.houseaffairs.app.feature.vault.ThingsTab
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private val pantry = PantryRepository()
private val things = ThingsRepository()

/** The iPhone's Things tab: the same screen as Android's (from :ui), fed from the shared repositories. */
@Composable
internal fun ThingsTab(s: SessionState.Ready, actions: Actions, onAdd: () -> Unit, onEdit: (String, String) -> Unit) {
    val hid = s.household.id
    val uid = s.uid
    // Documents are kept in Google Drive; adding one picks a file and uploads it.
    val tabs = listOfNotNull(
        ThingsTab.MEDICINES.takeIf { s.me.canAccess(AppModule.MEDICINE) },
        ThingsTab.SUPPLIES.takeIf { s.me.canAccess(AppModule.STOCK) },
        ThingsTab.KEPT.takeIf { s.me.canAccess(AppModule.VAULT) },
        ThingsTab.DOCUMENTS.takeIf { s.me.canAccess(AppModule.VAULT) },
    )
    // "-openTab THINGS/DOCUMENTS" (test runs only) opens on that part.
    var tab by remember { mutableStateOf(tabs.firstOrNull { it.name == startTab?.substringAfter("THINGS/", "")?.substringBefore("/") } ?: tabs.firstOrNull()) }
    var picked by remember { mutableStateOf<Pair<String, String>?>(null) }
    var uploading by remember { mutableStateOf<String?>(null) }
    var uploadError by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Test runs ("-openTab THINGS/DOCUMENTS/ADD" with -pickFile) start adding a document on opening.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (startTab == "THINGS/DOCUMENTS/ADD") filePicker?.pick { path, name -> if (path != null) picked = path to (name ?: "File") }
    }
    val medicines by remember(hid, uid, s.me) {
        if (s.me.canAccess(AppModule.MEDICINE)) pantry.observeMedicines(hid, uid, RolePolicy.ownMedicinesOnly(s.me.role)) else flowOf(emptyList())
    }.collectAsState(null)
    val supplies by remember(hid, uid, s.me) { if (s.me.canAccess(AppModule.STOCK)) pantry.observeSupplies(hid, uid) else flowOf(emptyList()) }.collectAsState(null)
    val kept by remember(hid, uid, s.me) { if (s.me.canAccess(AppModule.VAULT)) things.observeKept(hid, uid) else flowOf(emptyList()) }.collectAsState(null)
    val shopping by remember(hid, uid) { pantry.observeShopping(hid, uid) }.collectAsState(emptyList())
    // Kept for this visit; Android saves them per phone.
    var toggled by remember { mutableStateOf(emptySet<String>()) }
    val onList = shopping.map { it.title }

    fun usedUp(item: PantryStock, from: String, thrown: Boolean = false) =
        actions.run(if (thrown) "${item.title} thrown away" else "${item.title} used up") {
            val added = pantry.usedUp(hid, uid, item, onList, from, thrown, addToList = item.keptStocked)
            ({ pantry.putBack(hid, item, from, added) })
        }

    fun wastePart(item: PantryStock, share: Int, from: String) {
        if (share >= 100) usedUp(item, from, thrown = true)
        else actions.run("Part of ${item.title} thrown away") { val undo = pantry.wastePart(hid, uid, item, onList, share, from); ({ undo() }) }
    }

    ThingsScreen(
        tabs = tabs,
        tab = tab?.takeIf { it in tabs } ?: tabs.firstOrNull(),
        onTab = { tab = it },
        lists = ThingsLists(
            medicines?.filter { it.id !in actions.hidden },
            supplies?.filter { it.id !in actions.hidden },
            kept?.filter { it.id !in actions.hidden },
        ),
        toggledGroups = toggled,
        actions = ThingsActions(
            add = { t -> if (t == ThingsTab.DOCUMENTS) filePicker?.pick { path, name -> if (path != null) picked = path to (name ?: "File") } else onAdd() },
            edit = { t, id -> onEdit(when (t) { ThingsTab.MEDICINES -> "medicines"; ThingsTab.SUPPLIES -> "stock"; else -> "keptAt" }, id) },
            usedUp = { item, from -> usedUp(item, from) },
            useOne = { item -> actions.say("Used one ${item.title}") { pantry.useOne(hid, uid, item, onList) } },
            skipToday = { item -> actions.say("Skipping ${item.title} today") { pantry.skipDose(hid, item.id, systemToday()) } },
            wastePart = { item, share, from -> wastePart(item, share, from) },
            toggleGroup = { key -> toggled = if (key in toggled) toggled - key else toggled + key },
        ),
        today = systemToday(),
        docOrder = com.houseaffairs.app.core.data.DOC_TYPES,
        // This month for medicines or supplies, opening the year (as Android's).
        monthCard = { t ->
            val kind = if (t == ThingsTab.MEDICINES) com.houseaffairs.app.core.data.StockKind.MEDICINES else com.houseaffairs.app.core.data.StockKind.SUPPLIES
            StockUsage(s, kind) { item, share -> wastePart(item, share, if (t == ThingsTab.MEDICINES) "medicines" else "stock") }
        },
    )
    picked?.let { (path, name) ->
        com.houseaffairs.app.feature.vault.UploadDocumentSheet(
            name, uploading, uploadError,
            onUpload = { title, docType, byLink, onlyMe ->
                uploading = "Uploading to Google Drive…"
                uploadError = null
                scope.launch {
                    runCatching {
                        val bytes = readFileBytes(path) ?: error("Couldn't read that file")
                        uploadDocument(s, bytes, name, title, docType, byLink, if (onlyMe) listOf(uid) else com.houseaffairs.app.core.model.Visibility.everyone)
                    }.onSuccess { picked = null; actions.say("$title · saved to Drive") {} }
                        .onFailure { uploadError = it.message ?: "Upload failed" }
                    uploading = null
                }
            },
            onDismiss = { picked = null; uploadError = null },
        )
    }
}
