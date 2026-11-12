package com.houseaffairs.app.feature.money

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.houseaffairs.app.core.data.MoneyRepository
import com.houseaffairs.app.core.data.SessionManager
import com.houseaffairs.app.core.data.SessionState
import com.houseaffairs.app.core.data.Txn
import com.houseaffairs.app.core.designsystem.LocalHouseColors
import com.houseaffairs.app.core.ui.AddRow
import com.houseaffairs.app.core.ui.SectionTitle
import com.houseaffairs.app.core.util.formatMinor
import com.houseaffairs.app.feature.add.formatDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MySpendsViewModel @Inject constructor(session: SessionManager, money: MoneyRepository) : ViewModel() {
    private val ready = session.state.filterIsInstance<SessionState.Ready>()

    val spends: StateFlow<List<Txn>> = ready.map { it.household.id to it.user.uid }.distinctUntilChanged()
        .flatMapLatest { (hid, uid) -> money.observeMine(hid, uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currency: StateFlow<String> = ready.map { it.household.baseCurrency }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "INR")
}

/**
 * Money for kids, teens and helpers without the Money module: add what you spent and see your
 * own entries. The family's budget, totals and breakdown stay with the grown-ups (and the rules).
 */
@Composable
fun MySpendsScreen(onAdd: () -> Unit, onOpen: (String) -> Unit, viewModel: MySpendsViewModel = hiltViewModel()) {
    val spends by viewModel.spends.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    val accent = LocalHouseColors.current.money
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
                Text("My spends", style = MaterialTheme.typography.headlineLarge)
                Text("Add what you spend. The family budget is looked after by the grown-ups.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { AddRow("Add what I spent", accent, onAdd) }
        if (spends.isEmpty()) {
            item { Text("Nothing yet. Say “200 stationery” or tap above.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp)) }
        } else {
            item { SectionTitle("What I added") }
            items(spends, key = { it.id }) { t ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(t.id) }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = MaterialTheme.typography.bodyLarge)
                        Text("${t.category} · ${formatDate(t.date)}" + if (t.planned) " · planned" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(formatMinor(t.amountMinor, currency), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}
