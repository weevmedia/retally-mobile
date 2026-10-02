package com.retally.shared.domain.location

import kotlinx.serialization.Serializable

/**
 * A grocery store the user has shopped at, with a geofence around it. Known stores are
 * kept in a persistent directory in [StoreLocationRepository] — never from a third-party
 * POI feed, so it only ever covers stores the user actually visits, and deleting
 * receipts never removes a store from the directory.
 *
 * @param id stable id derived from the normalized store name + address.
 * @param radiusMeters geofence radius; 250m covers the store and its parking lot
 * while staying reliable on phone-grade GPS.
 * @param lastSeenAt epoch millis of the most recent receipt seen from this store;
 * used to rank the fence list when the 20-fence cap is hit.
 */
@Serializable
data class StoreLocation(
    val id: String,
    val storeName: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double = DEFAULT_RADIUS_METERS,
    val lastSeenAt: Long = 0L,
) {
    companion object {
        const val DEFAULT_RADIUS_METERS = 250.0
    }
}
