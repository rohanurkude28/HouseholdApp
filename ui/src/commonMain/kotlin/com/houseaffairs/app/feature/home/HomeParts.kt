package com.houseaffairs.app.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.Area
import com.houseaffairs.app.core.data.Moment
import com.houseaffairs.app.core.data.Priority
import com.houseaffairs.app.core.data.cheerLine
import com.houseaffairs.app.core.data.clearedLine
import com.houseaffairs.app.core.data.milestoneTitle
import com.houseaffairs.app.core.data.nextUp
import com.houseaffairs.app.core.data.streakLine
import com.houseaffairs.app.core.designsystem.Accent
import com.houseaffairs.app.core.designsystem.LocalHouseColors
import com.houseaffairs.app.core.time.LocalDate
import com.houseaffairs.app.core.time.daysBetween
import com.houseaffairs.app.core.time.format
import com.houseaffairs.app.core.ui.DayPart
import com.houseaffairs.app.core.ui.HaCard
import com.houseaffairs.app.core.ui.HeroBackground
import com.houseaffairs.app.core.ui.IconBlob
import com.houseaffairs.app.core.ui.SectionTitle
import com.houseaffairs.app.core.ui.opensEdit

// Home's pieces, drawn the same on both phones. Each phone decides what goes on them (its own
// data and actions); the rules behind them (momentCards, heroPeek, comingUp…) are in :shared.

@Composable
fun areaAccent(area: Area): Accent {
    val colors = LocalHouseColors.current
    return when (area) {
        Area.MONEY -> colors.money
        Area.KITCHEN -> colors.stock
        Area.HEALTH -> colors.vault
        Area.PLANS -> colors.plans
    }
}

fun areaIcon(area: Area): ImageVector = when (area) {
    Area.MONEY -> Icons.Outlined.AccountBalanceWallet
    Area.KITCHEN -> Icons.Outlined.Kitchen
    Area.HEALTH -> Icons.Outlined.LocalHospital
    Area.PLANS -> Icons.Outlined.Event
}

@Composable
fun priorityColor(priority: Priority?): Color = when (priority) {
    Priority.URGENT -> MaterialTheme.colorScheme.error
    Priority.TODAY -> LocalHouseColors.current.coral
    Priority.SOON -> LocalHouseColors.current.plans.color
    null -> MaterialTheme.colorScheme.outlineVariant
}

fun peekIcon(kind: PeekKind): ImageVector = when (kind) {
    PeekKind.BUDGET -> Icons.Outlined.AccountBalanceWallet
    PeekKind.SPENT -> Icons.Outlined.Receipt
    PeekKind.TASKS -> Icons.Outlined.TaskAlt
    PeekKind.EVENT -> Icons.Outlined.Cake
    PeekKind.EXPIRING -> Icons.Outlined.Timer
    PeekKind.SHOPPING -> Icons.Outlined.ShoppingCart
    PeekKind.CALM -> Icons.Outlined.CheckCircle
}

/**
 * The top of Home: a time-of-day sky, the date, search and people, the greeting, the day in one
 * line, the day's numbers, and the Ask bar sitting on the hill.
 */
@Composable
fun HomeHero(
    dayPart: DayPart,
    today: LocalDate,
    greeting: String,
    line: String,
    peeks: List<Peek>?,
    onPeek: (String) -> Unit,
    onAsk: () -> Unit,
    onSearch: () -> Unit,
    /** Shown instead of the numbers when this person turned them off. */
    summary: String? = null,
    /** Hidden amounts on or off, for people who can see money. */
    hidden: Boolean? = null,
    onToggleHidden: () -> Unit = {},
    people: @Composable () -> Unit = {},
) {
    HeroBackground(
        dayPart = dayPart,
        groundColor = MaterialTheme.colorScheme.background,
        // At least this tall, and taller with large text, so the Ask bar never covers the numbers.
        modifier = Modifier.fillMaxWidth().heightIn(min = (if (summary == null) 366 else 300).dp),
    ) {
        Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 76.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(today.format("EEEE, d MMMM").uppercase(), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
                // Search the whole home by name or topic ("travel").
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).clickable(onClick = onSearch),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Search, contentDescription = "Search", tint = Color.White, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(8.dp))
                people()
            }
            Spacer(Modifier.height(12.dp))
            // Clear of the sun or moon, and onto a second line with large text rather than cut.
            Text(greeting, style = MaterialTheme.typography.headlineMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 36.dp))
            if (summary == null) {
                // The whole day in one plain sentence, before any numbers.
                Spacer(Modifier.height(4.dp))
                Text(line, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.92f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(14.dp))
                if (peeks != null) PeekStrip(peeks, onPeek, hidden, onToggleHidden)
            } else {
                Spacer(Modifier.height(14.dp))
                Text(line, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.92f))
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
            }
        }
        AskBar(onAsk, Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 4.dp))
    }
}

