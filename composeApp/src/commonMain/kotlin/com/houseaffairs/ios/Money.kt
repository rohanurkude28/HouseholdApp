package com.houseaffairs.ios

private val SYMBOLS = mapOf("INR" to "₹", "USD" to "$", "EUR" to "€", "GBP" to "£", "JPY" to "¥", "AUD" to "A$", "CAD" to "C$", "SGD" to "S$", "AED" to "AED ")
private val NO_DECIMALS = setOf("JPY", "KRW")

/**
 * Minor units as money text, in the same shape Android shows: whole amounts drop ".00", and
 * rupees group in lakhs (₹1,23,456).
 */
fun formatMinor(minor: Long, currency: String): String {
    val digits = if (currency in NO_DECIMALS) 0 else 2
    val negative = minor < 0
    val abs = if (negative) -minor else minor
    val unit = if (digits == 0) 1L else 100L
    val whole = (abs / unit).toString()
    val fraction = abs % unit
    val grouped = if (currency == "INR" && whole.length > 3) {
        val head = whole.dropLast(3)
        head.reversed().chunked(2).joinToString(",").reversed() + "," + whole.takeLast(3)
    } else {
        whole.reversed().chunked(3).joinToString(",").reversed()
    }
    val text = (SYMBOLS[currency] ?: "$currency ") + grouped + if (fraction != 0L) "." + fraction.toString().padStart(2, '0') else ""
    return if (negative) "-$text" else text
}
