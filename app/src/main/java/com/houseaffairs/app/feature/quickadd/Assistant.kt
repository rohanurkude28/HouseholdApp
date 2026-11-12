package com.houseaffairs.app.feature.quickadd

import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate

/** What the assistant may know about the home when reading a line. Only names, never entries. */
data class AssistContext(val today: LocalDate, val memberNames: List<String>, val currency: String)

/** One recommended item, with a short reason in plain words. */
data class Pick(val title: String, val why: String)

/**
 * The app's smart layer, behind one interface so the model can change.
 * - [understand]: a quick-add line the rules couldn't place, as a draft to confirm.
 * - [pick]: ranks real items the app already has (dishes, the watch list) and says why. It never
 *   invents items: anything not in the options is dropped.
 * - [coach]: one small, specific step for the live-well habit the week is light on.
 * Each returns null or empty on any failure, and the app falls back to its own rules.
 */
interface Assistant {
    suspend fun understand(text: String, context: AssistContext): QuickAddDraft?
    suspend fun pick(goal: String, about: String, options: List<String>, count: Int): List<Pick>
    suspend fun coach(about: String): String?

    /** Reads a bill the on-phone rules couldn't; null when it can't (or isn't connected). */
    suspend fun readBill(text: String): com.houseaffairs.app.core.data.BillScan? = null
}

/** The instructions sent with each line. Few-shot, JSON out, so [draftFromAi] can check every field. */
fun assistPrompt(text: String, context: AssistContext): String = """
    You sort one line a family typed or said into a household app. Today is ${context.today} (${context.today.dayOfWeek.name.lowercase()}).
    Family members: ${context.memberNames.joinToString().ifEmpty { "unknown" }}. Home currency: ${context.currency}.

    Reply with one JSON object and nothing else:
    {"kind": one of "expense","subscription","shopping","grocery","medicine","task","event","list_item","unknown",
     "title": short name of the thing (no verbs like need/buy, no quantity),
     "amount": number or null, "currency": ISO code or null,
     "category": one of ${EXPENSE_CATEGORIES.joinToString(",")} or null,
     "quantity": number or null, "unit": one of g,kg,ml,L,pcs,pack or null,
     "date": "YYYY-MM-DD" or null (due date, event date, or expiry),
     "person": a family member's name if it's for someone, or null,
     "frequency": one of Weekly,Monthly,Quarterly,Yearly or null,
     "list": one of ${ListTemplate.entries.joinToString(",") { it.label }} or null,
     "eventKind": one of Birthday,Anniversary,Renewal,Appointment,Other or null}

    Meanings: shopping = something to buy soon; grocery = food just bought, now in the kitchen; expense = money
    already spent; subscription = a repeating payment; medicine = a medicine at home; task = something to do;
    event = something happening on a date; list_item = a wish (watch, read, listen, travel, eat out, buy someday, gift).
    Use "unknown" if it's none of these.

    Examples:
    "need 500g peanuts" -> {"kind":"shopping","title":"Peanuts","quantity":500,"unit":"g"}
    "peanut butter" -> {"kind":"shopping","title":"Peanut butter"}
    "riya's ptm next friday" -> {"kind":"event","title":"Riya's PTM","eventKind":"Appointment","date":"<next friday>","person":"Riya"}
    "spent 300 on chaat" -> {"kind":"expense","title":"Chaat","amount":300,"category":"Dining"}
    "want to see oppenheimer" -> {"kind":"list_item","title":"Oppenheimer","list":"Watch"}
    "get passport photos done before goa" -> {"kind":"task","title":"Get passport photos done"}

    Line: ${JSONObject.quote(text)}
""".trimIndent()

/**
 * The model's JSON reply as a draft, or null if it's unusable. Every field is checked: unknown
 * kinds, categories or lists are dropped rather than trusted. Pure, so it's tested.
 */
fun draftFromAi(json: String, context: AssistContext): QuickAddDraft? {
    val o = runCatching { JSONObject(json.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()) }.getOrNull() ?: return null
    fun str(key: String) = o.optString(key).takeIf { o.has(key) && !o.isNull(key) && it.isNotBlank() }?.trim()
    val title = str("title")?.replaceFirstChar { it.uppercaseChar() } ?: return null
    val amount = str("amount")?.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
    val currency = str("currency")?.uppercase()?.takeIf { it.length == 3 && it != context.currency }
    val date = str("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val person = str("person")?.takeIf { p -> context.memberNames.any { it.equals(p, ignoreCase = true) } }
    val quantity = str("quantity")?.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }?.let { q ->
        val unit = str("unit")?.let { u -> listOf("g", "kg", "ml", "L", "pcs", "pack").firstOrNull { it.equals(u, ignoreCase = true) } } ?: "pcs"
        "${q.stripTrailingZeros().toPlainString()} $unit"
    }
    return when (str("kind")?.lowercase()) {
        "expense" -> amount?.let {
            QuickAddDraft.Expense(it, currency, str("category")?.takeIf { c -> c in EXPENSE_CATEGORIES } ?: "Other", null, title)
        }
        "subscription" -> amount?.let {
            val frequency = str("frequency")?.takeIf { f -> f in setOf("Weekly", "Monthly", "Quarterly", "Yearly") } ?: "Monthly"
            QuickAddDraft.Subscription(title, it, currency, frequency)
        }
        "shopping" -> QuickAddDraft.Shopping(title, quantity)
        "grocery" -> QuickAddDraft.Grocery(title, quantity, date)
        "medicine" -> QuickAddDraft.Medicine(title, person, date)
        "task" -> QuickAddDraft.Task(title, date)
        "event" -> QuickAddDraft.Event(
            title,
            str("eventKind")?.takeIf { it in setOf("Birthday", "Anniversary", "Renewal", "Appointment", "Other") } ?: "Other",
            date,
        )
        "list_item" -> str("list")?.let { name ->
            ListTemplate.of(name) ?: ListTemplate.entries.firstOrNull { t -> t.title.equals(name, ignoreCase = true) || name.lowercase() in t.aliases }
        }
            ?.let { QuickAddDraft.ListItem(it, title) }
        else -> null
    }
}

