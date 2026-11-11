package com.houseaffairs.app.core.data

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/**
 * Reading what the camera saw (text from a receipt, a pack or a label) into form values. All
 * best-effort: anything not found is left for the person to fill. Pure, so it's tested.
 */

private val MONTH_NAMES = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
private val NUMBER = Regex("(?<![\\d.])(\\d{1,3}(?:,\\d{2,3})*(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?)(?![\\d])")

/** What a receipt says: the total, the shop and the date. */
data class ReceiptScan(val total: BigDecimal?, val merchant: String?, val date: LocalDate?)

/** The total is on the "Total"/"Grand total"/"Amount" line (its largest number), else the largest amount on the receipt. */
fun scanReceipt(text: String, today: LocalDate): ReceiptScan {
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    fun amounts(line: String) = NUMBER.findAll(line).mapNotNull { it.value.replace(",", "").toBigDecimalOrNull() }.filter { it.signum() > 0 }.toList()
    val totalLine = lines.lastOrNull { Regex("\\b(grand\\s*total|net\\s*(amount|payable|total)|total\\s*(amount|payable|due)?|amount\\s*(paid|due|payable)|to pay)\\b", RegexOption.IGNORE_CASE).containsMatchIn(it) && amounts(it).isNotEmpty() }
    val total = totalLine?.let { amounts(it).maxOrNull() }
        ?: lines.filter { Regex("[₹$]|rs\\.?|inr", RegexOption.IGNORE_CASE).containsMatchIn(it) }.flatMap(::amounts).maxOrNull()
    // The shop's name is usually the first line with words that isn't a heading like "Tax invoice".
    val merchant = lines.firstOrNull { l ->
        l.count { it.isLetter() } >= 3 && !Regex("invoice|receipt|bill|gst|tax|cash memo|welcome|date|time|tel|phone|www", RegexOption.IGNORE_CASE).containsMatchIn(l)
    }?.take(40)
    return ReceiptScan(total, merchant, firstDate(text, today))
}

/** "12/03/2026", "12-03-26", "12 Mar 2026", "Mar 12, 2026": the first full date, not in the future by more than a day. */
fun firstDate(text: String, today: LocalDate): LocalDate? {
    Regex("\\b(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{2,4})\\b").findAll(text).forEach { m ->
        val (d, mo, y) = m.destructured
        date(y.toInt(), mo.toInt(), d.toInt())?.takeIf { !it.isAfter(today.plusDays(1)) }?.let { return it }
    }
    Regex("\\b(\\d{1,2})\\s*([a-z]{3})[a-z]*[\\s,.-]*(\\d{2,4})\\b", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
        val (d, mon, y) = m.destructured
        val month = MONTH_NAMES.indexOf(mon.lowercase()) + 1
        if (month > 0) date(y.toInt(), month, d.toInt())?.let { return it }
    }
    return null
}

/**
 * The expiry on a pack or strip: "EXP 08/2027", "Exp. Date: AUG 2027", "Use by 12/03/27",
 * "Best before 15.11.2026". A month alone means its last day. Also "best before 6 months from
 * manufacture" with the MFG date.
 */
fun scanExpiry(text: String, today: LocalDate): LocalDate? {
    val t = text.replace('\n', ' ')
    val label = Regex("(?:exp(?:iry)?\\.?(?:\\s*date)?|use\\s*by|best\\s*before|bb|expires)\\s*[:.]?\\s*", RegexOption.IGNORE_CASE)
    label.findAll(t).forEach { m ->
        val after = t.substring(m.range.last + 1).take(24)
        parseWhen(after)?.let { return it }
    }
    // "Best before 6 months from manufacture" + "Mfg 03/2026".
    val months = Regex("best\\s*before\\s*(\\d{1,2})\\s*months?", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)?.toLongOrNull()
    val mfg = Regex("(?:mfg|mfd|manufactured|pkd|packed)(?:\\.|\\s*date|\\s*on)?\\s*[:.]?\\s*", RegexOption.IGNORE_CASE).find(t)?.let { parseWhen(t.substring(it.range.last + 1).take(24)) }
    if (months != null && mfg != null) return mfg.plusMonths(months)
    // No label: the latest month or date that's still ahead.
    return Regex("\\b(\\d{1,2})[/.-](\\d{2,4})\\b").findAll(t).mapNotNull { parseWhen(it.value) }.filter { it.isAfter(today) }.maxOrNull()
}

/** A date at the start of [s]: "08/2027", "AUG 2027", "12/03/27", "15.11.2026". */
private fun parseWhen(s: String): LocalDate? {
    val str = s.trim()
    Regex("^(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{2,4})").find(str)?.let { m ->
        val (d, mo, y) = m.destructured
        return date(y.toInt(), mo.toInt(), d.toInt())
    }
    Regex("^(\\d{1,2})[/.-](\\d{2,4})(?![/.\\d-])").find(str)?.let { m ->
        val (mo, y) = m.destructured
        return monthEnd(year(y.toInt()), mo.toInt())
    }
    Regex("^([a-z]{3})[a-z]*[\\s.,/-]*(\\d{2,4})", RegexOption.IGNORE_CASE).find(str)?.let { m ->
        val (mon, y) = m.destructured
        val month = MONTH_NAMES.indexOf(mon.lowercase()) + 1
        if (month > 0) return monthEnd(year(y.toInt()), month)
    }
    return null
}

private fun year(y: Int) = if (y < 100) 2000 + y else y
private fun date(y: Int, m: Int, d: Int): LocalDate? = runCatching { LocalDate.of(year(y), m, d) }.getOrNull()
private fun monthEnd(y: Int, m: Int): LocalDate? = runCatching { YearMonth.of(y, m).atEndOfMonth() }.getOrNull()

