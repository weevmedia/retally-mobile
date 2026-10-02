package com.retally.shared.domain.location

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLGeocoder
import platform.CoreLocation.CLPlacemark
import kotlin.coroutines.resume

/**
 * iOS `CLGeocoder` — free, no API key, rate-limited by the OS (fine for a handful
 * of stores). A fresh geocoder per call; the OS serializes them internally.
 */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun geocodeAddress(query: String): LatLng? = suspendCancellableCoroutine { cont ->
    CLGeocoder().geocodeAddressString(query) { placemarks, error ->
        val location = (placemarks?.firstOrNull() as? CLPlacemark)?.location
        if (error != null || location == null) {
            cont.resume(null)
        } else {
            val (lat, lon) = location.coordinate.useContents { latitude to longitude }
            cont.resume(LatLng(lat, lon))
        }
    }
}
