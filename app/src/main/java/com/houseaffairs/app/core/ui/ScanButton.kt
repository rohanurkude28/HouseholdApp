package com.houseaffairs.app.core.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.houseaffairs.app.core.photo.PhotoStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Point the camera at a receipt, a pack or a label (or pick a photo of one): the text is read on
 * the phone and handed to [onText], which fills what it can. Nothing is uploaded.
 */
@Composable
fun ScanButton(hint: String, onText: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<Uri?>(null) }
    fun read(uri: Uri?) {
        if (uri == null) return
        busy = true
        scope.launch {
            val text = runCatching {
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(InputImage.fromFilePath(context, uri)).await().text
            }.getOrDefault("")
            busy = false
            onText(text)
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) read(target) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { read(it) }
    Box {
        IconButton(onClick = { menu = true }, enabled = !busy) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Outlined.DocumentScanner, contentDescription = hint)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            // What it will read, so people know what to point it at.
            DropdownMenuItem(text = { Text(hint, style = androidx.compose.material3.MaterialTheme.typography.labelMedium) }, onClick = {}, enabled = false)
            DropdownMenuItem(text = { Text("Scan with camera") }, leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null) }, onClick = {
                menu = false
                target = PhotoStore.cameraTarget(context).also { camera.launch(it) }
            })
            DropdownMenuItem(text = { Text("From a photo") }, leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) }, onClick = {
                menu = false
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
        }
    }
}
