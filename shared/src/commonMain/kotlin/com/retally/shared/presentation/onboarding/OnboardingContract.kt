/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.presentation.onboarding

import com.retally.shared.core.Effect

/**
 * First-launch onboarding, shown once directly after the splash. It opens on the store-alerts
 * step, which explains the feature (the stores' prominent-disclosure rule for background
 * location) and immediately runs the location, then notification, prompts; once they are
 * answered the three value-prop pages (Snap / Track / save) follow in an auto-advancing pager
 * with a pinned CTA footer.
 *
 * Both exits route to the sign-in screen, which sits between onboarding and Home.
 */
interface OnboardingContract {

    /** Nothing to track: the pager state lives in the composable. */
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

        /** Auto-advance interval for the content pager; loops back to the first page. */
        const val AUTO_ADVANCE_MS = 2_000L
    }
}