/**
 * Protein, carbs, fat and fibre from a nutrition label, as the form's "25g · 30g · 12g · 4g".
 * Per serving when the label has a serving column is hard to tell from text, so it takes the
 * first number on each line (labels list "per 100 g" first). Null without at least protein and one other.
 */
fun scanNutrition(text: String): String? {
    fun grams(vararg names: String): Double? {
        val line = text.lines().firstOrNull { l -> names.any { n -> Regex("\\b$n", RegexOption.IGNORE_CASE).containsMatchIn(l) } } ?: return null
        val after = names.firstNotNullOfOrNull { n -> Regex("\\b$n[a-z ()]*?[:\\s]*", RegexOption.IGNORE_CASE).find(line) }?.let { line.substring(it.range.last + 1) } ?: line
        return Regex("(\\d+(?:[.,]\\d+)?)\\s*g", RegexOption.IGNORE_CASE).find(after)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
    }
    val protein = grams("protein")
    val carbs = grams("total carbohydrate", "carbohydrate", "carbs")
    val fat = grams("total fat", "fat")
    val fibre = grams("dietary fibre", "dietary fiber", "fibre", "fiber")
    if (protein == null || listOfNotNull(carbs, fat, fibre).isEmpty()) return null
    fun g(v: Double?) = (v ?: 0.0).let { if (it % 1.0 == 0.0) "${it.toLong()}g" else "${it}g" }
    return listOf(protein, carbs, fat, fibre).joinToString(" · ") { g(it) }
}

/** One line of a bill: what, how many, and the line's price when it says. */
data class BillItem(val name: String, val quantity: Int, val priceMinor: Long?)

/** A bill read off a photo: its total, shop and date, the items on it, and whether it's from a pharmacy. */
data class BillScan(val receipt: ReceiptScan, val items: List<BillItem>, val pharmacy: Boolean)

private val NOT_AN_ITEM = Regex(
    "total|subtotal|sub total|tax|gst|cgst|sgst|vat|discount|saving|round|cash|change|card|upi|paid|balance|amount|mrp|rate|qty|item|invoice|bill|date|time|tel|phone|thank|gstin|hsn|net|due|tender",
    RegexOption.IGNORE_CASE,
)

/**
 * The items on a bill: lines with a name and a price at the end ("Milk 1L  2  68.00  136.00",
 * "2 x Bread 45"). A small whole number before the prices, or "2 x" in front, is the quantity;
 * the last number is the line's price. Totals, taxes and payment lines are skipped. Pure, tested.
 */
fun scanBill(text: String, today: LocalDate): BillScan {
    val items = text.lines().map { it.trim() }.mapNotNull { line ->
        if (line.count { it.isLetter() } < 2 || NOT_AN_ITEM.containsMatchIn(line)) return@mapNotNull null
        var rest = line
        var qty = 1
        Regex("^(\\d{1,2})\\s*[x×*]\\s*", RegexOption.IGNORE_CASE).find(rest)?.let { m -> qty = m.groupValues[1].toInt(); rest = rest.substring(m.range.last + 1) }
        // Numbers at the end of the line: [qty] [rate] price.
        val tail = Regex("((?:\\s+\\d+(?:[.,]\\d{1,2})?)+)\\s*$").find(rest) ?: return@mapNotNull null
        val nums = tail.value.trim().split(Regex("\\s+")).mapNotNull { it.replace(',', '.').toDoubleOrNull() }
        if (nums.isEmpty()) return@mapNotNull null
        val name = rest.substring(0, tail.range.first).trim().trimEnd('-', ':', '.').trim()
        if (name.count { it.isLetter() } < 2) return@mapNotNull null
        val price = nums.last()
        if (nums.size >= 2 && nums.first() % 1.0 == 0.0 && nums.first() in 1.0..20.0) qty = nums.first().toInt()
        BillItem(name.replaceFirstChar { it.uppercaseChar() }, qty, Math.round(price * 100))
    }
    val pharmacy = Regex("pharma|chemist|medical|drug|\\btab(?:let)?s?\\b|\\bcap(?:sule)?s?\\b|syrup|\\d+\\s?mg\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
    return BillScan(scanReceipt(text, today), items, pharmacy)
}

/** What a scanned picture shows, so it fills the right form or says it's the wrong one. */
enum class ScanKind { RECEIPT, NUTRITION, PACK, UNKNOWN }

/**
 * A bill (totals, GST, several prices), a nutrition table (protein, fat, kcal…), a pack label
 * (expiry, batch, MRP) or none of these. Bills win over pack words, since bills print "MRP" too.
 * Pure, so it's tested.
 */
fun scanKind(text: String): ScanKind {
    val t = text.lowercase()
    fun count(words: List<String>) = words.count { Regex("\\b$it").containsMatchIn(t) }
    val nutrition = count(listOf("protein", "carbohydrate", "total fat", "fat", "energy", "kcal", "fibre", "fiber", "sugar", "sodium"))
    val bill = count(listOf("total", "grand total", "invoice", "bill", "gst", "cgst", "qty", "amount", "cash", "subtotal", "receipt", "upi", "paid"))
    val prices = Regex("\\d+[.,]\\d{2}\\b").findAll(t).count()
    val pack = count(listOf("exp", "expiry", "best before", "use by", "mfg", "mfd", "batch", "b.no", "mrp", "manufactured", "pkd"))
    return when {
        bill >= 2 && prices >= 2 -> ScanKind.RECEIPT
        nutrition >= 3 -> ScanKind.NUTRITION
        pack >= 1 -> ScanKind.PACK
        bill >= 1 && prices >= 1 -> ScanKind.RECEIPT
        else -> ScanKind.UNKNOWN
    }
}
