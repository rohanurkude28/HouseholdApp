package com.houseaffairs.ios

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText

// One client for the app's small web lookups (Darwin's engine on iPhone).
private val web by lazy { HttpClient() }

/** A page as text, saying which app is asking (Wikipedia turns away requests that don't). */
internal suspend fun fetchText(url: String): String = web.get(url) { header("User-Agent", "HouseAffairs/0.1 (family planner)") }.bodyAsText()

/** The shared Wikipedia lookup for a new trip stop. */
internal suspend fun lookUpStop(name: String, near: String): com.houseaffairs.app.core.data.PlaceInfo? =
    com.houseaffairs.app.core.data.lookUpPlace(name, near, ::fetchText)
