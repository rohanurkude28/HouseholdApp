package com.houseaffairs.app.feature.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.houseaffairs.app.core.data.Dream
import com.houseaffairs.app.core.data.DreamProgress
import com.houseaffairs.app.core.data.Pocket
import com.houseaffairs.app.core.data.dayLabel
import com.houseaffairs.app.core.time.systemToday
import com.houseaffairs.app.core.ui.HaCard

// A dream opened from the vision board or a list, the same on both phones: how far along it is,
// adding money to it from the gullak or an envelope, its trips, and editing or marking it achieved.

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DreamSheet(
    dream: Dream,
    progress: DreamProgress?,
    /** Null for people who can't see the family's money. */
    money: com.houseaffairs.app.core.data.MoneyState?,
    currency: String,
    full: (Long) -> String,
    short: (Long) -> String,
    onFund: (Pocket, Long, String) -> Unit,
    onAchieved: () -> Unit,
    onEdit: () -> Unit,
    onPlanTrip: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var amount by remember { mutableStateOf(progress?.remainingMinor?.takeIf { it > 0 }?.let { (it / 100).toString() } ?: "") }
    val sources: List<Triple<Pocket, String, Long>> = buildList {
        money?.let { add(Triple(Pocket.Pot, "Gullak", it.envelopes.potMinor)) }
        money?.envelopes?.envelopes?.filter { it.availableMinor > 0 }?.forEach { add(Triple(Pocket.Category(it.category), it.category, it.availableMinor)) }
    }
    var source by remember { mutableStateOf(sources.firstOrNull()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.imePadding().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dream.emoji, fontSize = 36.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(dream.title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        listOfNotNull(
                            dream.targetMinor.takeIf { it > 0 }?.let { full(it) },
                            dream.targetDate?.let { "by ${dayLabel(it, systemToday())}" },
                        ).joinToString(" "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (progress != null) {
                HaCard(Modifier.fillMaxWidth()) {
                    Text("${full(progress.savedMinor)} saved" + if (progress.creditsMinor > 0) " + ${full(progress.creditsMinor)} in credits" else "", style = MaterialTheme.typography.titleSmall)
                    Text(
                        when {
                            progress.remainingMinor == 0L -> "Fully funded. Time to book it!"
                            progress.affordableNow -> "The gullak (${full(money!!.envelopes.potMinor)}) covers the remaining ${full(progress.remainingMinor)}."
                            progress.monthlyNeededMinor != null -> "Set aside ${full(progress.monthlyNeededMinor!!)} a month to make it on time."
                            else -> "${full(progress.remainingMinor)} to go."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (progress.remainingMinor > 0 && sources.isNotEmpty()) {
                    Text("Add money from", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        sources.forEach { s ->
                            FilterChip(
                                selected = source == s,
                                onClick = { source = s },
                                label = { Text("${s.second} · ${short(s.third)}") },
                                shape = CircleShape,
                            )
                        }
                    }
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter { c -> c.isDigit() } },
                        label = { Text("Amount") },
                        prefix = { Text("$currency  ") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val minor = amount.toLongOrNull()?.let { com.houseaffairs.app.core.util.minorOfWhole(it, currency) }
                    val s = source
                    Button(
                        onClick = { if (s != null && minor != null) onFund(s.first, minor, s.second) },
                        enabled = s != null && minor != null && minor > 0 && minor <= s.third,
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Move to ${dream.title}") }
                }
            } else {
                Text("Adults in your home track savings for this dream.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            money?.trips?.filter { it.trip.dream == dream.title }?.takeIf { it.isNotEmpty() }?.let { linked ->
                Text("Trips for this dream", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                linked.forEach { t ->
                    Text(
                        "🧳 ${t.trip.title} · ${dayLabel(t.trip.start, systemToday())} · ${full(t.totalMinor)} spent" + (t.leftMinor?.let { if (it >= 0) " · ${full(it)} left" else "" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (money != null && (dream.kind == "Travel" || dream.kind == "Experience")) {
                TextButton(onClick = onPlanTrip, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Plan the trip") }
            }
            TextButton(onClick = onEdit, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Edit dream") }
            if (money != null) {
                TextButton(onClick = onAchieved, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(if (dream.achieved) "Mark as not achieved" else "We did it! Mark achieved 🎉")
                }
            }
        }
    }
}

