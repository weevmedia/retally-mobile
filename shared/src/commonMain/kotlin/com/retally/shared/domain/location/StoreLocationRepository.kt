/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.domain.location

import com.retally.shared.db.ReTallyDatabase
import com.retally.shared.domain.receipt.ReceiptRepository
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Clock

/**
 * Geofenced grocery stores, kept in a persistent known-stores table in the shared
 * SQLite database ([ReTallyDatabase]).
 *
 * - [refreshLocations] upserts every store seen on a receipt (geocoding new
 *   ones once) and re-derives the fence list from the table. The table
 *   only grows — deleting receipts never removes a store, so its geofence
 *   survives until the next refresh re-arms it.
 * - [clearKnownStores] wipes the table; called only by delete-all-receipts
 *   and account deletion.
 * - [shouldNotifyAndMark] debounces entry alerts to one per store per 24 hours, so a
 *   quick in-and-out of the parking lot doesn't spam the user.
 * - The enable toggle is Settings-backed and read by both platform hosts at launch.
 *
 * First run after the Settings-JSON era migrates the old `nearby.known_stores.v1`
 * list into the table once, then drops the key so it can never re-seed.
 *
 * The fence list is capped at [MAX_GEOFENCES] (the iOS region-monitoring limit),
 * ranked by current receipt count, then by recency.
 */
