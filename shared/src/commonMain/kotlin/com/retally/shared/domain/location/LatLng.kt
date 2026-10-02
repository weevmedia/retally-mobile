package com.retally.shared.domain.location

import kotlinx.serialization.Serializable

/** A latitude/longitude pair from the platform geocoder. */
@Serializable
data class LatLng(
    val latitude: Double,
    val longitude: Double,
)
