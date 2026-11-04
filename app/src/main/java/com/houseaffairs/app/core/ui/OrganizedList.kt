package com.houseaffairs.app.core.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.houseaffairs.app.core.data.Flag
import com.houseaffairs.app.core.data.Aisle
import com.houseaffairs.app.core.designsystem.Accent
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.Add
import com.houseaffairs.app.core.designsystem.LocalHouseColors
import com.houseaffairs.app.core.data.ListFilter
import com.houseaffairs.app.core.data.ListRow
import com.houseaffairs.app.core.data.OrganizedList
import com.houseaffairs.app.core.data.SettingsRepository
import com.houseaffairs.app.core.data.organize
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Above this many items a list also gets filter chips; the search box shows whenever there's anything. */
const val LONG_LIST = 10

/** Which list groups this phone has opened or closed, shared by every long list. */
@HiltViewModel
class ListGroupsViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    val toggled: StateFlow<Set<String>> = settings.toggledGroups.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    fun toggle(key: String) {
        viewModelScope.launch { settings.toggleGroup(key) }
    }
}

/** Search, filter and category choice for one list; kept across rotation. */
class ListControlsState(val query: MutableState<String>, val filter: MutableState<ListFilter>, val category: MutableState<String?>)

@Composable
fun rememberListControls(): ListControlsState = ListControlsState(
    rememberSaveable { mutableStateOf("") },
    rememberSaveable { mutableStateOf(ListFilter.ALL) },
    rememberSaveable { mutableStateOf<String?>(null) },
)

/** [rows] arranged for [listId] with the person's search, filters and open groups. */
@Composable
fun <T> rememberOrganized(
    listId: String,
    rows: List<ListRow<T>>,
    controls: ListControlsState,
    order: List<String> = emptyList(),
    openUpTo: Int = 5,
    groups: ListGroupsViewModel = hiltViewModel(),
    /** The list's own search, for lists that have a smarter one than matching words. */
    search: ((String) -> Set<String>)? = null,
): OrganizedList<T> {
    val toggled by groups.toggled.collectAsState()
    val mine = toggled.filter { it.startsWith("$listId:") }.map { it.removePrefix("$listId:") }.toSet()
    return remember(rows, controls.query.value, controls.filter.value, controls.category.value, mine, order, openUpTo) {
        val matching = search?.takeIf { controls.query.value.isNotBlank() }?.invoke(controls.query.value)
        organize(rows, controls.query.value, controls.filter.value, controls.category.value, order, mine, openUpTo, matching)
    }
}

/** Colour for a flag: red when expired, coral when expiring, amber when low. */
@Composable
fun flagColor(flag: Flag?): Color = when (flag) {
    Flag.EXPIRED -> MaterialTheme.colorScheme.error
    Flag.EXPIRING -> LocalHouseColors.current.coral
    Flag.LOW -> Color(0xFFD39B32)
    null -> MaterialTheme.colorScheme.outlineVariant
}

/**
 * Search box and chips: All, Expiring, Running low (when [flags]), then each category with its
 * count. Only drawn once a list is long enough to need it.
 */
@Composable
fun ListToolbar(
    controls: ListControlsState,
    organized: OrganizedList<*>,
    placeholder: String,
    flags: Boolean = true,
    categoryLabel: (String) -> String = { it },
) {
    var query by controls.query
    var filter by controls.filter
    var category by controls.category
    TextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, contentDescription = "Clear search") } },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp),
    )
    if (organized.total <= LONG_LIST && controls.filter.value == ListFilter.ALL && controls.category.value == null) return
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        val filters = if (flags) ListFilter.entries else listOf(ListFilter.ALL)
        items(filters) { f ->
            Chip(f.label, selected = filter == f && category == null) { filter = f; category = null }
        }
        items(organized.categories) { (name, count) ->
            Chip("${categoryLabel(name)} $count", selected = category == name) {
                category = if (category == name) null else name
                filter = ListFilter.ALL
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
    )
}

/**
 * "Needs a look" first, then a header per category (stays on top while scrolling, tap to open or
 * close) and its rows. [style] gives a category its emoji and colour, when it has them.
 */
@OptIn(ExperimentalFoundationApi::class)
fun <T> LazyListScope.organizedSections(
    listId: String,
    organized: OrganizedList<T>,
    onToggle: (String) -> Unit,
    style: (String) -> Pair<String, Color?> = ::categoryStyle,
    label: (String) -> String = { it },
    emptyText: String = "Nothing matches.",
    row: @Composable (ListRow<T>) -> Unit,
) {
    if (organized.needsLook.isNotEmpty()) {
        item(key = "$listId-look") {
            Text(
                "Needs a look · ${organized.needsLook.size}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 2.dp),
            )
        }
        items(organized.needsLook, key = { "$listId-look-${it.key}" }) { row(it) }
    }
    if (organized.narrowed && organized.sections.isEmpty()) {
        item(key = "$listId-none") {
            Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
        }
    }
    organized.sections.forEach { section ->
        stickyHeader(key = "$listId-head-${section.category}") {
            val (emoji, color) = style(section.category)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .clickable(enabled = !organized.narrowed, onClickLabel = if (section.open) "Close" else "Open") { onToggle(section.category) }
                    .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    listOf(emoji, "${label(section.category)} · ${section.rows.size} ${if (section.rows.size == 1) "item" else "items"}").filter { it.isNotEmpty() }.joinToString("  "),
                    style = MaterialTheme.typography.labelLarge,
                    color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                // A closed group still says when something inside needs a look.
                if (section.flagged > 0 && !section.open) {
                    Text("${section.flagged} need a look", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(6.dp))
                }
                if (!organized.narrowed) {
                    Icon(
                        if (section.open) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (section.open) items(section.rows, key = { "$listId-row-${it.key}" }) { row(it) }
    }
}

/**
 * One line per item: a status dot, the name, a short detail (quantity, expiry) and a menu with
 * its actions. Tapping the row opens it for editing.
 */
@Composable
fun CompactRow(
    title: String,
    detail: String,
    flag: Flag?,
    onClick: () -> Unit,
    actions: List<Pair<String, () -> Unit>> = emptyList(),
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        // Same height with or without a menu, so every list has the same rhythm.
        Modifier.fillMaxWidth().heightIn(min = 52.dp).opensEdit(onClick).padding(start = 20.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
        } else {
            Box(Modifier.size(8.dp).background(flagColor(flag), CircleShape))
        }
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = if (flag == null) MaterialTheme.colorScheme.onSurfaceVariant else flagColor(flag),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 170.dp),
        )
        // The menu's slot is always there, so the details line up across every list.
        if (actions.isEmpty()) {
            Spacer(Modifier.width(48.dp))
        } else {
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More for $title") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    actions.forEach { (label, action) -> DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; action() }) }
                }
            }
        }
    }
}

