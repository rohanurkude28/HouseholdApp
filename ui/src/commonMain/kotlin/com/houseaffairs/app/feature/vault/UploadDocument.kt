package com.houseaffairs.app.feature.vault

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.DOC_TYPES
import com.houseaffairs.app.core.data.guessDocType

/**
 * Saving a picked file to Google Drive, on both phones: its name, the folder (guessed from the
 * file's name), and who can open it. [busy] shows progress; [error] what went wrong.
 * [onUpload] gets the name, the folder, "family can open the link" and "only I see it".
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UploadDocumentSheet(
    fileName: String?,
    busy: String?,
    error: String?,
    onUpload: (title: String, docType: String, byLink: Boolean, onlyMe: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember(fileName) { mutableStateOf(fileName?.substringBeforeLast('.')?.replace('_', ' ')?.replaceFirstChar { it.uppercaseChar() }.orEmpty()) }
    var docType by remember(fileName) { mutableStateOf(guessDocType(fileName.orEmpty())) }
    var other by remember { mutableStateOf("") }
    var onlyMe by remember { mutableStateOf(false) }
    var byLink by remember { mutableStateOf(true) }
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
            busy?.let { Text(it); LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { onUpload(title, if (docType == "Other") other.trim().ifEmpty { "Other" } else docType, byLink && !onlyMe, onlyMe) },
                enabled = title.isNotBlank() && busy == null,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Upload") }
        }
    }
}
