/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.core.locale

import platform.Foundation.NSLocale
import platform.Foundation.countryCode
import platform.Foundation.currencyCode
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

actual fun deviceLocaleInfo(): LocaleInfo {
    val locale = NSLocale.currentLocale
    val country = locale.countryCode?.takeIf { it.isNotBlank() }?.uppercase() ?: "US"
    return LocaleInfo(
        language = locale.languageCode?.takeIf { it.isNotBlank() }?.lowercase() ?: "en",
        country = country,
        currencyCode = locale.currencyCode?.takeIf { it.isNotBlank() } ?: "USD",
    )
}
