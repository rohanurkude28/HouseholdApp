package com.houseaffairs.app.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.designsystem.LocalHouseColors

/** The five tabs, named and drawn the same on both phones. */
enum class HouseTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Home),
    MONEY("Money", Icons.Outlined.AccountBalanceWallet),
    KITCHEN("Kitchen", Icons.Outlined.Kitchen),
    PLANS("Plans", Icons.Outlined.TaskAlt),
    THINGS("Things", Icons.Outlined.Inventory2),
}

/** Floating pill navigation with the add button in the middle, one thumb-reach from anywhere. */
@Composable
fun HouseBottomBar(tabs: List<HouseTab>, selected: HouseTab?, onSelect: (HouseTab) -> Unit, onAdd: () -> Unit) {
    val split = (tabs.size + 1) / 2
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)) {
        Surface(
            shape = RoundedCornerShape(34.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shadowElevation = 14.dp,
            modifier = Modifier.fillMaxWidth().height(68.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                tabs.take(split).forEach { TabItem(it, it == selected) { onSelect(it) } }
                AddButton(onAdd)
                tabs.drop(split).forEach { TabItem(it, it == selected) { onSelect(it) } }
            }
        }
    }
}

/** Icon with its name always underneath: icons alone leave people guessing. */
@Composable
private fun TabItem(tab: HouseTab, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "tab background",
    )
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(28.dp).clip(CircleShape).background(background).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Icon(tab.icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
        }
        Text(tab.label, style = MaterialTheme.typography.labelSmall, color = content, maxLines = 1)
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    val gradient = Brush.linearGradient(listOf(LocalHouseColors.current.coral, MaterialTheme.colorScheme.primary))
    Box(
        Modifier
            .size(52.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(gradient)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = "Quick add", tint = Color.White, modifier = Modifier.size(28.dp))
    }
}
