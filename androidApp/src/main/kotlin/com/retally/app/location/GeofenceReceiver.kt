package com.retally.app.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.retally.shared.domain.location.StoreLocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * Receives geofence ENTER transitions from Play services — including while the app
 * is dead. Debounces through [StoreLocationRepository.shouldNotifyAndMark] (one
 * alert per store per 24h) and posts a local notification.
 */
class GeofenceReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.w(TAG, "GeofencingEvent error: ${event.errorCode}")
            return
        }
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return
        val ids = event.triggeringGeofences?.map { it.requestId }.orEmpty()
        if (ids.isEmpty()) return

        val pending = goAsync()
        scope.launch {
            try {
                val appContext = context.applicationContext
                val repo: StoreLocationRepository = GlobalContext.get().get()
                val now = System.currentTimeMillis()
                ids.forEach { id ->
                    if (repo.shouldNotifyAndMark(id, now)) {
                        val name = repo.locationName(id) ?: return@forEach
                        NearbyNotifications.show(appContext, name)
                    }
                }
            } catch (e: Exception) {
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
