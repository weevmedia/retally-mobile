/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.app.ui.notifications

import com.retally.shared.core.ViewModel
import com.retally.shared.presentation.notifications.NotificationsContract.Action
import com.retally.shared.presentation.notifications.NotificationsContract.SideEffect
import com.retally.shared.presentation.notifications.NotificationsContract.State
import com.retally.shared.presentation.notifications.NotificationsContract.Store

/** Android ViewModel for the Home bell's notification inbox. Owns the shared [Store]. */
class NotificationsViewModel(
    override val store: Store,
) : ViewModel<State, Action, SideEffect>()
