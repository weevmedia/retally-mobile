package com.retally.app.ui.shopping

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.retally.app.R
import com.retally.app.ui.base.BaseComposeFragment
import com.retally.app.ui.main.MainViewModel
import com.retally.shared.presentation.shopping.ShoppingListContract.Action
import com.retally.shared.presentation.shopping.ShoppingListContract.SideEffect
import com.retally.shared.presentation.shopping.ShoppingListContract.State
import com.retally.shared.ui.shopping.ShoppingListScreen
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

/** AI Shopping List tab (bottom navigation). */
class ShoppingListFragment : BaseComposeFragment<State, Action, SideEffect, ShoppingListViewModel>() {

    override val viewModel: ShoppingListViewModel by viewModel()
    private val mainViewModel: MainViewModel by activityViewModel()

    @Composable
    override fun ScreenContent(state: State) {
        val shell by mainViewModel.store.observeState().collectAsStateWithLifecycle()
        ShoppingListScreen(
            state = state,
            dispatch = ::dispatchViewAction,
            snackbarHostState = snackbarHostState,
            adsEnabled = shell.adsEnabled,
            bannerAdUnitId = mainViewModel.homeBannerUnitId,
        )
    }

    override fun onResume() {
        super.onResume()
        // Re-reads a store handed over by a "you're near {store}" notification tap.
        dispatchViewAction(Action.Appeared)
    }

    override suspend fun onViewEffect(effect: SideEffect) {
        when (effect) {
            is SideEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            SideEffect.OpenSignIn -> findNavController().navigate(R.id.action_aiList_to_auth)
            is SideEffect.ShareText -> startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, effect.text),
                    null,
                ),
            )
        }
    }
}
