package com.houseaffairs.app.core.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

// Android SDK helpers for the repositories not yet in :data.

internal fun LocalDate.toTimestamp() = Timestamp(Date.from(atStartOfDay(ZoneId.systemDefault()).toInstant()))

internal fun DocumentSnapshot.localDate(field: String): LocalDate? =
    getTimestamp(field)?.toDate()?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDate()
