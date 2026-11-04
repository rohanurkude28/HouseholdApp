package com.houseaffairs.app.feature.add

import com.houseaffairs.app.core.data.Ingredient
import com.houseaffairs.app.core.model.Member
import com.houseaffairs.app.core.util.formatAmount
import java.time.LocalDate
import java.time.format.DateTimeFormatter

typealias FormValues = Map<String, Any?>

data class MoneyValue(val amount: String, val currency: String) {
    val parsed get() = amount.replace(",", "").toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
}

data class QuantityValue(val amount: String, val unit: String) {
    val parsed get() = amount.toDoubleOrNull()?.takeIf { it > 0 }
}

/**
 * One field of an entry form. Forms are built from these, so a new kind of entry is a list of
 * fields rather than a new screen. [essential] fields show first; the rest sit under "More details".
 */
sealed interface FieldSpec {
    val key: String
    val label: String
    val required: Boolean
    val essential: Boolean
    val visibleWhen: (FormValues) -> Boolean
}

data class TextSpec(
    override val key: String,
    override val label: String,
    val placeholder: String = "",
    val multiline: Boolean = false,
    val isLink: Boolean = false,
    /** A phone number: number keyboard plus a button to pick from contacts. */
    val isPhone: Boolean = false,
    override val required: Boolean = false,
    override val essential: Boolean = true,
    override val visibleWhen: (FormValues) -> Boolean = { true },
) : FieldSpec

data class MoneySpec(
    override val key: String = "amount",
    override val label: String = "Amount",
    override val required: Boolean = true,
    override val essential: Boolean = true,
    override val visibleWhen: (FormValues) -> Boolean = { true },
) : FieldSpec

data class ChoiceSpec(
    override val key: String,
    override val label: String,
    val options: List<String>,
    /** Extra options supplied at runtime (e.g. this family's dreams), by this key. */
    val dynamicOptions: String? = null,
    /** Hide the field entirely when the runtime options are empty (e.g. no trips yet). */
    val hideWithoutDynamic: Boolean = false,
    /** An option the runtime ones stand in for: "Card" goes once the family's own cards are listed. */
    val replacedByDynamic: String? = null,
    val multi: Boolean = false,
    val allowCustom: Boolean = false,
    override val required: Boolean = false,
    override val essential: Boolean = true,
    override val visibleWhen: (FormValues) -> Boolean = { true },
) : FieldSpec

data class DateSpec(
    override val key: String,
    override val label: String,
    /** For dates that already happened ("bought on"): quick chips look back, not ahead. */
    val past: Boolean = false,
    override val required: Boolean = false,
    override val essential: Boolean = true,
    override val visibleWhen: (FormValues) -> Boolean = { true },
) : FieldSpec

data class QuantitySpec(
    override val key: String = "quantity",
    override val label: String = "Quantity",
    val units: List<String>,
    override val required: Boolean = false,
    override val essential: Boolean = true,
    override val visibleWhen: (FormValues) -> Boolean = { true },
) : FieldSpec

data class LevelSpec(
    override val key: String,
    override val label: String,
    val suffix: String,
    override val required: Boolean = false,
    override val essential: Boolean = false,
    override val visibleWhen: (FormValues) -> Boolean = { true },
) : FieldSpec

data class MembersSpec(
    override val key: String,
    override val label: String,
    val multi: Boolean = true,
    override val required: Boolean = false,
    override val essential: Boolean = true,
    override val visibleWhen: (FormValues) -> Boolean = { true },
) : FieldSpec

/** A recipe's ingredient list: name, quantity and unit per row, so cooking can update the pantry. */
data class IngredientsSpec(
    override val key: String = "ingredients",
    override val label: String = "Ingredients",
    override val required: Boolean = false,
    override val essential: Boolean = true,
    override val visibleWhen: (FormValues) -> Boolean = { true },
) : FieldSpec

fun FieldSpec.isFilled(value: Any?): Boolean = when (this) {
    is TextSpec -> (value as? String)?.isNotBlank() == true
    is MoneySpec -> (value as? MoneyValue)?.parsed != null
    is ChoiceSpec -> if (multi) (value as? List<*>)?.isNotEmpty() == true else (value as? String)?.isNotBlank() == true
    is DateSpec -> value is LocalDate
    is QuantitySpec -> (value as? QuantityValue)?.parsed != null
    is LevelSpec -> value is Int
    is MembersSpec -> (value as? List<*>)?.isNotEmpty() == true
    is IngredientsSpec -> (value as? List<*>)?.filterIsInstance<Ingredient>()?.any { it.name.isNotBlank() } == true
}

private val DATE_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
private val DATE_LABEL_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

fun formatDate(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(if (date.year == today.year) DATE_LABEL else DATE_LABEL_YEAR)
}

/** Short human text for a field's value, used in summaries; null when empty. */
fun FieldSpec.display(value: Any?, members: List<Member>): String? {
    if (!isFilled(value)) return null
    return when (this) {
        is TextSpec -> value as String
        is MoneySpec -> (value as MoneyValue).let { formatAmount(it.parsed!!, it.currency) }
        is ChoiceSpec -> if (multi) (value as List<*>).joinToString(", ") else value as String
        is DateSpec -> formatDate(value as LocalDate).let { date ->
            "$label ${if (date == "Today" || date == "Tomorrow") date.lowercase() else date}"
        }
        is QuantitySpec -> (value as QuantityValue).let { "${it.amount} ${it.unit}" }
        is LevelSpec -> "$value$suffix"
        is MembersSpec -> (value as List<*>).joinToString(", ") { uid ->
            members.firstOrNull { it.uid == uid }?.displayName?.substringBefore(' ') ?: "Someone"
        }
        is IngredientsSpec -> (value as List<*>).filterIsInstance<Ingredient>().count { it.name.isNotBlank() }.let { "$it ingredients" }
    }
}
