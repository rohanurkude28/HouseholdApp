package com.houseaffairs.app.feature.settings

import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.ThemeMode
import com.houseaffairs.app.core.designsystem.Accent
import com.houseaffairs.app.core.model.Member
import com.houseaffairs.app.core.model.Role
import com.houseaffairs.app.core.model.RolePolicy
import com.houseaffairs.app.core.ui.Avatar
import com.houseaffairs.app.core.ui.IconBlob

// Settings' building blocks, the same on both phones: grouped cards of rows with an icon, the
// role pill, a family member with "Change role", and the System / Light / Dark picker.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val options = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        options.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) { Text(label) }
        }
    }
}

@Composable
fun MemberRow(member: Member, isMe: Boolean, canEdit: Boolean, onRoleChange: (Role) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Avatar(member.displayName, size = 40.dp) },
        headlineContent = { Text(if (isMe) "${member.displayName} (you)" else member.displayName) },
        supportingContent = { Text(RolePolicy.label(member.role)) },
        trailingContent = if (canEdit) {
            {
                Box {
                    TextButton(onClick = { menuOpen = true }) { Text("Change role") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        RolePolicy.invitableRoles.forEach { role ->
                            DropdownMenuItem(text = { Text(RolePolicy.label(role)) }, onClick = { menuOpen = false; onRoleChange(role) })
                        }
                    }
                }
            }
        } else null,
    )
}

@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    ) {
        Column(Modifier.padding(vertical = 6.dp), content = content)
    }
}

@Composable
fun SettingsRow(icon: ImageVector, accent: Accent, title: String, subtitle: String?, onClick: (() -> Unit)? = null) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { IconBlob(icon, accent, size = 40.dp) },
        headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = if (onClick != null) {
            { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else null,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

/** A row with an on/off switch, as Settings' "Sounds & vibration" and "Daily digest". */
@Composable
fun SettingsSwitchRow(icon: ImageVector, accent: Accent, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { IconBlob(icon, accent, size = 40.dp) },
        headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@Composable
fun RolePill(role: Role) {
    Text(
        RolePolicy.label(role),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** No undo for this one: it says what goes, offers to keep family entries, and asks for DELETE typed. */
@Composable
fun DeleteAccountDialog(alone: Boolean, isOwner: Boolean, onDelete: (keepShared: Boolean) -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    // Family entries such as expenses shape everyone's budget history, so they stay unless asked.
    var keepShared by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete your account?") },
        text = {
            Column {
                Text(
                    when {
                        alone -> "You're the only member, so the whole home and everything in it is deleted with your account."
                        isOwner -> "Your private entries and your account are deleted. The adult who joined first becomes the owner."
                        else -> "Your private entries and your account are deleted, and you leave the home."
                    } + " Download your data first if you want a copy. This can't be undone.",
                )
                if (!alone) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = keepShared, onClick = { keepShared = !keepShared }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = keepShared, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Keep what I added for the whole family (expenses, groceries, tasks)", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = typed, onValueChange = { typed = it }, singleLine = true, label = { Text("Type DELETE to confirm") })
            }
        },
        confirmButton = {
            val ready = typed.trim().equals("DELETE", ignoreCase = true)
            TextButton(onClick = { onDelete(alone.not() && keepShared) }, enabled = ready) {
                Text("Delete", color = if (ready) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
