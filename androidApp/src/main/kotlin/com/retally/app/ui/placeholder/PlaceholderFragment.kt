package com.retally.app.ui.placeholder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.retally.shared.ui.placeholder.PlaceholderScreen
import com.retally.shared.ui.placeholder.PlaceholderTab
import com.retally.shared.ui.theme.ReTallyTheme

/**
 * Hosts [PlaceholderScreen] for tabs that exist in the shell but are not built yet. One
 * subclass per destination so the nav graph can reference them by class name.
 */
abstract class PlaceholderFragment(private val placeholder: PlaceholderTab) : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { ReTallyTheme { PlaceholderScreen(placeholder) } }
        }
}

