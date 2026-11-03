package com.houseaffairs.app.core.data

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(val lat: Double, val lng: Double)

/** Great-circle distance in kilometres. */
fun distanceKm(a: LatLng, b: LatLng): Double {
    val r = 6371.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLng = Math.toRadians(b.lng - a.lng)
    val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
    return 2 * r * asin(sqrt(h))
}

private val COORDS = Regex("(?:@|[?&](?:q|ll|query|destination)=)(-?\\d{1,2}\\.\\d+),\\s?(-?\\d{1,3}\\.\\d+)")

/** Coordinates inside a Google Maps link ("…/@12.97,77.64,17z" or "?q=12.97,77.64"), if any. */
fun latLngFromLink(link: String?): LatLng? {
    val m = COORDS.find(link ?: return null) ?: return null
    val lat = m.groupValues[1].toDoubleOrNull() ?: return null
    val lng = m.groupValues[2].toDoubleOrNull() ?: return null
    return if (lat in -90.0..90.0 && lng in -180.0..180.0) LatLng(lat, lng) else null
}

/** Kinds that serve the same need: heading to one, the others aren't worth suggesting. */
private val SAME_NEED = listOf(
    setOf("Grocery", "Market"),
    setOf("Restaurant", "Café"),
    setOf("Pharmacy"),
    setOf("Doctor", "Hospital"),
)

fun sameNeed(a: String, b: String): Boolean = a == b || SAME_NEED.any { a in it && b in it }

/** Places the family doesn't want suggested ("👎 Not great"). */
private fun unwanted(place: PlaceItem) = place.rating?.contains("Not great") == true

data class NearbyPlace(val place: PlaceItem, val km: Double?)

/**
 * Saved places worth visiting around [center] (within [radiusKm]) or, for places without
 * coordinates, whose area mentions [areaName]. Leaves out the destination itself, places of the
 * same kind as the destination, and ones rated "not great". Nearest and best-loved first.
 */
fun nearbyPlaces(
    places: List<PlaceItem>,
    center: LatLng?,
    areaName: String?,
    radiusKm: Double,
    destination: PlaceItem? = null,
): List<NearbyPlace> {
    val area = areaName?.lowercase()?.trim()?.takeIf { it.length > 2 }
    return places
        .filter { it.id != destination?.id && !unwanted(it) }
        .filter { destination == null || !sameNeed(it.kind, destination.kind) }
        .mapNotNull { place ->
            val km = if (center != null && place.lat != null && place.lng != null) distanceKm(center, LatLng(place.lat, place.lng)) else null
            when {
                km != null && km <= radiusKm -> NearbyPlace(place, km)
                km == null && area != null && (place.area?.lowercase()?.contains(area) == true || place.title.lowercase().contains(area)) -> NearbyPlace(place, null)
                else -> null
            }
        }
        .sortedWith(
            compareByDescending<NearbyPlace> { it.place.rating?.contains("Love") == true || it.place.rating?.contains("Want") == true }
                .thenBy { it.km ?: 0.0 },
        )
}
