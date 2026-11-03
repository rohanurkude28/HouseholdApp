package com.houseaffairs.app.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.model.Member

/** Who's eating: family members plus guests. Cooking scales the recipe to this many people. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhoIsEating(
    members: List<Member>,
    selected: List<String>,
    guests: Int,
    onSelected: (List<String>) -> Unit,
    onGuests: (Int) -> Unit,
    showError: Boolean = false,
) {
    Column {
        Text(
            "Who's eating?",
            style = MaterialTheme.typography.labelLarge,
            color = if (showError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(
                selected = members.isNotEmpty() && selected.size == members.size,
                onClick = { onSelected(if (selected.size == members.size) emptyList() else members.map { it.uid }) },
                label = { Text("Everyone") },
                shape = CircleShape,
            )
            members.forEach { m ->
                val on = m.uid in selected
                FilterChip(
                    selected = on,
                    onClick = { onSelected(if (on) selected - m.uid else selected + m.uid) },
                    label = { Text(m.displayName.substringBefore(' ')) },
                    leadingIcon = { Avatar(m.displayName, size = 22.dp) },
                    shape = CircleShape,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Guests", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { if (guests > 0) onGuests(guests - 1) }) { Icon(Icons.Outlined.Remove, contentDescription = "Fewer guests") }
            Text("$guests", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { onGuests(guests + 1) }) { Icon(Icons.Outlined.Add, contentDescription = "More guests") }
        }
        if (showError) Text("Pick who's eating so the right amounts come out of the pantry.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
