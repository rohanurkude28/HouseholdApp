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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UploadDocumentSheet(uri: Uri, drive: DriveViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val ds by drive.state.collectAsState()
    val fileName = remember(uri) {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }
    var title by remember { mutableStateOf(fileName?.substringBeforeLast('.')?.replace('_', ' ')?.replaceFirstChar { it.uppercaseChar() }.orEmpty()) }
    var docType by remember { mutableStateOf(guessDocType(fileName.orEmpty())) }
    var other by remember { mutableStateOf("") }
    var onlyMe by remember { mutableStateOf(false) }
    var byLink by remember { mutableStateOf(true) }
    var started by remember { mutableStateOf(false) }
    // Done: close once the upload finishes.
    LaunchedEffect(ds.busy, ds.uploaded) { if (started && ds.busy == null && ds.uploaded != null) onDismiss() }

    DriveConsent(ds.consent, drive::consentDone)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Save to Google Drive", style = MaterialTheme.typography.titleLarge)
            Text(fileName ?: "Picked file", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(title, { title = it }, label = { Text("Name") }, placeholder = { Text("Riya's passport") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Folder", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DOC_TYPES.forEach { t -> FilterChip(selected = docType == t, onClick = { docType = t }, label = { Text(t) }, shape = CircleShape) }
            }
            // "Other" asks what it is, and that becomes its folder.
            if (docType == "Other") {
                OutlinedTextField(other, { other = it }, label = { Text("What is it?") }, placeholder = { Text("Pets, rent agreements…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Family can open it", style = MaterialTheme.typography.titleSmall)
                    Text("Anyone with the link can view the file. Turn off for private papers.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = byLink && !onlyMe, onCheckedChange = { byLink = it; if (it) onlyMe = false })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Only I see it in the app", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(checked = onlyMe, onCheckedChange = { onlyMe = it; if (it) byLink = false })
            }
            ds.busy?.let { Text(it); LinearProgressIndicator(Modifier.fillMaxWidth()) }
            ds.message?.takeIf { ds.uploaded == null }?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    started = true
                    drive.upload(uri, title, if (docType == "Other") other.trim().ifEmpty { "Other" } else docType, byLink && !onlyMe, onlyMe)
                },
                enabled = title.isNotBlank() && ds.busy == null,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Upload") }
        }
    }
}

/** A first guess at the folder from the file's name; the person can change it. */
fun guessDocType(name: String): String {
    val n = name.lowercase()
    // Whole words, so "company" isn't a PAN card.
    fun has(vararg w: String) = Regex("(^|[^a-z])(${w.joinToString("|")})").containsMatchIn(n)
    return when {
        has("passport", "aadhaar", "aadhar", "pan", "licence", "license", "voter", "id") -> "IDs & passports"
        has("insurance", "policy", "premium") -> "Insurance"
        has("prescription", "report", "lab", "medical", "hospital", "vaccin") -> "Medical"
        has("rc", "puc", "vehicle", "car", "bike") -> "Vehicle"
        has("marksheet", "certificate", "degree", "school", "college") -> "Education"
        has("deed", "agreement", "property", "rent", "sale") -> "Property & home"
        has("itr", "tax", "form16", "bank", "statement") -> "Bank & tax"
        has("bill", "invoice", "warranty", "receipt") -> "Bills & warranties"
        else -> "Other"
    }
}
