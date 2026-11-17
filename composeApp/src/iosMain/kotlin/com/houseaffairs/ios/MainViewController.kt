package com.houseaffairs.ios

import com.houseaffairs.app.core.data.*
import androidx.compose.ui.window.ComposeUIViewController
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
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

    /**
     * A Google access token with [scope] (Calendar's or Drive's), asking the person to allow it the
     * first time. Calls back once: the token, or null with a message (null message when closed).
     */
    fun accessToken(scope: String, onResult: (token: String?, error: String?) -> Unit)
}

private var connected = false

/** Local testing only: the same emulators Android's local build talks to. Once, before Firestore is used. */
internal fun connectEmulatorsOnce() {
    if (!useEmulators || connected) return
    connected = true
    dev.gitlive.firebase.Firebase.firestore.useEmulator("localhost", 8080)
    dev.gitlive.firebase.Firebase.auth.useEmulator("localhost", 9099)
}

/** The Swift app hosts this; FirebaseApp.configure() runs first in the app's init. */
fun MainViewController(google: GoogleSignInProvider, voice: SpeechProvider, reader: ScanProvider, files: FilePickProvider): UIViewController = ComposeUIViewController {
    speech = voice
    filePicker = files
    googleToken = { scope ->
        suspendCancellableCoroutine { done ->
            google.accessToken(scope) { token, error ->
                if (token != null) done.resume(token) else done.resumeWithException(IllegalStateException(error ?: "Not allowed"))
            }
        }
    }
    scanner = reader
    connectEmulatorsOnce()
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
