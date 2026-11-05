package com.houseaffairs.app.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.houseaffairs.app.core.data.PantryRepository
import com.houseaffairs.app.core.data.SessionManager
import com.houseaffairs.app.core.data.SessionState
import com.houseaffairs.app.core.data.StockLife
import com.houseaffairs.app.core.data.usageOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UsageViewModel @Inject constructor(session: SessionManager, pantry: PantryRepository, money: com.houseaffairs.app.core.data.MoneyRepository) : ViewModel() {
    private val ready = session.state.filterIsInstance<SessionState.Ready>()
    private val ids = ready.map { Triple(it.household.id, it.user.uid, it.me.canAccess(com.houseaffairs.app.core.model.AppModule.MONEY)) }.distinctUntilChanged()

    val lives: StateFlow<List<StockLife>> = ids
        .flatMapLatest { (hid, uid, _) -> pantry.observeLives(hid, uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Items bought, from ticked shopping and groceries added. */
    val bought: StateFlow<List<Pair<String, LocalDate>>> = ids
        .flatMapLatest { (hid, uid, _) -> pantry.observeHistory(hid, uid) }
        .map { (purchases, _) -> purchases.map { it.title to it.date } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Grocery spending, for those who can see money; null otherwise. */
    val groceries: StateFlow<List<com.houseaffairs.app.core.data.Txn>?> = ids
        .flatMapLatest { (hid, uid, canMoney) -> if (canMoney) money.observeMoney(hid, uid).map { m -> m?.txns?.filter { it.category == "Groceries" } } else kotlinx.coroutines.flow.flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val currency: StateFlow<String> = ready.map { it.household.baseCurrency }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "INR")
}

/**
 * How long things last and what ran out each month, from packs bought and marked Finished:
 * "Detergent · lasts about 34 days · about 5 days left".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageSheet(onDismiss: () -> Unit, viewModel: UsageViewModel = hiltViewModel()) {
    val lives by viewModel.lives.collectAsStateWithLifecycle()
    val bought by viewModel.bought.collectAsStateWithLifecycle()
    val groceries by viewModel.groceries.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val kitchen = com.houseaffairs.app.core.data.kitchenMonths(bought, lives, groceries, today)
    val items = usageOf(lives).filter { it.averageDays != null }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Month by month first: what came home, what ran out, what groceries cost.
            Text("Kitchen by month", style = MaterialTheme.typography.headlineSmall)
            if (kitchen.isEmpty()) {
                Text("Tick items off the shopping list or add groceries, and each month's picture builds up here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            kitchen.forEach { k ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row {
                        Text(k.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        k.spentMinor?.takeIf { it > 0 }?.let { Text("${com.houseaffairs.app.core.util.formatMinor(it, currency)} on groceries", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                    }
                    if (k.bought.isNotEmpty()) Text("Bought: " + k.bought.joinToString(" · ") { (t, n) -> if (n > 1) "$t ×$n" else t }, style = MaterialTheme.typography.bodyMedium)
                    if (k.usedUp.isNotEmpty()) Text("Used up: " + k.usedUp.joinToString(" · ") { (t, n) -> if (n > 1) "$t ×$n" else t }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text("How long things last", style = MaterialTheme.typography.titleMedium)
            if (items.isEmpty()) {
                Text("Tap Finished when something runs out. After the first one, you'll see how long each pack lasts and what you use each month.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items.forEach { u ->
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(u.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(
                                "lasts about ${u.averageDays} days",
                                "${u.finished} used",
                                u.daysLeft(today)?.let { if (it == 0) "due to run out" else "about $it days left" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

        }
    }
}
