package com.retally.app.ui.splash

import com.retally.shared.core.ViewModel
import com.retally.shared.presentation.splash.SplashContract.Action
import com.retally.shared.presentation.splash.SplashContract.SideEffect
import com.retally.shared.presentation.splash.SplashContract.State
import com.retally.shared.presentation.splash.SplashContract.Store

/**
 * Android ViewModel for the splash. Owns the shared [Store]; Android-only concerns (e.g. a
 * Lottie/video intro, deep-link parsing from the launch Intent) go here, never in the Store.
 */
class SplashViewModel(
    override val store: Store,
) : ViewModel<State, Action, SideEffect>() {

    /** Idempotent — safe to call from every `onCreate`. */
    fun start() = dispatch(Action.Start)
}