/** Asks for the best [count] of [options] for [goal], given [about] (a short summary, no amounts). */
fun pickPrompt(goal: String, about: String, options: List<String>, count: Int): String = """
    You help a family decide: $goal
    About them right now: $about

    Choose the best $count from these options, best first. Use the exact titles given; never add others.
    For each, give one short, friendly reason (under 12 words) that refers to their situation.
    Reply with JSON only: {"picks":[{"title":"...","why":"..."}]}

    Options:
    ${options.joinToString("\n") { "- " + it }}
""".trimIndent()

/** The model's picks, keeping only titles that are really in [options] (as written there), at most once each. */
fun picksFromAi(json: String, options: List<String>): List<Pick> {
    val array = runCatching { JSONObject(json.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()).getJSONArray("picks") }.getOrNull()
        ?: return emptyList()
    val seen = mutableSetOf<String>()
    return (0 until array.length()).mapNotNull { i ->
        val o = array.optJSONObject(i) ?: return@mapNotNull null
        val title = options.firstOrNull { it.equals(o.optString("title").trim(), ignoreCase = true) } ?: return@mapNotNull null
        val why = o.optString("why").trim().takeIf { it.isNotEmpty() && it.length <= 90 } ?: return@mapNotNull null
        if (!seen.add(title)) null else Pick(title, why)
    }
}

/** Asks for one small step toward a long, healthy life, from the week's habit scores. */
fun coachPrompt(about: String): String = """
    You are a warm, practical coach inspired by how the world's longest-lived communities live:
    natural movement, plant-heavy home food, eating in moderation, family and friends, purpose, and calm.
    Here is one person's week (habit scores out of 100, and their own plan if any):
    $about

    Suggest ONE small, concrete thing they could do in the next day or two for their weakest area.
    Make it specific to an Indian family home, under 25 words, kind, not preachy, no medical advice.
    Reply with JSON only: {"tip":"..."}
""".trimIndent()

/** The coach's tip, or null if it's missing or too long to show. */
fun coachFromAi(json: String): String? =
    runCatching { JSONObject(json.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()).optString("tip").trim() }
        .getOrNull()?.takeIf { it.isNotEmpty() && it.length <= 200 }

/** Text off a bill, for Gemini to read into JSON that [billFromAi] checks. Only the bill's own text is sent. */
fun billPrompt(text: String): String = """
    This is text read from a photo of a shop bill or receipt, possibly with errors. Reply with one JSON object and nothing else:
    {"total": number or null, "shop": short shop name or null, "date": "YYYY-MM-DD" or null, "pharmacy": true or false,
     "items": [{"name": short item name, "qty": whole number, "price": number or null}]}
    Leave out taxes, discounts, totals and payment lines from items. Use null when you can't tell. If it isn't a bill, reply {"total": null, "items": []}.

    Text:
    ${text.take(4000)}
""".trimIndent()

/**
 * Gemini's reading of a bill, every field checked: a positive total, sane quantities, names
 * with letters, a real date not in the future. Null when nothing useful came back.
 */
fun billFromAi(json: String, today: LocalDate): com.houseaffairs.app.core.data.BillScan? = runCatching {
    val o = JSONObject(json.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
    val total = o.optDouble("total").takeIf { !it.isNaN() && it > 0 && it < 10_000_000 }?.let { BigDecimal.valueOf(it).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros() }
    val date = o.optString("date").takeIf { it.length == 10 }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.takeIf { !it.isAfter(today.plusDays(1)) }
    val arr = o.optJSONArray("items")
    val items = (0 until (arr?.length() ?: 0)).mapNotNull { i ->
        val it = arr!!.optJSONObject(i) ?: return@mapNotNull null
        val name = it.optString("name").trim().take(40).takeIf { n -> n.count(Char::isLetter) >= 2 } ?: return@mapNotNull null
        val qty = it.optInt("qty", 1).coerceIn(1, 50)
        val price = it.optDouble("price").takeIf { p -> !p.isNaN() && p > 0 }?.let { p -> Math.round(p * 100) }
        com.houseaffairs.app.core.data.BillItem(name.replaceFirstChar { c -> c.uppercaseChar() }, qty, price)
    }
    if (total == null && items.isEmpty()) return@runCatching null
    com.houseaffairs.app.core.data.BillScan(
        com.houseaffairs.app.core.data.ReceiptScan(total, o.optString("shop").trim().take(40).ifEmpty { null }?.takeIf { it != "null" }, date),
        items, o.optBoolean("pharmacy", false),
    )
}.getOrNull()
