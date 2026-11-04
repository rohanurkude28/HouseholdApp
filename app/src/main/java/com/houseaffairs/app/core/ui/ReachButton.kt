package com.houseaffairs.app.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.houseaffairs.app.core.util.reachOut

/** The one-tap Call / WhatsApp / SMS button on a "call mom" task. */
@Composable
fun ReachButton(reach: String, phone: String?, name: String) {
    val context = LocalContext.current
    val (icon, verb) = when (reach) {
        "WhatsApp" -> Icons.AutoMirrored.Outlined.Chat to "WhatsApp"
        "SMS" -> Icons.Outlined.Sms to "Text"
        else -> Icons.Outlined.Call to "Call"
    }
    FilledTonalIconButton(onClick = { reachOut(context, reach, phone) }) { Icon(icon, contentDescription = "$verb for $name") }
}
