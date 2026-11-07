/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.ui.maps

/** Universal Google Maps search link: opens the Maps app when installed, the browser otherwise. */
actual fun mapsSearchUrl(query: String): String =
    "https://www.google.com/maps/search/?api=1&query=${encodeUriComponent(query)}"
