package com.retally.shared.ui.maps

/** Apple Maps universal link; iOS routes it to the Maps app. */
actual fun mapsSearchUrl(query: String): String =
    "https://maps.apple.com/?q=${encodeUriComponent(query)}"
