/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.app.ui.storealerts

import androidx.compose.runtime.Composable
import com.retally.shared.presentation.storealerts.StoreAlertsContract
import com.retally.shared.presentation.storealerts.StoreAlertsContract.State
import com.retally.shared.ui.storealerts.StoreAlertsScreen as SharedStoreAlertsScreen
import com.retally.shared.ui.theme.ReTallyTheme

/** Android host for the shared store-alerts opt-in. */
@Composable
fun StoreAlertsScreen(state: State, dispatch: (StoreAlertsContract.Action) -> Unit) {
    ReTallyTheme {
        SharedStoreAlertsScreen(dispatch = dispatch, state = state)
    }
}
