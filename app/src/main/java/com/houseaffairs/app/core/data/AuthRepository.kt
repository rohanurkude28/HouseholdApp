package com.houseaffairs.app.core.data

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    @ApplicationContext private val appContext: Context,
) {
    val currentUser: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /** Google is the only sign-in method. [activityContext] must be an Activity for the account picker. */
    suspend fun signInWithGoogle(activityContext: Context): Result<Unit> = runCatching {
        // default_web_client_id comes from google-services.json once Google sign-in is enabled in the
        // Firebase project. Looked up by name so the app still builds (and the demo works) before that.
        val id = appContext.resources.getIdentifier("default_web_client_id", "string", appContext.packageName)
        check(id != 0) { "Google sign-in isn't set up for this app yet. Try the demo instead." }
        val option = GetSignInWithGoogleOption.Builder(appContext.getString(id)).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        require(
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
        ) { "Unexpected credential type" }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        Unit
    }

    /**
     * Local build only: the Auth emulator accepts an unsigned JSON "Google ID token",
     * so we can sign in as several family members without real accounts.
     */
    suspend fun signInWithTestAccount(account: TestAccount): Result<Unit> = runCatching {
        val fakeIdToken = JSONObject()
            .put("sub", account.id)
            .put("email", account.email)
            .put("name", account.name)
            .put("email_verified", true)
            .toString()
        auth.signInWithCredential(GoogleAuthProvider.getCredential(fakeIdToken, null)).await()
        Unit
    }

    /**
     * The demo: signs in without a Google account (Firebase anonymous sign-in), named "Demo".
     * Signing out ends it; the demo home isn't reachable again from another device.
     */
    suspend fun signInDemo(): Result<Unit> = runCatching {
        val user = auth.signInAnonymously().await().user ?: error("Demo sign-in failed")
        user.updateProfile(com.google.firebase.auth.UserProfileChangeRequest.Builder().setDisplayName("Demo").build()).await()
        Unit
    }

    suspend fun signOut() {
        auth.signOut()
        runCatching { CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest()) }
    }
}

data class TestAccount(val id: String, val name: String, val email: String)

val TEST_ACCOUNTS = listOf(
    TestAccount("test-asha", "Asha Test", "asha@example.com"),
    TestAccount("test-ravi", "Ravi Test", "ravi@example.com"),
    TestAccount("test-mia", "Mia Test", "mia@example.com"),
)
