/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.app.ui.howitworks

import com.retally.shared.core.ViewModel
import com.retally.shared.presentation.howitworks.HowItWorksContract.Action
import com.retally.shared.presentation.howitworks.HowItWorksContract.SideEffect
import com.retally.shared.presentation.howitworks.HowItWorksContract.State
import com.retally.shared.presentation.howitworks.HowItWorksContract.Store

/** Android ViewModel for "How it works". Owns the shared [Store]. */
class HowItWorksViewModel(
    override val store: Store,
) : ViewModel<State, Action, SideEffect>()
