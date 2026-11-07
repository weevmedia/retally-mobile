/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.app.ui.storealerts

import com.retally.shared.core.ViewModel
import com.retally.shared.presentation.storealerts.StoreAlertsContract.Action
import com.retally.shared.presentation.storealerts.StoreAlertsContract.SideEffect
import com.retally.shared.presentation.storealerts.StoreAlertsContract.State
import com.retally.shared.presentation.storealerts.StoreAlertsContract.Store

/** Android ViewModel for the first-launch store-alerts opt-in. Owns the shared [Store]. */
class StoreAlertsViewModel(
    override val store: Store,
) : ViewModel<State, Action, SideEffect>()
