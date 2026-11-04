package com.houseaffairs.app.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Per-device preferences. Household-wide settings (currency, budget cycle) live in Firestore. */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    val quickAddTried: Flow<Boolean> = dataStore.data.map { it[QUICK_ADD_TRIED] == true }

    /** Home section order and visibility, e.g. "ATTENTION:1,GLANCE:1,RECENT:0"; null means the default. */
    val homeLayout: Flow<String?> = dataStore.data.map { it[HOME_LAYOUT] }

    suspend fun setHomeLayout(layout: String) {
        dataStore.edit { it[HOME_LAYOUT] = layout }
    }

    /**
     * List groups the person opened or closed against their default, as "list:category"
     * ("pantry:Spices & masalas"). Only on this phone.
     */
    val toggledGroups: Flow<Set<String>> = dataStore.data.map { it[TOGGLED_GROUPS].orEmpty() }

    suspend fun toggleGroup(key: String) {
        dataStore.edit { prefs ->
            val now = prefs[TOGGLED_GROUPS].orEmpty()
            prefs[TOGGLED_GROUPS] = if (key in now) now - key else now + key
        }
    }

    /** Masks money amounts on screen (for showing the phone to someone). */
    val hideAmounts: Flow<Boolean> = dataStore.data.map { it[HIDE_AMOUNTS] == true }

    suspend fun setHideAmounts(hide: Boolean) {
        dataStore.edit { it[HIDE_AMOUNTS] = hide }
    }

    /**
     * Smart understanding: lines quick add isn't sure about are sent to Gemini. Off until the
     * person turns it on, since the typed line leaves the phone.
     */
    val smartAdd: Flow<Boolean> = dataStore.data.map { it[SMART_ADD] == true }

    suspend fun setSmartAdd(on: Boolean) {
        dataStore.edit { it[SMART_ADD] = on }
    }

    /**
     * Smart suggestions: Gemini ranks what to cook and watch and words the daily live-well tip.
     * Off until turned on; sends dish and show titles and habit scores, never amounts.
     */
    val smartSuggest: Flow<Boolean> = dataStore.data.map { it[SMART_SUGGEST] == true }

    suspend fun setSmartSuggest(on: Boolean) {
        dataStore.edit { it[SMART_SUGGEST] = on }
    }

    /** Daily digest: on/off, hour of day (24h), and which areas it covers. */
    data class NotificationPrefs(val enabled: Boolean = true, val hour: Int = 8, val areas: DigestPrefs = DigestPrefs())

    val notificationPrefs: Flow<NotificationPrefs> = dataStore.data.map { p ->
        val off = p[DIGEST_AREAS_OFF].orEmpty()
        NotificationPrefs(
            enabled = p[DIGEST_ENABLED] != false,
            hour = p[DIGEST_HOUR] ?: 8,
            areas = DigestPrefs(money = "money" !in off, kitchen = "kitchen" !in off, health = "health" !in off, plans = "plans" !in off),
        )
    }

    suspend fun setNotificationPrefs(prefs: NotificationPrefs) {
        dataStore.edit {
            it[DIGEST_ENABLED] = prefs.enabled
            it[DIGEST_HOUR] = prefs.hour
            it[DIGEST_AREAS_OFF] = buildSet {
                if (!prefs.areas.money) add("money")
                if (!prefs.areas.kitchen) add("kitchen")
                if (!prefs.areas.health) add("health")
                if (!prefs.areas.plans) add("plans")
            }
        }
    }

    /** ISO week ("2026-W45") whose weekly review was last opened; drives the story ring. */
    val reviewSeenWeek: Flow<String?> = dataStore.data.map { it[REVIEW_SEEN] }

    suspend fun markReviewSeen(week: String) {
        dataStore.edit { it[REVIEW_SEEN] = week }
    }

    /** The day ("2026-11-03") the live-well nudge was put away; it comes back tomorrow. */
    val nudgeDismissedDay: Flow<String?> = dataStore.data.map { it[NUDGE_DISMISSED] }

    suspend fun dismissNudge(day: String) {
        dataStore.edit { it[NUDGE_DISMISSED] = day }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    /**
     * The first-run tour on this phone: the step to show, or null once it's finished or skipped.
     * Owner's "Reset everything" and Settings → "Show the tour" start it again.
     */
    val tourStep: Flow<Int?> = dataStore.data.map { if (it[TOUR_DONE] == true) null else it[TOUR_STEP] ?: 0 }

    suspend fun setTourStep(step: Int) {
        dataStore.edit { it[TOUR_STEP] = step }
    }

    suspend fun finishTour() {
        dataStore.edit { it[TOUR_DONE] = true }
    }

    suspend fun restartTour() {
        dataStore.edit {
            it[TOUR_DONE] = false
            it[TOUR_STEP] = 0
        }
    }

    suspend fun markQuickAddTried() {
        dataStore.edit { it[QUICK_ADD_TRIED] = true }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val QUICK_ADD_TRIED = booleanPreferencesKey("quick_add_tried")
        val HOME_LAYOUT = stringPreferencesKey("home_layout")
        val HIDE_AMOUNTS = booleanPreferencesKey("hide_amounts")
        val SMART_ADD = booleanPreferencesKey("smart_add")
        val SMART_SUGGEST = booleanPreferencesKey("smart_suggest")
        val TOGGLED_GROUPS = stringSetPreferencesKey("toggled_groups")
        val DIGEST_ENABLED = booleanPreferencesKey("digest_enabled")
        val REVIEW_SEEN = stringPreferencesKey("review_seen_week")
        val NUDGE_DISMISSED = stringPreferencesKey("nudge_dismissed_day")
        val DIGEST_HOUR = intPreferencesKey("digest_hour")
        val DIGEST_AREAS_OFF = stringSetPreferencesKey("digest_areas_off")
        val TOUR_STEP = intPreferencesKey("tour_step")
        val TOUR_DONE = booleanPreferencesKey("tour_done")
    }
}
