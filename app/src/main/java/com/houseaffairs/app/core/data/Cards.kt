package com.houseaffairs.app.core.data

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.min

/**
 * A credit card. Its statement runs from [statementDay] to the day before it next month (21st to
 * 20th), and the bill is due on [dueDay] after the statement closes. This is separate from the
 * budget month, which follows the salary: card spends count in the budget when they happen; the
 * card cycle only tells you how big the bill is and when it's due.
 */
data class Card(val name: String, val statementDay: Int, val dueDay: Int)

data class CardBill(
    val card: Card,
    /** The statement period: start inclusive, end exclusive. */
    val cycle: Cycle,
    val dueDate: LocalDate,
    val amountMinor: Long,
    val count: Int,
    val paid: Boolean,
    /** Still open: spends today join it. False for the statement that has closed. */
    val open: Boolean,
) {
    val key: String get() = billKey(card, cycle)
    val closesOn: LocalDate get() = cycle.endExclusive.minusDays(1)
}

fun billKey(card: Card, cycle: Cycle) = "${card.name}|${cycle.start}"

/** The first [dueDay] on or after the statement closes (clamped to short months). */
fun dueDateFor(cycle: Cycle, dueDay: Int): LocalDate {
    val closed = cycle.endExclusive.minusDays(1)
    fun dueIn(month: YearMonth) = month.atDay(min(dueDay.coerceIn(1, 31), month.lengthOfMonth()))
    val sameMonth = dueIn(YearMonth.from(closed))
    return if (sameMonth.isAfter(closed)) sameMonth else dueIn(YearMonth.from(closed).plusMonths(1))
}

/** Which card an expense went on: the one named, or the only card when it just says "Card". */
fun cardOf(t: Txn, cards: List<Card>): Card? = when {
    t.card != null -> cards.firstOrNull { it.name.equals(t.card, ignoreCase = true) }
    // "Paid with" holds the card's own name when it was picked from the list.
    cards.any { it.name.equals(t.paidWith, ignoreCase = true) } -> cards.first { it.name.equals(t.paidWith, ignoreCase = true) }
    t.paidWith == "Card" && cards.size == 1 -> cards.first()
    else -> null
}

/**
 * For each card: the open statement (spends so far) and the last closed one (the bill to pay).
 * Pure, so the cycle maths is tested.
 */
fun cardBills(cards: List<Card>, txns: List<Txn>, paidKeys: Set<String>, today: LocalDate): List<CardBill> =
    cards.flatMap { card ->
        val open = cycleFor(today, card.statementDay)
        val closed = cycleFor(open.start.minusDays(1), card.statementDay)
        val mine = txns.filter { !it.planned && cardOf(it, cards) == card }
        listOf(closed to false, open to true).map { (cycle, isOpen) ->
            val inCycle = mine.filter { it.date in cycle }
            CardBill(
                card = card,
                cycle = cycle,
                dueDate = dueDateFor(cycle, card.dueDay),
                amountMinor = inCycle.sumOf { it.amountMinor },
                count = inCycle.size,
                paid = billKey(card, cycle) in paidKeys,
                open = isOpen,
            )
        }
    }
