package com.houseaffairs.app.core.util

/**
 * Digits for wa.me: a 10-digit number gets India's 91 (the app's home market); one already
 * starting with a country code (+44…, 0091…) keeps it.
 */
fun whatsAppNumber(raw: String, defaultCountry: String = "91"): String {
    val plus = raw.trim().startsWith("+")
    var digits = raw.filter { it.isDigit() }
    if (digits.startsWith("00")) return digits.drop(2)
    if (plus) return digits
    digits = digits.trimStart('0')
    return if (digits.length == 10) defaultCountry + digits else digits
}
