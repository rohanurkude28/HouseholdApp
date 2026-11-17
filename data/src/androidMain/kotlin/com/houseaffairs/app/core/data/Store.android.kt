package com.houseaffairs.app.core.data

internal actual fun platformValue(v: Any): Any? = when (v) {
    is com.google.firebase.Timestamp -> v.toDate().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    is Number -> v
    else -> v
}

internal actual fun platformBackupValue(v: Any): Any? = when (v) {
    is com.google.firebase.Timestamp -> mapOf("__time" to v.toDate().time)
    is com.google.firebase.firestore.GeoPoint -> mapOf("__geo" to listOf(v.latitude, v.longitude))
    is Number -> v
    else -> v.toString()
}
