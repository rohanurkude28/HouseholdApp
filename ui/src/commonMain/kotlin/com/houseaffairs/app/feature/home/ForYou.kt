package com.houseaffairs.app.feature.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.DishIdea
import com.houseaffairs.app.core.data.StockItem
import com.houseaffairs.app.core.data.Recipe
import com.houseaffairs.app.core.data.RightNow
import com.houseaffairs.app.core.data.SectionPref
import com.houseaffairs.app.core.data.HomeSection
import com.houseaffairs.app.core.data.ShoppingItem
import com.houseaffairs.app.core.data.SuggestionKind
import com.houseaffairs.app.core.data.WatchItem
import com.houseaffairs.app.core.designsystem.LocalHouseColors
import com.houseaffairs.app.core.time.LocalDate
import com.houseaffairs.app.core.time.isAfter
import com.houseaffairs.app.core.time.isBefore
import com.houseaffairs.app.core.time.plusDays
import com.houseaffairs.app.core.ui.DishCard
import com.houseaffairs.app.core.ui.HaCard
import com.houseaffairs.app.core.ui.IconBlob
import com.houseaffairs.app.core.ui.LocalHouseImages
import com.houseaffairs.app.core.ui.TickCircle
import com.houseaffairs.app.core.ui.opensEdit
import com.houseaffairs.app.core.util.oneDecimal

/** How many dishes For you's Cook tab shows. */
const val COOK_TAB_SIZE = 6

enum class ForYouTab(val label: String) { RIGHT_NOW("Right now"), COOK("Cook"), WATCH("Lists"), SHOPPING("Shopping") }

/** The For you tabs [layout] switches on, in its order, given what this person can see. */
fun forYouTabs(layout: List<SectionPref>, rightNow: RightNow?, kitchen: Boolean, plans: Boolean): List<ForYouTab> =
    layout.filter { it.visible }.mapNotNull { pref ->
        when (pref.section) {
            HomeSection.RIGHT_NOW -> ForYouTab.RIGHT_NOW.takeIf { rightNow != null }
            HomeSection.USE_IT_UP -> ForYouTab.COOK.takeIf { kitchen }
            HomeSection.WATCH -> ForYouTab.WATCH.takeIf { plans }
            HomeSection.SHOPPING -> ForYouTab.SHOPPING.takeIf { kitchen }
            else -> null
        }
    }

/** What For you shows, gathered by each phone. */
class ForYouData(
    val rightNow: RightNow?,
    val ideas: List<DishIdea>,
    val cookWhy: Map<String, String>,
    val watch: List<WatchItem>,
    val watchWhy: Map<String, String>,
    val shopping: List<ShoppingItem>,
    val pantry: List<StockItem>,
)

/** Where For you's taps go: another tab ("plans/LISTS", "stock/RECIPES"…), a form, or a quick tick. */
class ForYouActions(
    val openTab: (String) -> Unit,
    val addRecipe: (Recipe) -> Unit,
    val editWish: (String) -> Unit,
    val editShopping: (String) -> Unit,
    val watched: (String) -> Unit,
    val bought: (String) -> Unit,
)

