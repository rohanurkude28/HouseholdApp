package com.houseaffairs.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.HouseholdRepository
import com.houseaffairs.app.core.data.ThemeMode
import com.houseaffairs.app.core.designsystem.LocalHouseColors
import com.houseaffairs.app.core.model.Member
import com.houseaffairs.app.core.model.Role
import com.houseaffairs.app.core.model.RolePolicy
import com.houseaffairs.app.core.ui.Avatar
import com.houseaffairs.app.core.ui.HaCard
import com.houseaffairs.app.core.ui.SectionHeader
import com.houseaffairs.app.feature.settings.MemberRow
import com.houseaffairs.app.feature.settings.RolePill
import com.houseaffairs.app.feature.settings.SettingsGroup
import com.houseaffairs.app.feature.settings.SettingsRow
import com.houseaffairs.app.feature.settings.ThemeSelector
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.launch

private val household = HouseholdRepository()

/**
 * The iPhone's Settings, laid out as Android's with the same pieces (`:ui` SettingsParts): you,
 * the look, the home, the family, who sees what, and the account. What only Android does yet
 * (connected apps, the digest, your data) is named in one line at the end.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsScreen(s: SessionState.Ready, members: List<MemberInfo>, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val colors = LocalHouseColors.current
    val owner = s.me.role == Role.OWNER
    var code by remember { mutableStateOf<String?>(null) }
    var inviting by remember { mutableStateOf(false) }
    var privacy by remember { mutableStateOf(false) }
    var hidden by remember { mutableStateOf(s.household.hideCostsFrom.toSet()) }
    var healthOn by remember { mutableStateOf(loadPref("healthConnected") == "yes") }
    var calendar by remember { mutableStateOf(calendarState()) }
    var calendarBusy by remember { mutableStateOf<String?>(null) }
    var driveOpen by remember { mutableStateOf(false) }
    var digest by remember { mutableStateOf(digestSetting()) }
    var pickingHour by remember { mutableStateOf(false) }
    var dataNote by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var deleteNote by remember { mutableStateOf<String?>(null) }
    val people = members.map { Member(it.uid, it.displayName, role = it.role, moduleAccess = it.moduleAccess) }
    val email = remember { runCatching { Firebase.auth.currentUser?.email }.getOrNull() }

        val export: () -> Unit = {
            dataNote = "Gathering…"
            scope.launch {
                dataNote = runCatching {
                    val entries = household.exportEntries(s.household.id, Member(s.uid, s.me.displayName, role = s.me.role, moduleAccess = s.me.moduleAccess))
                    val day = today().toString()
                    shareFiles(
                        listOf(
                            "HouseAffairs-$day.json" to com.houseaffairs.app.core.data.exportJson(s.household.name, day, entries),
                            "HouseAffairs-expenses-$day.csv" to com.houseaffairs.app.core.data.expensesCsv(entries["transactions"].orEmpty(), members.associate { it.uid to it.displayName }),
                        ),
                    )
                    "Ready · ${entries.values.sumOf { it.size }} entries"
                }.getOrElse { "Couldn't gather it. Check your connection and try again." }
            }
        }
    // Test runs ("-openTab SETTINGS/EXPORT") start it on opening.
    androidx.compose.runtime.LaunchedEffect(Unit) { if (startTab == "SETTINGS/EXPORT") export() }

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Row(Modifier.padding(start = 4.dp, end = 20.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
            }
        }
        item {
            HaCard(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(s.me.displayName, size = 56.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.me.displayName, style = MaterialTheme.typography.titleLarge)
                        email?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    RolePill(s.me.role)
                }
            }
        }
        item { SectionHeader("Look & sound") }
        item { SettingsGroup { Spacer(Modifier.padding(top = 6.dp)); ThemeSelector(themeMode) { setThemeMode(it) }; Spacer(Modifier.padding(top = 6.dp)) } }
        if (healthAvailable) {
            item { SectionHeader("Health") }
            item {
                SettingsGroup {
                    // Steps, sleep and workouts for the live-to-100 wheel, as Android's Health Connect row.
                    SettingsRow(
                        Icons.Outlined.Favorite, colors.vault, "Steps & sleep",
                        if (healthOn) "From Apple Health · fills in your wheel" else "Read from Apple Health for your wheel and check-in",
                    ) {
                        scope.launch { if (connectHealth()) { savePref("healthConnected", "yes"); healthOn = true } }
                    }
                }
            }
        }
        // One summary a morning, as Android's daily digest; reminders ring on their own.
        item { SectionHeader("Notifications") }
        item {
            SettingsGroup {
                com.houseaffairs.app.feature.settings.SettingsSwitchRow(
                    Icons.Outlined.Notifications, colors.plans, "Daily digest", "What's due, expiring or over budget, in one note",
                    checked = digest.on,
                ) { on -> digest = digest.copy(on = on); setDigest(digest) }
                if (digest.on) SettingsRow(Icons.Outlined.Schedule, colors.plans, "Digest time", hourLabel(digest.hour)) { pickingHour = true }
            }
        }
        // Google Calendar and Drive, as Android's Connected apps: only what this app made.
        item { SectionHeader("Connected apps") }
        item {
            SettingsGroup {
                SettingsRow(
                    Icons.Outlined.CalendarMonth, colors.plans, "Google Calendar",
                    calendarBusy ?: when {
                        !calendar.on -> "Dated tasks and events in a HouseAffairs calendar"
                        !calendar.error.isNullOrBlank() -> calendar.error
                        else -> "On · tap to sync now"
                    },
                ) {
                    calendarBusy = "Syncing…"
                    scope.launch {
                        setCalendarOn(true)
                        val result = syncCalendar(s)
                        calendar = calendarState()
                        calendarBusy = result
                    }
                }
                if (calendar.on) SettingsRow(Icons.Outlined.EventBusy, colors.plans, "Stop calendar sync", "What's in Google stays there") {
                    setCalendarOn(false); calendar = calendarState(); calendarBusy = null
                }
                SettingsRow(Icons.Outlined.CloudUpload, colors.vault, "Google Drive", "Back up the family's entries, or bring them back") { driveOpen = true }
            }
        }
        item { SectionHeader("Home") }
        item {
            SettingsGroup {
                SettingsRow(Icons.Outlined.Home, colors.plans, s.household.name.ifBlank { "Home" }, "Home name")
                SettingsRow(Icons.Outlined.Payments, colors.money, "Currency", s.household.baseCurrency)
            }
        }
        item { SectionHeader("Family") }
        item {
            SettingsGroup {
                people.forEach { m ->
                    MemberRow(m, isMe = m.uid == s.uid, canEdit = owner && m.uid != s.uid) { role ->
                        scope.launch { runCatching { household.setRole(s.household.id, m.uid, role) } }
                    }
                }
                if (owner) SettingsRow(Icons.Outlined.PersonAdd, colors.vault, "Invite a family member", "Share a code that works for 48 hours") { inviting = true }
            }
        }
        if (owner && members.size > 1) {
            item { SectionHeader("Privacy") }
            item {
                SettingsGroup {
                    SettingsRow(
                        Icons.Outlined.PersonRemove, colors.coral.let { com.houseaffairs.app.core.designsystem.Accent(it, it.copy(alpha = 0.15f)) }, "Who sees what",
                        listOfNotNull(
                            s.household.privateTopics.size.takeIf { it > 0 }?.let { "$it private categor${if (it == 1) "y" else "ies"}" },
                            hidden.size.takeIf { it > 0 }?.let { "costs hidden from $it" },
                        ).joinToString(" · ").ifEmpty { "Everyone sees everything" },
                    ) { privacy = true }
                }
            }
        }
        item { SectionHeader("Account") }
        item {
            SettingsGroup {
                // Everything this person can see, as JSON plus the expenses as a spreadsheet (Android's export).
                SettingsRow(Icons.Outlined.Download, colors.vault, "Download my data", dataNote ?: "Everything you can see, plus expenses as a spreadsheet", onClick = export)
                SettingsRow(Icons.AutoMirrored.Outlined.Logout, com.houseaffairs.app.core.designsystem.Accent(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.errorContainer), "Sign out", null) {
                    scope.launch { DemoHome.signOut() }
                }
                SettingsRow(
                    Icons.Outlined.DeleteForever, com.houseaffairs.app.core.designsystem.Accent(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.errorContainer),
                    "Delete my account", deleteNote,
                ) { deleting = true }
            }
        }
        item {
            Text(
                "Smart help and document uploads are in the Android app's Settings for now.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
            )
        }
    }

    if (inviting) {
        AlertDialog(
            onDismissRequest = { inviting = false; code = null },
            title = { Text(if (code == null) "Who are you inviting?" else "Their code") },
            text = {
                code?.let { c ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(c, style = MaterialTheme.typography.displaySmall)
                        Text("They type this on their phone within 48 hours.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } ?: Column {
                    RolePolicy.invitableRoles.forEach { role ->
                        TextButton(onClick = { scope.launch { runCatching { code = Family.invite(s.household.id, role) } } }) { Text(RolePolicy.label(role)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { inviting = false; code = null }) { Text("Done") } },
        )
    }
    if (driveOpen) DriveDialog(s) { driveOpen = false }
    if (deleting) {
        com.houseaffairs.app.feature.settings.DeleteAccountDialog(
            alone = members.size <= 1, isOwner = owner,
            onDelete = { keepShared ->
                deleting = false
                deleteNote = "Deleting…"
                // Entries and membership go first (the rules need a signed-in member), then the sign-in.
                kotlinx.coroutines.MainScope().launch {
                    runCatching {
                        household.eraseMe(s.household.id, keepShared)
                        Firebase.auth.currentUser?.delete()
                    }.onFailure {
                        deleteNote = if ("recent" in (it.message ?: "").lowercase()) "Sign out and in again, then delete: Google wants a fresh sign-in." else "Your account couldn't be deleted. Check your connection and try again."
                    }
                }
            },
            onDismiss = { deleting = false },
        )
    }
    if (pickingHour) {
        AlertDialog(
            onDismissRequest = { pickingHour = false },
            title = { Text("When should it come?") },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(6, 7, 8, 9, 10, 12, 18, 20).forEach { h ->
                        FilterChip(selected = digest.hour == h, onClick = { digest = digest.copy(hour = h); setDigest(digest); pickingHour = false }, label = { Text(hourLabel(h)) }, shape = CircleShape)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickingHour = false }) { Text("Done") } },
        )
    }
    if (privacy) {
        AlertDialog(
            onDismissRequest = { privacy = false },
            title = { Text("Who sees what") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Hide the family's costs from", style = MaterialTheme.typography.titleSmall)
                    Text("Amounts outside Money show as •••.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        members.filter { it.uid != s.uid }.forEach { m ->
                            FilterChip(
                                selected = m.uid in hidden,
                                onClick = {
                                    hidden = if (m.uid in hidden) hidden - m.uid else hidden + m.uid
                                    scope.launch { runCatching { household.setHideCosts(s.household.id, hidden.toList()) } }
                                },
                                label = { Text(m.displayName.substringBefore(' ')) }, shape = CircleShape,
                            )
                        }
                    }
                    if (s.household.privateTopics.isNotEmpty()) {
                        Text("Private categories", style = MaterialTheme.typography.titleSmall)
                        s.household.privateTopics.keys.sorted().forEach { Text("🔒 $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { privacy = false }) { Text("Done") } },
        )
    }
}

/** Follow the phone, or always light or dark; kept on this phone. */
internal var themeMode by mutableStateOf(runCatching { ThemeMode.valueOf(loadPref("themeMode").orEmpty()) }.getOrDefault(ThemeMode.SYSTEM))
    private set

