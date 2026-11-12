package com.houseaffairs.app.feature.money

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.MoneyState
import com.houseaffairs.app.core.data.cyclesSince
import com.houseaffairs.app.core.data.monthOverview
import com.houseaffairs.app.core.ui.HaCard
import com.houseaffairs.app.core.ui.categoryIcon
import com.houseaffairs.app.feature.add.formatDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** One colour per category, in order of size, so the bar and the rows match. */
private val CATEGORY_COLORS = listOf(
    Color(0xFF2BC9A0), Color(0xFF6C7BFF), Color(0xFFFFB547), Color(0xFFE0507D),
    Color(0xFF36D1DC), Color(0xFFB06CFF), Color(0xFFFF8A65), Color(0xFF8BC34A),
)

/** The budget month's name: "November", or "25 Oct – 24 Nov" for a salary-day cycle. */
internal fun monthLabel(cycle: com.houseaffairs.app.core.data.Cycle): String =
    if (cycle.start.dayOfMonth == 1) cycle.start.format(DateTimeFormatter.ofPattern("MMMM"))
    else "${formatDate(cycle.start)} – ${formatDate(cycle.endExclusive.minusDays(1))}"

/**
 * This budget month at the top of "This month": what was spent and where, against last month.
 * Tapping it (outside a category) opens the year, where earlier months are.
 */
@Composable
internal fun MonthOverviewCard(m: MoneyState, money: (Long) -> String, cycle: com.houseaffairs.app.core.data.Cycle, onOpen: (String) -> Unit, onYear: () -> Unit) {
    val o = remember(m.txns, cycle) { monthOverview(m.txns, cycle, m.budget.cycleStartDay) }
    com.houseaffairs.app.core.ui.MonthCard(
        "${monthLabel(cycle)} in money", com.houseaffairs.app.core.designsystem.LocalHouseColors.current.money, onYear,
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).animateContentSize(),
    ) {
        Text("${money(o.totalMinor)} · ${o.count} expenses", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
        MonthOverviewBody(o, money, current = LocalDate.now() in cycle, onOpen = onOpen)
    }
}

/** Where one budget month's money went: against the month before, how it was paid, each category opening into its biggest expenses. */
@Composable
internal fun MonthOverviewBody(o: com.houseaffairs.app.core.data.MonthOverview, money: (Long) -> String, current: Boolean, onOpen: (String) -> Unit) {
    var expanded by remember(o.cycle) { mutableStateOf<String?>(null) }
    Column {
        if (o.categories.isEmpty()) {
            Text("Nothing spent this month yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        if (o.lastMonthMinor > 0) {
            val diff = o.totalMinor - o.lastMonthMinor
            Text(
                when {
                    current && diff < 0 -> "${money(-diff)} less than last month so far"
                    current -> "${money(diff)} more than all of last month"
                    diff < 0 -> "${money(-diff)} less than the month before"
                    else -> "${money(diff)} more than the month before"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (diff <= 0) LocalHouseColorsMoney() else MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
        // How it was paid: cash and card together make the month's total, so card spends are counted once.
        if (o.paidWith.size > 1 || o.paidWith.firstOrNull()?.first != "Not set") {
            Text(
                "Paid by " + o.paidWith.joinToString(" · ") { (method, amount) -> "$method ${money(amount)}" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
        // One stacked bar: every category's share of the month.
        Row(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            o.categories.forEachIndexed { i, c ->
                Box(Modifier.weight(c.share.coerceAtLeast(0.01f)).fillMaxHeight().background(CATEGORY_COLORS[i % CATEGORY_COLORS.size]))
            }
        }
        Spacer(Modifier.height(12.dp))
        o.categories.forEachIndexed { i, c ->
            val color = CATEGORY_COLORS[i % CATEGORY_COLORS.size]
            val open = expanded == c.name
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { expanded = if (open) null else c.name }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(categoryIcon(c.name), contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.titleSmall)
                    val delta = c.totalMinor - c.lastMonthMinor
                    Text(
                        listOfNotNull(
                            "${(c.share * 100).toInt()}%",
                            when {
                                c.lastMonthMinor == 0L -> "new this month"
                                delta > 0 -> "▲ ${money(delta)}"
                                delta < 0 -> "▼ ${money(-delta)}"
                                else -> "same as before"
                            },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(money(c.totalMinor), style = MaterialTheme.typography.titleSmall)
            }
            if (open) {
                Column(Modifier.padding(start = 44.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (c.subcategories.isNotEmpty()) {
                        Text(c.subcategories.joinToString(" · ") { (s, a) -> "$s ${money(a)}" }, style = MaterialTheme.typography.bodySmall, color = color)
                    }
                    c.top.forEach { t ->
                        Row(Modifier.fillMaxWidth().clickable { onOpen(t.id) }, verticalAlignment = Alignment.CenterVertically) {
                            Text("${t.title} · ${formatDate(t.date)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(money(t.amountMinor), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        if (o.trips.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Trips & outings: " + o.trips.joinToString(" · ") { (t, a) -> "$t ${money(a)}" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LocalHouseColorsMoney(): Color = com.houseaffairs.app.core.designsystem.LocalHouseColors.current.money.color
