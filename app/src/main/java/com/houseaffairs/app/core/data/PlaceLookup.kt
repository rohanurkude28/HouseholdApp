package com.houseaffairs.app.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Android's fetch for the shared Wikipedia lookup (`:shared` PlaceLookup.kt). */
suspend fun lookUpPlace(name: String, near: String?): PlaceInfo? = withContext(Dispatchers.IO) { lookUpPlace(name, near, ::get) }

private fun get(url: String): String {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 6000
    c.readTimeout = 6000
    c.setRequestProperty("User-Agent", "HouseAffairs/0.1 (family planner)")
    return c.inputStream.bufferedReader().use { it.readText() }
}
