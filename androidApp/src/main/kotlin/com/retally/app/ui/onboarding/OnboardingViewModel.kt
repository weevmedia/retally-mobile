package com.retally.app.ui.onboarding

import com.retally.shared.core.ViewModel
import com.retally.shared.presentation.onboarding.OnboardingContract.Action
import com.retally.shared.presentation.onboarding.OnboardingContract.SideEffect
import com.retally.shared.presentation.onboarding.OnboardingContract.State
import com.retally.shared.presentation.onboarding.OnboardingContract.Store

/** Android ViewModel for first-launch onboarding. Owns the shared [Store]. */
class OnboardingViewModel(
    override val store: Store,
) : ViewModel<State, Action, SideEffect>()
