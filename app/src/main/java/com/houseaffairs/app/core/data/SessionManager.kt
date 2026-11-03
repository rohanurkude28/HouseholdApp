package com.houseaffairs.app.core.data

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import com.houseaffairs.app.core.model.Household
import com.houseaffairs.app.core.model.Member
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class NeedsHousehold(val user: FirebaseUser) : SessionState
    data class Ready(val user: FirebaseUser, val household: Household, val me: Member) : SessionState
}

/** Single source of truth for who is signed in, which household they're in and what they may see. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SessionManager @Inject constructor(
    auth: AuthRepository,
    households: HouseholdRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val state: StateFlow<SessionState> = auth.currentUser
        .flatMapLatest { user ->
            if (user == null) return@flatMapLatest flowOf(SessionState.SignedOut)
            households.observeHouseholdId(user.uid)
                .flatMapLatest { householdId ->
                    if (householdId == null) {
                        flowOf(SessionState.NeedsHousehold(user))
                    } else {
                        combine(
                            households.observeHousehold(householdId),
                            households.observeMember(householdId, user.uid),
                        ) { household, me ->
                            if (household != null && me != null) {
                                SessionState.Ready(user, household, me)
                            } else {
                                SessionState.NeedsHousehold(user)
                            }
                        }
                            // Right after creating or joining, the listener can reach the server before
                            // the membership write commits, and rules deny it. Give the write a moment.
                            .retryWhen { cause, attempt ->
                                val denied = cause is FirebaseFirestoreException &&
                                    cause.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                                (denied && attempt < 5).also { if (it) delay(300L * (attempt + 1)) }
                            }
                            // Still denied, e.g. removed from the household: offer setup again.
                            .catch { emit(SessionState.NeedsHousehold(user)) }
                    }
                }
        }
        .stateIn(scope, SharingStarted.Eagerly, SessionState.Loading)
}
