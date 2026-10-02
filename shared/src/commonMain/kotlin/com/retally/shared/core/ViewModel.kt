package com.retally.shared.core

import androidx.lifecycle.ViewModel as LifecycleViewModel

/**
 * Screen-scoped owner of a [Store] — the "VM" in MVVM.
 *
 * Built on the multiplatform `androidx.lifecycle.ViewModel`: on Android it is the real
 * AndroidX ViewModel (survives rotation, cleared with the Fragment/Activity, injectable with
 * Koin's `viewModel {}`), and on iOS it is a plain retained object the host owns. Platform-
 * specific screen logic (camera intents, permission results, deep-link parsing) belongs in
 * subclasses here, never in the shared Store, so the Store stays pure and unit-testable.
 */
abstract class ViewModel<S : State, A : Action, E : Effect> : LifecycleViewModel() {
    abstract val store: Store<S, A, E>

    /** Convenience so screens can write `viewModel.dispatch(action)`. */
    fun dispatch(action: A) = store.dispatch(action)

    override fun onCleared() {
        super.onCleared()
        store.clear()
    }
}
