package com.houseaffairs.app.feature.home

import com.houseaffairs.app.core.data.line
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.houseaffairs.app.core.data.FOUND_PEEK
import com.houseaffairs.app.core.data.foundLine
import com.houseaffairs.app.core.data.searchWords
import com.houseaffairs.app.core.designsystem.LocalHouseColors
import com.houseaffairs.app.core.ui.CompactRow
import com.houseaffairs.app.feature.add.EntryType

/**
 * Search across the whole home: type a name or a topic ("travel") and see every trip, spend, list
 * item, document and task about it, grouped by kind. Tapping one opens it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSheet(
    onDismiss: () -> Unit,
    onEdit: (EntryType, String) -> Unit,
    onOpenTab: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    // Groups the person asked to see in full, forgotten when the words change.
    var opened by remember(state.query) { mutableStateOf(setOf<String>()) }
    LaunchedEffect(Unit) { viewModel.open(); focus.requestFocus() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::ask,
                    placeholder = { Text("Search… “petrol”, “Riya”, “travel”", maxLines = 1) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) IconButton(onClick = { viewModel.ask("") }) { Icon(Icons.Outlined.Close, contentDescription = "Clear") }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { viewModel.remember() }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).focusRequester(focus),
                )
            }
            if (searchWords(state.query).isEmpty()) {
                // Before typing: what was searched lately, then topics to try.
                if (recent.isNotEmpty()) item {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        androidx.compose.material3.TextButton(onClick = { viewModel.clearRecent() }) { Text("Clear") }
                    }
                    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(recent) { r -> FilterChip(selected = false, onClick = { viewModel.ask(r) }, label = { Text("🕘 $r") }) }
                    }
                }
                // The family's own tags, most used first: one tap shows everything tagged.
                if (state.tags.isNotEmpty()) item {
                    Text("Your tags", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp))
                    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.tags) { (tag, n) -> FilterChip(selected = false, onClick = { viewModel.ask("#$tag") }, label = { Text("#$tag · $n") }) }
                    }
                }
                item {
                    Text("Try a topic", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp))
                    Text(
                        "Ask the way you'd say it: “uber last month”, “dining over 1000”, “riya's medicines”, “expiring this week”, “#goa”.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp),
                    )
                    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.ideas) { idea -> FilterChip(selected = false, onClick = { viewModel.ask(idea) }, label = { Text(idea) }) }
                    }
                }
            } else if (!state.loading && state.groups.isEmpty()) {
                // A dead end gets a way out: the closest word, or what to try instead.
                item {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(foundLine(state.query, state.groups), style = MaterialTheme.typography.titleMedium)
                        state.suggestion?.let { fix ->
                            FilterChip(selected = false, onClick = { viewModel.ask(fix) }, label = { Text("Did you mean “$fix”?") })
                        }
                        Text("Try fewer words, or a topic like “travel” or “bills”.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                // How the search was read, so a person can see it understood ("Spending · Last month").
                if (state.understood.isNotEmpty()) item(key = "understood") {
                    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(state.understood) { u ->
                            Text(
                                u, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                // What was found at a glance: spending totalled by category, and the rest counted.
                state.summary?.let { sum ->
                    item(key = "summary") { SearchSummaryCard(sum) }
                }
                // Narrow to one kind; tap again for everything.
                if (state.groups.size > 1) item(key = "kinds") {
                    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(selected = state.kind == null, onClick = { viewModel.pickKind(null) }, label = { Text("All ${state.groups.sumOf { it.items.size }}") }) }
                        items(state.groups) { g -> FilterChip(selected = state.kind == g.group, onClick = { viewModel.pickKind(g.group) }, label = { Text("${g.group} ${g.items.size}") }) }
                    }
                }
                if (state.loading) item { Text("Looking…", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(20.dp)) }
                state.shown.forEach { group ->
                    val all = state.kind != null || group.group in opened || group.items.size <= FOUND_PEEK
                    item(key = "h-${group.group}") {
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${group.group} · ${group.items.size}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            if (!all) {
                                Text(
                                    "See all ${group.items.size}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { opened = opened + group.group }.padding(8.dp),
                                )
                            }
                        }
                    }
                    items(if (all) group.items else group.items.take(FOUND_PEEK), key = { "${it.collection}-${it.id}" }) { hit ->
                        val type = viewModel.typeOf(hit)
                        CompactRow(
                            title = hit.title,
                            // What it is in one line: amount and day for spending, due day for tasks, where it's kept…
                            detail = hit.line({ m, c -> com.houseaffairs.app.core.ui.shownMinor(m, c ?: "INR") }, java.time.LocalDate.now()),
                            flag = null,
                            onClick = {
                                viewModel.remember()
                                onDismiss()
                                // A dream opens on the vision board, where its progress is; the rest open their form.
                                if (hit.collection == "dreams") onOpenTab("plans/DREAM:${hit.id}") else type?.let { onEdit(it, hit.id) }
                            },
                            // The family's own photo says more than the kind's icon.
                            leading = hit.photo?.let { id -> { com.houseaffairs.app.core.photo.PhotoThumb(id, 32.dp) } } ?: type?.let { t ->
                                { Icon(t.icon, contentDescription = null, tint = t.accent(LocalHouseColors.current).color, modifier = Modifier.size(20.dp)) }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** "₹4,320 spent on 6 expenses", split by category, and what else turned up ("1 trips · 2 places"). */
@Composable
private fun SearchSummaryCard(sum: com.houseaffairs.app.core.data.SearchSummary) {
    val accent = LocalHouseColors.current.money
    Column(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp).clip(RoundedCornerShape(24.dp))
            .background(accent.container).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (sum.expenses > 0) {
            val money = { m: Long -> com.houseaffairs.app.core.ui.shownMinor(m, sum.currency ?: "INR") }
            Text("${money(sum.spentMinor)} spent", style = MaterialTheme.typography.headlineSmall)
            Text(if (sum.expenses == 1) "on 1 expense" else "on ${sum.expenses} expenses", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // The split by category, as thin bars, when it's more than one.
            if (sum.byCategory.size > 1) sum.byCategory.take(4).forEach { (cat, amt) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(com.houseaffairs.app.core.ui.categoryIcon(cat), contentDescription = null, tint = accent.color, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(cat, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(96.dp))
                    Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                        Box(Modifier.fillMaxWidth((amt.toFloat() / sum.spentMinor.coerceAtLeast(1)).coerceIn(0.03f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(accent.color))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(money(amt), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (sum.others.isNotEmpty()) Text((if (sum.expenses > 0) "Also " else "Found ") + sum.others, style = MaterialTheme.typography.bodyMedium)
    }
}