/** One card, four tabs: what suits this moment, what to cook, what to watch, what to buy. */
@Composable
fun ForYouCard(tabs: List<ForYouTab>, data: ForYouData, today: LocalDate, actions: ForYouActions) {
    if (tabs.isEmpty()) return
    var tab by rememberSaveable { mutableStateOf(tabs.first()) }
    val shown = if (tab in tabs) tab else tabs.first()
    Column(Modifier.padding(top = 24.dp)) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("For you", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                actions.openTab(
                    when (shown) {
                        ForYouTab.RIGHT_NOW, ForYouTab.WATCH -> "plans/LISTS"
                        ForYouTab.COOK -> "stock/RECIPES"
                        ForYouTab.SHOPPING -> "stock/SHOPPING"
                    },
                )
            }) { Text("See all") }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tabs.size) { i ->
                val t = tabs[i]
                val label = when (t) {
                    ForYouTab.RIGHT_NOW -> data.rightNow?.headline?.takeIf { it.length <= 22 } ?: t.label
                    ForYouTab.SHOPPING -> "${t.label} · ${data.shopping.size}"
                    ForYouTab.WATCH -> "🎬📚🎵 ${t.label} · ${data.watch.size}"
                    else -> t.label
                }
                FilterChip(
                    selected = t == shown,
                    onClick = { tab = t },
                    label = { Text(label) },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        when (shown) {
            ForYouTab.RIGHT_NOW -> data.rightNow?.let { RightNowRow(it, actions) }
            ForYouTab.COOK -> Column {
                // What's about to go off, so the dishes below read as "use these up".
                val expiring = data.pantry
                    .filter { s -> s.expiry?.let { !it.isBefore(today) && !it.isAfter(today.plusDays(3L)) } == true }
                    .sortedBy { it.expiry }
                if (expiring.isNotEmpty()) {
                    Text(
                        "Use soon: " + expiring.take(4).joinToString { "${it.title} (${dueLabel(it.expiry!!, today).first.lowercase()})" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
                    )
                }
                if (data.ideas.isEmpty()) {
                    HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        EmptyHint("Nothing to suggest yet.", "Add what's in your kitchen: “bought 1kg paneer expires Friday”", onClick = null)
                    }
                } else {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(data.ideas.take(COOK_TAB_SIZE).size) { index ->
                            val idea = data.ideas[index]
                            DishCard(idea, onClick = { if (idea.saved) actions.openTab("stock/RECIPES") else actions.addRecipe(idea.recipe) }, why = data.cookWhy[idea.recipe.title])
                        }
                    }
                }
            }
            ForYouTab.WATCH -> if (data.watch.isEmpty()) {
                HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    EmptyHint("No movies, books or songs yet.", "Say “add Dune 2 to watch list” or “add Atomic Habits to reading list”", onClick = null)
                }
            } else {
                WatchRow(data.watch, data.watchWhy, actions)
            }
            ForYouTab.SHOPPING -> ShoppingCard(data.shopping, actions)
        }
    }
}

