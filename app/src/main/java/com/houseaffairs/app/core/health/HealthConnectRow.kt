package com.houseaffairs.app.core.health

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HealthViewModel @Inject constructor(val health: HealthRepository) : ViewModel()

/**
 * "Steps & sleep": connects Health Connect so the live-well wheel counts steps and sleep. Read
 * only. Without Health Connect on the phone (before Android 14), it offers to get it.
 */
@Composable
fun HealthConnectRow(viewModel: HealthViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val health = viewModel.health
    var connected by remember { mutableStateOf<Boolean?>(null) }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(tick) { connected = health.connected() }
    val ask = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { tick++ }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Text("❤️", style = MaterialTheme.typography.titleLarge) },
        headlineContent = { Text("Steps & sleep", style = MaterialTheme.typography.titleSmall) },
        supportingContent = {
            Text(
                when {
                    connected == true -> "Connected. Your live-well wheel counts steps, sleep and workouts from your phone or watch."
                    !health.available() -> "Needs Health Connect, where Google Fit, Samsung Health and watches keep steps and sleep."
                    else -> "Count steps, sleep and workouts from your phone or watch in your live-well wheel. Read only, never shared."
                },
            )
        },
        trailingContent = {
            when {
                connected == true -> Text("✓", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                !health.available() -> Button(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }, shape = CircleShape) { Text("Get it") }
                else -> Button(onClick = { ask.launch(health.permissions) }, shape = CircleShape) { Text("Connect") }
            }
        },
    )
}
