package com.houseaffairs.app

import android.app.Application
import com.houseaffairs.app.core.data.SettingsRepository
import com.houseaffairs.app.core.data.SessionState
import com.houseaffairs.app.notify.DigestWorker
import com.houseaffairs.app.notify.Reminders
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
@HiltAndroidApp
class HouseAffairsApp : Application() {

    @Inject lateinit var settings: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Inject lateinit var calendar: com.houseaffairs.app.core.calendar.CalendarStore
    @Inject lateinit var session: com.houseaffairs.app.core.data.SessionManager
    @Inject lateinit var home: com.houseaffairs.app.core.data.HomeRepository
    @Inject lateinit var things: com.houseaffairs.app.core.data.ThingsRepository

    override fun onCreate() {
        super.onCreate()
        // Keep calendar sync running in the background while it's switched on.
        scope.launch {
            calendar.status.distinctUntilChanged { a, b -> a.enabled == b.enabled }.collect { s ->
                if (s.enabled) com.houseaffairs.app.notify.CalendarWorker.schedule(this@HouseAffairsApp) else com.houseaffairs.app.notify.CalendarWorker.cancel(this@HouseAffairsApp)
            }
        }
        DigestWorker.createChannel(this)
        Reminders.createChannel(this)
        Reminders.schedulePeriodic(this)
        // While the app runs, a new or changed task re-plans this phone's reminders straight away.
        scope.launch {
            session.state
                .flatMapLatest { s ->
                    when (s) {
                        is SessionState.Ready -> home.observe(s.household.id, s.me, s.household.baseCurrency).filter { it.loaded }.map { it to s.me.uid }
                        // Signed out: nothing should ring. Still loading (an alarm woke the app): leave them be.
                        SessionState.SignedOut -> flowOf(null)
                        else -> emptyFlow()
                    }
                }
                .debounce(1_500)
                .collect { pair ->
                    if (pair != null) Reminders.sync(this@HouseAffairsApp, pair.first, pair.second) else Reminders.schedule(this@HouseAffairsApp, emptyList())
                    // The home-screen widget follows the same changes.
                    com.houseaffairs.app.widget.HomeWidget.refreshSoon(this@HouseAffairsApp)
                }
        }
        // Once per start: finished things whose year in review is over are cleared.
        scope.launch {
            session.state.filterIsInstance<SessionState.Ready>().map { it.household.id to it.me.uid }.distinctUntilChanged().collect { (hid, uid) ->
                runCatching { things.clearOldFinished(hid, uid) }
            }
        }
        // Daily automatic backup while it's chosen, on Wi-Fi only or any network.
        scope.launch {
            settings.backupPrefs.distinctUntilChanged { a, b -> a.mode == b.mode && a.wifiOnly == b.wifiOnly }.collect { b ->
                if (b.mode == com.houseaffairs.app.core.data.BackupMode.AUTO) com.houseaffairs.app.core.drive.BackupWorker.schedule(this@HouseAffairsApp, b.wifiOnly)
                else com.houseaffairs.app.core.drive.BackupWorker.cancel(this@HouseAffairsApp)
            }
        }
        // Keep the daily digest scheduled at the chosen hour (or off).
        scope.launch {
            settings.notificationPrefs.distinctUntilChanged { a, b -> a.enabled == b.enabled && a.hour == b.hour }.collect { prefs ->
                if (prefs.enabled) DigestWorker.schedule(this@HouseAffairsApp, prefs.hour) else DigestWorker.cancel(this@HouseAffairsApp)
            }
        }
    }
}
