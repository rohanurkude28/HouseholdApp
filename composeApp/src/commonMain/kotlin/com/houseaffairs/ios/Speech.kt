package com.houseaffairs.ios

/**
 * Speaking into the + box. The iPhone's is Swift (Apple's Speech framework, `SpeechSwift` in
 * iOSApp.swift): [start] streams what it heard so far to onText, then calls onEnd once, with a
 * message when it couldn't listen. It stops by itself after a short pause, or on [stop].
 */
interface SpeechProvider {
    fun start(onText: (String) -> Unit, onEnd: (String?) -> Unit)
    fun stop()
}

/** Set by the app at start; null where there's no speech (previews). */
var speech: SpeechProvider? = null
