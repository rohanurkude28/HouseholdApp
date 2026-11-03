package com.houseaffairs.app.feature.household

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.graphics.Color
import com.houseaffairs.app.core.designsystem.LightStatusBarIcons
import com.houseaffairs.app.core.designsystem.LocalHouseColors
import com.houseaffairs.app.core.ui.DayPart
import com.houseaffairs.app.core.ui.HaCard
import com.houseaffairs.app.core.ui.HeroBackground
import com.houseaffairs.app.core.ui.IconBlob
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.houseaffairs.app.core.data.AuthRepository
import com.houseaffairs.app.core.data.HouseholdRepository
import com.houseaffairs.app.core.ui.CurrencyPickerDialog
import com.houseaffairs.app.core.util.currencyLabel
import com.houseaffairs.app.core.util.defaultCurrencyCode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HouseholdSetupUiState(
    val name: String = "",
    val currency: String = defaultCurrencyCode(),
    val code: String = "",
    val busy: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class HouseholdSetupViewModel @Inject constructor(
    private val households: HouseholdRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HouseholdSetupUiState())
    val state = _state.asStateFlow()

    fun onNameChange(name: String) = _state.update { it.copy(name = name, error = null) }
    fun onCurrencyChange(currency: String) = _state.update { it.copy(currency = currency) }
    fun onCodeChange(code: String) =
        _state.update { it.copy(code = code.uppercase().filter(Char::isLetterOrDigit).take(6), error = null) }

    fun create() = runBusy {
        val s = _state.value
        households.createHousehold(s.name.ifBlank { "Our Home" }, s.currency)
    }

    fun join() = runBusy { households.joinWithCode(_state.value.code) }

    fun signOut() {
        viewModelScope.launch { auth.signOut() }
    }

    private fun runBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            val error = runCatching { block() }.exceptionOrNull()
            _state.update { it.copy(busy = false, error = error?.message) }
        }
    }
}

@Composable
fun HouseholdSetupScreen(viewModel: HouseholdSetupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCurrencyPicker by remember { mutableStateOf(false) }
    LightStatusBarIcons()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .imePadding(),
    ) {
        HeroBackground(
            dayPart = DayPart.now(),
            groundColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth().height(260.dp),
        ) {
            Column(Modifier.statusBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp)) {
                Text("Welcome home", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Start a new home and invite your family, or join one with a code.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
        }

        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            HaCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBlob(Icons.Outlined.Home, LocalHouseColors.current.plans)
                    Spacer(Modifier.width(14.dp))
                    Text("Start a new home", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Home name") },
                    placeholder = { Text("Our Home") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Currency", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(currencyLabel(state.currency), style = MaterialTheme.typography.bodyLarge)
                    }
                    TextButton(onClick = { showCurrencyPicker = true }) { Text("Change") }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = viewModel::create,
                    enabled = !state.busy,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Create home", style = MaterialTheme.typography.titleMedium) }
            }

            HaCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBlob(Icons.Outlined.Key, LocalHouseColors.current.vault)
                    Spacer(Modifier.width(14.dp))
                    Text("Join with a code", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = state.code,
                    onValueChange = viewModel::onCodeChange,
                    label = { Text("6-character code") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    textStyle = MaterialTheme.typography.titleLarge,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = viewModel::join,
                    enabled = !state.busy && state.code.length == 6,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Join home", style = MaterialTheme.typography.titleMedium) }
            }

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            TextButton(onClick = viewModel::signOut, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp)) {
                Text("Use a different account")
            }
        }
    }

    if (showCurrencyPicker) {
        CurrencyPickerDialog(
            selected = state.currency,
            onSelect = {
                viewModel.onCurrencyChange(it)
                showCurrencyPicker = false
            },
            onDismiss = { showCurrencyPicker = false },
        )
    }
}
