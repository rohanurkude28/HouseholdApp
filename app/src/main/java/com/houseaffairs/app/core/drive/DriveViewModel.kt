package com.houseaffairs.app.core.drive

import android.app.PendingIntent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.houseaffairs.app.core.data.SessionManager
import com.houseaffairs.app.core.data.SessionState
import com.houseaffairs.app.core.model.Visibility
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DriveUiState(
    val busy: String? = null,
    val message: String? = null,
    val backups: List<DriveBackup>? = null,
    /** Google's consent screen to show; the action waiting on it runs again once allowed. */
    val consent: PendingIntent? = null,
    /** Link of the document just uploaded. */
    val uploaded: String? = null,
)

/** Backup, restore and document upload, each asking Google for Drive access first. */
@HiltViewModel
class DriveViewModel @Inject constructor(
    private val session: SessionManager,
    private val auth: DriveAuth,
    private val drive: DriveRepository,
    private val settings: com.houseaffairs.app.core.data.SettingsRepository,
) : ViewModel() {
    val backupPrefs = settings.backupPrefs

    fun setBackupMode(mode: com.houseaffairs.app.core.data.BackupMode) = viewModelScope.launch { settings.setBackupMode(mode) }
    fun setWifiOnly(on: Boolean) = viewModelScope.launch { settings.setBackupWifiOnly(on) }

    /** The once-a-day "back up today?" has been shown (or answered). */
    fun reminded() = viewModelScope.launch { settings.markBackupReminded(java.time.LocalDate.now().toString()) }
    private val _state = MutableStateFlow(DriveUiState())
    val state: StateFlow<DriveUiState> = _state.asStateFlow()

    private var waiting: (() -> Unit)? = null

    fun backup() = run("Backing up to Google Drive…") { token, r ->
        val n = drive.backup(token, r.household.id, r.user.uid)
        settings.markBackedUp(java.time.LocalDate.now().toString())
        _state.update { it.copy(message = "Backed up $n entries to Drive › HouseAffairs › Backups", backups = null) }
    }

    fun loadBackups() = run("Looking for backups…") { token, _ ->
        val list = drive.backups(token)
        _state.update { it.copy(backups = list, message = if (list.isEmpty()) "No backups in Drive yet" else null) }
    }

    fun restore(backup: DriveBackup) = run("Restoring ${backup.label}…") { token, r ->
        val n = drive.restore(token, backup.fileId, r.household.id, r.user.uid)
        _state.update { it.copy(message = "Restored $n entries from ${backup.label}") }
    }

    fun upload(uri: Uri, title: String, docType: String, shareByLink: Boolean, onlyMe: Boolean) = run("Uploading to Google Drive…") { token, r ->
        val visibleTo = if (onlyMe) listOf(r.user.uid) else Visibility.everyone
        val link = drive.uploadDocument(token, r.household.id, r.user.uid, uri, title, docType, shareByLink, visibleTo)
        _state.update { it.copy(message = "$title is in Drive › HouseAffairs › Documents › $docType", uploaded = link) }
    }

    /** Google's consent screen closed: carry on if allowed. */
    fun consentDone(allowed: Boolean) {
        _state.update { it.copy(consent = null) }
        val next = waiting
        waiting = null
        if (allowed) next?.invoke() else _state.update { it.copy(busy = null, message = "Drive access wasn't allowed") }
    }

    fun clearMessage() = _state.update { it.copy(message = null, uploaded = null) }

    private fun run(busy: String, action: suspend (String, SessionState.Ready) -> Unit) {
        val r = session.state.value as? SessionState.Ready ?: return
        _state.update { it.copy(busy = busy, message = null) }
        viewModelScope.launch {
            when (val a = auth.authorize()) {
                is DriveAuth.Result.NeedsConsent -> {
                    waiting = { run(busy, action) }
                    _state.update { it.copy(consent = a.intent) }
                }
                is DriveAuth.Result.Failed -> _state.update { it.copy(busy = null, message = a.message) }
                is DriveAuth.Result.Token -> {
                    runCatching { action(a.value, r) }.onFailure { e -> _state.update { it.copy(message = e.message ?: "Something went wrong with Drive") } }
                    _state.update { it.copy(busy = null) }
                }
            }
        }
    }
}
