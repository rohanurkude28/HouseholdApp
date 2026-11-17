package com.houseaffairs.app.core.data

import java.time.LocalDateTime

/** Android's clock for the shared [backupFileName] (`:shared` DriveFiles.kt). */
fun backupFileName(at: LocalDateTime): String = backupFileName(at.toLocalDate(), at.hour, at.minute)
