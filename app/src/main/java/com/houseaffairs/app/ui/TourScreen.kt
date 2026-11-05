package com.houseaffairs.app.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.houseaffairs.app.core.data.TourAction
import com.houseaffairs.app.core.data.TourStep

/**
 * The first-run tour: one feature per screen, with Back, Next and Skip. Where it was left is
 * saved on the phone. Finishing or skipping asks for notifications if that step didn't already.
 */
@Composable
fun TourScreen(
    steps: List<TourStep>,
    start: Int,
    onStep: (Int) -> Unit,
    onFinish: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val context = LocalContext.current
    var index by remember { mutableStateOf(start) }
    var asked by remember { mutableStateOf(false) }
    val needsPermission = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(!needsPermission) }
    var finishAfterAsk by remember { mutableStateOf(false) }
    // The setup step can't be skipped past: each permission gets an answer, yes or not now.
    var setupAnswered by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (finishAfterAsk) onFinish()
    }
    fun finish() {
        if (!granted && !asked && Build.VERSION.SDK_INT >= 33) {
            asked = true
            finishAfterAsk = true
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onFinish()
        }
    }
    fun go(to: Int) {
        index = to
        onStep(to)
    }
    BackHandler(enabled = index > 0) { go(index - 1) }
    val step = steps.getOrNull(index) ?: return

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1} of ${steps.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                if (index < steps.lastIndex && !(step.action == TourAction.SETUP && !setupAnswered)) TextButton(onClick = ::finish) { Text("Skip") }
            }
            AnimatedContent(targetState = step, label = "tour", modifier = Modifier.weight(1f)) { s ->
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.emoji, fontSize = 64.sp)
                    Spacer(Modifier.height(20.dp))
                    Text(s.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(s.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    s.tryIt?.let {
                        Spacer(Modifier.height(20.dp))
                        Text("Try saying", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "“$it”",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    when (s.action) {
                        TourAction.SETUP -> PhoneSetup(
                            notificationsOn = granted,
                            onNotifications = { asked = true; permission.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            // Answered here (even "not now"): don't ask again at the end of the tour.
                            onAnswered = { setupAnswered = it; if (it) asked = true },
                        )
                        TourAction.OPEN_MONEY -> OutlinedButton(onClick = { onFinish(); onOpen("money") }, shape = CircleShape) { Text("Set up the budget now") }
                        TourAction.OPEN_KITCHEN -> OutlinedButton(onClick = { onFinish(); onOpen("stock") }, shape = CircleShape) { Text("Open the kitchen") }
                        null -> Unit
                    }
                }
            }
            // Progress dots, then Back and Next.
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.Center) {
                steps.indices.forEach { i ->
                    Box(
                        Modifier.padding(3.dp).size(if (i == index) 10.dp else 7.dp).clip(CircleShape)
                            .background(if (i == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (index > 0) OutlinedButton(onClick = { go(index - 1) }, shape = CircleShape, modifier = Modifier.weight(1f)) { Text("Back") }
                else Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(0.dp))
                Button(
                    onClick = { if (index < steps.lastIndex) go(index + 1) else finish() },
                    enabled = step.action != TourAction.SETUP || setupAnswered,
                    shape = CircleShape,
                    modifier = Modifier.weight(1f),
                ) { Text(if (index < steps.lastIndex) "Next" else "Start") }
            }
        }
    }
}

/**
 * Every switch the app needs, on one screen: notifications, exact-time reminders, location for
 * "near me" and the home-screen widget. Each row turns into a tick once done; coming back from
 * the phone's settings re-checks them.
 */
@Composable
private fun PhoneSetup(notificationsOn: Boolean, onNotifications: () -> Unit, onAnswered: (Boolean) -> Unit) {
    val context = LocalContext.current
    var check by remember { mutableStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        check++
        onPauseOrDispose { }
    }
    fun has(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    val location = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { check++ }
    var widgetNote by remember { mutableStateOf<String?>(null) }
    // Read again whenever [check] moves (a result came back, or the app resumed).
    val exactOk = remember(check) { Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() }
    val locationOk = remember(check) { has(Manifest.permission.ACCESS_COARSE_LOCATION) }
    val widgetOk = remember(check) { com.houseaffairs.app.widget.HomeWidget.placed(context) }
    val canPin = remember { android.appwidget.AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported }

    // One pop-up per switch, in turn, until each is answered: tapping past a list is too easy.
    data class Ask(val id: String, val emoji: String, val title: String, val why: String, val done: Boolean, val allow: () -> Unit)
    val asks = listOfNotNull(
        Ask("notify", "🔔", "Allow notifications?", "So a reminder like “call mom at 5:30” pops up on time, plus one short summary a day.", notificationsOn || Build.VERSION.SDK_INT < 33, onNotifications),
        if (Build.VERSION.SDK_INT >= 31) Ask("exact", "⏰", "Ring on the exact minute?", "Android may otherwise hold reminders back by a few minutes. You'll see a switch: turn it on, then come back.", exactOk) {
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:" + context.packageName)))
        } else null,
        Ask("location", "📍", "Use your location for “near me”?", "To show saved places around you, like the pharmacy or a restaurant. Only while the app is open.", locationOk) {
            location.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        },
        if (canPin) Ask("widget", "📱", "Add the widget to your home screen?", "Today's tasks, the next birthday, your live-well score and something to watch, taking turns.", widgetOk) {
            com.houseaffairs.app.widget.HomeWidget.requestPin(context)
        } else null,
    )
    var answered by remember { mutableStateOf(setOf<String>()) }
    val current = asks.firstOrNull { !it.done && it.id !in answered }
    androidx.compose.runtime.LaunchedEffect(current == null) { onAnswered(current == null) }
    current?.let { a ->
        androidx.compose.material3.AlertDialog(
            // No tapping outside or Back: it needs a yes or a not now.
            onDismissRequest = {},
            properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            icon = { Text(a.emoji, fontSize = 32.sp) },
            title = { Text(a.title, textAlign = TextAlign.Center) },
            text = { Text(a.why, textAlign = TextAlign.Center) },
            confirmButton = { Button(onClick = { answered = answered + a.id; a.allow(); check++ }, shape = CircleShape) { Text("Allow") } },
            dismissButton = { TextButton(onClick = { answered = answered + a.id }) { Text("Not now") } },
        )
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SetupRow("🔔", "Notifications", "Reminders and one short summary a day", notificationsOn || Build.VERSION.SDK_INT < 33, "Allow", onNotifications)
        if (Build.VERSION.SDK_INT >= 31) {
            SetupRow("⏰", "Ring on the minute", "Without it, Android may ring a few minutes late", exactOk, "Allow") {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:" + context.packageName)))
            }
        }
        SetupRow("📍", "Location", "So “near me” finds saved places around you; only while the app is open", locationOk, "Allow") {
            location.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        SetupRow("📱", "Home-screen widget", widgetNote ?: "Today's tasks, birthdays, live-well and what to watch, taking turns", widgetOk, "Add") {
            if (!com.houseaffairs.app.widget.HomeWidget.requestPin(context)) widgetNote = "Long-press your home screen, tap Widgets, then find HouseAffairs"
            check++
        }
    }
}

@Composable
private fun SetupRow(emoji: String, title: String, detail: String, done: Boolean, action: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        if (done) Text("✓", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        else Button(onClick = onClick, shape = CircleShape) { Text(action) }
    }
}
