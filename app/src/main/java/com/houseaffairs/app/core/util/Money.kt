package com.houseaffairs.app.core.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

fun defaultCurrencyCode(locale: Locale = Locale.getDefault()): String =
    runCatching { Currency.getInstance(locale)?.currencyCode }.getOrNull() ?: "USD"

/** Money is stored as integer minor units (paise, cents) to avoid floating-point drift. */
fun toMinorUnits(amount: BigDecimal, currencyCode: String): Long {
    val digits = Currency.getInstance(currencyCode).defaultFractionDigits.coerceAtLeast(0)
    return amount.movePointRight(digits).setScale(0, RoundingMode.HALF_UP).longValueExact()
}

/** Whole amounts drop the decimals ("₹1,200", not "₹1,200.00"). */
fun formatAmount(amount: BigDecimal, currencyCode: String): String =
    // Rupees read best in lakh grouping (₹1,00,000) whatever the phone's language.
    NumberFormat.getCurrencyInstance(if (currencyCode == "INR") Locale("en", "IN") else Locale.getDefault()).apply {
        currency = Currency.getInstance(currencyCode)
        if (amount.stripTrailingZeros().scale() <= 0) maximumFractionDigits = 0
    }.format(amount)

fun currencyLabel(currencyCode: String): String =
    runCatching { Currency.getInstance(currencyCode) }.getOrNull()
        ?.let { "${it.currencyCode} · ${it.displayName}" } ?: currencyCode

/** Formats stored minor units; whole amounts drop the decimals ("₹450", not "₹450.00"). */
fun formatMinor(amountMinor: Long, currencyCode: String): String {
    val currency = Currency.getInstance(currencyCode)
    return formatAmount(BigDecimal.valueOf(amountMinor, currency.defaultFractionDigits.coerceAtLeast(0)), currencyCode)
}

const val HIDDEN_AMOUNT = "•••"

/** Short form for tight spaces: ₹61.5k, ₹1.2L (Indian rupees use lakh), $2.3M. */
fun formatCompact(amountMinor: Long, currencyCode: String): String {
    val currency = Currency.getInstance(currencyCode)
    val whole = BigDecimal.valueOf(amountMinor, currency.defaultFractionDigits.coerceAtLeast(0)).toDouble()
    val sign = if (whole < 0) "-" else ""
    val abs = kotlin.math.abs(whole)
    val symbol = currency.getSymbol(Locale.getDefault())
    fun fmt(v: Double) = if (v >= 100 || v % 1.0 == 0.0) "%.0f".format(v) else "%.1f".format(v)
    return sign + symbol + when {
        currencyCode == "INR" && abs >= 1_00_00_000 -> fmt(abs / 1_00_00_000) + "Cr"
        currencyCode == "INR" && abs >= 1_00_000 -> fmt(abs / 1_00_000) + "L"
        abs >= 1_000_000 -> fmt(abs / 1_000_000) + "M"
        abs >= 1_000 -> fmt(abs / 1_000) + "k"
        else -> fmt(abs)
    }
}
