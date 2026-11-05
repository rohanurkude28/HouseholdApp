package com.houseaffairs.app.core.data

import java.math.BigDecimal
import java.util.Currency

/**
 * "Download my data": everything a person can see, as one JSON file plus an expenses CSV for
 * spreadsheets. Values arrive already plain (Firestore timestamps turned into local date strings).
 */
fun exportJson(household: String, exportedOn: String, collections: Map<String, List<Map<String, Any?>>>): String =
    jsonOf(
        mapOf(
            "app" to "HouseAffairs",
            "household" to household,
            "exportedOn" to exportedOn,
            // Empty collections are left out so the file reads as what the family actually uses.
            "collections" to collections.filterValues { it.isNotEmpty() }.toSortedMap(),
        ),
    )

fun jsonOf(value: Any?): String = StringBuilder().also { appendJson(it, value) }.toString()

private fun appendJson(out: StringBuilder, value: Any?) {
    when (value) {
        null -> out.append("null")
        is Boolean -> out.append(value)
        is Int, is Long, is Short, is Byte -> out.append(value)
        // JSON has no NaN or infinity.
        is Number -> value.toDouble().let { if (it.isFinite()) out.append(BigDecimal.valueOf(it).stripTrailingZeros().toPlainString()) else out.append("null") }
        is Map<*, *> -> {
            out.append('{')
            value.entries.forEachIndexed { i, (k, v) ->
                if (i > 0) out.append(',')
                appendString(out, k.toString())
                out.append(':')
                appendJson(out, v)
            }
            out.append('}')
        }
        is Iterable<*> -> {
            out.append('[')
            value.forEachIndexed { i, v ->
                if (i > 0) out.append(',')
                appendJson(out, v)
            }
            out.append(']')
        }
        else -> appendString(out, value.toString())
    }
}

private fun appendString(out: StringBuilder, s: String) {
    out.append('"')
    s.forEach { c ->
        when (c) {
            '"' -> out.append("\\\"")
            '\\' -> out.append("\\\\")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            else -> if (c < ' ') out.append("\\u%04x".format(c.code)) else out.append(c)
        }
    }
    out.append('"')
}

/** Expenses for a spreadsheet, oldest first. [names] turns member uids into names. */
fun expensesCsv(transactions: List<Map<String, Any?>>, names: Map<String, String>): String {
    val header = listOf("Date", "What", "Category", "Amount", "Currency", "Paid with", "Paid by", "Status", "Tags", "Note")
    val rows = transactions
        .sortedBy { it["date"]?.toString().orEmpty() }
        .map { t ->
            val currency = t["currency"]?.toString().orEmpty()
            listOf(
                t["date"]?.toString().orEmpty().take(10),
                t["title"]?.toString().orEmpty(),
                t["category"]?.toString().orEmpty(),
                (t["amountMinor"] as? Number)?.let { amountText(it.toLong(), currency) }.orEmpty(),
                currency,
                t["paymentMethod"]?.toString().orEmpty(),
                // One payer is stored as a string; older entries as a list.
                when (val p = t["paidBy"]) {
                    is List<*> -> p.joinToString(", ") { names[it.toString()] ?: it.toString() }
                    null -> ""
                    else -> names[p.toString()] ?: p.toString()
                },
                t["status"]?.toString() ?: "Paid",
                (t["tags"] as? List<*>).orEmpty().joinToString(", "),
                t["note"]?.toString().orEmpty(),
            )
        }
    return (listOf(header) + rows).joinToString("\r\n") { row -> row.joinToString(",", transform = ::csvCell) } + "\r\n"
}

/** Plain digits a spreadsheet reads as a number: 1234.50, not "₹1,234.50". */
private fun amountText(minor: Long, currency: String): String {
    val digits = runCatching { Currency.getInstance(currency).defaultFractionDigits }.getOrNull()?.coerceAtLeast(0) ?: 2
    return BigDecimal.valueOf(minor, digits).toPlainString()
}

private fun csvCell(s: String): String =
    // A leading = + - @ would run as a formula when opened in a spreadsheet.
    (if (s.isNotEmpty() && s[0] in "=+-@" && s.toBigDecimalOrNull() == null) "'$s" else s)
        .let { if (it.any { c -> c == ',' || c == '"' || c == '\n' || c == '\r' }) "\"" + it.replace("\"", "\"\"") + "\"" else it }

/**
 * Who looks after the home when the owner deletes their account: the adult who joined first.
 * Null when no other adult is there, since a teen, kid or helper can't manage members.
 */
fun successorOf(members: List<com.houseaffairs.app.core.model.Member>, myUid: String): com.houseaffairs.app.core.model.Member? =
    members.filter { it.uid != myUid && it.role == com.houseaffairs.app.core.model.Role.ADULT }
        .minByOrNull { it.joinedAt?.seconds ?: Long.MAX_VALUE }
