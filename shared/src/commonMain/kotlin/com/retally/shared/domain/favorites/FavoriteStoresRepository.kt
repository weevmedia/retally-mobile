package com.retally.shared.domain.favorites

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A store the user hearted — shown as an Instagram-style circle in the More menu. */
@Serializable
data class FavoriteStore(
    /** Normalized identity: lowercase trimmed name. */
    val key: String,
    /** Display name as printed on the receipt. */
    val name: String,
)

/**
 * The user's favorite stores, hearted from receipt previews or picked in the More menu.
 * Backed by multiplatform Settings (survives restarts); the in-memory flow keeps the UI
 * reactive within a session.
 */
class FavoriteStoresRepository(
    private val settings: Settings,
    private val json: Json,
) {

    private val _favorites = MutableStateFlow(load())
    val favorites: StateFlow<List<FavoriteStore>> = _favorites.asStateFlow()

    fun isFavorite(storeName: String): Boolean =
        _favorites.value.any { it.key == keyOf(storeName) }

    /** Toggles the store; returns true when it is a favorite afterwards. */
    fun toggle(storeName: String): Boolean {
        val clean = storeName.trim()
        if (clean.isEmpty()) return false
        val key = keyOf(clean)
        val current = _favorites.value
        val next = if (current.any { it.key == key }) {
            current.filterNot { it.key == key }
        } else {
            current + FavoriteStore(key = key, name = clean)
        }
        save(next)
        return next.any { it.key == key }
    }

    fun remove(key: String) {
        val next = _favorites.value.filterNot { it.key == key }
        if (next.size != _favorites.value.size) save(next)
    }

    fun clear() = save(emptyList())

    private fun save(next: List<FavoriteStore>) {
        _favorites.value = next
        settings.putString(KEY_FAVORITES, json.encodeToString<List<FavoriteStore>>(next))
    }

    private fun load(): List<FavoriteStore> =
        settings.getStringOrNull(KEY_FAVORITES)?.let { raw ->
            runCatching { json.decodeFromString<List<FavoriteStore>>(raw) }.getOrDefault(emptyList())
        }.orEmpty()

    companion object {
        fun keyOf(storeName: String): String = storeName.trim().lowercase()
        private const val KEY_FAVORITES = "favorites.stores.v1"
    }
}
