/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.app.ui.storealerts

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import com.retally.app.location.NearbyStoreScheduler
import com.retally.app.ui.base.BaseComposeFragment
import com.retally.shared.domain.location.nearbyLog
import com.retally.shared.presentation.storealerts.StoreAlertsContract.Action
import com.retally.shared.presentation.storealerts.StoreAlertsContract.SideEffect
import com.retally.shared.presentation.storealerts.StoreAlertsContract.State
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * First launch only: the store-alerts opt-in between sign-in and Home. Runs the system
 * prompts the shared screen asks for — precise location, then "all the time" (its own prompt
 * on API 29+), then notifications (API 33+) — each step chaining the next from its result so
 * a denial mid-way still reports a single outcome.
 */
class StoreAlertsFragment : BaseComposeFragment<State, Action, SideEffect, StoreAlertsViewModel>() {

    override val viewModel: StoreAlertsViewModel by viewModel()

    private val foregroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        requestBackgroundLocation()
    }

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        requestNotifications()
    }

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        finishAlertsPermissionFlow()
    }

    @Composable
    override fun ScreenContent(state: State) {
        StoreAlertsScreen(state = state, dispatch = ::dispatchViewAction)
    }

    override suspend fun onViewEffect(effect: SideEffect) {
        when (effect) {
            SideEffect.RequestAlertPermissions -> requestAlertPermissions()
            SideEffect.AlertsEnabled -> NearbyStoreScheduler.refresh(requireContext())
            // Safe Args; the graph pops sign-in and this screen so Back from Home exits the app.
            SideEffect.Finished -> navigate(StoreAlertsFragmentDirections.actionStoreAlertsToHome())
            // Sign-in is still on the stack beneath us (the system Back does the same).
            SideEffect.NavigateBack -> popBack()
        }
    }

    private fun requestAlertPermissions() {
        if (isGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestBackgroundLocation()
        } else {
            foregroundLocationLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
            )
        }
    }

    private fun requestBackgroundLocation() {
        val fine = isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        val background = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        // On Android 11+ this opens the app's location page in Settings ("Allow all the
        // time") rather than a dialog. It is skipped by the OS when precise location was not
        // granted, or when the first grant was "Only this time".
        nearbyLog("android: store alerts location — precise=$fine, all-the-time=$background")
        if (fine && !background) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            requestNotifications()
        }
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !isGranted(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            finishAlertsPermissionFlow()
        }
    }

    private fun finishAlertsPermissionFlow() {
        val location = NearbyStoreScheduler.hasLocationPermission(requireContext())
        val notifications = NearbyStoreScheduler.hasNotificationPermission(requireContext())
        nearbyLog(
            "android: store alerts permissions — location all-the-time=$location, notifications=$notifications",
        )
        dispatchViewAction(Action.PermissionsResult(location && notifications))
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED
}
