package com.houseaffairs.app.core.calendar

import android.app.PendingIntent
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks Google for access to calendars this app creates (and nothing else). The first time, Google
 * shows a consent screen ([NeedsConsent]); after that a token comes back silently, also in the
 * background.
 */
@Singleton
class CalendarAuth @Inject constructor(@ApplicationContext private val context: Context) {

    sealed interface Result {
        data class Token(val value: String) : Result
        data class NeedsConsent(val intent: PendingIntent) : Result
        data class Failed(val message: String) : Result
    }

    suspend fun authorize(): Result = runCatching {
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()
        val result = Identity.getAuthorizationClient(context).authorize(request).await()
        when {
            result.hasResolution() -> Result.NeedsConsent(result.pendingIntent!!)
            result.accessToken != null -> Result.Token(result.accessToken!!)
            else -> Result.Failed("Google didn't return access")
        }
    }.getOrElse { Result.Failed(it.message ?: "Couldn't reach Google") }

    companion object {
        /** Only calendars this app created: it can't see the person's other calendars. */
        const val SCOPE = "https://www.googleapis.com/auth/calendar.app.created"
    }
}
