/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.core.locale

import java.util.Currency
import java.util.Locale

actual fun deviceLocaleInfo(): LocaleInfo {
    val locale = Locale.getDefault()
    val country = locale.country.ifBlank { "US" }.uppercase()
    val currency = runCatching { Currency.getInstance(Locale("", country)).currencyCode }.getOrNull() ?: "USD"
    return LocaleInfo(
        language = locale.language.ifBlank { "en" }.lowercase(),
        country = country,
        currencyCode = currency,
    )
}
