package com.houseaffairs.app.feature.add

import com.houseaffairs.app.core.data.BillScan
import com.houseaffairs.app.core.data.ScanKind
import com.houseaffairs.app.core.data.scanExpiry
import com.houseaffairs.app.core.data.scanKind
import com.houseaffairs.app.core.data.scanNutrition
import com.houseaffairs.app.core.data.scanReceipt
import com.houseaffairs.app.core.time.LocalDate
import com.houseaffairs.app.core.util.plainString
import com.houseaffairs.app.feature.quickadd.QuickAddDraft

/** What the 📷 on a form reads, or null where it reads nothing. */
fun scanHintFor(type: EntryType): String? = when (type) {
    EntryType.EXPENSE -> "Scan a receipt: amount, shop, date"
    EntryType.GROCERY, EntryType.MEDICINE, EntryType.HOUSEHOLD -> "Scan the pack for its expiry date"
    EntryType.CREDIT -> "Scan the card for its expiry"
    EntryType.RECIPE -> "Scan a nutrition label for protein, carbs, fat, fibre"
    else -> null
}

/** A form after a scan: the values with what was read, and one line saying what got filled. */
data class Scanned(val values: FormValues, val note: String, val filled: Boolean)

/**
 * Fills [type]'s form from scanned [text], on both phones: a receipt's amount, shop and date, a
 * nutrition label's macros, or a pack's expiry. A picture that doesn't belong (a bill on a
 * recipe) is said plainly and fills nothing.
 */
fun scannedInto(type: EntryType, values: FormValues, text: String, today: LocalDate, currency: String): Scanned {
    val kind = scanKind(text)
    val wrong = when {
        type == EntryType.EXPENSE && kind == ScanKind.NUTRITION -> "That looks like a nutrition label, not a bill. Nothing filled."
        type == EntryType.RECIPE && kind == ScanKind.RECEIPT -> "That looks like a bill. Use \"Scan a bill\" in the + sheet to add its items."
        type != EntryType.EXPENSE && type != EntryType.RECIPE && kind == ScanKind.RECEIPT ->
            "That looks like a bill. Use \"Scan a bill\" in the + sheet to add every item and the expense at once."
        else -> null
    }
    if (wrong != null) return Scanned(values, wrong, filled = false)
    val filled = mutableListOf<String>()
    var out = values
    when (type) {
        EntryType.EXPENSE -> {
            val r = scanReceipt(text, today)
            r.total?.let { t -> out = out + ("amount" to MoneyValue(t.plainString(), (out["amount"] as? MoneyValue)?.currency ?: currency)); filled += "amount" }
            r.merchant?.takeIf { (out["merchant"] as? String).isNullOrBlank() }?.let { out = out + ("merchant" to it); filled += "shop" }
            r.date?.let { out = out + ("date" to it); filled += "date" }
        }
        EntryType.RECIPE -> scanNutrition(text)?.let { out = out + ("macros" to it); filled += "protein, carbs, fat and fibre" }
        else -> scanExpiry(text, today)?.let { out = out + ("expiry" to it); filled += "expiry ${formatDate(it, today)}" }
    }
    return if (filled.isEmpty()) Scanned(values, "Couldn't read it. Try again closer, with good light.", filled = false)
    else Scanned(out, "Filled " + filled.joinToString(", "), filled = true)
}

/** Why scanned [text] can't be a bill (it's a label or a pack), or null when it may be one. */
fun notABill(text: String): String? = when (scanKind(text)) {
    ScanKind.NUTRITION -> "That looks like a nutrition label, not a bill. Open a recipe and use 📷 there to fill its macros."
    ScanKind.PACK -> "That looks like a pack label, not a bill. Open the item and use 📷 there to fill its expiry."
    else -> null
}

/**
 * A scanned bill as one batch: the expense, then each item, to medicines for a pharmacy bill and
 * the pantry otherwise (no cost on items, so nothing is counted twice). Null when nothing was read.
 */
fun billDrafts(bill: BillScan): QuickAddDraft.Batch? {
    val expense = bill.receipt.total?.let { total ->
        QuickAddDraft.Expense(total, null, if (bill.pharmacy) "Health" else "Groceries", bill.receipt.merchant, "Scanned bill", if (bill.pharmacy) "Medicines" else null, date = bill.receipt.date)
    }
    val items = bill.items.map { i -> if (bill.pharmacy) QuickAddDraft.Medicine(i.name, null, null) else QuickAddDraft.Grocery(i.name, "${i.quantity} pcs", null) }
    return (listOfNotNull(expense) + items).takeIf { it.isNotEmpty() }?.let { QuickAddDraft.Batch(it) }
}
