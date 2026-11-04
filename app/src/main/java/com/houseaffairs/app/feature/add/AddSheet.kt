package com.houseaffairs.app.feature.add

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import com.houseaffairs.app.feature.quickadd.LineKind
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.houseaffairs.app.core.designsystem.LocalHouseColors
import com.houseaffairs.app.core.ui.IconBlob
import kotlinx.coroutines.launch


/** Speech recognition through the system dialog; [onText] gets the best transcript. */
@Composable
private fun rememberVoiceInput(onText: (String) -> Unit, onUnavailable: () -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onText)
    }
    return {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "What should I add?")
        try {
            launcher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            onUnavailable()
        }
    }
}

/**
 * One place to add anything: speak or type a line (it pre-fills the right form), or pick what
 * to add and fill the form, by hand or by voice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(
    onDismiss: () -> Unit,
    onSaved: (SavedEntry) -> Unit,
    request: AddRequest = AddRequest(),
    viewModel: AddViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(request) {
        // Shared plain text lands in quick add, read like anything typed.
        (request.preset[QUICK_TEXT] as? String)?.let { viewModel.onTextChange(it) }
        val type = request.type ?: return@LaunchedEffect
        if (request.editId != null) viewModel.openEdit(type, request.editId) else viewModel.openForm(type, request.preset)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val finish: (SavedEntry?) -> Unit = { saved ->
        if (saved != null) {
            onSaved(saved)
            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.backToPicker()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        AnimatedContent(
            targetState = state.form?.type,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "add step",
        ) { formType ->
            val form = state.form
            if (formType == null || form == null) {
                Picker(
                    state,
                    viewModel,
                    onSave = { finish(viewModel.quickSave()) },
                    onCook = { scope.launch { finish(viewModel.cook()) } },
                    onMove = { finish(viewModel.performMove(it)) },
                    onLocationGranted = viewModel::refresh,
                    onSaveAll = { finish(viewModel.saveBatch()) },
                    onApplyChange = { finish(viewModel.applyChange()) },
                )
            } else {
                EntryForm(form, state, viewModel, onSave = { finish(viewModel.save()) }, onDelete = { scope.launch { finish(viewModel.deleteEditing()) } })
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Step 1: say it, type it, or pick it

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Picker(
    state: AddUiState,
    viewModel: AddViewModel,
    onSave: () -> Unit,
    onCook: () -> Unit,
    onMove: (MoveAction) -> Unit,
    onLocationGranted: () -> Unit,
    onSaveAll: () -> Unit,
    onApplyChange: () -> Unit,
) {
    val startVoice = rememberVoiceInput(viewModel::onTextChange, viewModel::onVoiceUnavailable)
    val suggestion = state.suggestion
    val blocked = suggestion != null && suggestion.type !in state.allowedTypes

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Add anything", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Say it, type it, or pick below",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            VoiceButton(onClick = startVoice)
        }

        TextField(
            value = state.text,
            onValueChange = viewModel::onTextChange,
            placeholder = {
                Text(
                    "Type or say anything…",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            },
            textStyle = MaterialTheme.typography.titleLarge,
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                when {
                    state.batch != null -> onSaveAll()
                    state.change?.entry != null && state.change.problem == null ->
                        if (state.change.draft.action == com.houseaffairs.app.feature.quickadd.QuickAddDraft.ChangeAction.EDIT) viewModel.editChange() else onApplyChange()
                    state.change != null -> Unit
                    state.move != null && state.move.problem == null -> onMove(state.move)
                    state.move != null || state.answer != null -> Unit
                    state.cook?.plan != null -> onCook()
                    state.cook != null -> viewModel.saveCookAsRecipe()
                    !blocked -> onSave()
                }
            }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.voiceUnavailable) {
            Text("Voice input isn't available on this device.", color = MaterialTheme.colorScheme.error)
        }

        val cook = state.cook
        val batch = state.batch
        val change = state.change
        if (batch != null) {
            BatchCard(batch, state.allowedTypes, viewModel, onSaveAll)
        } else if (change != null) {
            ChangeCard(change, viewModel, onApply = onApplyChange)
        } else if (state.move != null) {
            MoveCard(state.move, onMove)
        } else if (state.answer != null) {
            AnswerCard(state.answer, onMove, onLocationGranted)
        } else if (cook != null) {
            CookCard(cook, state.members, viewModel, onCook = onCook, onSaveRecipe = viewModel::saveCookAsRecipe)
        } else if (suggestion == null) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.examples.forEach { example ->
                    SuggestionChip(onClick = { viewModel.onTextChange(example) }, label = { Text(example) }, shape = CircleShape)
                }
            }
        } else {
            SuggestionCard(
                suggestion, blocked,
                thinking = state.thinking,
                canAskAi = state.canAskAi,
                hits = state.mediaHits,
                chosen = state.chosenHit,
                onPick = viewModel::pickHit,
                onKind = viewModel::changeKind,
                onAskAi = { viewModel.askAssistant() },
                onEdit = viewModel::editSuggestion,
                onSave = onSave,
                quickChoice = (suggestion.type.fields.firstOrNull { it.key == suggestion.missingKeys.firstOrNull() } as? ChoiceSpec)?.takeIf { !it.multi }?.let { spec ->
                    val extra = spec.dynamicOptions?.let { state.dynamicOptions[it] }.orEmpty()
                    spec to ((if (extra.isNotEmpty() && spec.replacedByDynamic != null) spec.options - spec.replacedByDynamic else spec.options) + extra).distinct()
                },
                onQuickChoice = viewModel::fillSuggestion,
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("Or choose what to add", style = MaterialTheme.typography.titleMedium)

        val colors = LocalHouseColors.current
        val tiles = EntryType.entries.filter { it in state.allowedTypes && it.listed }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 4,
            modifier = Modifier.fillMaxWidth(),
        ) {
            tiles.forEach { type ->
                Column(
                    Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(enabled = type.available) { viewModel.openForm(type) }
                        .alpha(if (type.available) 1f else 0.45f)
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IconBlob(type.icon, type.accent(colors), size = 52.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (type.available) type.label else "${type.label}\n(soon)",
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
            // Keep the last row's tiles the same width as the others.
            repeat((4 - tiles.size % 4) % 4) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: Suggestion,
    blocked: Boolean,
    thinking: Boolean,
    canAskAi: Boolean,
    hits: List<com.houseaffairs.app.core.data.MediaHit> = emptyList(),
    chosen: com.houseaffairs.app.core.data.MediaHit? = null,
    onPick: (com.houseaffairs.app.core.data.MediaHit) -> Unit = {},
    onKind: (LineKind) -> Unit,
    onAskAi: () -> Unit,
    onEdit: () -> Unit,
    onSave: () -> Unit,
    quickChoice: Pair<ChoiceSpec, List<String>>? = null,
    onQuickChoice: (String, String) -> Unit = { _, _ -> },
) {
    val accent = suggestion.type.accent(LocalHouseColors.current)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(accent.container)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBlob(suggestion.type.icon, com.houseaffairs.app.core.designsystem.Accent(Color.White, accent.color), size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(suggestion.type.label, style = MaterialTheme.typography.labelLarge, color = accent.color)
                Text(suggestion.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (suggestion.summary.isNotEmpty()) {
                    Text(suggestion.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when {
                    thinking -> "✨ Checking with Gemini…"
                    suggestion.source == SuggestionSource.AI -> "✨ Understood by Gemini"
                    suggestion.source == SuggestionSource.LEARNED -> "Learned from your family"
                    suggestion.source == SuggestionSource.GUESS -> "Best guess"
                    suggestion.unsure -> "Not sure what this is"
                    else -> null
                }?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = accent.color) }
            }
        }
        // Movies, books and songs: the exact title with its poster, from what was typed or said.
        if (hits.isNotEmpty() && !blocked) {
            Spacer(Modifier.height(10.dp))
            Text(if (chosen == null) "Did you mean…?" else "Picked · tap again to undo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(hits.size) { i ->
                    val h = hits[i]
                    val on = chosen == h
                    Column(
                        Modifier
                            .width(84.dp)
                            .clip(MaterialTheme.shapes.small)
                            .then(if (on) Modifier.background(accent.color.copy(alpha = 0.18f)) else Modifier)
                            .clickable(onClickLabel = "Pick ${h.title}") { onPick(h) }
                            .padding(4.dp),
                    ) {
                        Box(Modifier.size(width = 76.dp, height = 108.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                            if (h.poster != null) {
                                coil.compose.AsyncImage(model = h.poster, contentDescription = h.title, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.matchParentSize())
                            } else {
                                Text(h.kind.take(1), style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.Center))
                            }
                            if (on) Text("✓", color = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).clip(CircleShape).background(accent.color).padding(horizontal = 6.dp))
                        }
                        Text(h.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(h.year, h.kind).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        h.creator?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        h.rating?.let { Text("★ %.1f".format(it) + (h.ratingCount?.let { n -> " ($n)" } ?: ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        // A must-have the line didn't say (how it was paid): pick it right here.
        if (quickChoice != null && !blocked) {
            val (spec, options) = quickChoice
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(spec.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                options.filter { it != "Other" }.forEach { option ->
                    FilterChip(selected = false, onClick = { onQuickChoice(spec.key, option) }, label = { Text(option) }, shape = CircleShape)
                }
            }
        }
        // An unsure line: one tap to say what it really is. The whole family's quick add learns it.
        if (suggestion.unsure && !blocked) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("It's", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LineKind.entries.forEach { kind ->
                    FilterChip(selected = suggestion.kind == kind, onClick = { onKind(kind) }, label = { Text(kind.label) }, shape = CircleShape)
                }
                if (canAskAi) TextButton(onClick = onAskAi) { Text("✨ Ask Gemini") }
            }
        }
        Spacer(Modifier.height(12.dp))
        when {
            blocked -> Text("Your role can't add ${suggestion.type.label.lowercase()}s. Ask an adult in your home.", color = MaterialTheme.colorScheme.error)
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit, shape = CircleShape, modifier = Modifier.weight(1f)) { Text("Add details") }
                Button(onClick = onSave, shape = CircleShape, modifier = Modifier.weight(1f)) {
                    Text(if (suggestion.missing.isEmpty()) "Save" else "Fill ${suggestion.missing.first().lowercase()}")
                }
            }
        }
    }
}

/** "Add milk, eggs and bread": one row per item, saved together. */
@Composable
private fun BatchCard(batch: List<Suggestion>, allowed: List<EntryType>, viewModel: AddViewModel, onSaveAll: () -> Unit) {
    val colors = LocalHouseColors.current
    AssistantCard {
        Text("${batch.size} things to add", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        batch.forEachIndexed { index, item ->
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { viewModel.editFromBatch(index) }, verticalAlignment = Alignment.CenterVertically) {
                IconBlob(item.type.icon, item.type.accent(colors), size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when {
                            item.type !in allowed -> "Your role can't add this"
                            item.missing.isNotEmpty() -> "Needs ${item.missing.first().lowercase()} · tap to fill"
                            else -> listOf(item.type.label, item.summary).filter { it.isNotEmpty() }.joinToString(" · ")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.missing.isNotEmpty() || item.type !in allowed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = { viewModel.dropFromBatch(index) }) { Text("Remove") }
            }
        }
        val ready = batch.count { it.missing.isEmpty() && it.type in allowed }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onSaveAll, enabled = ready > 0, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
            Text(if (ready == batch.size) "Save all $ready" else "Save $ready of ${batch.size}")
        }
    }
}

