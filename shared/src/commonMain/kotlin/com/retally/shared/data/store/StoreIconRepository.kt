package com.retally.shared.data.store

import com.retally.shared.data.auth.HttpOtpService
import com.retally.shared.domain.store.StoreIcon
import com.retally.shared.domain.store.iconKeyOf
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * Store logos, looked up **one store at a time, on demand** — nothing is prefetched.
 *
 * When a screen needs a store's logo (a freshly scanned receipt, a receipt row, a favourite):
 * 1. [cachedIconUrl] — the device cache answers instantly if this store was seen before;
 * 2. otherwise [resolve] asks the server for that store, saves the answer, and returns it.
 *
 * Only the logo *link* is kept here (Settings, keyed by [iconKeyOf]); the image itself is
 * downloaded and kept by Coil's disk cache the first time it's shown (see `StoreLogo`).
 *
 * A store the server has no logo for is **marked** ("no logo"), so its rows never hit the
 * network on display. Known stores (marked or with a logo, so replaced logos update too) are
 * re-checked in one batched request by [recheckStores] — at app launch and when the
 * Receipts list opens, at most once per
 * [RECHECK_COOLDOWN_MS] — and a freshly scanned or edited store is always re-asked
 * ([resolve] with `recheckMissing`). Failures (offline, server error) are never cached.
 */
