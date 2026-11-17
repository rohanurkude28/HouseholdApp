package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.time.*

/**
 * General home-care guidance per "used for" tag. Deliberately conservative: no dosages, always a
 * clear "see a doctor" line. The family's own dosage notes come from their medicine entries.
 */
data class Care(val tag: String, val words: List<String>, val homeCare: List<String>, val seeDoctor: String)

val CARE: List<Care> = listOf(
    Care(
        "Dizziness", listOf("dizzy", "dizziness", "lightheaded", "light headed", "giddy", "giddiness", "vertigo"),
        listOf("Sit or lie down until it passes; stand up slowly", "Drink water, and eat something if it's been a while"),
        "Fainting, chest pain, a severe headache, slurred speech, weakness on one side, or it keeps coming back",
    ),
    Care(
        "Fever", listOf("fever", "feverish", "temperature", "chills"),
        listOf("Rest and drink plenty of fluids", "Wear light clothes; sponge with lukewarm (not cold) water"),
        "Fever over 103°F / 39.4°C, lasting more than 3 days, with a stiff neck or rash, or in a baby under 3 months",
    ),
    Care(
        "Headache", listOf("headache", "migraine", "head ache", "head is paining", "head hurts"),
        listOf("Rest in a quiet, dark room", "Drink water; a cool cloth on the forehead can help"),
        "A sudden, severe headache, or one with fever, stiff neck, confusion, weakness or after a head injury",
    ),
    Care(
        "Cold & cough", listOf("cold", "cough", "runny nose", "blocked nose", "sneezing", "congestion"),
        listOf("Warm fluids and steam inhalation", "Rest; honey in warm water soothes a cough (not for babies under 1)"),
        "Trouble breathing, high fever, or a cough lasting over 2 weeks",
    ),
    Care(
        "Sore throat", listOf("sore throat", "throat pain", "throat is paining", "throat hurts"),
        listOf("Gargle with warm salt water", "Warm drinks and rest your voice"),
        "Difficulty swallowing or breathing, or it lasts more than a week",
    ),
    Care(
        "Acidity", listOf("acidity", "heartburn", "gas", "bloating", "indigestion", "acid reflux"),
        listOf("Small, light meals; avoid spicy and oily food", "Don't lie down right after eating"),
        "Chest pain, vomiting blood, or it keeps coming back",
    ),
    Care(
        "Stomach", listOf("stomach ache", "stomach pain", "tummy ache", "stomach upset", "cramps", "vomiting", "nausea", "throwing up", "stomach upset", "upset stomach", "tummy ache", "tummy pain"),
        listOf("Sip water or ORS in small amounts", "Bland food (rice, banana, toast) once you can eat"),
        "Severe or one-sided pain, blood in vomit or stool, or signs of dehydration",
    ),
    Care(
        "Loose motions", listOf("loose motion", "loose motions", "diarrhea", "diarrhoea", "upset stomach"),
        listOf("ORS after every loose stool, to replace fluids", "Light food; avoid milk and oily food for a day"),
        "Blood in stool, high fever, or signs of dehydration (very little urine, dizziness), especially in children",
    ),
    Care(
        "Allergy", listOf("allergy", "allergic", "rash", "itching", "itchy", "hives"),
        listOf("Avoid whatever triggered it", "A cool compress eases itching"),
        "Swelling of lips, face or throat, or trouble breathing: emergency, call for help now",
    ),
    Care(
        "Cuts & wounds", listOf("cut", "wound", "bleeding", "scratch", "graze", "scraped", "cut knee", "cut finger", "cut his", "cut her", "scraped"),
        listOf("Press firmly with a clean cloth until bleeding stops", "Rinse with clean water, then cover with a bandage"),
        "Deep or gaping cuts, bleeding that won't stop after 10 minutes, or a dirty/rusty object",
    ),
    Care(
        "Burns", listOf("burn", "burnt", "burned", "scald", "scalded"),
        listOf("Cool under running water for 20 minutes (not ice)", "Cover loosely with clean cling film or cloth"),
        "Burns larger than your palm, on the face, hands or genitals, or that look white or charred",
    ),
    Care(
        "Sprain", listOf("sprain", "twisted", "twist", "swollen ankle", "pulled muscle"),
        listOf("Rest it and apply ice wrapped in cloth for 15–20 minutes", "Keep it raised; a crepe bandage can support it"),
        "You can't put weight on it, it looks misshapen, or pain is severe",
    ),
    Care(
        "Pain", listOf("body pain", "body ache", "back pain", "joint pain", "toothache", "pain", "back hurts", "backache", "back ache", "neck pain", "knee pain", "leg pain", "joint pain"),
        listOf("Rest; a warm compress helps muscle aches", "Gentle stretching once it eases"),
        "Severe pain, chest pain, or pain after an injury",
    ),
    Care(
        "Insect bite", listOf("insect bite", "mosquito bite", "bee sting", "sting", "bitten"),
        listOf("Wash with soap and water", "A cold compress reduces swelling and itching"),
        "Swelling of face or throat, trouble breathing, or a spreading red area with fever",
    ),
)

