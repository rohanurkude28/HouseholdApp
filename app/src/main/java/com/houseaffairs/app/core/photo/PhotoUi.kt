package com.houseaffairs.app.core.photo

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/** A square photo from this phone; a grey tile when it isn't here (taken on another phone, not restored yet). */
@Composable
fun PhotoThumb(id: String, size: Dp, onClick: (() -> Unit)? = null) {
    val context = LocalContext.current
    val file = remember(id) { PhotoStore.file(context, id) }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (file.exists()) AsyncImage(model = file, contentDescription = "Photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text("📷", style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * The form's photos: a short row of squares, then "Add photo" (camera or gallery). A tap opens
 * one full screen, where it can be removed.
 */
@Composable
fun PhotosEditor(ids: List<String>, onChange: (List<String>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<String?>(null) }
    var target by remember { mutableStateOf<Uri?>(null) }
    fun add(uri: Uri?) {
        if (uri == null) return
        scope.launch { runCatching { PhotoStore.import(context, uri) }.onSuccess { onChange(ids + it) } }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) add(target) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris -> uris.forEach(::add) }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(ids, key = { it }) { id -> PhotoThumb(id, 72.dp) { viewing = id } }
        item {
            Box {
                Column(
                    Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.secondaryContainer).clickable { menu = true },
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Outlined.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text("Add", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Take a photo") }, leadingIcon = { Icon(Icons.Outlined.AddAPhoto, null) }, onClick = {
                        menu = false
                        target = PhotoStore.cameraTarget(context).also { camera.launch(it) }
                    })
                    DropdownMenuItem(text = { Text("Choose from gallery") }, leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) }, onClick = {
                        menu = false
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    })
                }
            }
        }
    }
    viewing?.let { id ->
        PhotoViewer(id, onDismiss = { viewing = null }, onDelete = {
            // Only off this entry: the file stays until the form is saved, so cancelling keeps it.
            onChange(ids - id)
            viewing = null
        })
    }
}

/** One photo full screen, pinch to zoom; [onDelete] adds a remove button (in forms). */
@Composable
fun PhotoViewer(id: String, onDismiss: () -> Unit, onDelete: (() -> Unit)? = null) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = PhotoStore.file(context, id), contentDescription = "Photo", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            dx = if (scale == 1f) 0f else dx + pan.x
                            dy = if (scale == 1f) 0f else dy + pan.y
                        }
                    }
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = dx, translationY = dy),
            )
            Row(Modifier.statusBarsPadding().padding(8.dp).align(Alignment.TopEnd)) {
                onDelete?.let { IconButton(onClick = it) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove photo", tint = Color.White) } }
                IconButton(onClick = onDismiss, modifier = Modifier.clip(CircleShape)) { Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Color.White) }
            }
        }
    }
}

/** Up to three small photos in a list row; a tap opens the first full screen. */
@Composable
fun RowPhotos(ids: List<String>) {
    if (ids.isEmpty()) return
    var viewing by remember { mutableStateOf<String?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(end = 10.dp)) {
        ids.take(1).forEach { id -> PhotoThumb(id, 40.dp) { viewing = id } }
    }
    viewing?.let { PhotoViewer(it, onDismiss = { viewing = null }) }
}