/** "Delete the Uber expense", "mark milk bought": shows what was found before anything changes. */
@Composable
private fun ChangeCard(change: ChangeProposal, viewModel: AddViewModel, onApply: () -> Unit) {
    val colors = LocalHouseColors.current
    val action = change.draft.action
    AssistantCard {
        Text(
            when (action) {
                com.houseaffairs.app.feature.quickadd.QuickAddDraft.ChangeAction.DELETE -> "Delete"
                com.houseaffairs.app.feature.quickadd.QuickAddDraft.ChangeAction.DONE -> "Tick off"
                com.houseaffairs.app.feature.quickadd.QuickAddDraft.ChangeAction.EDIT -> "Change"
                com.houseaffairs.app.feature.quickadd.QuickAddDraft.ChangeAction.LINK -> "Add to a trip"
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (change.loading) {
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
            return@AssistantCard
        }
        change.matches.forEachIndexed { index, entry ->
            Spacer(Modifier.height(8.dp))
            val on = index == change.picked
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { viewModel.pickMatch(index) }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBlob(entry.type.icon, entry.type.accent(colors), size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(entry.type.label, entry.detail).filter { it.isNotEmpty() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (change.matches.size > 1) androidx.compose.material3.RadioButton(selected = on, onClick = { viewModel.pickMatch(index) })
            }
        }
        change.problem?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        change.fallback?.let { f ->
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = viewModel::useFallback, shape = CircleShape, modifier = Modifier.fillMaxWidth()) { Text("Add “${f.title}” as a new ${f.type.label.lowercase()}") }
        }
        val entry = change.entry
        if (entry != null && change.problem == null) {
            Spacer(Modifier.height(12.dp))
            when (action) {
                com.houseaffairs.app.feature.quickadd.QuickAddDraft.ChangeAction.EDIT ->
                    Button(onClick = viewModel::editChange, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
                        Text(change.draft.to?.let { "Change to “$it”" } ?: "Edit ${entry.title}")
                    }
                com.houseaffairs.app.feature.quickadd.QuickAddDraft.ChangeAction.DELETE ->
                    Button(
                        onClick = onApply,
                        shape = CircleShape,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Delete ${entry.title}") }
                com.houseaffairs.app.feature.quickadd.QuickAddDraft.ChangeAction.DONE ->
                    Button(onClick = onApply, shape = CircleShape, modifier = Modifier.fillMaxWidth()) { Text(viewModel.doneLabel(entry)) }
                com.houseaffairs.app.feature.quickadd.QuickAddDraft.ChangeAction.LINK ->
                    Button(onClick = onApply, shape = CircleShape, modifier = Modifier.fillMaxWidth()) { Text("Add to ${change.linkTo}") }
            }
        }
    }
}

@Composable
private fun AssistantCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun MoveCard(move: MoveAction, onMove: (MoveAction) -> Unit) {
    AssistantCard {
        Text(if (move.headline != null) "Update investment" else "Move money", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            move.headline ?: "${com.houseaffairs.app.core.util.formatMinor(move.amountMinor, move.currency)}  ·  ${move.fromLabel} → ${move.toLabel}",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(10.dp))
        if (move.problem != null) {
            Text(move.problem, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        } else {
            Button(onClick = { onMove(move) }, shape = CircleShape, modifier = Modifier.fillMaxWidth()) { Text(if (move.headline != null) "Update" else "Move it") }
        }
    }
}

@Composable
private fun AnswerCard(answer: Answer, onMove: (MoveAction) -> Unit, onLocationGranted: () -> Unit = {}) {
    val stock = LocalHouseColors.current.stock
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) onLocationGranted()
    }
    AssistantCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(answer.title, style = MaterialTheme.typography.titleMedium)
        }
        answer.lines.forEach { line ->
            Spacer(Modifier.height(4.dp))
            Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        @Composable
        fun IdeaRow(idea: com.houseaffairs.app.core.data.DishIdea) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBlob(EntryType.RECIPE.icon, stock, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(idea.recipe.title, style = MaterialTheme.typography.titleSmall)
                    answer.why[idea.recipe.title]?.let { Text("✨ $it", style = MaterialTheme.typography.bodySmall, color = stock.color) }
                    Text(
                        listOfNotNull(
                            idea.rescues.takeIf { it.isNotEmpty() }?.let { "uses ${it.joinToString()} before it expires" },
                            idea.fit.summary.replaceFirstChar { it.lowercase() },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        answer.ideas.forEach { IdeaRow(it) }
        // A craving's parts: "Crispy", then "Savoury curry", each with its dishes.
        answer.groups.forEach { (label, ideas) ->
            Spacer(Modifier.height(12.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = stock.color)
            ideas.forEach { IdeaRow(it) }
        }
        if (answer.loading) {
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (answer.needsLocation) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { permission.launch(arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION)) },
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Allow location") }
        }
        answer.move?.let { move ->
            Spacer(Modifier.height(12.dp))
            Button(onClick = { onMove(move) }, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
                Text("Move ${com.houseaffairs.app.core.util.formatMinor(move.amountMinor, move.currency)} from ${move.fromLabel}")
            }
        }
    }
}

@Composable
private fun CookCard(
    cook: CookSuggestion,
    members: List<com.houseaffairs.app.core.model.Member>,
    viewModel: AddViewModel,
    onCook: () -> Unit,
    onSaveRecipe: () -> Unit,
) {
    val accent = LocalHouseColors.current.stock
    val plan = cook.plan
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(accent.container)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBlob(EntryType.RECIPE.icon, com.houseaffairs.app.core.designsystem.Accent(Color.White, accent.color), size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (cook.people > 0) "Cooked · for ${cook.people}" else "Cooked", style = MaterialTheme.typography.labelLarge, color = accent.color)
                Text(cook.recipe?.title ?: cook.dish, style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (plan == null) {
            Text(
                "No recipe for this yet. Save it with ingredients and next time the pantry updates itself.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onSaveRecipe, shape = CircleShape, modifier = Modifier.fillMaxWidth()) { Text("Save as recipe") }
        } else {
            com.houseaffairs.app.core.ui.WhoIsEating(members, cook.eaters, cook.guests, viewModel::setEaters, viewModel::setGuests, cook.showError)
            Spacer(Modifier.height(8.dp))
            val used = plan.deductions.filter { it.after != null }.joinToString { d ->
                "${d.stock.title} −${d.need?.let { if (it % 1.0 == 0.0) it.toLong().toString() else "%.1f".format(it) } ?: "some"} ${d.stock.unit}"
            }
            Text(
                if (used.isEmpty()) "Nothing tracked in the pantry for this recipe." else "Takes $used",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (plan.toBuy.isNotEmpty()) {
                Text("Adds to shopping: ${plan.toBuy.joinToString()}", style = MaterialTheme.typography.bodyMedium, color = accent.color)
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onCook, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
                Text(if (cook.people == 0) "Update pantry" else "Update pantry for ${cook.people}")
            }
        }
    }
}

@Composable
private fun VoiceButton(onClick: () -> Unit, size: androidx.compose.ui.unit.Dp = 52.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(LocalHouseColors.current.coral, MaterialTheme.colorScheme.primary)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Mic, contentDescription = "Speak", tint = Color.White)
    }
}

// ---------------------------------------------------------------------------------------------
// Step 2: the form

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryForm(form: FormState, state: AddUiState, viewModel: AddViewModel, onSave: () -> Unit, onDelete: () -> Unit = {}) {
    val accent = form.type.accent(LocalHouseColors.current)
    val startVoice = rememberVoiceInput(viewModel::fillByVoice, viewModel::onVoiceUnavailable)
    val visible = form.type.fields.filter { spec ->
        spec.visibleWhen(form.values) &&
            !(spec is ChoiceSpec && spec.hideWithoutDynamic && state.dynamicOptions[spec.dynamicOptions].isNullOrEmpty())
    }
    val essential = visible.filter { it.essential }
    val extra = visible.filterNot { it.essential }
    val missingKeys = if (form.showErrors) form.missing.map { it.key }.toSet() else emptySet()

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = viewModel::backToPicker) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            IconBlob(form.type.icon, accent, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                if (form.editId != null) "Edit ${form.type.label.lowercase()}" else form.type.formTitle,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            VoiceButton(onClick = startVoice, size = 44.dp)
        }

        essential.forEach { spec ->
            FieldEditor(spec, form.values[spec.key], { viewModel.update(spec.key, it) }, accent, state.members, spec.key in missingKeys, state.dynamicOptions)
        }

        TextButton(onClick = viewModel::toggleMore) {
            Icon(if (form.showMore) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(if (form.showMore) "Fewer details" else "More details${if (extra.isNotEmpty()) " (${extra.size})" else ""}")
        }

        AnimatedVisibility(form.showMore) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                extra.forEach { spec ->
                    FieldEditor(spec, form.values[spec.key], { viewModel.update(spec.key, it) }, accent, state.members, spec.key in missingKeys, state.dynamicOptions)
                }
                WhoCanSeeEditor(form, state, viewModel, accent)
            }
        }

        if (form.showErrors && form.missing.isNotEmpty()) {
            Text(
                "Please fill: ${form.missing.joinToString { it.label.lowercase() }}",
                color = MaterialTheme.colorScheme.error,
            )
        }

        form.duplicateOf?.let { (id, title) ->
            Column(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(accent.container).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("You already have $title with these ingredients", style = MaterialTheme.typography.titleSmall)
                Text("Change the name or ingredients to save a new version.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { viewModel.openEdit(EntryType.RECIPE, id) }, shape = CircleShape) { Text("Open $title") }
            }
        }
        Button(onClick = onSave, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(if (form.editId != null) "Save changes" else "Save", style = MaterialTheme.typography.titleMedium)
        }
        if (form.editId != null) {
            // Two taps, so a stray one doesn't delete; Undo is offered afterwards too.
            var confirming by remember { androidx.compose.runtime.mutableStateOf(false) }
            TextButton(
                onClick = { if (confirming) onDelete() else confirming = true },
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text(if (confirming) "Tap again to delete" else "Delete ${form.type.label.lowercase()}") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhoCanSeeEditor(form: FormState, state: AddUiState, viewModel: AddViewModel, accent: com.houseaffairs.app.core.designsystem.Accent) {
    val colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent.container, selectedLabelColor = accent.color)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Who can see this", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WhoCanSee.entries.forEach { who ->
                FilterChip(
                    selected = form.whoCanSee == who,
                    onClick = { viewModel.setWhoCanSee(who) },
                    label = { Text(who.label) },
                    shape = CircleShape,
                    colors = colors,
                )
            }
        }
        if (form.whoCanSee == WhoCanSee.CHOSEN) {
            Spacer(Modifier.height(8.dp))
            FieldEditor(
                MembersSpec("__visible", "Visible to"),
                form.visibleMembers,
                { value -> viewModel.setVisibleMembers((value as? List<*>)?.filterIsInstance<String>().orEmpty()) },
                accent,
                state.members.filter { it.uid != state.myUid },
                showError = false,
            )
        }
        Text(
            "Use this for sensitive items like sanitary or personal care.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
