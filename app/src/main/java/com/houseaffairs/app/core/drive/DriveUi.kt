package com.houseaffairs.app.core.drive

import android.app.Activity
import android.app.PendingIntent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.DOC_TYPES

/** Shows Google's "allow Drive access" screen when an action needs it, and reports back. */
@Composable
fun DriveConsent(consent: PendingIntent?, onDone: (Boolean) -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        onDone(result.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(consent) {
        consent?.let { launcher.launch(IntentSenderRequest.Builder(it.intentSender).build()) }
    }
}

/**
 * Files a picked document into Drive › HouseAffairs › Documents › <type> and lists it in
 * Documents, where a tap opens it.
 */
/** The shared sheet (`:ui` UploadDocumentSheet), fed by [drive]; closes once the upload finishes. */
@Composable
fun UploadDocumentSheet(uri: Uri, drive: DriveViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val ds by drive.state.collectAsState()
    val fileName = remember(uri) {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(ds.busy, ds.uploaded) { if (started && ds.busy == null && ds.uploaded != null) onDismiss() }
    DriveConsent(ds.consent, drive::consentDone)
    com.houseaffairs.app.feature.vault.UploadDocumentSheet(
        fileName, ds.busy, ds.message?.takeIf { ds.uploaded == null },
        onUpload = { title, docType, byLink, onlyMe -> started = true; drive.upload(uri, title, docType, byLink, onlyMe) },
        onDismiss = onDismiss,
    )
}
