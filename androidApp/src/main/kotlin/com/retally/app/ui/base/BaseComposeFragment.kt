package com.retally.app.ui.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.retally.shared.core.Action
import com.retally.shared.core.Effect
import com.retally.shared.core.State
import com.retally.shared.core.ViewModel
import com.retally.shared.domain.preferences.AppPreferences
import com.retally.shared.ui.theme.ReTallyTheme
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * One screen = one Fragment = one [ViewModel] (Sidearm's `BaseComposeFragment` shape).
 *
 * The fragment owns the Android side of MVVM: it hosts a [ComposeView], feeds the ViewModel's
 * state into a stateless shared `*Screen` composable via [ScreenContent], and reacts to
 * one-shot side effects in [onViewEffect] (navigation, snackbars, intents). Business logic
 * never lives here — it stays in the shared Store so iOS gets it for free.
 *
 * To add a screen: create its Contract + Store in `shared/presentation/<screen>`, then in
 * `androidApp/ui/<screen>` add `<Screen>Fragment`, `<Screen>Screen` and `<Screen>ViewModel`
 * (Sidearm layout), add a `<fragment>` to `res/navigation/nav_graph.xml`, and register the
 * ViewModel in [com.retally.app.di.viewModelModule].
 */
abstract class BaseComposeFragment<S : State, A : Action, E : Effect, VM : ViewModel<S, A, E>> : Fragment() {

    protected abstract val viewModel: VM

    /** Lives outside composition so [onViewEffect] can show messages from the fragment scope. */
    protected val snackbarHostState = SnackbarHostState()

    private val preferences: AppPreferences by inject()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val prefs by preferences.observe().collectAsStateWithLifecycle(preferences.current)
                ReTallyTheme(themeMode = prefs.themeMode) {
                    val state by viewModel.store.observeState().collectAsStateWithLifecycle()
                    ScreenContent(state)
                }
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            // STARTED: effects emitted while in the back stack wait in the Store's channel and
            // are delivered when the screen is visible again — a navigation effect is never lost.
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.store.observeSideEffect().collect { onViewEffect(it) }
            }
        }
    }

    /** Forward a UI intent to the Store (Sidearm naming). */
    protected fun dispatchViewAction(action: A) = viewModel.dispatch(action)

    /** Navigate with Safe Args directions. */
    protected fun navigate(directions: NavDirections) = findNavController().navigate(directions)

    /** Render [state]. Must be side-effect free — call the shared stateless screen. */
    @Composable
    protected abstract fun ScreenContent(state: S)

    /** Handle a one-shot effect: navigate, toast, start an intent. Default: ignore. */
    protected open suspend fun onViewEffect(effect: E) {}
}
