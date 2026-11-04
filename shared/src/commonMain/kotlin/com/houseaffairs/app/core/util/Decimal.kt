package com.houseaffairs.app.core.util

/**
 * An exact decimal amount as typed ("2.5", "2,000" without the comma). On Android it *is*
 * java.math.BigDecimal, so the app keeps using it unchanged; on iOS it's a small exact decimal
 * with the same equality (scale counts: 2.5 != 2.50) and plain-string form.
 */
expect class Decimal

/** "250", "2.5", "-3"; null when it isn't a number (String.toBigDecimalOrNull). */
expect fun decimalOf(text: String): Decimal?

expect fun decimalOf(value: Long): Decimal

expect operator fun Decimal.times(other: Decimal): Decimal

/** Numeric order, ignoring scale (BigDecimal.compareTo). */
expect fun Decimal.compareValue(other: Decimal): Int

/** Without exponent: "2500.0" (BigDecimal.toPlainString). */
expect fun Decimal.plainString(): String
