package com.houseaffairs.app.core.calendar

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Calendar sync settings and what was last agreed with Google. On this phone only, per person. */
data class CalendarStatus(val enabled: Boolean = false, val lastSync: Long? = null, val error: String? = null)

@Singleton
class CalendarStore @Inject constructor(private val dataStore: DataStore<Preferences>) {

    val status: Flow<CalendarStatus> = dataStore.data.map { CalendarStatus(it[ENABLED] == true, it[LAST], it[ERROR]) }

    suspend fun setEnabled(on: Boolean) = dataStore.edit { it[ENABLED] = on }

    suspend fun setError(message: String?) = dataStore.edit { if (message == null) it.remove(ERROR) else it[ERROR] = message }

    data class Saved(val owner: String?, val calendarId: String?, val synced: Map<String, Synced>, val skipped: Set<String>)

    suspend fun load(): Saved = dataStore.data.first().let { p ->
        val synced = p[SYNCED]?.let { raw ->
            runCatching {
                val o = JSONObject(raw)
                o.keys().asSequence().associateWith { k ->
                    val s = o.getJSONObject(k)
                    Synced(s.getString("e"), s.getString("t"), LocalDate.parse(s.getString("d")))
                }
            }.getOrNull()
        }.orEmpty()
        Saved(p[OWNER], p[CALENDAR], synced, p[SKIPPED].orEmpty())
    }

    suspend fun save(owner: String, calendarId: String, synced: Map<String, Synced>, skipped: Set<String>, at: Long) = dataStore.edit { p ->
        p[OWNER] = owner
        p[CALENDAR] = calendarId
        p[SYNCED] = JSONObject().apply { synced.forEach { (k, s) -> put(k, JSONObject().put("e", s.eventId).put("t", s.title).put("d", s.date.toString())) } }.toString()
        p[SKIPPED] = skipped
        p[LAST] = at
        p.remove(ERROR)
    }

    /** Forgets everything synced (another person signed in, or the calendar was deleted). */
    suspend fun reset() = dataStore.edit { p -> listOf(OWNER, CALENDAR, SYNCED, LAST).forEach { p.remove(it) }; p.remove(SKIPPED) }

    private companion object {
        val ENABLED = booleanPreferencesKey("cal_enabled")
        val OWNER = stringPreferencesKey("cal_owner")
        val CALENDAR = stringPreferencesKey("cal_id")
        val SYNCED = stringPreferencesKey("cal_synced")
        val SKIPPED = stringSetPreferencesKey("cal_skipped")
        val LAST = longPreferencesKey("cal_last")
        val ERROR = stringPreferencesKey("cal_error")
    }
}
