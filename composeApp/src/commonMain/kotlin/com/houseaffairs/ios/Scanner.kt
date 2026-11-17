package com.houseaffairs.ios

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Reading text off a bill, a pack or a label. The iPhone's is Swift (`ScannerSwift` in
 * iOSApp.swift): Apple's document camera where there is one, else a photo from the library,
 * read by Vision, line by line as printed. Calls back once: the text, or null when closed.
 */
interface ScanProvider {
    fun scan(onText: (String?) -> Unit)
}

/** Set by the app at start; null where there's no scanning. */
var scanner: ScanProvider? = null

/** The 📷 on a form or in the + sheet, as Android's `ScanButton`: [hint] says what it reads. */
@Composable
internal fun ScanButton(hint: String, autoStart: Boolean = false, onText: (String) -> Unit) {
    val reader = scanner ?: return
    var busy by remember { mutableStateOf(false) }
    // Test runs ("-openTab ADD/SCAN") press it once on opening.
    androidx.compose.runtime.LaunchedEffect(autoStart) { if (autoStart) { busy = true; reader.scan { text -> busy = false; text?.let(onText) } } }
    IconButton(onClick = { busy = true; reader.scan { text -> busy = false; text?.let(onText) } }, enabled = !busy) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        else Icon(Icons.Outlined.DocumentScanner, contentDescription = hint)
    }
}

/**
 * Picking a file to save to Drive (Swift's `FilePickerSwift`, the Files app's picker). Calls back
 * once with a readable copy's path and its name, or nulls when closed.
 */
interface FilePickProvider {
    fun pick(onFile: (path: String?, name: String?) -> Unit)
}

var filePicker: FilePickProvider? = null

/** A picked file's bytes (iOS reads it from the copy the picker made). */
expect fun readFileBytes(path: String): ByteArray?
