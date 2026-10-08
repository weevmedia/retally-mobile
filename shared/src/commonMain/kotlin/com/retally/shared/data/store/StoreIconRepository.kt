package com.retally.shared.data.store

import com.retally.shared.data.auth.HttpOtpService
import com.retally.shared.domain.store.StoreIcon
import com.retally.shared.domain.store.iconKeyOf
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Store logos, pushed from the server (`GET getStoreIcons`, same Cloud Functions
 * backend as the OTP flow) so new stores never need an app release.
 *
 * The last fetched map is cached in Settings (keyed by normalized icon key).
 * Lookup is OCR-tolerant via [iconKeyOf]; unknown stores return null and the UI
 * falls back to its monogram tile. Failures are silent.
 */
class StoreIconRepository(
    private val client: HttpClient,
    private val settings: Settings,
    private val json: Json,
    private val baseUrl: String = HttpOtpService.DEFAULT_BASE_URL,
) {
    private val _icons = MutableStateFlow(loadCached())
    val icons: StateFlow<Map<String, StoreIcon>> = _icons.asStateFlow()

    /** Download URL for the store's logo, or null when it has none yet. */
    fun iconUrlFor(storeName: String): String? =
        _icons.value[iconKeyOf(storeName)]?.iconUrl

    /**
     * Pull the latest map from the server. Returns the map in effect after the
     * call (fresh or cached) — never throws.
     */
    suspend fun refresh(): Map<String, StoreIcon> {
        val fetched = runCatching {
            val response = client.get("$baseUrl/getStoreIcons")
            if (!response.status.isSuccess()) return@runCatching null
            response.body<IconsResponse>().icons
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return _icons.value
        settings.putString(KEY_ICONS, json.encodeToString(iconListSerializer, fetched))
        _icons.value = fetched.associateBy { it.key }
        return _icons.value
    }

    private fun loadCached(): Map<String, StoreIcon> {
        val raw = settings.getStringOrNull(KEY_ICONS) ?: return emptyMap()
        return runCatching {
            json.decodeFromString(iconListSerializer, raw)
        }.getOrDefault(emptyList()).associateBy { it.key }
    }

    @Serializable
    private data class IconsResponse(
        val ok: Boolean = false,
        val icons: List<StoreIcon> = emptyList(),
    )

    private companion object {
        const val KEY_ICONS = "store.icons.v1"
        val iconListSerializer = ListSerializer(StoreIcon.serializer())
    }
}
