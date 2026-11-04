package com.houseaffairs.app.core.util

import java.math.BigDecimal

actual typealias Decimal = BigDecimal

actual fun decimalOf(text: String): Decimal? = text.toBigDecimalOrNull()
actual fun decimalOf(value: Long): Decimal = BigDecimal.valueOf(value)
actual operator fun Decimal.times(other: Decimal): Decimal = multiply(other)
actual fun Decimal.compareValue(other: Decimal): Int = compareTo(other)
actual fun Decimal.plainString(): String = toPlainString()
