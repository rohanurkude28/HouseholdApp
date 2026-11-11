package com.houseaffairs.app.feature.home

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
                    placeholder = { Text("Search your home… “travel”") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) IconButton(onClick = { viewModel.ask("") }) { Icon(Icons.Outlined.Close, contentDescription = "Clear") }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).focusRequester(focus),
                )
            }
            if (searchWords(state.query).isEmpty()) {
                item {
                    Text(
                        "Try a topic. It finds trips, spends, lists, documents and tasks together.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
                    )
                    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.ideas) { idea -> FilterChip(selected = false, onClick = { viewModel.ask(idea) }, label = { Text(idea) }) }
                    }
                }
            } else {
                item {
                    Text(
                        if (state.loading) "Looking…" else foundLine(state.query, state.groups),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                state.groups.forEach { group ->
                    val all = group.group in opened || group.items.size <= FOUND_PEEK
                    item(key = "h-${group.group}") {
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
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
                            detail = hit.detail,
                            flag = null,
                            onClick = {
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
