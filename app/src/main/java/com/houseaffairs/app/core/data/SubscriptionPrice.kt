package com.houseaffairs.app.core.data

import java.time.LocalDate

/** From [from] on, a subscription costs [amountMinor]. A price history is a list of these. */
data class PriceChange(val from: LocalDate, val amountMinor: Long)

/** Stands for "since the start" as the first entry of a history, holding the price before any change. */
val SINCE_START: LocalDate = LocalDate.of(1970, 1, 1)

/** What the form asks when a subscription's amount changes: which payments the new price is for. */
enum class PriceFrom(val label: String) {
    NEXT("From the next payment"),
    DATE("From a date"),
    ALWAYS("Every payment (fix a mistake)"),
    ;
    companion object { fun of(label: Any?): PriceFrom = entries.firstOrNull { it.label == label } ?: NEXT }
}

/**
 * The price of a renewal on [date]: the latest change on or before it. With no history, the
 * subscription's own [amountMinor]. Pure, so it's tested.
 */
fun priceOn(date: LocalDate, amountMinor: Long, history: List<PriceChange>): Long =
    history.filter { !it.from.isAfter(date) }.maxByOrNull { it.from }?.amountMinor
        ?: history.minByOrNull { it.from }?.amountMinor
        ?: amountMinor

/**
 * The history after the price goes from [oldMinor] to [newMinor] starting [from]; renewals before
 * keep what they cost. A null [from] means it was always this price (a mistake fixed), so there's
 * no history at all. Later changes already planned past [from] are replaced.
 */
fun changePrice(history: List<PriceChange>, oldMinor: Long, newMinor: Long, from: LocalDate?): List<PriceChange> {
    if (from == null) return emptyList()
    val base = history.ifEmpty { listOf(PriceChange(SINCE_START, oldMinor)) }
    return base.filter { it.from.isBefore(from) } + PriceChange(from, newMinor)
}

fun encodePrices(history: List<PriceChange>): List<Map<String, Any>> = history.map { mapOf("from" to it.from.toString(), "amountMinor" to it.amountMinor) }

fun decodePrices(raw: Any?): List<PriceChange> = (raw as? List<*>).orEmpty().mapNotNull { e ->
    val m = e as? Map<*, *> ?: return@mapNotNull null
    val from = (m["from"] as? String)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
    PriceChange(from, (m["amountMinor"] as? Number)?.toLong() ?: return@mapNotNull null)
}

/** One logged renewal of a subscription, for the list in its form. */
data class PastPayment(val id: String, val date: LocalDate, val amountMinor: Long, val currency: String)
