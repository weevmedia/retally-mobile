/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.presentation.storealerts

import com.retally.shared.core.Effect
import com.retally.shared.domain.location.StoreLocationRepository
import com.russhwolf.settings.Settings

/**
 * "Know when you're at the store" — the nearby-store alerts opt-in, shown once, right after
 * the first sign-in / guest choice and before Home.
 *
 * The screen only explains and asks; the system prompts (location "Always", then
 * notifications) are the host's. "Enable location" starts them, Skip goes straight on, Back returns
 * to sign-in. Enabled or skipped, the screen never shows again ([StoreAlertsPrompt.markShown]); alerts can be turned on
 * later from the Store alerts toggle in More.
 */
interface StoreAlertsContract {

    data class State(
        /** Permission prompts in flight (the host owns them); the button shows a spinner. */
        val isRequesting: Boolean = false,
    ) : com.retally.shared.core.State

    sealed class Action : com.retally.shared.core.Action {
        /** "Enable location": run the location, then notification, prompts. */
        data object EnableClicked : Action()
        /** "Skip": leave alerts off and continue. */
        data object SkipClicked : Action()
        /** Back chevron (or the system Back): return to the sign-in screen. */
        data object BackClicked : Action()
        /**
         * Outcome of the prompts, from the host: true only when precise + background location
         * and notifications were all granted. Anything less leaves alerts off.
         */
        data class PermissionsResult(val granted: Boolean) : Action()
    }

    sealed class SideEffect : Effect {
        /** Host must prompt for location (precise, then "all the time") and then notifications. */
        data object RequestAlertPermissions : SideEffect()
        /** Alerts were turned on: the host registers the store geofences now. */
        data object AlertsEnabled : SideEffect()
        /** Done, granted or not: on to Home. */
        data object Finished : SideEffect()
        /** Back to sign-in; the screen is not marked shown, so it returns after the next sign-in. */
        data object NavigateBack : SideEffect()
    }

    class Store(
        private val prompt: StoreAlertsPrompt,
    ) : com.retally.shared.core.Store<State, Action, SideEffect>() {

        override fun initState(): State = State()

        override fun dispatch(action: Action) {
            when (action) {
                Action.EnableClicked -> {
                    if (currentState.isRequesting) return
                    currentState = currentState.copy(isRequesting = true)
                    sideEffect(SideEffect.RequestAlertPermissions)
                }
                Action.SkipClicked -> finish()
                Action.BackClicked -> if (!currentState.isRequesting) sideEffect(SideEffect.NavigateBack)
                is Action.PermissionsResult -> {
                    prompt.nearbyStores?.setEnabled(action.granted)
                    if (action.granted) sideEffect(SideEffect.AlertsEnabled)
                    currentState = currentState.copy(isRequesting = false)
                    finish()
                }
            }
        }

        private fun finish() {
            prompt.markShown()
            sideEffect(SideEffect.Finished)
        }
    }
}

/**
 * Whether the opt-in screen still has to be shown. Hosts ask [isNeeded] before routing from
 * sign-in to Home, so the screen is skipped without a flash once it has been seen, or when
 * alerts are already on (a reinstall that kept its grants).
 */
class StoreAlertsPrompt(
    private val settings: Settings,
    /** Null in tests and previews. */
    val nearbyStores: StoreLocationRepository? = null,
) {
    val isNeeded: Boolean
        get() = !settings.getBoolean(KEY_SHOWN, false) && nearbyStores?.isEnabled != true

    fun markShown() {
        settings.putBoolean(KEY_SHOWN, true)
    }

    private companion object {
        const val KEY_SHOWN = "storeAlerts.promptShown"
    }
}
