package com.retally.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * TEST-LOCATION-PING: after every saved scan, posts a local notification showing the
 * device's current reverse-geocoded address. Verifies the location + notification
 * plumbing end to end. Debug only — remove before release.
 */
object DebugLocationPing {
    const val ENABLED = true

    fun ping(context: Context) {
        if (!ENABLED) return
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Main).launch {
            val body = resolveAddress(appContext)
            NearbyNotifications.showDebug(appContext, "TEST · Current location", body)
        }
    }

    private suspend fun resolveAddress(context: Context): String {
        if (!hasLocationPermission(context)) return "Location permission not granted"
        val client = LocationServices.getFusedLocationProviderClient(context)
        val location = try {
            suspendCancellableCoroutine<android.location.Location?> { cont ->
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            }
        } catch (e: SecurityException) {
            return "Location permission not granted"
        } ?: return "No location fix"
        if (!Geocoder.isPresent()) {
            return "%.5f, %.5f".format(Locale.US, location.latitude, location.longitude)
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                Geocoder(context, Locale.getDefault())
                    .getFromLocation(location.latitude, location.longitude, 1)
                    ?.firstOrNull()
                    ?.let { addr ->
                        (0..addr.maxAddressLineIndex).map { addr.getAddressLine(it) }.joinToString(", ")
                    }
            }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: "%.5f, %.5f".format(Locale.US, location.latitude, location.longitude)
        }
    }

    private fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}
