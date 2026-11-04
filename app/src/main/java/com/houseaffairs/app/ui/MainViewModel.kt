package com.houseaffairs.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.houseaffairs.app.core.data.SessionManager
import com.houseaffairs.app.core.data.SessionState
import com.houseaffairs.app.core.data.SettingsRepository
import com.houseaffairs.app.core.data.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    sessionManager: SessionManager,
    private val settings: SettingsRepository,
) : ViewModel() {
    val session: StateFlow<SessionState> = sessionManager.state
    val themeMode: StateFlow<ThemeMode> =
        settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    /** The tour step to show, or null when there's no tour; -1 until it's read, so it never flashes. */
    val tourStep: StateFlow<Int?> = settings.tourStep.stateIn(viewModelScope, SharingStarted.Eagerly, -1)

    fun setTourStep(step: Int) {
        viewModelScope.launch { settings.setTourStep(step) }
    }

    fun finishTour() {
        viewModelScope.launch { settings.finishTour() }
    }
}