@Composable
private fun RightNowRow(now: RightNow, actions: ForYouActions) {
    val colors = LocalHouseColors.current
    val images = LocalHouseImages.current
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(now.items.size) { i ->
            val s = now.items[i]
            val (icon, accent) = when (s.kind) {
                SuggestionKind.FOOD -> Icons.Outlined.RestaurantMenu to colors.stock
                SuggestionKind.WATCH -> Icons.Outlined.Tv to colors.vault
                SuggestionKind.EAT_OUT -> Icons.Outlined.Restaurant to colors.plans
                SuggestionKind.SHOP -> Icons.Outlined.ShoppingBag to colors.money
            }
            HaCard(
                Modifier.width(180.dp).height(150.dp),
                onClick = {
                    val dish = s.dish
                    when {
                        dish != null && !dish.saved -> actions.addRecipe(dish.recipe)
                        s.kind == SuggestionKind.FOOD -> actions.openTab("stock/RECIPES")
                        s.kind == SuggestionKind.SHOP -> actions.openTab("money")
                        s.kind == SuggestionKind.WATCH -> actions.openTab("plans/LISTS")
                        else -> actions.openTab("plans/PLACES")
                    }
                },
            ) {
                val image = s.image
                if (image != null && images.web) images.Web(image, null, Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)))
                else IconBlob(icon, accent, size = 40.dp)
                Spacer(Modifier.weight(1f))
                Text(s.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(s.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Posters with rating and where to watch. */
@Composable
private fun WatchRow(watch: List<WatchItem>, why: Map<String, String>, actions: ForYouActions) {
    val images = LocalHouseImages.current
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(watch.size) { i ->
            val w = watch[i]
            Column(Modifier.width(112.dp).clip(RoundedCornerShape(18.dp)).clickable { actions.editWish(w.id) }) {
                Box(
                    Modifier
                        .size(width = 112.dp, height = 156.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF8E6BFF), Color(0xFF5B3FD9)))),
                ) {
                    val poster = w.poster
                    if (poster != null && images.web) {
                        images.Web(poster, w.title, Modifier.matchParentSize())
                    } else {
                        Text(
                            w.title.take(1).uppercase(),
                            style = MaterialTheme.typography.displayMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    // Which list it's from, so movies, books and songs can sit in one row.
                    Text(
                        w.template.emoji,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    w.rating?.let { r ->
                        Text(
                            "★ ${oneDecimal(r)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    // A round ✓ on the poster: tap once it's watched (read, heard); Undo follows.
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable(onClickLabel = w.template.markLabel) { actions.watched(w.id) }
                            .semantics { contentDescription = "${w.template.markLabel}: ${w.title}" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(w.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                why[w.title]?.let { Text("✨ $it", style = MaterialTheme.typography.bodySmall, color = LocalHouseColors.current.vault.color, maxLines = 3) }
                Text(
                    listOfNotNull(w.creator ?: w.kind, w.year).joinToString(" · ").ifEmpty { w.template.title },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Always one line, so the cards are the same height across posters.
                val yt = w.youtubeUrl
                Text(
                    if (yt != null) "▶ Play on YouTube" else w.whereToWatch?.let { "▶ $it" } ?: " ",
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalHouseColors.current.vault.color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (yt != null) Modifier.clickable { runCatching { uri.openUri(yt) } } else Modifier,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun ShoppingCard(items: List<ShoppingItem>, actions: ForYouActions) {
    HaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp).animateContentSize()) {
        if (items.isEmpty()) {
            EmptyHint("Your list is empty.", "Say “add milk, eggs and bread to shopping”", onClick = null)
        } else {
            // Grouped by aisle, each with its own colour, so the list reads like the shop.
            items.groupBy { it.aisle }.entries.sortedBy { it.key.ordinal }.forEachIndexed { gi, (aisle, list) ->
                val color = Color(aisle.argb)
                if (gi > 0) Spacer(Modifier.height(12.dp))
                Text("${aisle.emoji}  ${aisle.label}", style = MaterialTheme.typography.labelMedium, color = color)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.forEach { item ->
                        // Circle = bought; the name (tap or hold) = edit.
                        Row(Modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)), verticalAlignment = Alignment.CenterVertically) {
                            TickCircle(color, onTick = { actions.bought(item.id) }, size = 18.dp, contentDescription = "Bought ${item.name}")
                            Text(
                                item.name,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.clip(CircleShape).opensEdit { actions.editShopping(item.id) }.padding(start = 2.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Tap the circle once it's in the cart · tap the name to edit",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Customise Home: each section on or off and up or down, kept on this phone. [showOnly] is Android's narrowing chips. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomiseSheet(
    layout: List<SectionPref>,
    onToggle: (HomeSection) -> Unit,
    onMove: (HomeSection, Boolean) -> Unit,
    onDismiss: () -> Unit,
    showOnly: (@Composable () -> Unit)? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.navigationBarsPadding().padding(start = 20.dp, end = 12.dp, bottom = 16.dp)) {
            Text("Customise Home", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Choose what you see and in which order. Only on this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showOnly != null) {
                Spacer(Modifier.height(12.dp))
                Text("Show only", style = MaterialTheme.typography.titleSmall)
                showOnly()
            }
            Spacer(Modifier.height(12.dp))
            layout.forEachIndexed { index, pref ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(pref.section.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onMove(pref.section, true) }, enabled = index > 0) {
                        Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Move up")
                    }
                    IconButton(onClick = { onMove(pref.section, false) }, enabled = index < layout.lastIndex) {
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Move down")
                    }
                    Switch(checked = pref.visible, onCheckedChange = { onToggle(pref.section) })
                }
            }
        }
    }
}
