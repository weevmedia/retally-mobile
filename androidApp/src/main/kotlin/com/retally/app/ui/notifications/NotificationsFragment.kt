package com.retally.app.ui.notifications

import androidx.compose.runtime.Composable
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.retally.app.R
import com.retally.app.ui.base.BaseComposeFragment
import com.retally.shared.presentation.notifications.NotificationsContract.Action
import com.retally.shared.presentation.notifications.NotificationsContract.SideEffect
import com.retally.shared.presentation.notifications.NotificationsContract.State
import com.retally.shared.ui.notifications.NotificationsScreen
import org.koin.androidx.viewmodel.ext.android.viewModel

/** Home bell: notifications raised in the last 48 hours, until dismissed. */
class NotificationsFragment : BaseComposeFragment<State, Action, SideEffect, NotificationsViewModel>() {

    override val viewModel: NotificationsViewModel by viewModel()

    @Composable
    override fun ScreenContent(state: State) {
        NotificationsScreen(state = state, dispatch = ::dispatchViewAction, snackbarHostState = snackbarHostState)
    }

    override suspend fun onViewEffect(effect: SideEffect) {
        when (effect) {
            SideEffect.Closed -> findNavController().popBackStack()
            // The store already queued the store name on ShoppingListRepository; switch tabs
            // the same way MainActivity does for a tapped system notification.
            SideEffect.OpenShoppingList -> {
                val nav = findNavController()
                nav.popBackStack()
                val options = NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setRestoreState(true)
                    .setPopUpTo(R.id.homeFragment, inclusive = false, saveState = true)
                    .build()
                nav.navigate(R.id.aiListFragment, null, options)
            }
        }
    }
}
