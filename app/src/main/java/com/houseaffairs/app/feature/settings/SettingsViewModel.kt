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
    /** Private topic → who may see it. */
    val privateTopics: Map<String, List<String>> = emptyMap(),
    val calendar: com.houseaffairs.app.core.calendar.CalendarStatus = com.houseaffairs.app.core.calendar.CalendarStatus(),
    /** Family-wide service keys ("omdb", "tmdb"), set by the owner in Connections. */
    val integrations: Map<String, String> = emptyMap(),
    /** The last "Test & save" result per service, shown under its row. */
    val keyStatus: Map<String, String> = emptyMap(),
    val calendarSyncing: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val session: SessionManager,
    private val households: HouseholdRepository,
    private val settings: SettingsRepository,
    private val auth: AuthRepository,
    private val calendarStore: com.houseaffairs.app.core.calendar.CalendarStore,
    private val calendarSyncer: com.houseaffairs.app.core.calendar.CalendarSyncer,
    private val calendarAuth: com.houseaffairs.app.core.calendar.CalendarAuth,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val watch: com.houseaffairs.app.core.data.WatchRepository,
) : ViewModel() {
    private val keyStatus = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Checks a service key with the service first, and saves it for the family only if it works. */
    fun saveKey(service: String, key: String) {
        keyStatus.value = keyStatus.value + (service to "Checking…")
        withHousehold { hid ->
            val ok = watch.testKey(service, key)
            if (ok) households.setIntegration(hid, service, key)
            keyStatus.value = keyStatus.value + (service to if (ok) "Connected" else "That key didn't work. Check it and try again.")
        }
    }

    fun removeKey(service: String) {
        keyStatus.value = keyStatus.value - service
        withHousehold { households.setIntegration(it, service, null) }
    }
    private val calendarSyncing = MutableStateFlow(false)

    /** Google's consent screen, when Calendar access hasn't been allowed yet; the screen launches it. */
    val calendarConsent = MutableStateFlow<android.app.PendingIntent?>(null)
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
            privateTopics = r.household.privateTopics,
            integrations = r.household.integrations,
        )
    }.combine(combine(calendarStore.status, calendarSyncing, keyStatus) { c, busy, k -> Triple(c, busy, k) }) { st, (cal, busy, keys) ->
        st.copy(calendar = cal, calendarSyncing = busy, keyStatus = keys)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** Switching on asks Google for access first (a consent screen the first time), then syncs. */
    fun setCalendarSync(on: Boolean) {
        viewModelScope.launch {
            if (!on) {
                calendarStore.setEnabled(false)
                com.houseaffairs.app.notify.CalendarWorker.cancel(context)
                return@launch
            }
            when (val a = calendarAuth.authorize()) {
                is com.houseaffairs.app.core.calendar.CalendarAuth.Result.NeedsConsent -> calendarConsent.value = a.intent
                is com.houseaffairs.app.core.calendar.CalendarAuth.Result.Token -> startCalendarSync()
                is com.houseaffairs.app.core.calendar.CalendarAuth.Result.Failed -> calendarStore.setError(a.message)
            }
        }
    }

    /** After the consent screen: allowed starts syncing; cancelled leaves it off. */
    fun onCalendarConsent(allowed: Boolean) {
        calendarConsent.value = null
        if (allowed) viewModelScope.launch { startCalendarSync() } else viewModelScope.launch { calendarStore.setError("Calendar access wasn't allowed") }
    }

    private suspend fun startCalendarSync() {
        calendarStore.setEnabled(true)
        com.houseaffairs.app.notify.CalendarWorker.schedule(context)
        syncCalendarNow()
    }

    fun syncCalendarNow() {
        if (calendarSyncing.value) return
        viewModelScope.launch {
            calendarSyncing.value = true
            try {
                if (calendarSyncer.sync() is com.houseaffairs.app.core.calendar.CalendarSyncer.Outcome.NeedsConsent) setCalendarSync(true)
            } finally {
                calendarSyncing.value = false
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    /** Turns a private topic on (seen by [uids]) or off (null). */
    fun setPrivateTopic(topic: String, uids: List<String>?) = withHousehold { households.setPrivateTopic(it, topic, uids) }

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
