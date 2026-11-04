package com.houseaffairs.app.core.util

/** [unscaled] × 10^-[scale]; amounts people type fit comfortably in a Long. */
actual class Decimal(val unscaled: Long, val scale: Int) {
    override fun equals(other: Any?): Boolean = other is Decimal && unscaled == other.unscaled && scale == other.scale
    override fun hashCode(): Int = 31 * unscaled.hashCode() + scale
    override fun toString(): String = plainString()
}

private val NUMBER = Regex("[+-]?(\\d+\\.?\\d*|\\.\\d+)")

actual fun decimalOf(text: String): Decimal? {
    if (!NUMBER.matches(text)) return null
    val negative = text.startsWith("-")
    val body = text.trimStart('+', '-')
    val dot = body.indexOf('.')
    val digits = body.replace(".", "").ifEmpty { "0" }
    val unscaled = digits.toLongOrNull() ?: return null
    val scale = if (dot < 0) 0 else body.length - dot - 1
    return Decimal(if (negative) -unscaled else unscaled, scale)
}

actual fun decimalOf(value: Long): Decimal = Decimal(value, 0)

actual operator fun Decimal.times(other: Decimal): Decimal = Decimal(unscaled * other.unscaled, scale + other.scale)

actual fun Decimal.compareValue(other: Decimal): Int {
    val s = maxOf(scale, other.scale)
    fun up(d: Decimal): Long { var u = d.unscaled; repeat(s - d.scale) { u *= 10 }; return u }
    return up(this).compareTo(up(other))
}

actual fun Decimal.plainString(): String {
    if (scale <= 0) return unscaled.toString() + "0".repeat(-scale)
    val negative = unscaled < 0
    val digits = (if (negative) -unscaled else unscaled).toString().padStart(scale + 1, '0')
    return (if (negative) "-" else "") + digits.dropLast(scale) + "." + digits.takeLast(scale)
}