/** The day at a glance: budget left, what's due, the next birthday, what's expiring. */
@Composable
private fun PeekStrip(peeks: List<Peek>, onPeek: (String) -> Unit, hidden: Boolean?, onToggleHidden: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(peeks.size) { index ->
                val p = peeks[index]
                Column(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .then(p.route?.let { r -> Modifier.clickable { onPeek(r) } } ?: Modifier)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(peekIcon(p.kind), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(p.value, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    }
                    Text(p.label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
                }
            }
        }
        if (hidden != null) {
            IconButton(onClick = onToggleHidden) {
                Icon(
                    if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (hidden) "Show amounts" else "Hide amounts",
                    tint = Color.White,
                )
            }
        }
    }
}

/** Looks like a search box: one tap to add anything by typing or speaking. */
@Composable
fun AskBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 10.dp,
        modifier = modifier.fillMaxWidth().height(60.dp),
    ) {
        Row(Modifier.padding(start = 10.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(listOf(LocalHouseColors.current.coral, MaterialTheme.colorScheme.primary))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(12.dp))
            Text("Ask, add or find anything…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Mic, contentDescription = "Speak", tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

/** "🌆 This evening", a ring of cleared/total, and "11 of 15 done · 🔥 2 days in a row". */
@Composable
fun TodayHeader(moment: Moment, done: Int, total: Int, streak: Int) {
    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${moment.emoji}  ${moment.label.replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        // Today as a ring that fills as things get cleared, like an activity ring.
        if (total > 0) {
            CircularProgressIndicator(
                progress = { done.toFloat() / total },
                modifier = Modifier.size(28.dp),
                strokeWidth = 4.dp,
                color = LocalHouseColors.current.money.color,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
    }
    val progress = listOfNotNull("$done of $total done".takeIf { total > 0 }, streakLine(streak)).joinToString(" · ")
    if (progress.isNotEmpty()) {
        Text(progress, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp))
    }
}

val DECK_HEIGHT = 216.dp

/** Cards swiped sideways, the next one peeking in, with dots under them. [keys] name the pages. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeckPager(keys: List<String>, page: @Composable (Int) -> Unit) {
    val pager = rememberPagerState { keys.size }
    HorizontalPager(
        state = pager,
        contentPadding = PaddingValues(start = 20.dp, end = if (keys.size <= 1) 20.dp else 48.dp),
        pageSpacing = 12.dp,
        key = { keys.getOrElse(it) { "page$it" } },
    ) { page(it) }
    if (keys.size > 1) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
            repeat(keys.size) { i ->
                val on = i == pager.currentPage
                Box(
                    Modifier.padding(horizontal = 3.dp).size(width = if (on) 18.dp else 6.dp, height = 6.dp).clip(CircleShape)
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
    }
}

/** One card of the deck: a small heading, one big line, and [buttons] that say what tapping does. */
@Composable
fun DeckCard(
    eyebrow: String,
    position: String,
    title: String,
    detail: String,
    accent: Accent,
    emoji: String = "",
    icon: ImageVector? = null,
    onOpen: (() -> Unit)? = null,
    buttons: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = accent.container,
        modifier = Modifier.fillMaxWidth().height(DECK_HEIGHT).clip(RoundedCornerShape(28.dp)).then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelMedium, color = accent.color, modifier = Modifier.weight(1f))
                Text(position, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(12.dp))
                if (emoji.isNotEmpty()) Text(emoji, style = MaterialTheme.typography.displaySmall) else if (icon != null) IconBlob(icon, accent, size = 44.dp)
            }
            Spacer(Modifier.weight(1f))
            buttons()
        }
    }
}

