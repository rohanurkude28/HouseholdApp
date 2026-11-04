package com.houseaffairs.app.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens the dialer, WhatsApp or SMS for [phone]; without a number the app opens empty. Nothing is sent. */
fun reachOut(context: Context, reach: String, phone: String?) {
    val number = phone?.trim().orEmpty()
    val intent = when (reach) {
        "WhatsApp" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + if (number.isEmpty()) "" else whatsAppNumber(number)))
        "SMS" -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))
        else -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
    }
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
