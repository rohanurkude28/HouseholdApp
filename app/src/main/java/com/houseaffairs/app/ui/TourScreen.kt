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
                if (index < steps.lastIndex) TextButton(onClick = ::finish) { Text("Skip") }
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
                        TourAction.NOTIFICATIONS -> if (granted) {
                            Text("✓ Notifications are on", color = MaterialTheme.colorScheme.primary)
                            // Without "Alarms & reminders", Android may ring a few minutes late.
                            if (Build.VERSION.SDK_INT >= 31 && !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
                                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) }) { Text("Ring at the exact minute") }
                            }
                        } else {
                            Button(onClick = { asked = true; permission.launch(Manifest.permission.POST_NOTIFICATIONS) }, shape = CircleShape) { Text("Allow notifications") }
                        }
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
                    shape = CircleShape,
                    modifier = Modifier.weight(1f),
                ) { Text(if (index < steps.lastIndex) "Next" else "Start") }
            }
        }
    }
}
