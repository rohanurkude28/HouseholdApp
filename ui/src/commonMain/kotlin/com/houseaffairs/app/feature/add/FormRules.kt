package com.houseaffairs.app.feature.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.MoneyState
import com.houseaffairs.app.core.data.expenseCategories
import com.houseaffairs.app.core.data.hiddenFrom
import com.houseaffairs.app.core.data.isOngoing
import com.houseaffairs.app.core.data.privateTopicOf
import com.houseaffairs.app.core.data.privateVisibleTo
import com.houseaffairs.app.core.data.surpriseFor
import com.houseaffairs.app.core.designsystem.Accent
import com.houseaffairs.app.core.model.Member
import com.houseaffairs.app.core.model.Visibility
import com.houseaffairs.app.core.time.LocalDate
import com.houseaffairs.app.core.time.isBefore
import com.houseaffairs.app.core.time.minusDays
import com.houseaffairs.app.feature.quickadd.EXPENSE_CATEGORIES
import com.houseaffairs.app.feature.quickadd.ListTemplate

enum class WhoCanSee(val label: String) { EVERYONE("Everyone"), ONLY_ME("Only me"), CHOSEN("Choose people") }

/** The form choices that come from the family's own things, by the key each field asks for. */
fun formChoices(money: MoneyState?, eventTitles: List<String>, today: LocalDate): Map<String, List<String>> = mapOf(
    "dreams" to money?.dreams?.map { it.dream.title }.orEmpty(),
    "cards" to money?.budget?.cards?.map { it.name }.orEmpty(),
    // The family's own categories (cook, maid…); the fixed ones are already in the form.
    "categories" to expenseCategories(money?.budget).filter { it !in EXPENSE_CATEGORIES },
    // Ongoing and upcoming trips. One marked done is gone at once (an expense already filed under it
    // keeps it); otherwise a finished one drops off a week after it ends.
    "trips" to money?.trips?.map { it.trip }?.filter { !it.done && !(it.end ?: it.start).isBefore(today.minusDays(7L)) }?.map { it.title }.orEmpty(),
    "events" to eventTitles,
)

/** A trip happening today, which a new expense offers to file under with one tap. */
fun ongoingTripOf(money: MoneyState?, today: LocalDate): String? = money?.trips?.firstOrNull { it.trip.isOngoing(today) }?.trip?.title

/**
 * Who sees a new or edited entry: the person's choice; on a new entry left for everyone, a
 * topic the owner made private narrows it; and a gift or surprise trip is never shown to the
 * person it's for.
 */
fun visibleToFor(
    type: EntryType, values: FormValues, who: WhoCanSee, chosen: List<String>, uid: String,
    memberIds: List<String>, privateTopics: Map<String, List<String>>, editing: Boolean,
): List<String> {
    val base = when (who) {
        WhoCanSee.EVERYONE -> Visibility.everyone
        WhoCanSee.ONLY_ME -> listOf(uid)
        WhoCanSee.CHOSEN -> (listOf(uid) + chosen).distinct()
    }
    val title = (type.titleKey?.let { values[it] as? String }).orEmpty()
    val topic = if (!editing && who == WhoCanSee.EVERYONE) {
        val category = listOf("category", "supplyType", "groceryType", "aisle").firstNotNullOfOrNull { values[it] as? String }
        privateTopicOf(type.collection, title, category, privateTopics.keys)?.takeIf { it in privateTopics }
    } else null
    val surprise = surpriseFor(type.collection, ListTemplate.of(values["template"] as? String) == ListTemplate.GIFTS, values).filter { it != uid }
    return hiddenFrom(topic?.let { privateVisibleTo(privateTopics.getValue(it), uid) } ?: base, memberIds, surprise, uid, Visibility.EVERYONE)
}

/** What an entry's saved visibility means as a choice, for editing it. */
fun whoCanSeeOf(visibleTo: List<String>, uid: String): Pair<WhoCanSee, List<String>> = when {
    Visibility.EVERYONE in visibleTo -> WhoCanSee.EVERYONE to emptyList()
    visibleTo.all { it == uid } -> WhoCanSee.ONLY_ME to emptyList()
    else -> WhoCanSee.CHOSEN to visibleTo.filter { it != uid }
}

/** "Who can see this": everyone, only me, or chosen people. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhoCanSeeEditor(who: WhoCanSee, onWho: (WhoCanSee) -> Unit, chosen: List<String>, onChosen: (List<String>) -> Unit, others: List<Member>, accent: Accent) {
    val colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent.container, selectedLabelColor = accent.color)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Who can see this", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WhoCanSee.entries.forEach { w -> FilterChip(selected = who == w, onClick = { onWho(w) }, label = { Text(w.label) }, shape = CircleShape, colors = colors) }
        }
        if (who == WhoCanSee.CHOSEN) {
            Spacer(Modifier.height(8.dp))
            FieldEditor(MembersSpec("__visible", "Visible to"), chosen, { value -> onChosen((value as? List<*>)?.filterIsInstance<String>().orEmpty()) }, accent, others, showError = false)
        }
        Text(
            "Use this for sensitive items like sanitary or personal care.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
