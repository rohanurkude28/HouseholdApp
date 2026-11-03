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
            val found = latLngFromLink(place.mapLink) ?: geocode(listOfNotNull(place.title, place.area).joinToString(", "))
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
