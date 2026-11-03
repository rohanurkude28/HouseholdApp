package com.houseaffairs.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.houseaffairs.app.core.data.AuthRepository
import com.houseaffairs.app.core.data.HouseholdRepository
import com.houseaffairs.app.core.data.SessionManager
import com.houseaffairs.app.core.data.SessionState
import com.houseaffairs.app.core.data.SettingsRepository
import com.houseaffairs.app.core.data.ThemeMode
import com.houseaffairs.app.core.model.Member
import com.houseaffairs.app.core.model.Role
import com.houseaffairs.app.core.model.RolePolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val householdName: String = "",
    val baseCurrency: String = "USD",
    val myUid: String = "",
    val email: String = "",
    val isOwner: Boolean = false,
    val members: List<Member> = emptyList(),
    val inviteCode: String? = null,
    val error: String? = null,
    val notifications: SettingsRepository.NotificationPrefs = SettingsRepository.NotificationPrefs(),
    val smartAdd: Boolean = false,
    val smartSuggest: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val session: SessionManager,
    private val households: HouseholdRepository,
    private val settings: SettingsRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    private val ready = session.state.filterIsInstance<SessionState.Ready>()
    private val inviteCode = MutableStateFlow<String?>(null)
    private val error = MutableStateFlow<String?>(null)

    private data class Prefs(val theme: ThemeMode, val notifications: SettingsRepository.NotificationPrefs, val smartAdd: Boolean, val smartSuggest: Boolean)

    private val prefs = combine(settings.themeMode, settings.notificationPrefs, settings.smartAdd, settings.smartSuggest, ::Prefs)

    val state: StateFlow<SettingsUiState> = combine(
        ready,
        ready.map { it.household.id }.distinctUntilChanged().flatMapLatest { households.observeMembers(it) },
        prefs,
        inviteCode,
        error,
    ) { r, members, p, code, err ->
        SettingsUiState(
            themeMode = p.theme,
            householdName = r.household.name,
            baseCurrency = r.household.baseCurrency,
            myUid = r.user.uid,
            email = r.user.email.orEmpty(),
            isOwner = RolePolicy.canManageMembers(r.me.role),
            members = members,
            inviteCode = code,
            error = err,
            notifications = p.notifications,
            smartAdd = p.smartAdd,
            smartSuggest = p.smartSuggest,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setCurrency(currency: String) = withHousehold { households.setBaseCurrency(it, currency) }

    fun createInvite(role: Role) = withHousehold { inviteCode.value = households.createInvite(it, role) }

    fun dismissInvite() {
        inviteCode.value = null
    }

    fun changeRole(member: Member, role: Role) = withHousehold { households.setRole(it, member.uid, role) }

    fun dismissError() {
        error.value = null
    }

    fun setSmartSuggest(on: Boolean) {
        viewModelScope.launch { settings.setSmartSuggest(on) }
    }

    fun setSmartAdd(on: Boolean) {
        viewModelScope.launch { settings.setSmartAdd(on) }
    }

    fun setNotifications(prefs: SettingsRepository.NotificationPrefs) {
        viewModelScope.launch { settings.setNotificationPrefs(prefs) }
    }

    fun signOut() {
        viewModelScope.launch { auth.signOut() }
    }

    private fun withHousehold(block: suspend (householdId: String) -> Unit) {
        val householdId = (session.state.value as? SessionState.Ready)?.household?.id ?: return
        viewModelScope.launch {
            runCatching { block(householdId) }.onFailure { error.value = it.message ?: "Something went wrong" }
        }
    }
}