internal fun setThemeMode(mode: ThemeMode) { themeMode = mode; savePref("themeMode", mode.name) }

/** Back up now, or pick a backup to bring back (Android's Drive part of Connected apps). */
@Composable
private fun DriveDialog(s: SessionState.Ready, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var list by remember { mutableStateOf<List<Backup>?>(null) }
    var picked by remember { mutableStateOf<Backup?>(null) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Google Drive") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Backups go to HouseAffairs/Backups in your Drive. The app sees only what it made there.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                TextButton(onClick = {
                    status = "Backing up…"
                    scope.launch { status = runCatching { "Backed up ${backUp(s)} entries ✓" }.getOrElse { it.message ?: "Backup failed" } }
                }) { Text("Back up now") }
                TextButton(onClick = {
                    status = "Looking for backups…"
                    scope.launch { runCatching { backups() }.onSuccess { list = it; status = if (it.isEmpty()) "No backups yet." else null }.onFailure { status = it.message ?: "Couldn't reach Drive" } }
                }) { Text("Bring back a backup…") }
                list?.forEach { b ->
                    TextButton(onClick = { picked = b }) { Text("↩︎ ${b.label}") }
                }
                picked?.let { b ->
                    // Two taps, since it writes over today's entries with the backup's.
                    Text("Bring back ${b.label}? Entries added since stay; changed ones go back to how they were.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        picked = null
                        status = "Bringing it back…"
                        scope.launch { status = runCatching { "Brought back ${restore(s, b)} entries ✓" }.getOrElse { it.message ?: "Restore failed" } }
                    }) { Text("Yes, bring it back") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Done") } },
    )
}

private fun hourLabel(h: Int) = "${if (h % 12 == 0) 12 else h % 12} ${if (h < 12) "am" else "pm"}"
