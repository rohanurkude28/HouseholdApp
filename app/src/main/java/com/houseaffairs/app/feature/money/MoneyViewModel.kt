package com.houseaffairs.app.feature.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.houseaffairs.app.core.data.Budget
import com.houseaffairs.app.core.data.BudgetSummary
import com.houseaffairs.app.core.data.MoneyRepository
import com.houseaffairs.app.core.data.MoneyState
import com.houseaffairs.app.core.data.Pocket
import com.houseaffairs.app.core.util.formatMinor
import com.houseaffairs.app.feature.add.SavedEntry
import com.houseaffairs.app.core.data.SessionManager
import com.houseaffairs.app.core.data.SessionState
import com.houseaffairs.app.core.data.SettingsRepository
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

data class MoneyUiState(
    val loading: Boolean = true,
    val budget: Budget? = null,
    val summary: BudgetSummary? = null,
    /** Envelopes with rollover, gullak, credits and dreams. */
    val money: MoneyState? = null,
    val currency: String = "USD",
    val hideAmounts: Boolean = false,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MoneyViewModel @Inject constructor(
    private val session: SessionManager,
    private val money: MoneyRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val ready = session.state.filterIsInstance<SessionState.Ready>()
    private val ids = ready.map { it.household.id to it.user.uid }.distinctUntilChanged()
    private val error = MutableStateFlow<String?>(null)

    private val budgetAndSummary = ids.flatMapLatest { (householdId, uid) ->
        combine(money.observeBudget(householdId), money.observeMoney(householdId, uid)) { b, m -> b to m }
    }

    val state: StateFlow<MoneyUiState> = combine(ready, budgetAndSummary, settings.hideAmounts, error) { r, (budget, m), hide, err ->
        MoneyUiState(
            loading = false,
            budget = budget,
            summary = m?.summary,
            money = m,
            currency = budget?.currency ?: r.household.baseCurrency,
            hideAmounts = hide,
            error = err,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoneyUiState())

    /** How many expenses use [name]: decides whether deleting it needs to ask about them. */
    fun categoryUse(name: String, onCount: (Int) -> Unit) {
        val r = session.state.value as? SessionState.Ready ?: return
        viewModelScope.launch { onCount(runCatching { money.categoryUse(r.household.id, r.user.uid, name) }.getOrDefault(0)) }
    }

    /** Renames [from] (or with [to] null, removes it; its expenses move to Other). */
    fun moveCategory(from: String, to: String?, onDone: () -> Unit) {
        val r = session.state.value as? SessionState.Ready ?: return
        viewModelScope.launch {
            runCatching {
                if (to == null && money.categoryUse(r.household.id, r.user.uid, from) > 0) money.moveCategory(r.household.id, r.user.uid, from, "Other")
                money.moveCategory(r.household.id, r.user.uid, from, to)
            }.onSuccess { onDone() }.onFailure { error.value = "Couldn't change the category. Check your connection and try again." }
        }
    }

    /** Deletes [name] and its expenses; Undo brings the expenses back. */
    fun deleteCategoryWithHistory(name: String, onSaved: (com.houseaffairs.app.feature.add.SavedEntry) -> Unit, onDone: () -> Unit) {
        val r = session.state.value as? SessionState.Ready ?: return
        viewModelScope.launch {
            runCatching { money.deleteCategoryWithHistory(r.household.id, r.user.uid, name) }
                .onSuccess { undo -> onDone(); onSaved(com.houseaffairs.app.feature.add.SavedEntry("$name and its expenses deleted") { undo() }) }
                .onFailure { error.value = "Couldn't delete the category. Check your connection and try again." }
        }
    }

    fun saveBudget(budget: Budget, from: com.houseaffairs.app.core.data.LimitFrom = com.houseaffairs.app.core.data.LimitFrom.THIS_MONTH, onDone: () -> Unit) {
        val r = session.state.value as? SessionState.Ready ?: return
        viewModelScope.launch {
            runCatching { money.saveBudget(r.household.id, r.user.uid, budget, from) }
                .onSuccess { onDone() }
                .onFailure { error.value = "Couldn't save the budget. Check your connection and try again." }
        }
    }

    fun markPaid(txnId: String) {
        (session.state.value as? SessionState.Ready)?.let { money.markPaid(it.household.id, txnId) }
    }

    fun move(from: Pocket, fromLabel: String, to: Pocket, toLabel: String, amountMinor: Long): SavedEntry? {
        val r = session.state.value as? SessionState.Ready ?: return null
        val ref = money.transfer(r.household.id, r.user.uid, from, to, amountMinor)
        return SavedEntry("Moved ${formatMinor(amountMinor, state.value.currency)} · $fromLabel → $toLabel") { ref.delete() }
    }

    /** Any ledger entry: add money, withdraw, interest (from Growth) or a value change. */
    fun record(from: Pocket, to: Pocket, amountMinor: Long, note: String): SavedEntry? {
        val r = session.state.value as? SessionState.Ready ?: return null
        val ref = money.transfer(r.household.id, r.user.uid, from, to, amountMinor, note)
        return SavedEntry(note) { ref.delete() }
    }

    fun closeInvestment(id: String) {
        (session.state.value as? SessionState.Ready)?.let { money.closeInvestment(it.household.id, id) }
    }

    /** Takes back a move (deletes its ledger entry); Undo puts it back. */
    fun undoMove(t: com.houseaffairs.app.core.data.Transfer): SavedEntry? {
        val r = session.state.value as? SessionState.Ready ?: return null
        val restore = money.deleteTransfer(r.household.id, t.id)
        return SavedEntry("Move of ${formatMinor(t.amountMinor, state.value.currency)} taken back") { restore() }
    }


    fun creditUsed(id: String) {
        (session.state.value as? SessionState.Ready)?.let { money.markCreditUsed(it.household.id, id) }
    }

    fun toggleHide() {
        viewModelScope.launch { settings.setHideAmounts(!state.value.hideAmounts) }
    }

    fun dismissError() {
        error.value = null
    }
}
