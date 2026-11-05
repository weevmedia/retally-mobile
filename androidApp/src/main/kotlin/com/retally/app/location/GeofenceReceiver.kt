/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.app.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.retally.shared.domain.location.nearbyLog

/**
 * Receives geofence ENTER transitions from Play services — including while the app
 * is dead. Debounces through [StoreLocationRepository.shouldNotifyAndMark] (one
 * alert per store per 24h) and posts a local notification.
 */
class GeofenceReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: run {
            nearbyLog("android: zone event without data — ignored")
            return
        }
        if (event.hasError()) {
            nearbyLog("android: zone event ERROR ${event.errorCode}")
            Log.w(TAG, "GeofencingEvent error: ${event.errorCode}")
            return
        }
        val ids = event.triggeringGeofences?.map { it.requestId }.orEmpty()
        nearbyLog("android: zone event transition=${event.geofenceTransition} zones=$ids")
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return
        if (ids.isEmpty()) return

        val pending = goAsync()
        scope.launch {
            try {
                NearbyAlerts.handleEntry(context.applicationContext, ids)
            } catch (e: Exception) {
                nearbyLog("android: handling zone entry FAILED: ${e.message}")
                Log.w(TAG, "Failed to handle geofence entry", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "NearbyGeofence"
    }
}
