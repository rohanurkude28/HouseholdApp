package com.houseaffairs.app.core.data

/** A first guess at the folder from the file's name; the person can change it. */
fun guessDocType(name: String): String {
    val n = name.lowercase()
    // Whole words, so "company" isn't a PAN card.
    fun has(vararg w: String) = Regex("(^|[^a-z])(${w.joinToString("|")})").containsMatchIn(n)
    return when {
        has("passport", "aadhaar", "aadhar", "pan", "licence", "license", "voter", "id") -> "IDs & passports"
        has("insurance", "policy", "premium") -> "Insurance"
        has("prescription", "report", "lab", "medical", "hospital", "vaccin") -> "Medical"
        has("rc", "puc", "vehicle", "car", "bike") -> "Vehicle"
        has("marksheet", "certificate", "degree", "school", "college") -> "Education"
        has("deed", "agreement", "property", "rent", "sale") -> "Property & home"
        has("itr", "tax", "form16", "bank", "statement") -> "Bank & tax"
        has("bill", "invoice", "warranty", "receipt") -> "Bills & warranties"
        else -> "Other"
    }
}
