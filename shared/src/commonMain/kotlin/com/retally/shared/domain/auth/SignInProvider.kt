/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.domain.auth

/**
 * Social sign-in options. iOS offers Apple and Google (App Store rules require Sign in with
 * Apple alongside any other social login); Android offers Google only.
 */
enum class SignInProvider(val wireName: String) {
    APPLE("apple"),
    GOOGLE("google"),
}

/** The providers this platform's sign-in screen offers, in display order. */
expect val platformSignInProviders: List<SignInProvider>
