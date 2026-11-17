package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.time.*
import com.houseaffairs.app.core.util.*
import kotlinx.datetime.DayOfWeek


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
            "collections" to collections.filterValues { it.isNotEmpty() }.toList().sortedBy { it.first }.toMap(),
        ),
    )

fun jsonOf(value: Any?): String = StringBuilder().also { appendJson(it, value) }.toString()

private fun appendJson(out: StringBuilder, value: Any?) {
    when (value) {
        null -> out.append("null")
        is Boolean -> out.append(value)
        is Int, is Long, is Short, is Byte -> out.append(value)
        // JSON has no NaN or infinity.
        is Number -> value.toDouble().let { if (it.isFinite()) out.append(if (it == kotlin.math.floor(it) && kotlin.math.abs(it) < 1e15) it.toLong().toString() else it.toString()) else out.append("null") }
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
            else -> if (c < ' ') out.append("\\u" + c.code.toString(16).padStart(4, '0')) else out.append(c)
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
    // Yen and won have no smaller unit; everything else the app handles has hundredths.
    if (currency in setOf("JPY", "KRW")) return minor.toString()
    val sign = if (minor < 0) "-" else ""
    val abs = kotlin.math.abs(minor)
    return "$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
}

private fun csvCell(s: String): String =
    // A leading = + - @ would run as a formula when opened in a spreadsheet.
    (if (s.isNotEmpty() && s[0] in "=+-@" && s.let { decimalOf(it) } == null) "'$s" else s)
        .let { if (it.any { c -> c == ',' || c == '"' || c == '\n' || c == '\r' }) "\"" + it.replace("\"", "\"\"") + "\"" else it }

