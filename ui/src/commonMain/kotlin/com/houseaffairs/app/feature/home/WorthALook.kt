package com.houseaffairs.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.ui.HaCard

private val WORTH_A_LOOK_HEIGHT = 150.dp

/** One step of setting up a new home; [onClick] goes where it's done. */
data class SetupStep(val title: String, val done: Boolean, val onClick: (() -> Unit)?)

/** The owner's first steps: home made, family invited, something added, a budget set. Others get none. */
fun setupSteps(isOwner: Boolean, people: Int, addedSomething: Boolean, hasBudget: Boolean, onInvite: () -> Unit, onAdd: () -> Unit, onBudget: () -> Unit): List<SetupStep> =
    if (!isOwner) emptyList()
    else listOf(
        SetupStep("Set up your home", done = true, onClick = null),
        SetupStep("Invite your family", done = people > 1, onClick = onInvite),
        SetupStep("Add something with quick add", done = addedSomething, onClick = onAdd),
        SetupStep("Set a monthly budget", done = hasBudget, onClick = onBudget),
    )

/** "Finish setting up · Next: invite your family", with a ring of steps done. */
@Composable
fun SetupCard(steps: List<SetupStep>, modifier: Modifier = Modifier) {
    val done = steps.count { it.done }
    val next = steps.firstOrNull { !it.done } ?: return
    HaCard(modifier.fillMaxWidth(), onClick = next.onClick, color = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { done / steps.size.toFloat() },
                    modifier = Modifier.size(52.dp),
                    strokeWidth = 6.dp,
                    strokeCap = StrokeCap.Round,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                )
                Text("$done/${steps.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Finish setting up", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Next: ${next.title}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
            }
        }
    }
}

/**
 * Setting up, the day's live-well nudge and tidying up, as one row of cards to swipe through:
 * all still there, none pushing Today down the page. Each page gets a modifier filling the row's
 * height, so short cards don't leave a gap. Shows nothing when there's nothing.
 */
@Composable
fun WorthALookRow(pages: List<@Composable (Modifier) -> Unit>) {
    if (pages.isEmpty()) return
    if (pages.size == 1) {
        Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) { pages.single()(Modifier) }
        return
    }
    val pager = rememberPagerState { pages.size }
    Column(Modifier.padding(top = 16.dp)) {
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(start = 20.dp, end = 44.dp),
            pageSpacing = 10.dp,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.height(WORTH_A_LOOK_HEIGHT),
        ) { page -> pages[page](Modifier.fillMaxHeight()) }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { i ->
                val on = i == pager.currentPage
                Box(
                    Modifier.padding(horizontal = 3.dp).size(width = if (on) 14.dp else 6.dp, height = 6.dp).clip(CircleShape)
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
    }
}
