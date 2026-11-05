/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.core.locale

/**
 * Everything in the app that depends on where the user is or what language they speak,
 * in one place: currency, number and date conventions, and which address/recall rules
 * apply. Today the only supported region is the US and the only language English, so
 * [effective] collapses whatever the device reports onto [US] — but every US assumption
 * in the code (the `$` in `formatCurrency`, "City, ST 12345" in the address extractor,
 * MM/DD dates in the parser, the FDA/USDA recall feed) should read from this object rather
 * than hard-code it, so adding a country is a matter of adding a [Region] entry.
 *
 * Language is not chosen in the app: the OS picks it per device (and, on iOS 13+ /
 * Android 13+, per app in the system settings), and the string resolvers follow it.
 */
data class LocaleInfo(
    /** ISO 639-1, lowercase: "en". */
    val language: String,
    /** ISO 3166-1 alpha-2, uppercase: "US". */
    val country: String,
    /** ISO 4217: "USD". */
    val currencyCode: String,
    /** What the OS reported before [effective] applied the supported-region fallback. */
    val deviceCountry: String = country,
) {
    /** BCP 47 tag for analytics / Firestore: "en-US". */
    val tag: String get() = "$language-$country"

    val region: Region get() = Region.forCountry(country)

    /** True when the device's own country is one the app supports. */
    val isDeviceRegionSupported: Boolean get() = Region.isSupported(deviceCountry)

    companion object {
        val US = LocaleInfo(language = "en", country = "US", currencyCode = "USD")
    }
}

/** Per-country conventions. One entry per launch market; everything else falls back to [US]. */
enum class Region(
    val country: String,
    val currencyCode: String,
    val decimalSeparator: Char,
    val groupingSeparator: Char,
    /** Where the currency symbol sits in a formatted amount. */
    val symbolBefore: Boolean,
    val currencySymbol: String,
    /** How a numeric date on a receipt is printed: "MDY" (09/12/2026) or "DMY". */
    val dateOrder: String,
) {
    US("US", "USD", '.', ',', symbolBefore = true, currencySymbol = "$", dateOrder = "MDY");

    companion object {
        fun isSupported(country: String): Boolean = entries.any { it.country.equals(country, ignoreCase = true) }
        fun forCountry(country: String): Region =
            entries.firstOrNull { it.country.equals(country, ignoreCase = true) } ?: US
        fun forCurrency(currencyCode: String): Region =
            entries.firstOrNull { it.currencyCode.equals(currencyCode, ignoreCase = true) } ?: US
    }
}

/** The device's language and region as the OS reports them. */
expect fun deviceLocaleInfo(): LocaleInfo

/**
 * The locale the app actually runs with: the device's language (the OS already picked a
 * supported one through the string resources) and the device's region when it is a launch
 * market, otherwise US with the device's real country kept in [LocaleInfo.deviceCountry] so
 * the backend can see where unsupported-region users are.
 */
fun LocaleInfo.effective(): LocaleInfo =
    if (Region.isSupported(country)) this
    else LocaleInfo.US.copy(language = language, deviceCountry = country)
