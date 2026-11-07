/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.app.ui.web

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.retally.shared.domain.preferences.AppPreferences
import com.retally.shared.ui.theme.ReTallyTheme
import com.retally.shared.ui.web.WebPageScreen
import org.koin.android.ext.android.inject

/**
 * One of our own web pages (privacy, terms, support) inside the app, with our top bar and no
 * address bar. Reached through the global `action_global_webPage` with a title and URL.
 */
class WebPageFragment : Fragment() {

    private val args: WebPageFragmentArgs by navArgs()
    private val preferences: AppPreferences by inject()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val prefs by preferences.observe().collectAsStateWithLifecycle(preferences.current)
                ReTallyTheme(themeMode = prefs.themeMode) {
                    WebPageScreen(title = args.title, url = args.url, onBack = { findNavController().popBackStack() })
                }
            }
        }
}
