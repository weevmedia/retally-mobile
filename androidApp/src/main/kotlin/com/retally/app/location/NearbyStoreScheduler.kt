/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.app.location

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.retally.shared.domain.location.StoreLocationRepository
import com.retally.shared.domain.location.nearbyLog
import com.retally.shared.domain.receipt.ReceiptRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/** Re-registers geofences after a reboot (Play services forgets them). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        NearbyStoreScheduler.refresh(context.applicationContext)
        // Alarms are wiped by a reboot; re-arm the remaining budget reminders.
        com.retally.app.budget.AndroidBudgetReminderScheduler.rearm(context.applicationContext)
    }
}

/**
 * Single entry point for (re)starting nearby-store monitoring. Called from the
 * Application, the settings toggle flow, and after boot. No-ops when the toggle
 * is off or location permission is missing.
 */
object NearbyStoreScheduler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun refresh(context: Context) {
        val appContext = context.applicationContext
        val repo: StoreLocationRepository = try {
            GlobalContext.get().get()
        } catch (_: Exception) {
            return // Koin not started yet (early boot); the Application pass will cover it.
        }
        if (!repo.isEnabled) {
            nearbyLog("android: Nearby is OFF in More — zones removed")
            NearbyGeofenceManager(appContext).stop()
            return
        }
        // Permission revoked since the fences were registered: drop them too.
        if (!hasLocationPermission(appContext)) {
            nearbyLog("android: missing location permission (need fine + 'Allow all the time') — zones removed")
            NearbyGeofenceManager(appContext).stop()
            return
        }
        nearbyLog("android: refresh started")
        scope.launch {
            repo.refreshLocations()
            val zones = repo.locations.value
            NearbyGeofenceManager(appContext).start(zones)
            NearbyAlerts.checkAlreadyInside(appContext, zones)
        }
    }

    /**
     * Rebuilds the zones whenever the set of stores on receipts changes — above all after an
     * existing account signs in and its receipts are restored, which otherwise left the
     * restored stores unmonitored until the next launch. Call once, from the Application.
     */
    @OptIn(FlowPreview::class)
    fun watchStores(context: Context) {
        val appContext = context.applicationContext
        val receipts: ReceiptRepository = GlobalContext.get().get()
        scope.launch {
            receipts.observeReceipts()
                .map { list -> list.map { it.storeName.trim().lowercase() to it.storeAddress.orEmpty().trim() }.toSet() }
                .distinctUntilChanged()
                .drop(1) // the launch-time refresh already covers the current receipts
                .debounce(STORES_CHANGED_DEBOUNCE_MS) // a restore lands many receipts at once
                .collect {
                    nearbyLog("android: stores on receipts changed — refreshing zones")
                    refresh(appContext)
                }
        }
    }

    private const val STORES_CHANGED_DEBOUNCE_MS = 2_000L

    /** Background location (API 29+) is required for geofences to fire while the app is dead. */
    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val background = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return fine && background
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
