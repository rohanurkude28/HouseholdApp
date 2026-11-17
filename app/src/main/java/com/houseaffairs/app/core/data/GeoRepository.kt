package com.houseaffairs.app.core.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Turns names into coordinates (on-device Geocoder) and reads the phone's location. */
@Singleton
class GeoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: FirebaseFirestore,
) {
    private val cache = mutableMapOf<String, LatLng?>()

    val hasLocationPermission: Boolean
        get() = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * Full addresses matching what's typed ("indiranagar" → "Indiranagar, Bengaluru, Karnataka"),
     * nearest to the phone first when its location is known. A full address finds the right
     * place later, so "near me" works.
     */
    suspend fun suggest(query: String): List<String> {
        if (query.trim().length < 3 || !Geocoder.isPresent()) return emptyList()
        val here = if (hasLocationPermission) runCatching { currentLocation() }.getOrNull() else null
        val found: List<android.location.Address> = withTimeoutOrNull(6_000) {
            val geocoder = Geocoder(context)
            // About 60 km around the phone, so "MG Road" means the one in your city.
            val box = here?.let { doubleArrayOf(it.lat - 0.5, it.lng - 0.5, it.lat + 0.5, it.lng + 0.5) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    val listener = object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<android.location.Address>) = cont.resume(addresses.toList())
                        override fun onError(errorMessage: String?) = cont.resume(emptyList())
                    }
                    if (box != null) geocoder.getFromLocationName(query, 5, box[0], box[1], box[2], box[3], listener)
                    else geocoder.getFromLocationName(query, 5, listener)
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    runCatching {
                        (if (box != null) geocoder.getFromLocationName(query, 5, box[0], box[1], box[2], box[3]) else geocoder.getFromLocationName(query, 5)).orEmpty()
                    }.getOrDefault(emptyList())
                }
            }
        }.orEmpty()
        return found.mapNotNull { a ->
            (0..a.maxAddressLineIndex).mapNotNull { a.getAddressLine(it) }.joinToString(", ").ifBlank { null }
                ?.also { line -> cache[line.trim().lowercase()] = LatLng(a.latitude, a.longitude) }
        }.distinct()
    }

    /** "Indiranagar" or "Toit, Indiranagar" → coordinates; null offline or when unknown. */
    suspend fun geocode(query: String): LatLng? {
        val key = query.trim().lowercase()
        if (key.isEmpty() || !Geocoder.isPresent()) return null
        cache[key]?.let { return it }
        val result = withTimeoutOrNull(8_000) {
            val geocoder = Geocoder(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocationName(query, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<android.location.Address>) {
                            cont.resume(addresses.firstOrNull()?.let { LatLng(it.latitude, it.longitude) })
                        }
                        override fun onError(errorMessage: String?) = cont.resume(null)
                    })
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    runCatching { geocoder.getFromLocationName(query, 1)?.firstOrNull()?.let { LatLng(it.latitude, it.longitude) } }.getOrNull()
                }
            }
        }
        if (result != null) cache[key] = result
        return result
    }

    /** The phone's current (or last known) position, if location is allowed. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): LatLng? {
        if (!hasLocationPermission) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            for (provider in providers) {
                val fix = withTimeoutOrNull(6_000) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        lm.getCurrentLocation(provider, null, ContextCompat.getMainExecutor(context)) { cont.resume(it) }
                    }
                }
                if (fix != null) return LatLng(fix.latitude, fix.longitude)
            }
        }
        return providers.firstNotNullOfOrNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }?.let { LatLng(it.latitude, it.longitude) }
    }

    /** Gives places without coordinates some, from their Maps link or name + area, and saves them. */
    suspend fun fillCoordinates(householdId: String, places: List<PlaceItem>): List<PlaceItem> =
        places.map { place ->
            if (place.lat != null && place.lng != null) return@map place
            // A picked full address ("…, Bengaluru, Karnataka") is the surest; then name and area together.
            val found = latLngFromLink(place.mapLink) ?: place.area?.takeIf { ',' in it }?.let { geocode(it) }
                ?: geocode(listOfNotNull(place.title, place.area).joinToString(", "))
                ?: place.area?.let { geocode(it) }
            if (found == null) {
                place
            } else {
                db.collection("households").document(householdId).collection("places").document(place.id)
                    .update(mapOf("lat" to found.lat, "lng" to found.lng))
                place.copy(lat = found.lat, lng = found.lng)
            }
        }
}
