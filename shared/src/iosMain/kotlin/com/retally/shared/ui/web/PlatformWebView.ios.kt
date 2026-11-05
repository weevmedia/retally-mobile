/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.ui.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun PlatformWebView(
    url: String,
    allowedHost: String,
    onLoadingChanged: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val loadingCallback by rememberUpdatedState(onLoadingChanged)
    // WKWebView holds its navigation delegate weakly: keep a strong reference for the view's life.
    val delegate = remember(allowedHost) { NavigationDelegate(allowedHost) { loadingCallback(it) } }
    UIKitView(
        modifier = modifier,
        factory = {
            WKWebView(frame = CGRectZero.readValue(), configuration = WKWebViewConfiguration()).apply {
                navigationDelegate = delegate
                opaque = false
                backgroundColor = UIColor.clearColor
                scrollView.backgroundColor = UIColor.clearColor
                NSURL.URLWithString(url)?.let { loadRequest(NSURLRequest.requestWithURL(it)) }
            }
        },
    )
}

@OptIn(ExperimentalForeignApi::class)
private class NavigationDelegate(
    private val allowedHost: String,
    private val onLoadingChanged: (Boolean) -> Unit,
) : NSObject(), WKNavigationDelegateProtocol {

    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        val target = decidePolicyForNavigationAction.request.URL
        if (target == null || target.host == allowedHost) {
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
        } else {
            // Mail, phone and other sites: hand to the system, never load here.
            UIApplication.sharedApplication.openURL(target, options = emptyMap<Any?, Any>(), completionHandler = null)
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
        }
    }

    // Same Kotlin signature as didFinishNavigation: the annotation tells the compiler they are
    // different Objective-C methods.
    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didStartProvisionalNavigation: WKNavigation?) = onLoadingChanged(true)

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) = onLoadingChanged(false)
}