/** The deck's usual buttons: the main one in the card's colour, and a quiet "Not now". */
@Composable
fun DeckButtons(main: String, onMain: () -> Unit, accent: Accent, later: String = "", onLater: () -> Unit = {}) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onMain, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = accent.color, contentColor = Color.White)) { Text(main) }
        if (later.isNotEmpty()) TextButton(onClick = onLater) { Text(later, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** A milestone on the way (3, 5, 10… cleared today): noticed, cheered, then out of the way. */
@Composable
fun MilestoneCard(n: Int, today: LocalDate, keepGoing: () -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = LocalHouseColors.current.money.container, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🎉", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(milestoneTitle(n), style = MaterialTheme.typography.titleLarge)
                Text(cheerLine(today), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = keepGoing) { Text("Keep going") }
        }
    }
}

/** Nothing left for this person today: a cheer, a different nudge each day, and how much got cleared. */
@Composable
fun CheerCard(moment: Moment, cleared: Int, today: LocalDate, onAdd: () -> Unit, compact: Boolean = false) {
    val colors = LocalHouseColors.current
    val line = (if (cleared > 0) "You cleared $cleared today. " else "") + cheerLine(today)
    // With ideas still to swipe underneath, the cheer is one short row, not a page.
    if (compact) {
        Surface(shape = RoundedCornerShape(24.dp), color = colors.money.container, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🎉", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Hurray, nothing for you!", style = MaterialTheme.typography.titleMedium)
                    Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        return
    }
    Surface(shape = RoundedCornerShape(28.dp), color = colors.money.container, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Column(Modifier.padding(24.dp)) {
            Text("🎉", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(8.dp))
            Text("Hurray, nothing for you!", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(line, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(nextUp(moment), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onAdd, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Add something") }
        }
    }
}

/** The last card: this part of the day is done, and here's what the next one brings. */
@Composable
fun EndCard(moment: Moment, answered: Boolean, onAdd: () -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().height(DECK_HEIGHT)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.Center) {
            Text(if (answered) "🎉" else moment.emoji, style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            Text(if (answered) "That's all for ${moment.label.removePrefix("this ")}" else "Nothing needs you ${moment.label}", style = MaterialTheme.typography.titleLarge)
            Text(nextUp(moment), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onAdd, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Add something") }
        }
    }
}

/** "Left to clear" and how far along: everything else open, each a tap from cleared. */
@Composable
fun LeftToClearTitle(cleared: Int, left: Int) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Left to clear", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(clearedLine(cleared, left), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One open thing: a priority stripe, the area's icon, two lines, and its one-tap action. */
@Composable
fun AttentionRow(priority: Priority?, area: Area, title: String, detail: String, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(Modifier.clip(MaterialTheme.shapes.small).opensEdit(onClick), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 4.dp, height = 36.dp).clip(CircleShape).background(priorityColor(priority)))
        Spacer(Modifier.width(12.dp))
        IconBlob(areaIcon(area), areaAccent(area), size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

/** A calendar-style tile: weekday over day number, or "TODAY". */
@Composable
fun DateTile(date: LocalDate?, today: LocalDate, accent: Accent, urgent: Boolean) {
    val bg = if (urgent) MaterialTheme.colorScheme.error.copy(alpha = 0.12f) else accent.container
    val fg = if (urgent) MaterialTheme.colorScheme.error else accent.color
    Column(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(bg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        when {
            date == null -> Text("—", style = MaterialTheme.typography.titleMedium, color = fg)
            else -> {
                Text(if (date == today) "TODAY" else date.format("EEE").uppercase(), style = MaterialTheme.typography.labelSmall, color = fg)
                Text(date.format("d"), style = MaterialTheme.typography.titleMedium, color = fg)
            }
        }
    }
}

@Composable
fun EmptyHint(title: String, hint: String, onClick: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A strip of small date cards, like a calendar widget: the next month at a glance, swiped. */
@Composable
fun ComingUpStrip(rows: List<ComingUpRow>, today: LocalDate, onOpen: (ComingUpRow) -> Unit, onGifts: () -> Unit) {
    SectionTitle("Coming up")
    if (rows.isEmpty()) {
        HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            EmptyHint("Nothing in the next 30 days", "Add birthdays and renewals: “Riya birthday 12 Dec”")
        }
        return
    }
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(rows.size) { i ->
            val row = rows[i]
            val accent = areaAccent(row.area)
            val days = daysBetween(today, row.date)
            Column(
                Modifier.width(148.dp).height(150.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .clickable { onOpen(row) }.padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DateTile(row.date, today, accent, urgent = false)
                    Spacer(Modifier.weight(1f))
                    Text(when (days) { 0L -> "Today"; 1L -> "Tmrw"; else -> "${days}d" }, style = MaterialTheme.typography.labelLarge, color = accent.color)
                }
                Spacer(Modifier.height(10.dp))
                Text(row.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                // Its own tap target: the card opens the date, this opens its gift ideas.
                if (row.gifts > 0) {
                    Text(
                        "🎁 ${row.gifts} idea${if (row.gifts > 1) "s" else ""} ›",
                        style = MaterialTheme.typography.labelMedium, color = accent.color,
                        modifier = Modifier.clip(CircleShape).clickable(onClick = onGifts).padding(vertical = 2.dp),
                    )
                } else {
                    Text(row.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** A few gentle pushes: tidy what's gone stale, add what's missing. "Not today" puts it away till tomorrow. */
@Composable
fun TidyCard(nudges: List<HomeNudge>, onDismiss: () -> Unit, onOpen: (String) -> Unit, modifier: Modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (nudges.any { it.tidy }) "Tidy up" else "Worth adding", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Not today") }
        }
        nudges.forEach { n ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(n.route) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(n.emoji, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(12.dp))
                Text(n.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { onOpen(n.route) }) { Text(n.action) }
            }
        }
    }
}

/** What the rest of the family added lately, so there's something new each time Home opens. */
@Composable
fun FamilyFeedCard(recent: List<com.houseaffairs.app.core.data.ActivityItem>, myUid: String, nameOf: (String) -> String, onOpen: (String) -> Unit) {
    val now = com.houseaffairs.app.core.time.nowMillis()
    val feed = remember(recent) { com.houseaffairs.app.core.data.familyFeed(recent, myUid, now, nameOf) }
    if (feed.isEmpty()) return
    SectionTitle("What's new at home")
    HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        feed.forEachIndexed { i, line ->
            if (i > 0) Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable {
                    onOpen(
                        when (line.kind) {
                            com.houseaffairs.app.core.data.ActivityKind.EXPENSE, com.houseaffairs.app.core.data.ActivityKind.SUBSCRIPTION -> "money"
                            com.houseaffairs.app.core.data.ActivityKind.SHOPPING -> "stock/SHOPPING"
                            com.houseaffairs.app.core.data.ActivityKind.GROCERY -> "stock"
                            com.houseaffairs.app.core.data.ActivityKind.TASK -> "plans/TASKS"
                            com.houseaffairs.app.core.data.ActivityKind.LIST_ITEM -> "plans/LISTS"
                            com.houseaffairs.app.core.data.ActivityKind.EVENT -> "plans/EVENTS"
                            com.houseaffairs.app.core.data.ActivityKind.MEDICINE -> "vault/MEDICINES"
                        },
                    )
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(line.emoji, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(12.dp))
                Text(line.text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(com.houseaffairs.app.core.data.agoText(line.whenMillis, now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
