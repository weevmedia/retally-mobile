/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.retally.shared.domain.location.StoreLocation
import com.retally.shared.domain.location.StoreLocationRepository
import com.retally.shared.domain.location.nearbyLog
import com.retally.shared.domain.shopping.ShoppingListRepository
import com.retally.shared.domain.shopping.notificationSummary
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.context.GlobalContext

/** Turns "the user is in these zones" into alerts — used by geofence events and the start-up check. */
object NearbyAlerts {

    suspend fun handleEntry(context: Context, ids: List<String>) {
        val repo: StoreLocationRepository = GlobalContext.get().get()
        // Toggle off or location permission gone (a fence registered earlier can still
        // fire): never notify — clean the fences up instead.
        if (!repo.isEnabled || !NearbyStoreScheduler.hasLocationPermission(context)) {
            nearbyLog("android: entry ignored — Nearby off or location permission missing")
            NearbyGeofenceManager(context).stop()
            return
        }
        val lists: ShoppingListRepository = GlobalContext.get().get()
        val now = System.currentTimeMillis()
        ids.forEach { id ->
            if (repo.shouldNotifyAndMark(id, now)) {
                val name = repo.locationName(id) ?: run {
                    nearbyLog("android: entry for unknown zone $id — no alert")
                    return@forEach
                }
                // Cached AI list for this store: works offline and with the app closed.
                val summary = lists.cachedListFor(name)?.let { list ->
                    list.notificationSummary(lists.dismissedItems(list.storeKey))
                }
                nearbyLog("android: alert for \"$name\" (shopping list ${if (summary != null) "found" else "none"})")
                NearbyNotifications.show(context, name, summary)
            }
        }
    }

    /**
     * Play services only reports an ENTER when it notices a change, so opening the app while
     * already standing in a zone often gives nothing. Take one fresh fix at start-up and alert
     * for any zone we're inside (the repository drops it if the OS also reports it).
     */
    @SuppressLint("MissingPermission") // callers check NearbyStoreScheduler.hasLocationPermission
    suspend fun checkAlreadyInside(context: Context, zones: List<StoreLocation>) {
        if (zones.isEmpty()) return
        val client = LocationServices.getFusedLocationProviderClient(context)
        val here: Location? = runCatching {
            withTimeoutOrNull(FIX_TIMEOUT_MS) {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).await()
            } ?: client.lastLocation.await()
        }.onFailure { nearbyLog("android: start-up location check FAILED: ${it.message}") }.getOrNull()
        if (here == null) {
            nearbyLog("android: start-up check — no location fix (device Location off?)")
            return
        }
        val inside = zones.filter { zone ->
            val d = FloatArray(1)
            Location.distanceBetween(here.latitude, here.longitude, zone.latitude, zone.longitude, d)
            d[0] <= zone.radiusMeters
        }
        val nearest = zones.minByOrNull { zone ->
            val d = FloatArray(1)
            Location.distanceBetween(here.latitude, here.longitude, zone.latitude, zone.longitude, d)
            d[0]
        }
        nearbyLog(
            "android: start-up check at (${here.latitude}, ${here.longitude}) ±${here.accuracy.toInt()}m — " +
                if (inside.isEmpty()) "not inside any zone (nearest: ${nearest?.storeName})"
                else "inside ${inside.map { it.storeName }}",
        )
        if (inside.isNotEmpty()) handleEntry(context, inside.map { it.id })
    }

    private const val FIX_TIMEOUT_MS = 15_000L
}
