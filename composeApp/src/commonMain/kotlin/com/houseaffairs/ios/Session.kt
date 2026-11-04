package com.houseaffairs.ios

import com.houseaffairs.app.core.model.AppModule
import com.houseaffairs.app.core.model.Role
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen

data class HouseholdInfo(val id: String, val name: String, val baseCurrency: String, val createdBy: String)

data class MemberInfo(val uid: String, val displayName: String, val role: Role, val moduleAccess: Map<String, Boolean>) {
    fun canAccess(module: AppModule): Boolean = moduleAccess[module.key] == true
}

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class NeedsHousehold(val uid: String) : SessionState
    data class Ready(val uid: String, val household: HouseholdInfo, val me: MemberInfo) : SessionState
}

/** Who is signed in and which household they're in; mirrors Android's SessionManager. */
@OptIn(ExperimentalCoroutinesApi::class)
fun observeSession(): Flow<SessionState> {
    val db = Firebase.firestore
    return Firebase.auth.authStateChanged.flatMapLatest { user ->
        if (user == null) return@flatMapLatest flowOf(SessionState.SignedOut)
        db.collection("users").document(user.uid).snapshots
            .map { if (it.exists) it.str("householdId") else null }
            .catch { emit(null) }
            .flatMapLatest { hid ->
                if (hid == null) return@flatMapLatest flowOf(SessionState.NeedsHousehold(user.uid))
                val home = db.collection("households").document(hid)
                combine(home.snapshots, home.collection("members").document(user.uid).snapshots) { h, m ->
                    if (!h.exists || !m.exists) return@combine SessionState.NeedsHousehold(user.uid)
                    SessionState.Ready(
                        user.uid,
                        HouseholdInfo(h.id, h.str("name").orEmpty(), h.str("baseCurrency") ?: "USD", h.str("createdBy").orEmpty()),
                        MemberInfo(
                            m.id, m.str("displayName") ?: "Member",
                            m.str("role")?.let { r -> Role.entries.firstOrNull { it.name == r } } ?: Role.ADULT,
                            m.flags("moduleAccess"),
                        ),
                    ) as SessionState
                }
                    // Right after creating a home the listener can beat the membership write; rules deny it briefly.
                    .retryWhen { _, attempt -> (attempt < 5).also { if (it) delay(300L * (attempt + 1)) } }
                    .catch { emit(SessionState.NeedsHousehold(user.uid)) }
            }
    }
}
