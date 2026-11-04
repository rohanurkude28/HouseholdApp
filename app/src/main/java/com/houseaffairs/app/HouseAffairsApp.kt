package com.houseaffairs.app

import android.app.Application
import com.houseaffairs.app.core.data.SettingsRepository
import com.houseaffairs.app.notify.DigestWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class HouseAffairsApp : Application() {

    @Inject lateinit var settings: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Inject lateinit var calendar: com.houseaffairs.app.core.calendar.CalendarStore

    override fun onCreate() {
        super.onCreate()
        // Keep calendar sync running in the background while it's switched on.
        scope.launch {
            calendar.status.distinctUntilChanged { a, b -> a.enabled == b.enabled }.collect { s ->
                if (s.enabled) com.houseaffairs.app.notify.CalendarWorker.schedule(this@HouseAffairsApp) else com.houseaffairs.app.notify.CalendarWorker.cancel(this@HouseAffairsApp)
            }
        }
        DigestWorker.createChannel(this)
        // Keep the daily digest scheduled at the chosen hour (or off).
        scope.launch {
            settings.notificationPrefs.distinctUntilChanged { a, b -> a.enabled == b.enabled && a.hour == b.hour }.collect { prefs ->
                if (prefs.enabled) DigestWorker.schedule(this@HouseAffairsApp, prefs.hour) else DigestWorker.cancel(this@HouseAffairsApp)
            }
        }
    }
}
