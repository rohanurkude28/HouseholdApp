package com.houseaffairs.app.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.FollowUp
import kotlinx.coroutines.flow.MutableStateFlow

/** The follow-up waiting to be shown; screens post here after a "done". */
object FollowUps {
    val pending = MutableStateFlow<FollowUp?>(null)

    fun show(followUp: FollowUp) {
        pending.value = followUp
    }
}

/** Shows the pending follow-up as a small sheet: each group is one tap, closing keeps the defaults. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FollowUpHost() {
    val followUp by FollowUps.pending.collectAsState()
    val f = followUp ?: return
    val picked = remember(f) { mutableStateMapOf<String, String>() }
    ModalBottomSheet(onDismissRequest = { FollowUps.pending.value = null }) {
        Column(Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
            Text(f.title, style = MaterialTheme.typography.titleLarge)
            f.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            f.groups.forEach { group ->
                Spacer(Modifier.height(14.dp))
                Text(group.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.choices.forEach { choice ->
                        FilterChip(
                            selected = picked[group.label] == choice.label,
                            onClick = {
                                if (picked[group.label] != choice.label) {
                                    picked[group.label] = choice.label
                                    choice.apply()
                                }
                            },
                            label = { Text(choice.label) },
                            shape = CircleShape,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { FollowUps.pending.value = null }, shape = CircleShape, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}
