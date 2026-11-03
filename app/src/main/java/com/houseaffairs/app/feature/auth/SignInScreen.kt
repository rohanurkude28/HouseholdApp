package com.houseaffairs.app.feature.auth

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import com.houseaffairs.app.core.designsystem.LightStatusBarIcons
import com.houseaffairs.app.core.ui.DayPart
import com.houseaffairs.app.core.ui.HeroBackground
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.houseaffairs.app.BuildConfig
import com.houseaffairs.app.R
import com.houseaffairs.app.core.data.AuthRepository
import com.houseaffairs.app.core.data.TEST_ACCOUNTS
import com.houseaffairs.app.core.data.TestAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignInUiState(val loading: Boolean = false, val error: String? = null)

@HiltViewModel
class SignInViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(SignInUiState())
    val state = _state.asStateFlow()

    fun signIn(activityContext: Context) {
        viewModelScope.launch {
            _state.value = SignInUiState(loading = true)
            val error = auth.signInWithGoogle(activityContext).exceptionOrNull()
            // On success SessionManager moves the app on; this screen just goes away.
            _state.value = SignInUiState(error = error?.let(::messageFor))
        }
    }

    fun signInAsTestAccount(account: TestAccount) {
        viewModelScope.launch {
            _state.value = SignInUiState(loading = true)
            val error = auth.signInWithTestAccount(account).exceptionOrNull()
            _state.value = SignInUiState(error = error?.let(::messageFor))
        }
    }

    private fun messageFor(error: Throwable): String? = when (error) {
        is GetCredentialCancellationException -> null
        is NoCredentialException -> "No Google account on this device. Add one in phone Settings and try again."
        else -> error.message ?: "Sign-in didn't work. Please try again."
    }
}

@Composable
fun SignInScreen(viewModel: SignInViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LightStatusBarIcons()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HeroBackground(
            dayPart = DayPart.now(),
            groundColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            Column(
                Modifier
                    .align(Alignment.CenterStart)
                    .safeDrawingPadding()
                    .padding(horizontal = 28.dp),
            ) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Money, groceries, plans and paperwork for the whole family. In one calm place.",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (BuildConfig.USE_FIREBASE_EMULATORS) {
                Text("Local test mode", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                TEST_ACCOUNTS.forEach { account ->
                    OutlinedButton(
                        onClick = { viewModel.signInAsTestAccount(account) },
                        enabled = !state.loading,
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Continue as ${account.name}") }
                }
            } else {
                Button(
                    onClick = { viewModel.signIn(context) },
                    enabled = !state.loading,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Continue with Google", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Text(
                    "No passwords. Your family's data stays private to your home.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
