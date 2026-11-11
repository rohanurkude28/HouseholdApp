package com.houseaffairs.app.feature.settings

import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.outlined.Event
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.core.content.ContextCompat
import com.houseaffairs.app.core.data.SettingsRepository
import com.houseaffairs.app.notify.DigestWorker
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.material3.Checkbox
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import com.houseaffairs.app.core.designsystem.Accent
import com.houseaffairs.app.core.designsystem.LocalHouseColors
import com.houseaffairs.app.core.ui.HaCard
import com.houseaffairs.app.core.ui.IconBlob
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.outlined.FavoriteBorder
import com.houseaffairs.app.feature.review.WellPlanSheet
import com.houseaffairs.app.feature.review.WellPlanViewModel
import com.houseaffairs.app.feature.review.summary
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.houseaffairs.app.core.data.ThemeMode
import com.houseaffairs.app.core.model.Member
import com.houseaffairs.app.core.model.Role
import com.houseaffairs.app.core.model.RolePolicy
import com.houseaffairs.app.core.ui.Avatar
import com.houseaffairs.app.core.ui.CurrencyPickerDialog
import com.houseaffairs.app.core.ui.SectionHeader
import com.houseaffairs.app.core.util.currencyLabel

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var showInviteRolePicker by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var resetNote by remember { mutableStateOf<String?>(null) }
    var exportNote by remember { mutableStateOf<String?>(null) }
    var showDeleteAccount by remember { mutableStateOf(false) }
    var deleteNote by remember { mutableStateOf<String?>(null) }
    val me = state.members.firstOrNull { it.uid == state.myUid }
    val colors = LocalHouseColors.current

    // Padding the list (not its first row) keeps scrolled content clear of the status bar.
    val planVm: WellPlanViewModel = hiltViewModel()
    val wellPlan by planVm.plan.collectAsStateWithLifecycle()
    var planning by remember { mutableStateOf(false) }
    if (planning) WellPlanSheet(onDismiss = { planning = false }, viewModel = planVm)

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            Row(
                Modifier.padding(start = 4.dp, end = 20.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
            }
        }

        item {
            HaCard(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(me?.displayName.orEmpty(), size = 56.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(me?.displayName.orEmpty(), style = MaterialTheme.typography.titleLarge)
                        Text(state.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    me?.let { RolePill(it.role) }
                }
            }
        }

        item { SectionHeader("Live to 100") }
        item {
            SettingsGroup {
                SettingsRow(
                    Icons.Outlined.FavoriteBorder,
                    colors.coral.let { Accent(it, it.copy(alpha = 0.14f)) },
                    "My plan for a long, good life",
                    wellPlan?.summary() ?: "Pick a few habits for a long, happy life",
                ) { planning = true }
            }
        }

        item { SectionHeader("Appearance") }
        item {
            SettingsGroup {
                Column(Modifier.padding(16.dp)) { ThemeSelector(state.themeMode, viewModel::setThemeMode) }
            }
        }

        // Connections: everything the family links to, in one place.
        item { SectionHeader("Connections") }
        item { ServiceKeysGroup(state, viewModel) }
        item { SectionHeader("Connections · Smart help (each person)") }
        item {
            SettingsGroup {
                // One switch for both: on by default, since it's free and sends no amounts or lists.
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { IconBlob(Icons.Outlined.AutoAwesome, LocalHouseColors.current.vault, size = 40.dp) },
                    headlineContent = { Text("Smart help", style = MaterialTheme.typography.titleSmall) },
                    supportingContent = {
                        Text("Google Gemini understands unclear lines, picks what to cook and watch, and writes the daily tip. Only that line or those names are sent, never amounts. Free.")
                    },
                    trailingContent = {
                        Switch(checked = state.smartAdd && state.smartSuggest, onCheckedChange = { on -> viewModel.setSmartAdd(on); viewModel.setSmartSuggest(on) })
                    },
                )
            }
        }

        item { SectionHeader("Connections · Google Calendar (each person)") }
        item { CalendarGroup(state, viewModel) }

        item { SectionHeader("Connections · Health (each person)") }
        item { SettingsGroup { com.houseaffairs.app.core.health.HealthConnectRow() } }

        item { SectionHeader("Sounds & vibration") }
        item { FeedbackGroup() }

        item { SectionHeader("Backup · Google Drive") }
        item { DriveBackupGroup(state.isOwner) }

        item { SectionHeader("Notifications") }
        item { NotificationsGroup(state.notifications, viewModel::setNotifications) }

        item { SectionHeader("Home") }
        item {
            SettingsGroup {
                SettingsRow(Icons.Outlined.Home, colors.plans, state.householdName, "Home name")
                SettingsRow(
                    Icons.Outlined.Payments,
                    colors.money,
                    "Currency",
                    currencyLabel(state.baseCurrency),
                    onClick = { showCurrencyPicker = true }.takeIf { state.isOwner },
                )
            }
        }

        item { SectionHeader("Family") }
        item {
            SettingsGroup {
                state.members.forEach { member ->
                    MemberRow(
                        member = member,
                        isMe = member.uid == state.myUid,
                        canEdit = state.isOwner && member.uid != state.myUid,
                        onRoleChange = { viewModel.changeRole(member, it) },
                    )
                }
                if (state.isOwner) {
                    SettingsRow(Icons.Outlined.PersonAdd, colors.vault, "Invite a family member", "Share a code that works for 48 hours") {
                        showInviteRolePicker = true
                    }
                }
            }
        }

        if (state.isOwner) {
            item { SectionHeader("Private topics") }
            item { PrivateTopicsGroup(state, viewModel::setPrivateTopic) }
        }

        item { SectionHeader("Account") }
        item {
            SettingsGroup {
                SettingsRow(Icons.Outlined.Explore, colors.plans, "Show the app tour", "Every feature, one at a time", onClick = viewModel::showTour)
                // The rotating widget: tasks, the next birthday, live-well, something to watch.
                val widgetContext = androidx.compose.ui.platform.LocalContext.current
                var widgetNote by remember { mutableStateOf<String?>(null) }
                SettingsRow(Icons.Outlined.Explore, colors.money, "Add to home screen", widgetNote ?: "A widget that takes turns showing today's tasks, birthdays, live-well and what to watch") {
                    widgetNote = if (com.houseaffairs.app.widget.HomeWidget.requestPin(widgetContext)) "Pick a spot on your home screen" else "Long-press your home screen, tap Widgets, then find HouseAffairs"
                }
                // Two taps, since it signs you out of your home (sign back in with Google anytime).
                var demoAsk by remember { mutableStateOf(false) }
                var demoNote by remember { mutableStateOf<String?>(null) }
                SettingsRow(
                    Icons.Outlined.Explore, colors.stock, if (demoAsk) "Tap again to open the demo" else "Try the demo home",
                    demoNote ?: "Sample family data to practise on. Signs you out; your home stays as it is.",
                ) { if (demoAsk) viewModel.tryDemo { demoNote = it } else demoAsk = true }
                val danger = Accent(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                if (state.isOwner) {
                    SettingsRow(Icons.Outlined.DeleteForever, danger, "Reset everything", resetNote ?: "Start fresh: clears all lists and entries for the family") {
                        showReset = true
                    }
                }
                SettingsRow(Icons.AutoMirrored.Outlined.Logout, danger, "Sign out", null, onClick = viewModel::signOut)
            }
        }

        item { SectionHeader("Your data") }
        item {
            SettingsGroup {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                SettingsRow(
                    Icons.Outlined.Download, colors.money, "Download my data",
                    exportNote ?: "Everything you can see as a JSON file, plus your expenses as a spreadsheet",
                ) {
                    exportNote = "Getting it ready…"
                    viewModel.exportData { intent ->
                        exportNote = null
                        ctx.startActivity(android.content.Intent.createChooser(intent, "Save or send your data"))
                    }
                }
                val danger = Accent(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                SettingsRow(Icons.Outlined.PersonRemove, danger, "Delete my account", deleteNote ?: "Removes you and what you added, then signs you out") {
                    showDeleteAccount = true
                }
            }
        }
    }

    if (showCurrencyPicker) {
        CurrencyPickerDialog(
            selected = state.baseCurrency,
            onSelect = {
                viewModel.setCurrency(it)
                showCurrencyPicker = false
            },
            onDismiss = { showCurrencyPicker = false },
        )
    }
    if (showInviteRolePicker) {
        InviteRoleDialog(
            onInvite = {
                viewModel.createInvite(it)
                showInviteRolePicker = false
            },
            onDismiss = { showInviteRolePicker = false },
        )
    }
    if (showReset) {
        ResetDialog(
            onReset = {
                showReset = false
                resetNote = "Clearing…"
                viewModel.resetEverything { n -> resetNote = "Cleared $n entries" }
            },
            onDismiss = { showReset = false },
        )
    }
    if (showDeleteAccount) {
        val activity = androidx.compose.ui.platform.LocalContext.current
        DeleteAccountDialog(
            alone = state.members.size <= 1,
            isOwner = state.isOwner,
            onDelete = { keepShared ->
                showDeleteAccount = false
                deleteNote = "Deleting…"
                viewModel.deleteAccount(activity, keepShared) { deleteNote = it }
            },
            onDismiss = { showDeleteAccount = false },
        )
    }
    state.inviteCode?.let { code -> InviteCodeDialog(code, onDismiss = viewModel::dismissInvite) }
    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("OK") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
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
private fun MemberRow(member: Member, isMe: Boolean, canEdit: Boolean, onRoleChange: (Role) -> Unit) {
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
                            DropdownMenuItem(
                                text = { Text(RolePolicy.label(role)) },
                                onClick = {
                                    menuOpen = false
                                    onRoleChange(role)
                                },
                            )
                        }
                    }
                }
            }
        } else {
            null
        },
    )
}

