package com.houseaffairs.app.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * The one way to tick something off, everywhere: tap the circle. It fills with a check for a
 * moment so you see it happen, then [onTick] runs (the item leaves the list, with Undo).
 */
@Composable
fun TickCircle(color: Color, onTick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 26.dp, contentDescription: String = "Mark done") {
    var ticked by remember { mutableStateOf(false) }
    val fill by animateFloatAsState(if (ticked) 1f else 0f, label = "tick")
    LaunchedEffect(ticked) {
        if (ticked) {
            delay(350)
            onTick()
            ticked = false
        }
    }
    // A bigger touch area than the circle itself, so it's easy to hit.
    Box(
        modifier.size(size + 18.dp).clip(CircleShape).clickable(role = Role.Checkbox, onClickLabel = contentDescription) { ticked = true },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(size).clip(CircleShape).background(color.copy(alpha = fill)).border(2.dp, color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.65f).alpha(fill))
        }
    }
}

/** The rest of a row: tap opens it to edit, and so does tap-and-hold. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.opensEdit(onEdit: () -> Unit): Modifier = combinedClickable(onClickLabel = "Edit", onClick = onEdit, onLongClick = onEdit)
