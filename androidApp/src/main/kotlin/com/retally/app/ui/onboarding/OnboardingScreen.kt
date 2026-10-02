package com.retally.app.ui.onboarding

import androidx.compose.runtime.Composable
import com.retally.shared.presentation.onboarding.OnboardingContract
import com.retally.shared.ui.onboarding.OnboardingScreen as SharedOnboardingScreen
import com.retally.shared.ui.theme.ReTallyTheme

/** Android host for the shared onboarding pager. */
@Composable
fun OnboardingScreen(dispatch: (OnboardingContract.Action) -> Unit) {
    ReTallyTheme {
        SharedOnboardingScreen(dispatch = dispatch)
    }
}
