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

/** Android's My spends: the shared list (`:ui` MySpends), fed by Hilt. */
@Composable
fun MySpendsScreen(onAdd: () -> Unit, onOpen: (String) -> Unit, viewModel: MySpendsViewModel = hiltViewModel()) {
    val spends by viewModel.spends.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    MySpendsList(spends, currency, onAdd, onOpen)
}