class StoreLocationRepository(
    private val receipts: ReceiptRepository,
    private val settings: Settings,
    private val json: Json,
    private val database: ReTallyDatabase,
) {
    private val _locations = MutableStateFlow(
        runCatching { loadCached() }.getOrDefault(emptyList())
    )
    val locations: StateFlow<List<StoreLocation>> = _locations.asStateFlow()

    val isEnabled: Boolean get() = settings[KEY_ENABLED, false]

    fun setEnabled(enabled: Boolean) {
        settings[KEY_ENABLED] = enabled
    }

    /**
     * Rebuild the fence list from the persistent known-stores table.
     *
     * Every store seen on a receipt is upserted into the table (new ones
     * geocoded once); the table is never pruned here. The fence list is
     * re-derived from the table on every refresh, so a store keeps its
     * geofence even after all of its receipts are deleted. Ranked by current
     * receipt count, then by recency; capped at [MAX_GEOFENCES].
     */
    suspend fun refreshLocations() {
        val all = receipts.observeReceipts().first()
        val groups = all
            .groupBy { it.storeName.trim() to it.storeAddress.orEmpty().trim() }
            .filterKeys { (name, _) -> name.isNotBlank() }
        nearbyLog("zone refresh: ${all.size} receipts, ${groups.size} stores on receipts")
        val now = Clock.System.now().toEpochMilliseconds()
        val known = loadKnownStores().associateBy { it.id }.toMutableMap()
        // Where the user shops: lookups far from here are wrong (e.g. a "Whole Foods" placed in
        // Australia from a garbled address) and are rejected.
        val home = homeArea(known.values)
        for ((key, _) in groups) {
            val (name, address) = key
            val id = locationId(name, address)
            val existing = known[id]
            if (existing != null) {
                // Force the current default radius: entries may carry an older value.
                nearbyLog("zone known: \"$name\" (${existing.latitude}, ${existing.longitude})")
                known[id] = existing.copy(
                    storeName = name,
                    address = address,
                    radiusMeters = StoreLocation.DEFAULT_RADIUS_METERS,
                    lastSeenAt = now,
                )
            } else {
                val ll = geocodeStore(name, address, home)
                if (ll == null) {
                    nearbyLog("zone lookup FAILED for \"$name\" — no zone for this store")
                    continue
                }
                nearbyLog("zone lookup OK: \"$name\" → (${ll.latitude}, ${ll.longitude})")
                known[id] = StoreLocation(
                    id = id,
                    storeName = name,
                    address = address,
                    latitude = ll.latitude,
                    longitude = ll.longitude,
                    lastSeenAt = now,
                )
            }
        }
        saveKnownStores(known.values.toList())
        val receiptCount = { loc: StoreLocation -> groups[loc.storeName to loc.address]?.size ?: 0 }

        // Drop zones far from where the user shops (bad lookups); only once there's a clear home area.
        val homeNow = homeArea(known.values)
        val plausible = known.values.filter { loc ->
            val far = homeNow != null && distanceMeters(loc.latitude, loc.longitude, homeNow.latitude, homeNow.longitude) > MAX_DISTANCE_FROM_HOME_M
            if (far) nearbyLog("zone dropped: \"${loc.storeName}\" (${loc.latitude}, ${loc.longitude}) is far from your other stores")
            !far
        }

        // One zone per place: the same store read slightly differently off several receipts
        // ("Walmart" ×7 at the same spot) used to take up to 7 of the 20 slots. Zones whose
        // centres are within MERGE_DISTANCE_M merge; the best-known one stays, counts add up.
        val byStrength = plausible.sortedWith(
            compareByDescending<StoreLocation> { receiptCount(it) }.thenByDescending { it.lastSeenAt },
        )
        val merged = mutableListOf<Pair<StoreLocation, Int>>()
        for (loc in byStrength) {
            val i = merged.indexOfFirst { (kept, _) ->
                distanceMeters(kept.latitude, kept.longitude, loc.latitude, loc.longitude) <= MERGE_DISTANCE_M
            }
            if (i >= 0) {
                val (kept, count) = merged[i]
                merged[i] = kept.copy(lastSeenAt = maxOf(kept.lastSeenAt, loc.lastSeenAt)) to count + receiptCount(loc)
            } else {
                merged += loc to receiptCount(loc)
            }
        }
        if (merged.size < plausible.size) nearbyLog("zone merge: ${plausible.size} stores → ${merged.size} distinct places")

        val testZone = testZone()
        val ranked = (listOfNotNull(testZone) + merged
            .sortedWith(compareByDescending<Pair<StoreLocation, Int>> { it.second }.thenByDescending { it.first.lastSeenAt })
            .map { it.first })
            .take(MAX_GEOFENCES)
        settings[KEY_LOCATIONS] = json.encodeToString(ListSerializer(StoreLocation.serializer()), ranked)
        _locations.value = ranked
        nearbyLog(
            "zone refresh done: ${known.size} known stores, ${ranked.size} zones → " +
                ranked.joinToString { "\"${it.storeName}\"" },
        )
    }

    /**
     * Wipe the known-stores table and the derived fence list. Called only by
     * delete-all-receipts and account deletion — the nuclear options. Deleting a
     * single receipt never calls this; its store keeps its geofence.
     */
    fun clearKnownStores() {
        ensureMigrated()
        database.knownStoreQueries.deleteAll()
        settings.remove(KEY_KNOWN_STORES)
        settings[KEY_LOCATIONS] = "[]"
        _locations.value = emptyList()
    }

    /**
     * True when an entry alert should fire for [locationId] right now. Records the
     * timestamp so repeat entries within [NOTIFY_COOLDOWN_MS] are suppressed.
     */
    fun shouldNotifyAndMark(locationId: String, nowEpochMillis: Long): Boolean {
        val last = loadLastNotified()
        val since = last[locationId]?.let { (nowEpochMillis - it) / 60_000 }
        nearbyLog("zone entered: ${locationName(locationId) ?: locationId} (last alert ${since?.let { "$it min ago" } ?: "never"})")
        // TEMP-TESTING (restore before release): the one-alert-per-store-per-24h limit is off so
        // every geofence entry notifies. Uncomment the line below to bring the limit back.
        // if (nowEpochMillis - (last[locationId] ?: 0L) < NOTIFY_COOLDOWN_MS) return false
        // Always on: the app-start "already inside" check and the OS entry event can report the
        // same arrival a moment apart — alert once.
        if (nowEpochMillis - (last[locationId] ?: 0L) < DUPLICATE_WINDOW_MS) {
            nearbyLog("zone entered again within ${DUPLICATE_WINDOW_MS / 1000}s — duplicate, no alert")
            return false
        }
        settings[KEY_LAST_NOTIFIED] = json.encodeToString(
            MapSerializer(String.serializer(), Long.serializer()),
            last + (locationId to nowEpochMillis),
        )
        return true
    }

    /**
     * DEBUG-ONLY (remove before release): clears the 24h per-store notification
     * cooldowns so geofence alerts can be re-tested immediately.
     */
    fun resetNotificationCooldowns() {
        settings.remove(KEY_LAST_NOTIFIED)
    }

    /**
     * Coordinates for a store, trying progressively simpler queries: the full name + address,
     * then name + "City, ST ZIP" (or just the ZIP) pulled out of a garbled address. A result
     * far from [home] is rejected and the next query tried.
     */
    private suspend fun geocodeStore(name: String, address: String, home: LatLng?): LatLng? {
        val flat = address.replace("\n", ", ")
        val locality = LOCALITY.find(flat)?.value?.trim() ?: ZIP.find(flat)?.value
        val queries = listOfNotNull(
            listOf(name, flat).filter { it.isNotBlank() }.joinToString(", "),
            locality?.let { "$name, $it" },
        ).distinct()
        for (query in queries) {
            nearbyLog("zone lookup: geocoding \"$query\"")
            val ll = geocodeAddress(query) ?: continue
            if (home != null && distanceMeters(ll.latitude, ll.longitude, home.latitude, home.longitude) > MAX_DISTANCE_FROM_HOME_M) {
                nearbyLog("zone lookup: \"$query\" → (${ll.latitude}, ${ll.longitude}) is far from your other stores, ignored")
                continue
            }
            nearbyLog("zone lookup OK: \"$name\" → (${ll.latitude}, ${ll.longitude})")
            return ll
        }
        return null
    }

    /** Median position of the known stores; null until there are enough to trust. */
    private fun homeArea(stores: Collection<StoreLocation>): LatLng? {
        if (stores.size < MIN_STORES_FOR_HOME) return null
        fun median(values: List<Double>) = values.sorted().let { it[it.size / 2] }
        return LatLng(median(stores.map { it.latitude }), median(stores.map { it.longitude }))
    }

    /**
     * Debug builds only: a fixed test zone ([NearbyTestZone.address]) always registered first,
     * so alerts can be tested at a known spot without a receipt from there.
     */
    private suspend fun testZone(): StoreLocation? {
        val address = NearbyTestZone.address ?: return null
        val ll = NearbyTestZone.cached ?: geocodeAddress(address)?.also { NearbyTestZone.cached = it }
        if (ll == null) {
            nearbyLog("test zone lookup FAILED for \"$address\"")
            return null
        }
        nearbyLog("test zone: \"$address\" → (${ll.latitude}, ${ll.longitude})")
        return StoreLocation(
            id = NearbyTestZone.ID,
            storeName = NearbyTestZone.NAME,
            address = address,
            latitude = ll.latitude,
            longitude = ll.longitude,
        )
    }

    fun locationName(locationId: String): String? =
        _locations.value.firstOrNull { it.id == locationId }?.storeName
            ?: loadCached().firstOrNull { it.id == locationId }?.storeName

    private fun loadCached(): List<StoreLocation> {
        val raw = settings.getStringOrNull(KEY_LOCATIONS) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(StoreLocation.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private fun loadKnownStores(): List<StoreLocation> {
        ensureMigrated()
        return database.knownStoreQueries.selectAll().executeAsList().map { row ->
            StoreLocation(
                id = row.id,
                storeName = row.store_name,
                address = row.address,
                latitude = row.latitude,
                longitude = row.longitude,
                radiusMeters = row.radius_meters,
                lastSeenAt = row.last_seen_at,
            )
        }
    }

    private fun saveKnownStores(stores: List<StoreLocation>) {
        ensureMigrated()
        database.knownStoreQueries.transaction {
            stores.forEach { insertKnownStore(it) }
        }
    }

    private fun insertKnownStore(store: StoreLocation) {
        database.knownStoreQueries.insertOrReplace(
            id = store.id,
            storeName = store.storeName,
            address = store.address,
            latitude = store.latitude,
            longitude = store.longitude,
            radiusMeters = store.radiusMeters,
            lastSeenAt = store.lastSeenAt,
        )
    }

    /**
     * One-time move of the pre-SQLite `nearby.known_stores.v1` Settings JSON into
     * the `known_store` table. Runs lazily on first DB access; the Settings key is
     * removed afterwards so it can never re-seed — this matters because
     * [clearKnownStores] must leave the table empty.
     */
    private var migrationDone = false

    private fun ensureMigrated() {
        if (migrationDone) return
        migrationDone = true
        // Guard against corrupted DB or bad legacy data: on any failure, drop the
        // legacy key so we never retry, and continue with an empty table. Geofences
        // rebuild from receipts on the next refreshLocations() (scan-save).
        runCatching {
            val queries = database.knownStoreQueries
            if (queries.selectAll().executeAsList().isNotEmpty()) {
                settings.remove(KEY_KNOWN_STORES)
                return@runCatching
            }
            val raw = settings.getStringOrNull(KEY_KNOWN_STORES) ?: return@runCatching
            val seeded = runCatching {
                json.decodeFromString(ListSerializer(StoreLocation.serializer()), raw)
            }.getOrDefault(emptyList())
            if (seeded.isNotEmpty()) {
                queries.transaction {
                    seeded.forEach { insertKnownStore(it) }
                }
            }
            settings.remove(KEY_KNOWN_STORES)
        }.onFailure {
            runCatching { settings.remove(KEY_KNOWN_STORES) }
        }
    }

    private fun loadLastNotified(): Map<String, Long> {
        val raw = settings.getStringOrNull(KEY_LAST_NOTIFIED) ?: return emptyMap()
        return runCatching {
            json.decodeFromString(MapSerializer(String.serializer(), Long.serializer()), raw)
        }.getOrDefault(emptyMap())
    }

    companion object {
        /** iOS caps an app at 20 monitored regions; Android allows 100. */
        const val MAX_GEOFENCES = 20
        const val NOTIFY_COOLDOWN_MS = 24L * 60 * 60 * 1000
        /**
         * Two reports of the same zone this close together are one arrival (the start-up check
         * and the OS event land seconds apart). Short, so kill + relaunch still alerts again.
         */
        const val DUPLICATE_WINDOW_MS = 20_000L
        /** Zones closer than this are the same place (duplicate receipts of one store). */
        const val MERGE_DISTANCE_M = 150.0
        /** A store farther than this from the user's other stores is a bad lookup. */
        const val MAX_DISTANCE_FROM_HOME_M = 300_000.0
        /** Known stores needed before "far from the others" is judged. */
        const val MIN_STORES_FOR_HOME = 3
        /** "Lakeville, MA 02347" in an address line. */
        private val LOCALITY = Regex("""[A-Za-z][A-Za-z .'-]+,?\s+[A-Z]{2}\s+\d{5}""")
        private val ZIP = Regex("""\b\d{5}\b""")

        private const val KEY_ENABLED = "nearby.enabled"
        private const val KEY_LOCATIONS = "nearby.store_locations.v1"
        /** Legacy pre-SQLite directory; read once by [ensureMigrated], then removed. */
        private const val KEY_KNOWN_STORES = "nearby.known_stores.v1"
        private const val KEY_LAST_NOTIFIED = "nearby.last_notified.v1"

        /** Stable across launches: String.hashCode is specified, not identity-based. */
        fun locationId(storeName: String, address: String): String =
            (storeName.trim().lowercase() + "|" + address.trim().lowercase()).hashCode()
                .let { if (it == Int.MIN_VALUE) "0" else it.toString() }
                .let { if (it.startsWith("-")) "n" + it.drop(1) else "p$it" }
    }
}

/**
 * Nearby-store diagnostics, tagged `[Nearby]` on both platforms (Logcat on Android, the Xcode
 * console on iOS), so one filter shows the whole path: zone lookup → registration → entry → alert.
 */
fun nearbyLog(message: String) = println("[Nearby] $message")

/** Great-circle distance in metres (haversine). */
internal fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = (lat2 - lat1) * PI / 180
    val dLon = (lon2 - lon1) * PI / 180
    val a = sin(dLat / 2).pow(2) + cos(lat1 * PI / 180) * cos(lat2 * PI / 180) * sin(dLon / 2).pow(2)
    return 2 * r * asin(sqrt(a))
}

/**
 * TEMP-TESTING (debug builds only): a fixed address that always gets a nearby-store zone, so
 * alerts can be tested there. Set at app start in debug builds; null in release (no test zone).
 */
object NearbyTestZone {
    const val ID = "debug-test-zone"
    const val NAME = "Home Store"
    var address: String? = null
    internal var cached: LatLng? = null
}
