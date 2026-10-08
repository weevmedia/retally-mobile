package com.retally.shared.domain.location

/**
 * One-shot forward geocode of a free-text address. Implemented per platform with the
 * free on-device service (Android `Geocoder`, iOS `CLGeocoder`) — no API keys, no
 * network SDK. Returns null when the address can't be resolved on this device.
 */
expect suspend fun geocodeAddress(query: String): LatLng?
