package com.houseaffairs.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.ItemLink

/** Small pills that open a link in one tap: in YouTube, Instagram or IMDb's app when installed, else the browser. */
@Composable
fun LinkChips(links: List<ItemLink>, modifier: Modifier = Modifier) {
    if (links.isEmpty()) return
    val uri = LocalUriHandler.current
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        links.forEach { l ->
            Text(
                l.label + " ↗",
                style = MaterialTheme.typography.labelMedium,
                color = if (l.label.startsWith("▶")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClickLabel = "Open ${l.label}") { runCatching { uri.openUri(l.url) } }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}
