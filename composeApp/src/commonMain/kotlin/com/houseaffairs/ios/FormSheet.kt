package com.houseaffairs.ios

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.houseaffairs.app.core.model.Member
import com.houseaffairs.app.core.model.Visibility
import com.houseaffairs.app.core.ui.LoadingRow
import com.houseaffairs.app.feature.add.EntryFormView
import com.houseaffairs.app.feature.add.EntryStore
import com.houseaffairs.app.feature.add.EntryType
import com.houseaffairs.app.feature.add.FORM_ONLY_KEYS
import com.houseaffairs.app.feature.add.FormValues
import com.houseaffairs.app.feature.add.missingFields
import com.houseaffairs.app.feature.add.WhoCanSeeEditor
import com.houseaffairs.app.feature.add.formChoices
import com.houseaffairs.app.feature.add.ongoingTripOf
import com.houseaffairs.app.feature.add.visibleToFor
import com.houseaffairs.app.feature.add.whoCanSeeOf
import androidx.compose.runtime.collectAsState

private val entries = EntryStore()
private val formMoney = com.houseaffairs.app.core.data.MoneyRepository()
private val formThings = com.houseaffairs.app.core.data.ThingsRepository()

/** The kind behind a collection the iPhone's lists open; stock is a pantry item unless it says it's a supply. */
internal fun typeFor(collection: String, docType: String? = null): EntryType? = when (collection) {
    "stock" -> if (docType == EntryType.HOUSEHOLD.name) EntryType.HOUSEHOLD else EntryType.GROCERY
    "goals" -> EntryType.GOAL
    else -> EntryType.entries.firstOrNull { it.collection == collection && it.available }
}

/** What opened the form: an entry to edit, or a new one of [type] with [preset] values. */
internal data class FormOpen(val type: EntryType?, val collection: String? = null, val id: String? = null, val preset: FormValues = emptyMap())

/**
 * Android's form on the iPhone (`:ui` EntryFormView), saved through the shared EntryStore as
 * the same documents Android writes. Saving a new one, or deleting, offers Undo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FormSheet(s: SessionState.Ready, members: List<MemberInfo>, actions: Actions, open: FormOpen, onDone: () -> Unit) {
    val hid = s.household.id
    var type by remember { mutableStateOf(open.type) }
    var values by remember { mutableStateOf<FormValues?>(if (open.id == null) open.type?.let { it.defaults(s.uid, s.household.baseCurrency) + open.preset } else null) }
    var visibleTo by remember { mutableStateOf(listOf(Visibility.EVERYONE)) }
    var showErrors by remember { mutableStateOf(false) }
    var who by remember { mutableStateOf(com.houseaffairs.app.feature.add.WhoCanSee.EVERYONE) }
    var chosen by remember { mutableStateOf(listOf<String>()) }
    // The family's cards, trips, dreams and dates for the form's choices, as on Android.
    val money by remember(hid, s.uid) { if (s.me.canAccess(com.houseaffairs.app.core.model.AppModule.MONEY)) formMoney.observeMoney(hid, s.uid) else kotlinx.coroutines.flow.flowOf(null) }.collectAsState(null)
    val events by remember(hid) { formThings.observeEventTitles(hid, s.uid) }.collectAsState(emptyList())
    // The family's own tags, most used first, so the form suggests ones already in use.
    var knownTags by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(hid) {
        knownTags = runCatching {
            com.houseaffairs.app.core.data.SearchRepository().load(hid, s.uid, listOf("transactions", "tasks", "stock", "medicines", "listItems", "events", "trips", "keptAt", "places", "dreams"), com.houseaffairs.app.core.model.RolePolicy.ownMedicinesOnly(s.me.role))
                .flatMap { it.tags }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
        }.getOrDefault(emptyList())
    }
    LaunchedEffect(open) {
        val id = open.id ?: return@LaunchedEffect
        val collection = open.collection ?: type?.collection ?: return@LaunchedEffect
        val kind = typeFor(collection, if (collection == "stock") entries.kindOf(hid, collection, id) else null) ?: return@LaunchedEffect
        type = kind
        entries.load(hid, kind, id)?.let { (v, seen) -> values = v; visibleTo = seen; whoCanSeeOf(seen, s.uid).let { (w, c) -> who = w; chosen = c } }
    }
    var scanNote by remember { mutableStateOf<String?>(null) }
    val people = members.map { Member(it.uid, it.displayName, role = it.role, moduleAccess = it.moduleAccess) }
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val kind = type
        val v = values
        if (kind == null || v == null) { LoadingRow(); return@ModalBottomSheet }
        val editing = open.id != null
        fun save(again: Boolean) {
            if (missingFields(kind, v).isNotEmpty()) { showErrors = true; return }
            val clean = v - FORM_ONLY_KEYS
            val visibleTo = visibleToFor(kind, clean, who, chosen, s.uid, members.map { it.uid }, s.household.privateTopics, editing)
            if (editing) {
                actions.say("Saved") { entries.update(hid, s.uid, kind, open.id!!, clean, visibleTo) }
            } else {
                val title = (kind.titleKey?.let { clean[it] as? String })?.takeIf { it.isNotBlank() } ?: kind.label
                actions.run("$title · saved") { val undo = entries.save(hid, s.uid, kind, clean, visibleTo); ({ undo() }) }
            }
            if (again) { values = kind.defaults(s.uid, s.household.baseCurrency) + open.preset; showErrors = false } else onDone()
        }
        EntryFormView(
            // 📷 fills the form from a receipt, a pack or a label (shared rules, as Android's).
            actions = {
                com.houseaffairs.app.feature.add.scanHintFor(kind)?.let { hint ->
                    ScanButton(hint) { text ->
                        val r = com.houseaffairs.app.feature.add.scannedInto(kind, v, text, today(), s.household.baseCurrency)
                        values = r.values
                        scanNote = r.note
                    }
                }
            },
            note = scanNote?.let { "📷 $it" },
            type = kind, values = v, onChange = { key, value -> values = (values.orEmpty() + (key to value)) },
            members = people, myUid = s.uid, editing = editing,
            onBack = onDone, onSave = { save(again = false) },
            locked = open.preset.keys, showErrors = showErrors, editId = open.id,
            dynamicOptions = formChoices(money, events, today()), ongoingTrip = ongoingTripOf(money, today()),
            knownTags = knownTags,
            moreDetails = { WhoCanSeeEditor(who, { who = it }, chosen, { chosen = it }, people.filter { it.uid != s.uid }, kind.accent(com.houseaffairs.app.core.designsystem.LocalHouseColors.current)) },
            onSaveAnother = { save(again = true) },
            onDelete = open.id?.let { id ->
                {
                    onDone()
                    actions.run("Deleted") { val undo = entries.remove(hid, kind, id) ?: error("Already gone"); ({ undo() }) }
                }
            },
        )
    }
}
