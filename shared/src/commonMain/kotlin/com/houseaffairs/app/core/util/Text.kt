package com.houseaffairs.app.core.util

import kotlin.math.abs
import kotlin.math.roundToLong

/** Percent-encodes [text] for a URL query, as URLEncoder does (spaces become "+"). */
fun urlEncode(text: String): String = buildString {
    text.encodeToByteArray().forEach { b ->
        val c = b.toInt().toChar()
        when {
            c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in "-_.*" -> append(c)
            c == ' ' -> append('+')
            else -> append('%').append(((b.toInt() and 0xff) or 0x100).toString(16).substring(1).uppercase())
        }
    }
}

/** The host of a web address ("www.youtube.com" from "https://www.youtube.com/watch?…"), or null. */
fun hostOf(url: String): String? =
    Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://([^/?#:]+)").find(url.trim())?.groupValues?.get(1)

/** [value] with one decimal ("7.5"), as "%.1f" does. */
fun oneDecimal(value: Double): String {
    val tenths = (value * 10).roundToLong()
    return "${if (tenths < 0) "-" else ""}${abs(tenths) / 10}.${abs(tenths) % 10}"
}

/** A whole number with thousands commas ("8,000"), as "%,d" does. */
fun grouped(value: Long): String {
    val digits = abs(value).toString().reversed().chunked(3).joinToString(",").reversed()
    return if (value < 0) "-$digits" else digits
}

fun grouped(value: Int): String = grouped(value.toLong())

// Regex's \p{L} classes aren't supported on iPhone, so words are split by Char checks instead.

/** The pieces of this text between characters that [isBreak], empty ones dropped. */
fun String.splitOn(isBreak: (Char) -> Boolean): List<String> {
    val out = mutableListOf<String>()
    val word = StringBuilder()
    forEach { c -> if (isBreak(c)) { if (word.isNotEmpty()) out += word.toString(); word.clear() } else word.append(c) }
    if (word.isNotEmpty()) out += word.toString()
    return out
}

/** Letters-and-digits words ("Dune 2!" → "Dune", "2"). */
fun String.words(): List<String> = splitOn { !it.isLetterOrDigit() }

/** This text with every character that isn't [kept] turned into a space. */
fun String.keepOnly(kept: (Char) -> Boolean): String = map { if (kept(it)) it else ' ' }.joinToString("")
