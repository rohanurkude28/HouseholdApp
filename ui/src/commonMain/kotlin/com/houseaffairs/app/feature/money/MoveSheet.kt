package com.houseaffairs.app.feature.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.MoneyState
import com.houseaffairs.app.core.data.Pocket
import com.houseaffairs.app.core.data.available

/** Moving money between the gullak, an envelope and a dream, the same on both phones. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MoveSheet(m: MoneyState, from: Pocket, fromLabel: String, currency: String, preset: Pocket? = null, onMove: (Pocket, String, Long) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val available = m.available(from)
    val targets: List<Pair<Pocket, String>> = buildList {
        m.dreams.filter { !it.dream.achieved }.forEach { add(Pocket.Dream(it.dream.id) to "${it.dream.emoji} ${it.dream.title}") }
        if (from != Pocket.Pot) add(Pocket.Pot to "Gullak")
        m.envelopes.envelopes.filter { Pocket.Category(it.category) != from }.forEach { add(Pocket.Category(it.category) to it.category) }
    }
    var target by remember { mutableStateOf(targets.firstOrNull { it.first == preset } ?: targets.firstOrNull()) }
    var amount by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.imePadding().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Move from $fromLabel", style = MaterialTheme.typography.headlineSmall)
            Text("${com.houseaffairs.app.core.util.moneyText(available, currency)} available", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("To", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                targets.forEach { t ->
                    FilterChip(selected = target == t, onClick = { target = t }, label = { Text(t.second) }, shape = CircleShape)
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
            val t = target
            Button(
                onClick = { if (t != null && minor != null) onMove(t.first, t.second.substringAfter(' ').takeIf { t.first is Pocket.Dream || t.first is Pocket.Investment } ?: t.second, minor) },
                enabled = t != null && minor != null && minor > 0 && minor <= available,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Move") }
        }
    }
}
