/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.ui.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.str
import com.retally.shared.ui.components.CircleIconButton
import com.retally.shared.ui.theme.ReTallyTheme

/** Host of our own hosted pages (privacy, terms, support). Links to anywhere else open outside. */
const val RETALLY_WEB_HOST = "retally-c5212.web.app"

/** `?app=1` makes our pages hide their website header and footer (and keep it on their links). */
fun inAppPageUrl(url: String): String = if ('?' in url) "$url&app=1" else "$url?app=1"

/**
 * One of ReTally's own web pages (privacy policy, terms, support) inside the app: our top bar
 * with a title and Back, then the page in a platform web view. No address bar, so the URL is
 * never shown; links that leave [RETALLY_WEB_HOST] (mail, other sites) open outside the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebPageScreen(
    title: String,
    url: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ReTallyTheme.colors
    var loading by remember(url) { mutableStateOf(true) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title, style = ReTallyTheme.typography.title, color = c.ink, maxLines = 1) },
                navigationIcon = {
                    CircleIconButton(onClick = onBack) {
                        Icon(
                            Icons.Rounded.ChevronLeft,
                            contentDescription = str(S.ACTION_BACK),
                            tint = c.ink,
                            // Same back chevron as Budget, Notifications and Recalls.
                            modifier = Modifier.size(28.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background),
            )
        },
        containerColor = c.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            PlatformWebView(
                url = url,
                allowedHost = RETALLY_WEB_HOST,
                onLoadingChanged = { loading = it },
                modifier = Modifier.fillMaxSize(),
            )
            if (loading) CircularProgressIndicator(color = c.primary, modifier = Modifier.align(Alignment.Center))
        }
    }
}

/**
 * Platform web view: Android `WebView`, iOS `WKWebView`. Pages on [allowedHost] load inside;
 * any other link (mailto:, other sites) is handed to the system and not loaded here.
 */
@Composable
internal expect fun PlatformWebView(
    url: String,
    allowedHost: String,
    onLoadingChanged: (Boolean) -> Unit,
    modifier: Modifier,
)
