package com.retally.app.location

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.retally.shared.domain.location.StoreLocation

/**
 * Registers one Play-services geofence per store (ENTER only, never expires).
 * The OS watches these even when the app is killed; entries land in
 * [GeofenceReceiver]. Idempotent — re-registering replaces the previous set.
 */
class NearbyGeofenceManager(private val context: Context) {

    private val client: GeofencingClient by lazy {
        LocationServices.getGeofencingClient(context.applicationContext)
    }

    fun start(locations: List<StoreLocation>) {
        if (locations.isEmpty()) {
            stop()
            return
        }
        val geofences = locations.map { loc ->
            Geofence.Builder()
                .setRequestId(loc.id)
                .setCircularRegion(loc.latitude, loc.longitude, loc.radiusMeters.toFloat())
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build()
        }
        val request = GeofencingRequest.Builder()
            // Fire immediately if we're already inside a fence when registering.
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofences)
            .build()
        try {
            client.addGeofences(request, geofencePendingIntent())
                .addOnFailureListener { e ->
                    Log.w(TAG, "addGeofences failed: ${e.message}", e)
                }
        } catch (se: SecurityException) {
            Log.w(TAG, "Missing location permission for geofences", se)
        }
    }

    fun stop() {
        client.removeGeofences(geofencePendingIntent())
    }

    private fun geofencePendingIntent(): PendingIntent {
        val intent = Intent(context.applicationContext, GeofenceReceiver::class.java)
        // Mutable: the system appends the triggering geofence ids as extras (Android 12+).
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getBroadcast(context.applicationContext, 0, intent, flags)
    }

    companion object {
        private const val TAG = "NearbyGeofence"
    }
}
