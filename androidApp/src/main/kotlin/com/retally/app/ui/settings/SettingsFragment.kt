package com.retally.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import com.retally.app.R
import com.retally.app.location.NearbyStoreScheduler
import com.retally.app.ui.base.BaseComposeFragment
import com.retally.shared.core.Platform
import com.retally.shared.presentation.settings.SettingsContract.Action
import com.retally.shared.presentation.settings.SettingsContract.SideEffect
import com.retally.shared.presentation.settings.SettingsContract.SideEffect.OpenUrl
import com.retally.shared.presentation.settings.SettingsContract.SideEffect.ShowMessage
import com.retally.shared.presentation.settings.SettingsContract.State
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class SettingsFragment : BaseComposeFragment<State, Action, SideEffect, SettingsViewModel>() {

    override val viewModel: SettingsViewModel by viewModel()
    private val platform: Platform by inject()

    private val foregroundLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // Background location needs its own separate prompt on API 29+.
        requestBackgroundLocation()
    }

    private val backgroundLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        finishNearbyPermissionFlow()
    }

    @Composable
    override fun ScreenContent(state: State) {
        SettingsScreen(state = state, dispatch = ::dispatchViewAction, snackbarHostState = snackbarHostState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Back from the Account or store-picker page returns to the More menu. Dialogs
        // register their own (later) callbacks, so they consume back first.
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val page = viewModel.store.observeState().value
                    when {
                        page.showStorePickerPage -> dispatchViewAction(Action.StorePickerDismissed)
                        page.showAccountPage -> dispatchViewAction(Action.AccountDismissed)
                        else -> {
                        isEnabled = false
                        requireActivity().onBackPressed()
                    }
                }
            }
            },
        )
    }

    override suspend fun onViewEffect(effect: SideEffect) {
        when (effect) {
            is OpenUrl -> if (!platform.openUrl(effect.url)) snackbarHostState.showSnackbar(getString(R.string.host_link_open_failed))
            is ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            is SideEffect.RequestNearbyPermissions -> requestNearbyPermissions()
            is SideEffect.NearbyMonitoringChanged -> NearbyStoreScheduler.refresh(requireContext())
            is SideEffect.OpenAuth -> findNavController().navigate(
                SettingsFragmentDirections.actionSettingsToAuth(),
            )
            is SideEffect.LoggedOut -> snackbarHostState.showSnackbar(effect.message)
            is SideEffect.AccountDeleted -> snackbarHostState.showSnackbar(effect.message)
        }
    }

    private fun requestNearbyPermissions() {
        val missing = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !isGranted(Manifest.permission.POST_NOTIFICATIONS)
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (!isGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        if (missing.isEmpty()) {
            requestBackgroundLocation()
        } else {
            foregroundLauncher.launch(missing.toTypedArray())
        }
    }

    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        ) {
            backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            finishNearbyPermissionFlow()
        }
    }

    private fun finishNearbyPermissionFlow() {
        val granted = NearbyStoreScheduler.hasLocationPermission(requireContext()) &&
            NearbyStoreScheduler.hasNotificationPermission(requireContext())
        dispatchViewAction(Action.NearbyPermissionsResult(granted))
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED
}
