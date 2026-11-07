/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.presentation.notifications

import com.retally.shared.core.Effect
import com.retally.shared.domain.notifications.AppNotification
import com.retally.shared.domain.notifications.AppNotificationKind
import com.retally.shared.domain.notifications.NotificationInboxRepository
import com.retally.shared.domain.budget.BudgetReminderLog
import com.retally.shared.domain.shopping.ShoppingListRepository
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * The Home bell: notifications ReTally raised on this device in the last 48 hours.
 * Each one stays until it expires or the user dismisses it (X, or "Clear all").
 */
interface NotificationsContract {

    data class State(
        val notifications: List<AppNotification> = emptyList(),
    ) : com.retally.shared.core.State {
        val isEmpty: Boolean get() = notifications.isEmpty()
    }

    sealed class Action : com.retally.shared.core.Action {
        data object Appeared : Action()
        data object CloseClicked : Action()
        data class NotificationClicked(val id: String) : Action()
        data class DismissClicked(val id: String) : Action()
        data object ClearAllClicked : Action()
    }

    sealed class SideEffect : Effect {
        data object Closed : SideEffect()
        /** Host: switch to the Shopping List tab (the store is already queued on the repository). */
        data object OpenShoppingList : SideEffect()
        /** Host: open the monthly Budget screen (a budget reminder or month-end wrap-up was tapped). */
        data object OpenBudget : SideEffect()
    }

    class Store(
        private val inbox: NotificationInboxRepository,
        private val shoppingLists: ShoppingListRepository,
        private val budgetReminderLog: BudgetReminderLog? = null,
    ) : com.retally.shared.core.Store<State, Action, SideEffect>() {

        override fun initState() = State(notifications = inbox.notifications.value)

        init {
            inbox.notifications
                .onEach { list -> currentState = currentState.copy(notifications = list) }
                .launchIn(this)
        }

        override fun dispatch(action: Action) {
            when (action) {
                Action.Appeared -> {
                    budgetReminderLog?.collectFired() // a budget notice that just fired shows here too
                    inbox.prune()
                    inbox.markAllSeen() // stops the Home bell shaking
                }
                Action.CloseClicked -> sideEffect(SideEffect.Closed)
                is Action.NotificationClicked -> open(action.id)
                is Action.DismissClicked -> inbox.dismiss(action.id)
                Action.ClearAllClicked -> inbox.dismissAll()
            }
        }

        private fun open(id: String) {
            val notification = currentState.notifications.firstOrNull { it.id == id } ?: return
            when (notification.kind) {
                AppNotificationKind.NEARBY_STORE -> {
                    shoppingLists.pendingStoreName = notification.storeName
                    sideEffect(SideEffect.OpenShoppingList)
                }
                AppNotificationKind.BUDGET -> sideEffect(SideEffect.OpenBudget)
            }
        }
    }
}
