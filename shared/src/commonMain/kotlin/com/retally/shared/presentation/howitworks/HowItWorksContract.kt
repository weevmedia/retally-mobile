/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.presentation.howitworks

import com.retally.shared.core.Effect
import com.retally.shared.core.newId
import com.retally.shared.domain.receipt.SampleReceipts
import com.retally.shared.domain.receipt.model.Receipt
import com.retally.shared.presentation.main.ScanRequests

/**
 * "See how it works": a short demo built from the app's own receipt screens (the scan loader,
 * line-item cards and double-charge verdict) on a sample receipt, then the steps and scan tips.
 * Nothing is saved; the sample receipt only lives in this screen's state.
 */
interface HowItWorksContract {

    data class State(
        /** Sample receipt with one double-charged line, shown in the demo. */
        val sample: Receipt = SampleReceipts.make(idFactory = { newId() }),
    ) : com.retally.shared.core.State

    sealed class Action : com.retally.shared.core.Action {
        data object CloseClicked : Action()
        /** "Scan a receipt": close this screen and open the scanner from Home. */
        data object ScanClicked : Action()
    }

    sealed class SideEffect : Effect {
        data object Closed : SideEffect()
    }

    class Store : com.retally.shared.core.Store<State, Action, SideEffect>() {
        override fun initState() = State()

        override fun dispatch(action: Action) {
            when (action) {
                Action.CloseClicked -> sideEffect(SideEffect.Closed)
                Action.ScanClicked -> {
                    sideEffect(SideEffect.Closed)
                    ScanRequests.request() // Home owns the scanner flow (same path as the scan FAB)
                }
            }
        }
    }
}
