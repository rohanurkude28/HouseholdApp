package com.houseaffairs.ios

/** Amounts as Android shows them; the shared [com.houseaffairs.app.core.util.moneyText]. */
fun formatMinor(minor: Long, currency: String): String = com.houseaffairs.app.core.util.moneyText(minor, currency)
