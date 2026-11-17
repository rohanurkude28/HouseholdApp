package com.houseaffairs.app.feature.money

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.BudgetSummary
import com.houseaffairs.app.core.data.CategoryStatus
import com.houseaffairs.app.core.data.MoneyState
import com.houseaffairs.app.core.data.Pocket
import com.houseaffairs.app.core.data.Pace
import com.houseaffairs.app.core.ui.categoryIcon
import androidx.compose.material.icons.outlined.Redeem
import com.houseaffairs.app.core.data.Fit
import com.houseaffairs.app.core.data.dayLabel
import com.houseaffairs.app.core.data.upcomingFit
import com.houseaffairs.app.core.designsystem.Accent
import com.houseaffairs.app.core.designsystem.LocalHouseColors
import com.houseaffairs.app.core.time.systemToday
import com.houseaffairs.app.core.ui.HaCard
import com.houseaffairs.app.core.ui.IconBlob

// Money's Month pieces, drawn the same on both phones: the budget card, the add chips, the
// first-time prompt, and planned spends against what the month can carry.

@Composable
fun ActionChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        shape = CircleShape,
        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    )
}

@Composable
fun SetupPrompt(onSetUp: () -> Unit) {
    val money = LocalHouseColors.current.money
    HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), color = money.container) {
        IconBlob(Icons.Outlined.AccountBalanceWallet, Accent(Color.White, money.color), size = 52.dp)
        Spacer(Modifier.height(16.dp))
        Text("Set your monthly budget", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "Say ₹1,00,000 a month, starting on salary day. Every expense, planned spend and subscription counts against it, so you always know what's left.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onSetUp, shape = CircleShape) { Text("Set budget") }
    }
}

/** Big "left this month" number over a stacked bar of spent / planned / due / left. */
@Composable
fun BudgetHero(summary: BudgetSummary, money: (Long) -> String, gullakMinor: Long = 0, onCover: (() -> Unit)? = null) {
    val gradient = Brush.linearGradient(listOf(Color(0xFF0E9F76), Color(0xFF1A7FA0)))
    val over = summary.leftMinor < 0
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(gradient)
            .padding(24.dp),
    ) {
        Text(if (over) "Over budget by" else "Left this month", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
        Text(money(kotlin.math.abs(summary.leftMinor)), style = MaterialTheme.typography.displaySmall, color = Color.White)
        Text("of ${money(summary.limitMinor)}", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.85f))
        Spacer(Modifier.height(18.dp))

        val total = summary.limitMinor.coerceAtLeast(1).toFloat()
        val parts = listOf(
            summary.spentMinor to Color.White,
            summary.plannedMinor to Color.White.copy(alpha = 0.6f),
            summary.committedMinor to Color.White.copy(alpha = 0.35f),
        )
        Row(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f))) {
            parts.forEach { (amount, color) ->
                if (amount > 0) Box(Modifier.weight((amount / total).coerceIn(0.01f, 1f)).fillMaxHeight().background(color))
            }
            val rest = 1f - parts.sumOf { it.first.toDouble() }.toFloat() / total
            if (rest > 0.001f) Spacer(Modifier.weight(rest))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend("Spent", money(summary.spentMinor), Color.White)
            Legend("Planned", money(summary.plannedMinor), Color.White.copy(alpha = 0.6f))
            Legend("Due", money(summary.committedMinor), Color.White.copy(alpha = 0.35f))
        }
        Spacer(Modifier.height(16.dp))
        // What to do, in money terms (shared with iPhone): spend this much more, or cover the overspend.
        val line = com.houseaffairs.app.core.data.spendLine(summary, money, gullakMinor)
        Text(
            line,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 12.dp, vertical = 8.dp),
        )
        if (over && gullakMinor > 0 && onCover != null) {
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.FilledTonalButton(onClick = onCover) { Text("Cover it from the gullak") }
        }
    }
}

