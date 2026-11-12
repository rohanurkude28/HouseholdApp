package com.houseaffairs.ios

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.auth.auth

/** Google's tokens for Firebase: the id token, and the access token iOS also hands back. */
data class GoogleTokens(val idToken: String, val accessToken: String?)

/**
 * The platform's Google sign-in sheet (Google's own SDK, in Swift on iOS). Returns null when the
 * person closes it; throws with a readable message when it fails.
 */
typealias GoogleSignInSheet = suspend () -> GoogleTokens?

/** The same Firebase account as on Android, so someone already in a home lands straight in it. */
suspend fun signInWithGoogle(tokens: GoogleTokens) {
    Firebase.auth.signInWithCredential(GoogleAuthProvider.credential(tokens.idToken, tokens.accessToken))
}
