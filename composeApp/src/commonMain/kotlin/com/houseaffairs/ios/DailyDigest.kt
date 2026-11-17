package com.houseaffairs.ios

import com.houseaffairs.app.core.data.DigestPrefs
import com.houseaffairs.app.core.data.HomeData
import com.houseaffairs.app.core.data.MoneyState
import com.houseaffairs.app.core.data.PlannedReminder
import com.houseaffairs.app.core.data.buildDigest
import com.houseaffairs.app.core.model.AppModule
import com.houseaffairs.app.core.time.LocalDate
import com.houseaffairs.app.core.time.plusDays

/**
 * The daily digest, as Android's DigestWorker sends it: one notification at the chosen hour with
 * what's due, expiring or over budget, from the shared `buildDigest`. iOS can't wake the app at
 * an hour, so the next one is planned with the reminders whenever the app has fresh data.
 */
internal data class DigestSetting(val on: Boolean, val hour: Int)

internal fun digestSetting() = DigestSetting(loadPref("digestOn") != "no", loadPref("digestHour")?.toIntOrNull() ?: 8)

internal fun setDigest(setting: DigestSetting) {
    savePref("digestOn", if (setting.on) "yes" else "no")
    savePref("digestHour", setting.hour.toString())
}

/** The next digest: today's if its hour hasn't come yet, else tomorrow's. Null when off or nothing needs anyone. */
internal fun digestReminder(s: SessionState.Ready, data: HomeData, money: MoneyState?, today: LocalDate, nowMinute: Int): PlannedReminder? {
    val setting = digestSetting()
    if (!setting.on) return null
    val day = if (nowMinute < setting.hour * 60) today else today.plusDays(1L)
    // Only the areas this person's role can see, as Android narrows them.
    val areas = DigestPrefs(
        money = s.me.canAccess(AppModule.MONEY) && s.uid !in s.household.hideCostsFrom,
        kitchen = s.me.canAccess(AppModule.STOCK),
        health = s.me.canAccess(AppModule.MEDICINE),
        plans = s.me.canAccess(AppModule.PLANS),
    )
    val currency = money?.budget?.currency ?: s.household.baseCurrency
    val d = buildDigest(data, money?.summary, money, areas, day) { formatMinor(it, currency) } ?: return null
    val body = (d.lines + listOfNotNull(d.more.takeIf { it > 0 }?.let { "+$it more in the app" })).joinToString("\n")
    return PlannedReminder("digest:$day", d.title, body, day, setting.hour * 60)
}
