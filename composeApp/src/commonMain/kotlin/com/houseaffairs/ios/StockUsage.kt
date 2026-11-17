package com.houseaffairs.ios

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.houseaffairs.app.core.data.MoneyRepository
import com.houseaffairs.app.core.data.PantryRepository
import com.houseaffairs.app.core.data.PantryStock
import com.houseaffairs.app.core.data.StockHistory
import com.houseaffairs.app.core.data.StockKind
import com.houseaffairs.app.core.data.kitchenMonths
import com.houseaffairs.app.core.model.AppModule
import com.houseaffairs.app.core.model.RolePolicy
import com.houseaffairs.app.core.time.systemToday
import com.houseaffairs.app.core.ui.StockMonthView
import com.houseaffairs.app.core.ui.StockYearView
import com.houseaffairs.app.core.ui.category
import kotlinx.coroutines.flow.flowOf

private val usagePantry = PantryRepository()
private val usageMoney = MoneyRepository()

/** Two years of months, so last year's whole story is there in January (as Android's). */
private const val MONTHS = 24

/**
 * This month in the kitchen, medicines or supplies, opening the year: the same card and sheet as
 * Android's (`:ui` StockUsage.kt), fed as Android's `UsageViewModel` feeds them.
 */
@Composable
internal fun StockUsage(s: SessionState.Ready, kind: StockKind, onWaste: (PantryStock, Int) -> Unit) {
    val hid = s.household.id
    val uid = s.uid
    val none = StockHistory(emptyList(), emptyList(), emptyList())
    val history by remember(hid, uid, kind, s.me) {
        if (kind == StockKind.MEDICINES && !s.me.canAccess(AppModule.MEDICINE)) flowOf(none)
        else usagePantry.observeHistory(hid, uid, kind, ownOnly = RolePolicy.ownMedicinesOnly(s.me.role))
    }.collectAsState(none)
    // Spending shows only for those who can see money.
    val money by remember(hid, uid, s.me) { if (s.me.canAccess(AppModule.MONEY)) usageMoney.observeMoney(hid, uid) else flowOf(null) }.collectAsState(null)
    val months = remember(history, money) {
        val m = money
        kitchenMonths(
            history.bought, history.lives, history.wasted,
            m?.txns?.filter { it.category == kind.category() }, systemToday(),
            groceryBudgetMinor = m?.budget?.categories?.get(kind.category()), months = MONTHS,
        )
    }
    var open by remember { mutableStateOf(false) }
    StockMonthView(kind, months, s.household.baseCurrency, onOpen = { open = true }, modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp))
    if (open) StockYearView(kind, months, history, s.household.baseCurrency, onWaste = onWaste, onDismiss = { open = false })
}
