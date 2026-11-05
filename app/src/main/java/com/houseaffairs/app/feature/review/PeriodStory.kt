package com.houseaffairs.app.feature.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.PeriodReview
import com.houseaffairs.app.core.designsystem.LocalHouseColors
import com.houseaffairs.app.core.ui.HaCard
import com.houseaffairs.app.core.ui.SectionTitle
import com.houseaffairs.app.core.util.formatMinor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** "1 goal", "3 goals". */
private fun count(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"

private fun hours(minutes: Int) = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

/**
 * The month or year in the story: the headline numbers first, then money, the kitchen, what was
 * finished and how it felt. Arrows step back through earlier months or years.
 */
fun LazyListScope.periodStory(state: ReviewUi, currency: String, onPrev: () -> Unit, onNext: () -> Unit, onRetry: () -> Unit) {
    val year = state.mode == StoryMode.YEAR
    val start = state.periodStart
    val today = LocalDate.now()
    val current = if (year) start.year == today.year else start.year == today.year && start.month == today.month
    item {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = if (year) "Earlier year" else "Earlier month") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when {
                        year && current -> "This year"
                        year -> "${start.year}"
                        current -> "This month"
                        else -> start.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (year) "${start.year}" + if (current) " so far" else "" else start.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)) + if (current) " so far" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onNext, enabled = !current) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = if (year) "Later year" else "Later month") }
        }
    }
    val p = state.period
    if (p == null) {
        item {
            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                if (state.periodLoading) CircularProgressIndicator()
                else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Couldn't load this ${if (year) "year" else "month"}. Check your connection.", textAlign = TextAlign.Center)
                    TextButton(onClick = onRetry) { Text("Try again") }
                }
            }
        }
        return
    }
    if (p.isEmpty) {
        item { Text("Nothing logged yet. Tick things off, log walks and write in your diary, and your story builds up here.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp)) }
        return
    }
    val before = if (year) "${start.year - 1}" else start.minusMonths(1).month.getDisplayName(TextStyle.FULL, Locale.US)
    item { GlanceCard(p, currency, before) }
    item { Highlights(p) }
    p.spentMinor?.let { spent -> item { SectionTitle("Money") }; item { MoneyCard(p, spent, currency, year) } }
    if (p.bought > 0 || p.groceriesMinor != null) {
        item { SectionTitle("Kitchen") }
        item {
            HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                p.groceriesMinor?.let { Text("${formatMinor(it, currency)} on groceries", style = MaterialTheme.typography.titleMedium) }
                Text(
                    listOf(
                        count(p.bought, "thing") + " bought",
                        when {
                            p.wasted == 0 -> "🎉 nothing wasted"
                            p.wastedMinor > 0 -> "🗑️ ${formatMinor(p.wastedMinor, currency)} wasted (${p.wasted})"
                            else -> "🗑️ ${p.wasted} wasted"
                        },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
    if (p.finished.isNotEmpty() || p.goalsReached.isNotEmpty()) {
        item { SectionTitle("Finished") }
        item {
            HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    p.goalsReached.takeIf { it.isNotEmpty() }?.let { goals ->
                        StoryLine("🎯", "Goals reached · ${goals.size}", goals.joinToString(" · "))
                    }
                    p.finished.forEach { g -> StoryLine(g.emoji, "${g.verb} · ${g.titles.size}", g.titles.joinToString(" · ")) }
                }
            }
        }
    }
    if (p.moods.isNotEmpty()) {
        item { SectionTitle("How it felt") }
        item {
            HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text(p.moods.joinToString("    ") { (m, n) -> "$m $n" }, style = MaterialTheme.typography.titleLarge)
                Text("Moods from " + count(p.diaryDays, "diary day"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * The period at a glance: one line that sums it up, then the four numbers that matter most,
 * each against the month or year before (green when it went the right way).
 */
@Composable
private fun GlanceCard(p: PeriodReview, currency: String, before: String) {
    val prev = p.previous
    val plans = LocalHouseColors.current.plans
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(28.dp),
        color = plans.container,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            com.houseaffairs.app.core.data.storyHeadline(p, before)?.let { Text(it, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
            Row(Modifier.fillMaxWidth()) {
                p.spentMinor?.let { spent ->
                    // Spending less is the good direction.
                    GlanceStat("Spent", formatCompactMoney(spent, currency), prev?.spentMinor?.let { com.houseaffairs.app.core.data.percentChange(spent, it) }, lowerIsBetter = true, Modifier.weight(1f))
                }
                GlanceStat("Active", hours(p.activeMinutes), prev?.let { com.houseaffairs.app.core.data.percentChange(p.activeMinutes.toLong(), it.activeMinutes.toLong()) }, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                val finished = p.finished.sumOf { it.titles.size } + p.goalsReached.size
                GlanceStat("Finished", "$finished", prev?.let { com.houseaffairs.app.core.data.percentChange(finished.toLong(), (it.finished.sumOf { g -> g.titles.size } + it.goalsReached.size).toLong()) }, modifier = Modifier.weight(1f))
                GlanceStat("Mostly felt", p.moods.firstOrNull()?.first ?: "—", null, modifier = Modifier.weight(1f))
            }
            if (prev != null) Text(
                // Still going: it's compared with the same number of days, not the whole of the one before.
                if (!LocalDate.now().isAfter(p.to)) "Compared with the same days of $before" else "Compared with $before",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GlanceStat(label: String, value: String, change: Int?, lowerIsBetter: Boolean = false, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        change?.takeIf { it != 0 }?.let { c ->
            val good = if (lowerIsBetter) c < 0 else c > 0
            Text(
                (if (c > 0) "▲ " else "▼ ") + "${kotlin.math.abs(c)}%",
                style = MaterialTheme.typography.labelLarge,
                color = if (good) androidx.compose.ui.graphics.Color(0xFF1E9E6A) else MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** "₹35.6k": big numbers read at a glance. */
private fun formatCompactMoney(minor: Long, currency: String) = com.houseaffairs.app.core.util.formatCompact(minor, currency)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Highlights(p: PeriodReview) {
    // Finished and active time lead in the card above; these are the rest.
    val tiles = listOfNotNull(
        p.tasksDone.takeIf { it > 0 }?.let { "✅" to count(it, "task") + " done" },
        p.goalsReached.size.takeIf { it > 0 }?.let { "🎯" to count(it, "goal") + " reached" },
        p.mealsCooked.takeIf { it > 0 }?.let { "🍲" to count(it, "home meal") },
        p.diaryDays.takeIf { it > 0 }?.let { "📔" to "${p.diaryDays} of ${count(p.days, "day")} written" },
    )
    if (tiles.isEmpty()) return
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 2) {
        tiles.forEach { (emoji, text) ->
            HaCard(Modifier.weight(1f), color = LocalHouseColors.current.plans.container) {
                Text(emoji, style = MaterialTheme.typography.headlineSmall)
                Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun MoneyCard(p: PeriodReview, spent: Long, currency: String, year: Boolean) {
    val accent = LocalHouseColors.current.money
    HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(formatMinor(spent, currency), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text("spent by the home", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (p.topSpends.isNotEmpty()) {
            Text(
                "Most on " + p.topSpends.joinToString(" · ") { (c, n) -> "$c ${formatMinor(n, currency)}" },
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (p.savedMinor > 0) Text("💰 ${formatMinor(p.savedMinor, currency)} put towards dreams", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        // A year shows its shape: one bar per month.
        if (year && p.spendByMonth.size > 1) {
            val most = p.spendByMonth.maxOf { it.second }.coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(90.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                p.spendByMonth.forEach { (m, n) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.width(14.dp).height((60f * n / most).dp.coerceAtLeast(2.dp)).clip(RoundedCornerShape(4.dp)).background(accent.color))
                        Text(m.month.getDisplayName(TextStyle.NARROW, Locale.US), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryLine(emoji: String, title: String, body: String) {
    Row {
        Text(emoji, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(32.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
