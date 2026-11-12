package com.houseaffairs.ios

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UIKit.UIViewController
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Implemented in Swift with Google's GoogleSignIn SDK, which needs a view controller to present
 * from. Calls back once: tokens on success, all null when closed, or an error message.
 */
interface GoogleSignInProvider {
    fun signIn(onResult: (idToken: String?, accessToken: String?, error: String?) -> Unit)
}

/** The Swift app hosts this; FirebaseApp.configure() runs first in the app's init. */
fun MainViewController(google: GoogleSignInProvider): UIViewController = ComposeUIViewController {
    App(googleSignIn = {
        suspendCancellableCoroutine { done ->
            google.signIn { idToken, accessToken, error ->
                when {
                    idToken != null -> done.resume(GoogleTokens(idToken, accessToken))
                    error.isNullOrEmpty() -> done.resume(null)
                    else -> done.resumeWithException(IllegalStateException(error))
                }
            }
        }
    })
}
