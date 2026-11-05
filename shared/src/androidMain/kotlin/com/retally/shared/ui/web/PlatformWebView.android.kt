/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.ui.web

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled") // our own pages; JS only hides the site header in the app
@Composable
internal actual fun PlatformWebView(
    url: String,
    allowedHost: String,
    onLoadingChanged: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val loadingCallback by rememberUpdatedState(onLoadingChanged)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                setBackgroundColor(Color.TRANSPARENT)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val target = request.url
                        if (target.host == allowedHost) return false
                        // Mail, phone and other sites: hand to the system, never load here.
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                        return true
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) = loadingCallback(true)
                    override fun onPageFinished(view: WebView?, url: String?) = loadingCallback(false)
                }
                loadUrl(url)
            }
        },
        onRelease = { it.destroy() },
    )
}