/** The tinted "+ Add …" button at the top of every list. */
@Composable
fun AddRow(label: String, accent: Accent, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .clip(MaterialTheme.shapes.large)
            .background(accent.container)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null, tint = accent.color)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = accent.color)
    }
}

/** What an empty list says: a title and a hint with an example to say. */
@Composable
fun Empty(title: String, body: String) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Emoji and colour for every category of every list, so headers look the same everywhere. */
fun categoryStyle(category: String): Pair<String, Color> {
    Aisle.from(category)?.let { return it.emoji to Color(it.argb) }
    return CATEGORY_STYLES[category] ?: ("•" to Color(0xFF8E8E93))
}

private val CATEGORY_STYLES: Map<String, Pair<String, Color>> = mapOf(
    // Supplies
    "Cosmetics & skincare" to ("💄" to Color(0xFFE07AA8)), "Toiletries" to ("🧴" to Color(0xFF4C9BE0)),
    "Cleaning" to ("🧽" to Color(0xFF26A69A)), "Laundry" to ("🧺" to Color(0xFF5AB4C8)), "Kitchen" to ("🍳" to Color(0xFFD39B32)),
    "Batteries" to ("🔋" to Color(0xFF4CAF50)), "Bulbs" to ("💡" to Color(0xFFE2BE1E)), "Stationery" to ("✏️" to Color(0xFF6F7FE0)),
    "First aid" to ("🩹" to Color(0xFFE8664F)),
    // Medicines, by what they're for
    "Fever" to ("🌡️" to Color(0xFFE8664F)), "Headache" to ("🤕" to Color(0xFFA66BD6)), "Pain" to ("💢" to Color(0xFFC0504D)),
    "Cold & cough" to ("🤧" to Color(0xFF4C9BE0)), "Sore throat" to ("🗣️" to Color(0xFF5AB4C8)), "Allergy" to ("🌼" to Color(0xFFE2BE1E)),
    "Acidity" to ("🔥" to Color(0xFFD9682B)), "Stomach" to ("🫃" to Color(0xFFD39B32)), "Loose motions" to ("🚽" to Color(0xFF8E8E93)),
    "Cuts & wounds" to ("🩹" to Color(0xFFE07AA8)), "Burns" to ("🔥" to Color(0xFFE8664F)), "Sprain" to ("🦶" to Color(0xFF26A69A)),
    "Insect bite" to ("🦟" to Color(0xFF4CAF50)), "Vitamins" to ("💊" to Color(0xFF6F7FE0)),
    // Where things are kept
    "Jewellery" to ("💍" to Color(0xFFD39B32)), "Documents" to ("📄" to Color(0xFF4C9BE0)), "Keys" to ("🔑" to Color(0xFFE2BE1E)),
    "Cash" to ("💵" to Color(0xFF4CAF50)), "Electricals" to ("🔌" to Color(0xFFD9682B)), "Gadgets" to ("📱" to Color(0xFF6F7FE0)),
    "Clothes" to ("👗" to Color(0xFFE07AA8)), "Seasonal" to ("🪔" to Color(0xFFE8664F)),
    "Other" to ("📦" to Color(0xFF8E8E93)),
)

/**
 * One list tab, laid out the same everywhere: the Add button, then (once there's anything) the
 * search box and chips, "Needs a look", the category groups, and an optional footer note.
 */
fun <T> LazyListScope.itemList(
    listId: String,
    organized: OrganizedList<T>,
    controls: ListControlsState,
    accent: Accent,
    addLabel: String,
    onAdd: () -> Unit,
    searchHint: String,
    emptyTitle: String,
    emptyBody: String,
    onToggle: (String) -> Unit,
    flags: Boolean = true,
    label: (String) -> String = { it },
    footer: String? = null,
    row: @Composable (ListRow<T>) -> Unit,
) {
    item(key = "$listId-add") { AddRow(addLabel, accent, onAdd) }
    if (organized.total == 0) {
        item(key = "$listId-empty") { Empty(emptyTitle, emptyBody) }
    } else {
        item(key = "$listId-tools") { ListToolbar(controls, organized, searchHint, flags, label) }
        organizedSections(listId, organized, onToggle, label = label, emptyText = "Nothing matches “${controls.query.value}”.", row = row)
    }
    footer?.let {
        item(key = "$listId-footer") {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        }
    }
}