/** No undo for this one, so it asks the owner to type RESET first. */
@Composable
private fun ResetDialog(onReset: () -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset everything?") },
        text = {
            Column {
                Text("This deletes all money, kitchen, medicine, plans and things entries for the whole family. The home, members, settings and connections stay. It can't be undone.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = typed, onValueChange = { typed = it }, singleLine = true, label = { Text("Type RESET to confirm") })
            }
        },
        confirmButton = {
            val ready = typed.trim().equals("RESET", ignoreCase = true)
            TextButton(onClick = onReset, enabled = ready) {
                Text("Reset", color = if (ready) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Like Reset, there's no undo, so it asks for DELETE to be typed. */
@Composable
private fun DeleteAccountDialog(alone: Boolean, isOwner: Boolean, onDelete: (keepShared: Boolean) -> Unit, onDismiss: () -> Unit) {
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

@Composable
private fun InviteRoleDialog(onInvite: (Role) -> Unit, onDismiss: () -> Unit) {
    var role by remember { mutableStateOf(Role.ADULT) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What can they see?") },
        text = {
            Column {
                RolePolicy.invitableRoles.forEach { option ->
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { RadioButton(selected = role == option, onClick = null) },
                        headlineContent = { Text(RolePolicy.label(option)) },
                        supportingContent = { Text(RolePolicy.describe(option)) },
                        modifier = Modifier.selectable(
                            selected = role == option,
                            onClick = { role = option },
                            role = SemanticsRole.RadioButton,
                        ),
                    )
                }
                Text(
                    "You can fine-tune access for each person later.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onInvite(role) }) { Text("Create invite") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun InviteCodeDialog(code: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite code") },
        text = {
            Column {
                Text(
                    code,
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text("They sign in with Google, tap \"Join with an invite code\" and enter this. It works for 48 hours.")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Join our home on HouseAffairs! Sign in with Google and enter code $code (valid for 48 hours).",
                    )
                }
                context.startActivity(Intent.createChooser(send, "Share invite"))
            }) { Text("Share") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/** A soft ding on save, a little chime when something's done; quiet when the phone is on silent. */
@Composable
private fun FeedbackGroup(main: com.houseaffairs.app.ui.MainViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val on by main.feedbackOn.collectAsState()
    val view = androidx.compose.ui.platform.LocalView.current
    SettingsGroup {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { IconBlob(Icons.Outlined.Notifications, LocalHouseColors.current.stock, size = 40.dp) },
            headlineContent = { Text("Sounds & vibration", style = MaterialTheme.typography.titleSmall) },
            supportingContent = { Text("A soft sound and tap when you save, finish or undo something. Quiet when your phone is on silent.") },
            trailingContent = {
                Switch(checked = on, onCheckedChange = { v ->
                    main.setFeedback(v)
                    // A sample, so you hear what you switched on.
                    if (v) main.feedback.play(com.houseaffairs.app.core.ui.Cue.DONE, view)
                })
            },
        )
    }
}

/** Back up everything you can see to Drive › HouseAffairs › Backups; the owner can restore one. */
@Composable
private fun DriveBackupGroup(isOwner: Boolean, drive: com.houseaffairs.app.core.drive.DriveViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val ds by drive.state.collectAsState()
    val colors = LocalHouseColors.current
    com.houseaffairs.app.core.drive.DriveConsent(ds.consent, drive::consentDone)
    var restoring by remember { mutableStateOf<String?>(null) }
    SettingsGroup {
        val prefs by drive.backupPrefs.collectAsState(initial = com.houseaffairs.app.core.data.SettingsRepository.BackupPrefs())
        SettingsRow(
            Icons.Outlined.Backup, colors.plans, "Back up now",
            ds.busy ?: ds.message ?: prefs.lastDay?.let { "Last backup: ${if (it == java.time.LocalDate.now().toString()) "today" else it}. Saved in Drive › HouseAffairs › Backups" }
                ?: "Saves every entry to your Google Drive, in HouseAffairs › Backups",
        ) {
            if (ds.busy == null) drive.backup()
        }
        // How backups happen on this phone: a daily nudge to tap it, or on their own every day.
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Backups", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    com.houseaffairs.app.core.data.BackupMode.AUTO to "Every day, by itself",
                    com.houseaffairs.app.core.data.BackupMode.REMIND to "Remind me daily",
                    com.houseaffairs.app.core.data.BackupMode.OFF to "Only when I tap",
                ).forEach { (mode, label) ->
                    FilterChip(selected = prefs.mode == mode, onClick = { drive.setBackupMode(mode) }, label = { Text(label) }, shape = CircleShape)
                }
            }
            Text(
                when (prefs.mode) {
                    com.houseaffairs.app.core.data.BackupMode.AUTO -> "Once a day in the background, when the battery isn't low."
                    com.houseaffairs.app.core.data.BackupMode.REMIND -> "If you haven't backed up that day, the app asks once when you open it."
                    com.houseaffairs.app.core.data.BackupMode.OFF -> "No reminders; tap Back up now whenever you like."
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (prefs.mode == com.houseaffairs.app.core.data.BackupMode.AUTO) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Wi-Fi only", style = MaterialTheme.typography.titleSmall)
                        Text(if (prefs.wifiOnly) "Waits for Wi-Fi, so mobile data isn't used." else "Uses mobile data too when there's no Wi-Fi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = prefs.wifiOnly, onCheckedChange = drive::setWifiOnly)
                }
            }
        }
        if (isOwner) {
            SettingsRow(Icons.Outlined.Restore, colors.vault, "Restore from a backup", "Puts back entries from a backup; anything added since stays") {
                if (ds.busy == null) drive.loadBackups()
            }
            ds.backups?.take(5)?.forEach { b ->
                // Two taps, since it writes over the family's entries.
                SettingsRow(Icons.Outlined.History, colors.vault, if (restoring == b.fileId) "Tap again to restore" else b.label, if (restoring == b.fileId) "Restores this backup for the whole family" else null) {
                    if (restoring == b.fileId) { restoring = null; drive.restore(b) } else restoring = b.fileId
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    ) {
        Column(Modifier.padding(vertical = 6.dp), content = content)
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, accent: Accent, title: String, subtitle: String?, onClick: (() -> Unit)? = null) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { IconBlob(icon, accent, size = 40.dp) },
        headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = if (onClick != null) {
            { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            null
        },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

@Composable
private fun RolePill(role: Role) {
    Text(
        RolePolicy.label(role),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotificationsGroup(prefs: SettingsRepository.NotificationPrefs, onChange: (SettingsRepository.NotificationPrefs) -> Unit) {
    val context = LocalContext.current
    val colors = LocalHouseColors.current
    var pendingTest by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pendingTest) DigestWorker.runNow(context)
        pendingTest = false
    }
    fun needsPermission() = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    SettingsGroup {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { IconBlob(Icons.Outlined.Notifications, colors.plans, size = 40.dp) },
            headlineContent = { Text("Daily digest", style = MaterialTheme.typography.titleSmall) },
            supportingContent = { Text("One notification a day, only when something needs you") },
            trailingContent = {
                Switch(checked = prefs.enabled, onCheckedChange = { on ->
                    if (on && needsPermission()) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    onChange(prefs.copy(enabled = on))
                })
            },
        )
        if (prefs.enabled) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text("Time", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7 to "7 am", 8 to "8 am", 9 to "9 am", 13 to "1 pm", 20 to "8 pm").forEach { (h, label) ->
                        FilterChip(selected = prefs.hour == h, onClick = { onChange(prefs.copy(hour = h)) }, label = { Text(label) }, shape = CircleShape)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Include", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val a = prefs.areas
                    FilterChip(selected = a.money, onClick = { onChange(prefs.copy(areas = a.copy(money = !a.money))) }, label = { Text("Money") }, shape = CircleShape)
                    FilterChip(selected = a.kitchen, onClick = { onChange(prefs.copy(areas = a.copy(kitchen = !a.kitchen))) }, label = { Text("Kitchen") }, shape = CircleShape)
                    FilterChip(selected = a.health, onClick = { onChange(prefs.copy(areas = a.copy(health = !a.health))) }, label = { Text("Health") }, shape = CircleShape)
                    FilterChip(selected = a.plans, onClick = { onChange(prefs.copy(areas = a.copy(plans = !a.plans))) }, label = { Text("Plans") }, shape = CircleShape)
                }
                TextButton(onClick = {
                    if (needsPermission()) {
                        pendingTest = true
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        DigestWorker.runNow(context)
                    }
                }) { Text("Send a test now") }
            }
        }
    }
}

/**
 * Topics only some people should see. New entries on them (pads, condoms, doctor bills…) are saved
 * visible only to the people ticked here, plus whoever adds them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrivateTopicsGroup(state: SettingsUiState, onChange: (String, List<String>?) -> Unit) {
    SettingsGroup {
        Text(
            "New entries on these are only seen by the people you pick (and whoever adds them).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )
        com.houseaffairs.app.core.data.PRIVATE_TOPICS.forEach { topic ->
            val allowed = state.privateTopics[topic]
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(topic, style = MaterialTheme.typography.titleSmall) },
                supportingContent = { Text(if (allowed == null) "Everyone" else "Private") },
                trailingContent = { Switch(checked = allowed != null, onCheckedChange = { on -> onChange(topic, if (on) listOf(state.myUid) else null) }) },
            )
            if (allowed != null) {
                FlowRow(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.members.forEach { m ->
                        val on = m.uid in allowed
                        FilterChip(
                            selected = on,
                            onClick = { onChange(topic, if (on) (allowed - m.uid).ifEmpty { listOf(state.myUid) } else allowed + m.uid) },
                            label = { Text(m.displayName.substringBefore(' ')) },
                            shape = CircleShape,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Two-way sync with a "HouseAffairs" calendar in the person's Google Calendar. The first switch-on
 * shows Google's consent screen; the app can only touch calendars it created.
 */
@Composable
private fun CalendarGroup(state: SettingsUiState, viewModel: SettingsViewModel) {
    val consent by viewModel.calendarConsent.collectAsState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onCalendarConsent(result.resultCode == android.app.Activity.RESULT_OK)
    }
    LaunchedEffect(consent) {
        consent?.let { launcher.launch(androidx.activity.result.IntentSenderRequest.Builder(it.intentSender).build()) }
    }
    val cal = state.calendar
    SettingsGroup {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { IconBlob(Icons.Outlined.Event, LocalHouseColors.current.plans, size = 40.dp) },
            headlineContent = { Text("Sync with Google Calendar", style = MaterialTheme.typography.titleSmall) },
            supportingContent = {
                Text("Your dated tasks and the family's events, in a separate “HouseAffairs” calendar. Changes there come back here. The app can't see your other calendars.")
            },
            trailingContent = { Switch(checked = cal.enabled, onCheckedChange = viewModel::setCalendarSync) },
        )
        if (cal.enabled || cal.error != null) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    cal.error ?: when {
                        state.calendarSyncing -> "Syncing…"
                        cal.lastSync != null -> "Last synced " + android.text.format.DateUtils.getRelativeTimeSpanString(cal.lastSync)
                        else -> "Not synced yet"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (cal.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (cal.enabled) TextButton(onClick = viewModel::syncCalendarNow, enabled = !state.calendarSyncing) { Text("Sync now") }
            }
        }
    }
}

private data class Service(val id: String, val name: String, val gives: String, val getKey: String)

private val SERVICES = listOf(
    Service("omdb", "OMDb", "IMDb and Rotten Tomatoes scores on movies and shows", "omdbapi.com/apikey.aspx (free)"),
    Service("tmdb", "TMDB", "Movie posters and where to watch in India", "themoviedb.org → Settings → API (free)"),
)

/**
 * Family-wide service keys. The owner connects each once and everyone's app uses it; others see
 * whether it's connected. Keys are checked with the service before they're saved.
 */
@Composable
private fun ServiceKeysGroup(state: SettingsUiState, viewModel: SettingsViewModel) {
    SettingsGroup {
        SERVICES.forEach { svc ->
            val connected = state.integrations[svc.id]?.isNotBlank() == true
            var editing by remember(svc.id) { mutableStateOf(false) }
            var key by remember(svc.id) { mutableStateOf("") }
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(svc.name, style = MaterialTheme.typography.titleSmall) },
                supportingContent = {
                    Text(
                        svc.gives + "\n" + when {
                            connected -> "✅ Connected for the whole family"
                            state.isOwner -> "Not connected · get a key at ${svc.getKey}"
                            else -> "Not connected · ask the owner to add it"
                        },
                    )
                },
                trailingContent = {
                    if (state.isOwner) {
                        TextButton(onClick = { if (connected) viewModel.removeKey(svc.id) else editing = !editing }) { Text(if (connected) "Remove" else "Connect") }
                    }
                },
            )
            if (editing && !connected) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = key, onValueChange = { key = it }, singleLine = true, placeholder = { Text("Paste the key") }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { viewModel.saveKey(svc.id, key) }, enabled = key.isNotBlank(), shape = CircleShape) { Text("Test & save") }
                }
            }
            state.keyStatus[svc.id]?.takeIf { !connected }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it == "Connected" || it == "Checking…") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
        }
    }
}