/** The care entry a sentence is about ("I'm feeling feverish" → Fever); longest match wins. */
fun careFor(text: String): Care? = caresFor(text).firstOrNull()

/**
 * Every condition a sentence mentions, in the order said: "fever and cold" → Fever, Cold & cough.
 * Where words overlap ("sore throat" holds no "cold"), the longer match decides.
 */
fun caresFor(text: String): List<Care> {
    val t = " " + text.lowercase().replace(Regex("[^a-z ]"), " ").replace(Regex("\\s+"), " ") + " "
    val hits = CARE.flatMap { care -> care.words.map { care to it } }
        .mapNotNull { (care, word) -> (t.indexOf(" $word ").takeIf { it >= 0 } ?: t.indexOf(" ${word}s ").takeIf { it >= 0 })?.let { Triple(care, word, it) } }
    // A word inside a longer match ("cut" in "haircut"…) or an already-matched phrase doesn't count twice.
    val kept = hits.filter { (_, word, at) -> hits.none { (_, other, oAt) -> other.length > word.length && at >= oAt && at < oAt + other.length + 1 } }
    return kept.sortedBy { it.third }.map { it.first }.distinct()
}

/**
 * Common names of over-the-counter medicines for each condition, so a Crocin or Sinarest in the
 * cabinet is found even when nobody set its "Used for" tag. Matched as words in the medicine's name.
 */
val KNOWN_MEDICINES: Map<String, List<String>> = mapOf(
    "Fever" to listOf("paracetamol", "crocin", "dolo", "calpol", "ibuprofen", "brufen", "combiflam", "meftal", "tylenol"),
    "Headache" to listOf("paracetamol", "crocin", "dolo", "saridon", "disprin", "ibuprofen", "combiflam"),
    "Cold & cough" to listOf("cetirizine", "levocetirizine", "sinarest", "vicks", "benadryl", "ascoril", "cofsils", "otrivin", "allegra", "cough syrup", "strepsils"),
    "Sore throat" to listOf("strepsils", "cofsils", "betadine gargle", "honitus"),
    "Acidity" to listOf("digene", "eno", "gelusil", "pantoprazole", "pantop", "omeprazole", "omez", "rantac", "ranitidine"),
    "Stomach" to listOf("cyclopam", "meftal spas", "buscopan", "digene", "eno"),
    "Loose motions" to listOf("ors", "electral", "eldoper", "loperamide", "racecadotril"),
    "Allergy" to listOf("cetirizine", "levocetirizine", "allegra", "avil", "calamine"),
    "Cuts & wounds" to listOf("betadine", "soframycin", "dettol", "savlon", "band aid", "bandage"),
    "Burns" to listOf("burnol", "silverex", "aloe"),
    "Sprain" to listOf("volini", "moov", "iodex", "crepe bandage"),
    "Pain" to listOf("combiflam", "volini", "moov", "iodex", "ibuprofen", "paracetamol", "dolo"),
    "Insect bite" to listOf("calamine", "odomos", "soframycin"),
    "Dizziness" to listOf("ors", "electral", "glucon"),
)

data class MedicineInfo(
    val id: String,
    val title: String,
    val usedFor: List<String>,
    val forWhom: List<String>,
    val dosage: String?,
    val expiry: LocalDate?,
)

/** Medicines at home for [tag], not expired; ones meant for [personUid] first. */
fun medicinesFor(tag: String, medicines: List<MedicineInfo>, personUid: String?, today: LocalDate): List<MedicineInfo> {
    val known = KNOWN_MEDICINES[tag].orEmpty()
    fun matches(m: MedicineInfo) = m.usedFor.any { u ->
        u.equals(tag, ignoreCase = true) || tag.lowercase().split(" & ").any { part -> u.lowercase().contains(part) } ||
            (tag == "Pain" && u.lowercase().contains("pain")) || (tag == "Stomach" && u.lowercase().let { it.contains("stomach") || it.contains("acid") })
    } || (" " + m.title.lowercase() + " ").let { name -> known.any { name.contains(" $it") } }
    return medicines
        .filter { matches(it) && (it.expiry == null || !it.expiry.isBefore(today)) }
        .sortedWith(compareByDescending<MedicineInfo> { personUid != null && personUid in it.forWhom }.thenBy { it.forWhom.isNotEmpty() && personUid !in it.forWhom })
}
