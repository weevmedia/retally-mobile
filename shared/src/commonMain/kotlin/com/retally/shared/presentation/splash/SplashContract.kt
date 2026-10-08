package com.retally.shared.presentation.splash

import com.retally.shared.core.Effect
import com.retally.shared.domain.ads.AdsReadiness
import com.retally.shared.domain.ads.AdsSession
import com.retally.shared.domain.preferences.AppPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Splash: plays the brand animation and kicks off the ads bootstrap through [AdsSession].
 * It gives the SDK a short grace period so a fast start shows the banner immediately, but
 * never blocks on it — [MainContract.Store] keeps observing readiness after hand-off.
 */
interface SplashContract {

    data class State(
        val progress: Float = 0f,
        val adsReadiness: AdsReadiness? = null,
        val isFirstLaunch: Boolean = false,
    ) : com.retally.shared.core.State

    sealed class Action : com.retally.shared.core.Action {
        /** Fired once from the screen's LaunchedEffect. Idempotent. */
        data object Start : Action()
    }

    sealed class SideEffect : Effect {
        /**
         * @param adsReady whether the ads SDK warmed up (hosts gate live banners on this).
         * @param isFirstLaunch true on the very first app launch — hosts route to onboarding.
         */
        data class NavigateToMain(val adsReady: Boolean, val isFirstLaunch: Boolean) : SideEffect()
    }

    class Store(
        private val adsSession: AdsSession,
        private val preferences: AppPreferences,
    ) : com.retally.shared.core.Store<State, Action, SideEffect>() {

        private var started = false

        override fun initState() = State(isFirstLaunch = !preferences.hasLaunchedBefore)

        override fun dispatch(action: Action) {
            when (action) {
                Action.Start -> start()
            }
        }

        private fun start() {
            if (started) return
            started = true
            adsSession.ensureStarted()
            launch {
                // Brand moment: the logo animation needs ~MIN_SPLASH_MS to read as intentional
                // rather than a flash. Progress is purely cosmetic.
                val steps = 20
                repeat(steps) { step ->
                    delay(MIN_SPLASH_MS / steps)
                    currentState = currentState.copy(progress = (step + 1) / steps.toFloat())
                }
                // Grace period only: if the SDK is still starting we hand off anyway and the
                // shell flips adsEnabled on when it finishes.
                val readiness = adsSession.awaitReadiness(MAX_SPLASH_MS - MIN_SPLASH_MS)
                currentState = currentState.copy(adsReadiness = readiness, progress = 1f)
                preferences.hasLaunchedBefore = true
                sideEffect(
                    SideEffect.NavigateToMain(
                        adsReady = readiness == AdsReadiness.READY,
                        isFirstLaunch = currentState.isFirstLaunch,
                    ),
                )
            }
        }

        companion object {
            const val MIN_SPLASH_MS = 1_200L
            const val MAX_SPLASH_MS = 3_000L
        }
    }
}
