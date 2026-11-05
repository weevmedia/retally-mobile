/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.presentation.onboarding

import com.retally.shared.core.Effect

/**
 * First-launch onboarding: three value-prop pages (Snap / Track / save) in an auto-advancing
 * pager with a pinned CTA footer. Shown once, directly after the splash.
 *
 * Both exits route to the sign-in screen, which sits between onboarding and Home.
 */
interface OnboardingContract {

    /** No dynamic state yet — the pager owns its own page state in the composable. */
    class State : com.retally.shared.core.State

    sealed class Action : com.retally.shared.core.Action {
        /** Pinned "Get Started" primary CTA. */
        data object GetStarted : Action()

        /** "I already have an account" — lands on the same sign-in screen as Get Started. */
        data object SignInClicked : Action()
    }

    sealed class SideEffect : Effect {
        /**
         * Go to sign-in. [showGuestOption] is true for Get Started and false for "I already have
         * an account": someone signing in doesn't need "Continue as guest".
         */
        data class NavigateToMain(val showGuestOption: Boolean) : SideEffect()
    }

    class Store : com.retally.shared.core.Store<State, Action, SideEffect>() {
        override fun initState(): State = State()

        override fun dispatch(action: Action) {
            when (action) {
                Action.GetStarted -> sideEffect(SideEffect.NavigateToMain(showGuestOption = true))
                Action.SignInClicked -> sideEffect(SideEffect.NavigateToMain(showGuestOption = false))
            }
        }
    }

    companion object {
        const val PAGE_COUNT = 3

        /** Auto-advance interval for the content pager (Mukesh: 1s). */
        const val AUTO_ADVANCE_MS = 1_000L
    }
}
