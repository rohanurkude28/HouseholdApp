package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.time.*

/**
 * General home-care guidance per "used for" tag. Deliberately conservative: no dosages, always a
 * clear "see a doctor" line. The family's own dosage notes come from their medicine entries.
 */
data class Care(val tag: String, val words: List<String>, val homeCare: List<String>, val seeDoctor: String)

val CARE: List<Care> = listOf(
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
        "Stomach", listOf("stomach ache", "stomach pain", "tummy ache", "stomach upset", "cramps", "vomiting", "nausea", "throwing up"),
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
        "Cuts & wounds", listOf("cut", "wound", "bleeding", "scratch", "graze", "scraped"),
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
        "Pain", listOf("body pain", "body ache", "back pain", "joint pain", "toothache", "pain"),
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
fun careFor(text: String): Care? {
    val t = " " + text.lowercase().replace(Regex("[^a-z ]"), " ").replace(Regex("\\s+"), " ") + " "
    return CARE
        .flatMap { care -> care.words.map { care to it } }
        .filter { (_, word) -> t.contains(" $word ") || t.contains(" ${word}s ") }
        .maxByOrNull { (_, word) -> word.length }
        ?.first
}

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
    fun matches(m: MedicineInfo) = m.usedFor.any { u ->
        u.equals(tag, ignoreCase = true) || tag.lowercase().split(" & ").any { part -> u.lowercase().contains(part) } ||
            (tag == "Pain" && u.lowercase().contains("pain")) || (tag == "Stomach" && u.lowercase().let { it.contains("stomach") || it.contains("acid") })
    }
    return medicines
        .filter { matches(it) && (it.expiry == null || !it.expiry.isBefore(today)) }
        .sortedWith(compareByDescending<MedicineInfo> { personUid != null && personUid in it.forWhom }.thenBy { it.forWhom.isNotEmpty() && personUid !in it.forWhom })
}
