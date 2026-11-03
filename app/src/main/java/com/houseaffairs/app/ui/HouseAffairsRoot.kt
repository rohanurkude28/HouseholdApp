package com.houseaffairs.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.houseaffairs.app.core.data.SessionState
import com.houseaffairs.app.core.designsystem.HouseAffairsTheme
import com.houseaffairs.app.feature.auth.SignInScreen
import com.houseaffairs.app.feature.household.HouseholdSetupScreen

@Composable
fun HouseAffairsRoot(viewModel: MainViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

    HouseAffairsTheme(themeMode) {
        Surface(Modifier.fillMaxSize()) {
            when (val state = session) {
                SessionState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                SessionState.SignedOut -> SignInScreen()
                is SessionState.NeedsHousehold -> HouseholdSetupScreen()
                is SessionState.Ready -> MainScaffold(state)
            }
        }
    }
}
