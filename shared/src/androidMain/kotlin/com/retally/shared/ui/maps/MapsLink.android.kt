package com.retally.shared.ui.maps

/** Universal Google Maps search link: opens the Maps app when installed, the browser otherwise. */
actual fun mapsSearchUrl(query: String): String =
    "https://www.google.com/maps/search/?api=1&query=${encodeUriComponent(query)}"