class StoreIconRepository(
    private val client: HttpClient,
    private val settings: Settings,
    private val json: Json,
    private val baseUrl: String = HttpOtpService.DEFAULT_BASE_URL,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    /**
     * Cache entry: [iconUrl] null marks a store the server has no logo for (yet).
     * [weeklyAdUrl] is the store's weekly-ad page, set in the admin console (null = none).
     */
    @Serializable
    private data class Entry(val iconUrl: String? = null, val checkedAt: Long = 0L, val weeklyAdUrl: String? = null)

    private val _cache = MutableStateFlow(loadCache())
    private val lookups = mutableMapOf<String, CompletableDeferred<String?>>()
    private val lookupsLock = Mutex()

    private val _view = MutableStateFlow(_cache.value.mapValues { it.value.iconUrl })

    /** Cached logo links by icon key (null = known to have none); logos recompose as lookups land. */
    val cachedIcons: StateFlow<Map<String, String?>> = _view.asStateFlow()

    private val _weeklyAds = MutableStateFlow(adsOf(_cache.value))

    /** Weekly-ad page by icon key, for stores that have one. Arrives with the logo lookup. */
    val weeklyAds: StateFlow<Map<String, String>> = _weeklyAds.asStateFlow()

    /** The cached logo link for [storeName], or null when unknown or known to have none. */
    fun cachedIconUrl(storeName: String): String? = _cache.value[iconKeyOf(storeName)]?.iconUrl

    /** True when the cache has an answer for [storeName]: its logo, or the "no logo" mark. */
    fun isCached(storeName: String): Boolean = iconKeyOf(storeName) in _cache.value

    /** True when [storeName] is marked "no logo". */
    fun isMarkedMissing(storeName: String): Boolean =
        _cache.value[iconKeyOf(storeName)]?.let { it.iconUrl == null } == true

    private var lastRecheckAt: Long? = null
    private val recheckLock = Mutex()

    /**
     * Re-asks the server, in one request, about every store in [storeNames] the cache knows:
     * marked "no logo" stores pick up a logo added since, and stores with a logo pick up a
     * replaced one (a new upload gets a new download link). Skipped when it already ran in the
     * last [RECHECK_COOLDOWN_MS] (e.g. hopping between tabs) unless [force]. Stores never
     * looked up are left to their rows, which resolve them on first display.
     */
    suspend fun recheckStores(storeNames: Collection<String>, force: Boolean = false) {
        // Trimmed and capped like the server does, so its answers key back to the same names.
        val marked = storeNames.map { it.trim().take(128) }.filter { it.isNotEmpty() && isCached(it) }
            .distinctBy { iconKeyOf(it) }
            .take(MAX_BATCH)
        if (marked.isEmpty()) return
        recheckLock.withLock {
            val last = lastRecheckAt
            if (!force && last != null && now() - last < RECHECK_COOLDOWN_MS) return
            lastRecheckAt = now()
        }
        val results = fetchMany(marked) ?: return // failed: keep the marks, try next time
        var changed = 0
        for (name in marked) {
            val key = iconKeyOf(name)
            val found = results[key]
            val url = found?.iconUrl?.takeIf { it.isNotBlank() }
            if (_cache.value[key]?.iconUrl != url) changed++
            save(key, Entry(iconUrl = url, checkedAt = now(), weeklyAdUrl = found?.adUrl()))
        }
        log("re-checked ${marked.size} store(s); $changed logo(s) changed")
    }

    /**
     * Cache first, then the server. Concurrent calls for the same store share one request.
     * Returns the logo link, or null when the store has none (or the server can't be reached).
     *
     * [recheckMissing] skips a cached "no logo" answer and asks again — used right after a
     * receipt is scanned or its store is edited, so a logo added in the console since the last
     * check shows up on that receipt immediately. A cached logo is still used as-is.
     */
    suspend fun resolve(storeName: String, recheckMissing: Boolean = false): String? {
        cachedIconUrl(storeName)?.let { return it }
        if (!recheckMissing && isCached(storeName)) return null
        val key = iconKeyOf(storeName)
        if (key == "unknown") return null
        val (deferred, owner) = lookupsLock.withLock {
            lookups[key]?.let { it to false } ?: CompletableDeferred<String?>().also { lookups[key] = it }.let { it to true }
        }
        if (!owner) return deferred.await()
        return try {
            val result = fetch(storeName, key)
            val found = result?.getOrNull()
            val logo = found?.iconUrl?.takeIf { it.isNotBlank() }
            log("lookup \"$storeName\" (key=$key) -> ${if (result == null) "failed" else logo ?: "no logo"}")
            if (result != null) save(key, Entry(iconUrl = logo, checkedAt = now(), weeklyAdUrl = found?.adUrl()))
            val url = logo ?: cachedIconUrl(storeName)
            deferred.complete(url)
            url
        } catch (e: CancellationException) {
            deferred.complete(null)
            throw e
        } finally {
            lookupsLock.withLock { lookups.remove(key) }
        }
    }

    /**
     * Asks the server for one store. Sends `?store=` so the backend can answer for just that
     * store (resolving aliases). An older backend that ignores it and returns every store still
     * works: only the entry we asked for is kept.
     *
     * @return success(the store's record, or null = unknown store), or null when the lookup failed.
     */
    private suspend fun fetch(storeName: String, key: String): Result<StoreIcon?>? = try {
        val response = client.get("$baseUrl/getStoreIcons") { parameter("store", storeName) }
        if (!response.status.isSuccess()) {
            log("HTTP ${response.status.value} for \"$storeName\"")
            null
        } else {
            val body = response.body<IconsResponse>()
            // New backend: answers for the one store (aliases resolved) in `match`.
            // Older backend: ignores `?store=` and lists every store — keep only ours.
            // Keys typed by hand in the console may carry spaces/dashes ("patel-brothers"), so both
            // the key and the display name are normalised the same way as the receipt's name.
            val match = if (body.lookup) {
                body.match
            } else {
                body.icons.firstOrNull { iconKeyOf(it.key) == key || iconKeyOf(it.name) == key }
            }
            Result.success(match)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log("request failed for \"$storeName\": ${e.message}")
        null
    }

    /**
     * Batch lookup for [names] in one request (`?stores=a&stores=b…`).
     * @return store record by icon key (missing key = unknown store), or null when the request failed.
     */
    private suspend fun fetchMany(names: List<String>): Map<String, StoreIcon>? = try {
        val response = client.get("$baseUrl/getStoreIcons") {
            url { names.forEach { name -> parameters.append("stores", name) } }
        }
        if (!response.status.isSuccess()) {
            log("batch HTTP ${response.status.value}")
            null
        } else {
            val body = response.body<IconsResponse>()
            val wanted = names.map { iconKeyOf(it) }.toSet()
            if (body.lookup) {
                // New backend: answers keyed by the name we sent (aliases resolved server-side).
                names.mapNotNull { name -> body.matches[name]?.let { iconKeyOf(name) to it } }.toMap()
            } else {
                // Older backend: every store listed — keep the ones we asked about.
                body.icons.mapNotNull { icon ->
                    listOf(iconKeyOf(icon.key), iconKeyOf(icon.name)).firstOrNull { it in wanted }?.let { it to icon }
                }.toMap()
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log("batch request failed: ${e.message}")
        null
    }

    private fun log(message: String) = println("[ReTallyStoreIcons] $message")

    private fun save(key: String, entry: Entry) {
        _cache.update { it + (key to entry) }
        _view.value = _cache.value.mapValues { it.value.iconUrl }
        _weeklyAds.value = adsOf(_cache.value)
        settings.putString(KEY_CACHE, json.encodeToString(CACHE_SERIALIZER, _cache.value))
    }

    private fun adsOf(cache: Map<String, Entry>): Map<String, String> =
        cache.mapNotNull { (key, entry) -> entry.weeklyAdUrl?.let { key to it } }.toMap()

    /** Only http(s) links are opened. */
    private fun StoreIcon.adUrl(): String? =
        weeklyAdUrl?.trim()?.takeIf { it.startsWith("https://") || it.startsWith("http://") }

    private fun loadCache(): Map<String, Entry> {
        val raw = settings.getStringOrNull(KEY_CACHE) ?: return emptyMap()
        return runCatching { json.decodeFromString(CACHE_SERIALIZER, raw) }.getOrDefault(emptyMap())
    }

    @Serializable
    private data class IconsResponse(
        val ok: Boolean = false,
        val icons: List<StoreIcon> = emptyList(),
        /** True when the backend handled `?store=`; [match] is then the answer (null = no logo). */
        val lookup: Boolean = false,
        val match: StoreIcon? = null,
        /** Batch answer (`?stores=`): requested name → its logo, or null for none. */
        val matches: Map<String, StoreIcon?> = emptyMap(),
    )

    private companion object {
        /** v4: re-looks-up every store once so cached entries pick up their weekly-ad link. */
        const val KEY_CACHE = "store.icons.v4"
        /** Minimum gap between store re-checks (launch, opening Receipts): 10 minutes. */
        const val RECHECK_COOLDOWN_MS = 10L * 60 * 1000
        /** Most stores sent in one re-check request. */
        const val MAX_BATCH = 50
        val CACHE_SERIALIZER = MapSerializer(String.serializer(), Entry.serializer())
    }
}