@Composable
private fun Legend(label: String, value: String, dot: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(6.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
            Text(value, style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
    }
}


/** Planned spends and bills in date order, each marked by whether the month can still carry it. */
@Composable
fun UpcomingCard(
    summary: BudgetSummary,
    envelopes: List<com.houseaffairs.app.core.data.Envelope>,
    money: (Long) -> String,
    onMarkPaid: (String) -> Unit,
    onOpen: (com.houseaffairs.app.core.data.UpcomingSpend) -> Unit,
) {
    val accent = LocalHouseColors.current.money
    val coral = LocalHouseColors.current.coral
    val error = MaterialTheme.colorScheme.error
    val fits = upcomingFit(summary, envelopes)
    HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        if (fits.isEmpty()) {
            Text(
                "Nothing planned. Use “Plan a spend” for trips, gifts or big buys and see if they fit before they happen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@HaCard
        }
        // The verdict for the whole list first.
        val last = fits.last()
        val (headline, color) = when (last.fit) {
            Fit.FITS -> "All of it fits. ${money(last.leftAfterMinor)} left after, savings goal kept." to accent.color
            Fit.EATS_SAVINGS -> "Fits, but leaves ${money(last.leftAfterMinor)}: ${money(summary.savingsGoalMinor - last.leftAfterMinor)} short of the savings goal." to coral
            Fit.OVER -> "${money(-last.leftAfterMinor)} more than this month's budget." to error
        }
        Text(headline, style = MaterialTheme.typography.titleSmall, color = color)
        Spacer(Modifier.height(12.dp))
        fits.forEachIndexed { index, f ->
            val u = f.spend
            if (index > 0) Spacer(Modifier.height(14.dp))
            val rowColor = when (f.fit) { Fit.FITS -> accent.color; Fit.EATS_SAVINGS -> coral; Fit.OVER -> error }
            Row(Modifier.clip(MaterialTheme.shapes.small).clickable { onOpen(u) }, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 4.dp, height = 40.dp).clip(CircleShape).background(rowColor))
                Spacer(Modifier.width(10.dp))
                IconBlob(if (u.planned) Icons.Outlined.EventNote else Icons.Outlined.Autorenew, accent, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(u.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // Not paid yet: say when, and whether the budget can carry it. No tick, which read as "paid".
                    Text(
                        if (u.id.startsWith(com.houseaffairs.app.core.data.TRIP_RESERVE)) "Left of the trip's budget · add trip expenses to use it"
                        else listOfNotNull(u.category.takeIf { u.planned }, (if (u.planned) "Planned for " else "Renews ") + dayLabel(u.date, systemToday())).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        when (f.fit) {
                            Fit.FITS -> "Within budget"
                            Fit.EATS_SAVINGS -> "Dips into savings"
                            Fit.OVER -> "Over budget"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = rowColor,
                        modifier = Modifier.padding(top = 3.dp).clip(CircleShape).background(rowColor.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                    f.envelopeShortMinor?.let {
                        Text("${money(it)} more than the ${u.category} envelope has", style = MaterialTheme.typography.bodySmall, color = coral)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(money(u.amountMinor), style = MaterialTheme.typography.titleSmall)
                    // A trip's line is its unspent budget: it goes down as expenses are added to the trip.
                    if (u.planned && !u.id.startsWith(com.houseaffairs.app.core.data.TRIP_RESERVE)) TextButton(onClick = { onMarkPaid(u.id) }) { Text("Mark paid") }
                }
            }
        }
    }
}


// Savings: envelopes per category and gift cards.

@Composable
fun EnvelopesCard(m: MoneyState, categories: List<CategoryStatus>, money: (Long) -> String, showAll: Boolean = true, onCategory: (String) -> Unit = {}, onMove: (Pocket, String) -> Unit) {
    val accent = LocalHouseColors.current.money
    HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        // Tightest first, so the ones that need attention are in the first few.
        val list = m.envelopes.envelopes.sortedBy { it.availableMinor.toDouble() / it.limitMinor.coerceAtLeast(1) }
        (if (showAll) list else list.take(4)).forEachIndexed { index, e ->
            if (index > 0) Spacer(Modifier.height(16.dp))
            val status = categories.firstOrNull { it.name == e.category }
            val total = (e.limitMinor + e.movedInMinor - e.movedOutMinor).coerceAtLeast(1)
            val negative = e.availableMinor < 0
            // Over only because of planned spends: a heads-up, not overspending.
            val overWithPlans = negative && e.availableMinor + e.plannedMinor >= 0
            val warn = if (overWithPlans) LocalHouseColors.current.coral else MaterialTheme.colorScheme.error
            // Tap for the category's whole history.
            Row(Modifier.clickable { onCategory(e.category) }, verticalAlignment = Alignment.CenterVertically) {
                IconBlob(categoryIcon(e.category), accent, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(e.category, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(
                            when {
                                overWithPlans -> "${money(-e.availableMinor)} over with plans"
                                negative -> "${money(-e.availableMinor)} over"
                                else -> "${money(e.availableMinor)} available"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (negative) warn else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                        val used = ((e.spentMinor + e.plannedMinor).toFloat() / total).coerceIn(0f, 1f)
                        if (used > 0f) Box(Modifier.weight(used).fillMaxHeight().background(if (negative) warn else accent.color))
                        if (used < 1f) Spacer(Modifier.weight(1f - used))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        listOfNotNull(
                            "${money(e.spentMinor)} spent of ${money(e.limitMinor)}",
                            e.plannedMinor.takeIf { it > 0 }?.let { "${money(it)} planned" },
                            (e.movedInMinor - e.movedOutMinor).takeIf { it != 0L }?.let { if (it > 0) "+${money(it)} moved in" else "−${money(-it)} moved out" },
                            e.creditsMinor.takeIf { it > 0 }?.let { "${money(it)} in gift cards" },
                            if (status?.pace == Pace.AHEAD && !negative) "going faster than the month allows" else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (e.availableMinor > 0) {
                    TextButton(onClick = { onMove(Pocket.Category(e.category), e.category) }) { Text("Move") }
                }
            }
        }
        if (showAll) categories.filter { it.limitMinor == null && it.spentMinor > 0 }.forEach { c ->
            Spacer(Modifier.height(14.dp))
            Row(Modifier.clickable { onCategory(c.name) }, verticalAlignment = Alignment.CenterVertically) {
                IconBlob(categoryIcon(c.name), accent, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.titleSmall)
                    Text("No envelope · comes out of the gullak", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(money(c.spentMinor), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun CreditsCard(m: MoneyState, money: (Long) -> String, onUsed: (String) -> Unit, onOpen: (String) -> Unit) {
    val accent = LocalHouseColors.current.money
    HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        if (m.credits.isEmpty()) {
            Text(
                "Add gift cards, card points, cashback or vouchers, and save each for a category or a dream. Say “got Amazon gift card 2000 for gifting”.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        m.credits.forEachIndexed { index, c ->
            if (index > 0) Spacer(Modifier.height(14.dp))
            Row(Modifier.clip(MaterialTheme.shapes.small).clickable { onOpen(c.id) }, verticalAlignment = Alignment.CenterVertically) {
                IconBlob(Icons.Outlined.Redeem, accent, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(c.kind, c.earmark?.let { "for $it" } ?: "for anything", c.expiry?.let { "expires ${dayLabel(it, systemToday())}" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(money(c.valueMinor), style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { onUsed(c.id) }) { Text("Used") }
                }
            }
        }
    }
}
