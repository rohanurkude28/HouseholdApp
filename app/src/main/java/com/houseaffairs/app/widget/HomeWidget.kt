package com.houseaffairs.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.houseaffairs.app.MainActivity
import com.houseaffairs.app.R
import com.houseaffairs.app.core.data.HomeRepository
import com.houseaffairs.app.core.data.SessionManager
import com.houseaffairs.app.core.data.SessionState
import com.houseaffairs.app.core.data.WatchRepository
import com.houseaffairs.app.core.data.WeeklyRepository
import com.houseaffairs.app.core.data.WidgetCard
import com.houseaffairs.app.core.data.widgetCards
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** A widget card tapped: the tab to open ("plans/TASKS"), picked up by the main screen. */
object WidgetInbox {
    val route = MutableStateFlow<String?>(null)
    const val EXTRA_ROUTE = "widget_route"
}

/**
 * The home-screen widget: a few cards that take turns (tasks, the next birthday, live-well,
 * something to watch). The cards are worked out in the background and kept on the phone, so the
 * widget draws instantly and keeps rotating without waking the app.
 */
object HomeWidget {
    private const val PREFS = "home_widget"
    private const val KEY = "cards"
    private const val WORK = "home_widget"

    fun save(context: Context, cards: List<WidgetCard>) {
        val json = JSONArray(cards.map { JSONObject().put("e", it.emoji).put("t", it.title).put("l", it.line).put("r", it.route) })
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json.toString()).apply()
        render(context)
    }

    private fun load(context: Context): List<WidgetCard> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return listOf(WidgetCard("🏠", "HouseAffairs", "Open the app once to fill this in", "home"))
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map { WidgetCard(it.getString("e"), it.getString("t"), it.getString("l"), it.getString("r")) }
    }

    /** Draws every placed widget from the saved cards. */
    fun render(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, HomeWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val cards = load(context)
        ids.forEach { id ->
            val root = RemoteViews(context.packageName, R.layout.widget_rotating)
            root.removeAllViews(R.id.widget_flipper)
            cards.forEachIndexed { i, card ->
                val v = RemoteViews(context.packageName, R.layout.widget_card)
                v.setTextViewText(R.id.card_emoji, card.emoji)
                v.setTextViewText(R.id.card_title, card.title)
                v.setTextViewText(R.id.card_line, card.line)
                v.setOnClickPendingIntent(R.id.card, open(context, card.route, id * 100 + i))
                root.addView(R.id.widget_flipper, v)
            }
            manager.updateAppWidget(id, root)
        }
    }

    private fun open(context: Context, route: String, code: Int): PendingIntent = PendingIntent.getActivity(
        context, code,
        Intent(context, MainActivity::class.java).putExtra(WidgetInbox.EXTRA_ROUTE, route).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Fresh cards soon (after a change in the app); several changes in a row make one refresh. */
    fun refreshSoon(context: Context) {
        if (!placed(context)) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            "${WORK}_now", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<HomeWidgetWorker>().setInitialDelay(5, TimeUnit.SECONDS).build(),
        )
    }

    /** Every hour while a widget is placed, so "today" and the picks stay current. */
    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<HomeWidgetWorker>(1, TimeUnit.HOURS).build())
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }

    fun placed(context: Context): Boolean =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, HomeWidgetProvider::class.java)).isNotEmpty()

    /** Asks the launcher to place the widget; false when it can't, so the app explains how instead. */
    fun requestPin(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        return manager.requestPinAppWidget(ComponentName(context, HomeWidgetProvider::class.java), null, null)
    }
}

class HomeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        HomeWidget.render(context)
        HomeWidget.schedule(context)
        HomeWidget.refreshSoon(context)
    }

    override fun onDisabled(context: Context) {
        HomeWidget.cancel(context)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun session(): SessionManager
    fun home(): HomeRepository
    fun weekly(): WeeklyRepository
    fun watch(): WatchRepository
}

/** Works out the cards from the same data Home shows and redraws the widget. */
class HomeWidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)
        val ready = withTimeoutOrNull(20_000) { deps.session().state.first { it is SessionState.Ready || it == SessionState.SignedOut } }
        if (ready !is SessionState.Ready) {
            HomeWidget.save(applicationContext, listOf(WidgetCard("🏠", "HouseAffairs", "Sign in to see your day here", "home")))
            return Result.success()
        }
        val data = withTimeoutOrNull(20_000) { deps.home().observe(ready.household.id, ready.me, ready.household.baseCurrency).first { it.loaded } } ?: return Result.retry()
        val review = runCatching { deps.weekly().load(ready.household.id, ready.me) }.getOrNull()
        // Lists are Plans; a role without it doesn't see picks from them.
        val media = if (!ready.me.canAccess(com.houseaffairs.app.core.model.AppModule.PLANS)) emptyList()
            else withTimeoutOrNull(10_000) { deps.watch().observe(ready.household.id, ready.user.uid).first() }.orEmpty()
        HomeWidget.save(applicationContext, widgetCards(data, review?.wheel, review?.nudge, media, LocalDate.now()))
        return Result.success()
    }
}
