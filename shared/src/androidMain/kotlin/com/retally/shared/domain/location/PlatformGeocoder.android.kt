package com.retally.shared.domain.location

import android.location.Geocoder
import com.retally.shared.core.Platform
import com.retally.shared.platform.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform
import java.util.Locale

/**
 * Android `Geocoder` (Play-services-backed, free, no API key). Blocking call, so it
 * runs on Dispatchers.IO. Returns null when the device has no geocoder backend.
 */
actual suspend fun geocodeAddress(query: String): LatLng? {
    val platform = KoinPlatform.getKoin().get<Platform>() as? AndroidPlatform ?: return null
    if (!Geocoder.isPresent()) return null
    return withContext(Dispatchers.IO) {
        runCatching {
            @Suppress("DEPRECATION")
            Geocoder(platform.appContext(), Locale.getDefault())
                .getFromLocationName(query, 1)
                ?.firstOrNull()
                ?.let { LatLng(it.latitude, it.longitude) }
        }.getOrNull()
    }
}
