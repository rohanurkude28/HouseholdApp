package com.houseaffairs.app.core.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.houseaffairs.app.core.data.KitchenMonth
import com.houseaffairs.app.core.data.PantryRepository
import com.houseaffairs.app.core.data.SessionManager
import com.houseaffairs.app.core.data.SessionState
import com.houseaffairs.app.core.data.StockHistory
import com.houseaffairs.app.core.data.StockKind
import com.houseaffairs.app.core.data.StockYear
import com.houseaffairs.app.core.data.kitchenMonths
import com.houseaffairs.app.core.data.stockYears
import com.houseaffairs.app.core.data.usageOf
import com.houseaffairs.app.core.designsystem.Accent
import com.houseaffairs.app.core.designsystem.LocalHouseColors
import com.houseaffairs.app.core.model.AppModule
import com.houseaffairs.app.core.model.RolePolicy
import com.houseaffairs.app.core.ui.shownMinor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

/** Two years of months, so last year's whole story is there in January. */
private const val MONTHS = 24

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UsageViewModel @Inject constructor(session: SessionManager, private val pantry: PantryRepository, money: com.houseaffairs.app.core.data.MoneyRepository) : ViewModel() {
    private val ready = session.state.filterIsInstance<SessionState.Ready>()

    private data class Who(val hid: String, val uid: String, val money: Boolean, val medicine: Boolean, val ownMedicines: Boolean)

    private val who = ready.map {
        Who(it.household.id, it.user.uid, it.me.canAccess(AppModule.MONEY), it.me.canAccess(AppModule.MEDICINE), RolePolicy.ownMedicinesOnly(it.me.role))
    }.distinctUntilChanged()

    /** All spending and the budget, for those who can see money; null otherwise. */
    private val moneyState = who.flatMapLatest { w -> if (w.money) money.observeMoney(w.hid, w.uid) else flowOf(null) }

    /** The kind's spending and its budget line: groceries, health (medicines) or home (supplies). */
    private fun spending(kind: StockKind) = moneyState.map { m -> m?.let { it.txns.filter { t -> t.category == kind.category() } to it.budget.categories[kind.category()] } }

    private fun history(kind: StockKind): Flow<StockHistory> = who.flatMapLatest { w ->
        if (kind == StockKind.MEDICINES && !w.medicine) flowOf(StockHistory(emptyList(), emptyList(), emptyList()))
        else pantry.observeHistory(w.hid, w.uid, kind, ownOnly = w.ownMedicines)
    }

    private val histories = StockKind.entries.associateWith { kind ->
        history(kind).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StockHistory(emptyList(), emptyList(), emptyList()))
    }

    private fun months(kind: StockKind): StateFlow<List<KitchenMonth>> =
        combine(histories.getValue(kind), spending(kind)) { h, g -> h to g }
            .map { (h, g) -> kitchenMonths(h.bought, h.lives, h.wasted, g?.first, LocalDate.now(), groceryBudgetMinor = g?.second, months = MONTHS) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val byKind = StockKind.entries.associateWith { months(it) }

    /** Newest month first; this month is always there. */
    fun monthsOf(kind: StockKind): StateFlow<List<KitchenMonth>> = byKind.getValue(kind)

    /** Bought, finished and wasted, for "how long things last". */
    fun historyOf(kind: StockKind): StateFlow<StockHistory> = histories.getValue(kind)

    /** "Record waste" from the month view: part (or all) of something in stock went off. */
    fun wastePart(kind: StockKind, stock: com.houseaffairs.app.core.data.PantryStock, share: Int) {
        viewModelScope.launch {
            val w = who.first()
            val from = if (kind == StockKind.MEDICINES) "medicines" else "stock"
            // Same choices as from the list: add to the buy list, or put it back.
            val undo = runCatching {
                if (share >= 100) pantry.usedUp(w.hid, w.uid, stock, emptyList(), from, thrown = true, addToList = false).let { added -> { pantry.putBack(w.hid, stock, from, added) } }
                else pantry.wastePart(w.hid, w.uid, stock, emptyList(), share, from)
            }.getOrNull() ?: return@launch
            com.houseaffairs.app.core.ui.FollowUps.show(
                com.houseaffairs.app.core.data.wasteFollowUp(stock.title, share, onList = false, add = { pantry.addToShopping(w.hid, w.uid, stock.title) }, putBack = undo),
            )
        }
    }

    val currency: StateFlow<String> = ready.map { it.household.baseCurrency }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "INR")
}

/** This month for [kind], fed by [UsageViewModel]; the card itself is shared (`StockMonthView`). */
@Composable
fun StockMonthCard(kind: StockKind, onOpen: () -> Unit, modifier: Modifier = Modifier, viewModel: UsageViewModel = hiltViewModel()) {
    val months by viewModel.monthsOf(kind).collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    StockMonthView(kind, months, currency, onOpen, modifier)
}

/** The year for [kind] (shared `StockYearView`), with waste recorded through [UsageViewModel]. */
@Composable
fun StockYearSheet(kind: StockKind, onDismiss: () -> Unit, viewModel: UsageViewModel = hiltViewModel()) {
    val months by viewModel.monthsOf(kind).collectAsStateWithLifecycle()
    val history by viewModel.historyOf(kind).collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    StockYearView(kind, months, history, currency, onWaste = { item, share -> viewModel.wastePart(kind, item, share) }, onDismiss = onDismiss)
}
