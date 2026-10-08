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
    private val _locations = MutableStateFlow(loadCached())
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
        val now = Clock.System.now().toEpochMilliseconds()
        val known = loadKnownStores().associateBy { it.id }.toMutableMap()
        for ((key, _) in groups) {
            val (name, address) = key
            val id = locationId(name, address)
            val existing = known[id]
            if (existing != null) {
                // Force the current default radius: entries may carry an older value.
                known[id] = existing.copy(
                    storeName = name,
                    address = address,
                    radiusMeters = StoreLocation.DEFAULT_RADIUS_METERS,
                    lastSeenAt = now,
                )
            } else {
                val query = listOf(name, address.replace("\n", ", "))
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
                val ll = geocodeAddress(query)
                if (ll == null) {
                    // Visible in the Xcode console — tells us whether a missing
                    // fence is a geocoding failure vs. never being registered.
                    println("[StoreLocationRepository] geocode failed for \"$query\" — no fence")
                    continue
                }
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
        val ranked = known.values
            .sortedWith(
                compareByDescending<StoreLocation> { groups[it.storeName to it.address]?.size ?: 0 }
                    .thenByDescending { it.lastSeenAt },
            )
            .take(MAX_GEOFENCES)
        settings[KEY_LOCATIONS] = json.encodeToString(ListSerializer(StoreLocation.serializer()), ranked)
        _locations.value = ranked
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
        if (nowEpochMillis - (last[locationId] ?: 0L) < NOTIFY_COOLDOWN_MS) return false
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
        val queries = database.knownStoreQueries
        if (queries.selectAll().executeAsList().isNotEmpty()) {
            settings.remove(KEY_KNOWN_STORES)
            return
        }
        val raw = settings.getStringOrNull(KEY_KNOWN_STORES) ?: return
        val seeded = runCatching {
            json.decodeFromString(ListSerializer(StoreLocation.serializer()), raw)
        }.getOrDefault(emptyList())
        if (seeded.isNotEmpty()) {
            queries.transaction {
                seeded.forEach { insertKnownStore(it) }
            }
        }
        settings.remove(KEY_KNOWN_STORES)
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
