package com.retally.shared.data.category

import com.retally.shared.data.auth.HttpOtpService
import com.retally.shared.domain.category.CategorySource
import com.retally.shared.domain.category.ItemCategory
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
 * Item spending categories, pushed from the server (`GET getCategories`, same
 * Cloud Functions backend as the OTP flow) so the list and its tagging keywords
 * can evolve without an app release.
 *
 * The last fetched list is cached in Settings; before the first successful fetch
 * (or offline with no cache) the bundled grocery + miscellaneous list is served.
 * Failures are silent — the app always has a usable list.
 */
class CategoryRepository(
    private val client: HttpClient,
    private val settings: Settings,
    private val json: Json,
    private val baseUrl: String = HttpOtpService.DEFAULT_BASE_URL,
) : CategorySource {
    private val _categories = MutableStateFlow(loadCached())
    override val categories: StateFlow<List<ItemCategory>> = _categories.asStateFlow()

    /** Display name for a stored category id; falls back to the id itself. */
    fun displayName(categoryId: String): String =
        _categories.value.firstOrNull { it.id == categoryId }?.name ?: categoryId

    /**
     * Pull the latest list from the server. Returns the list in effect after the
     * call (fresh or previously cached/bundled) — never throws.
     */
    suspend fun refresh(): List<ItemCategory> {
        val fetched = runCatching {
            val response = client.get("$baseUrl/getCategories")
            if (!response.status.isSuccess()) return@runCatching null
            response.body<CategoriesResponse>().categories
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return _categories.value
        settings.putString(KEY_CATEGORIES, json.encodeToString(categoryListSerializer, fetched))
        _categories.value = fetched
        return fetched
    }

    private fun loadCached(): List<ItemCategory> {
        val raw = settings.getStringOrNull(KEY_CATEGORIES) ?: return ItemCategory.bundled()
        return runCatching {
            json.decodeFromString(categoryListSerializer, raw)
        }.getOrDefault(ItemCategory.bundled()).takeIf { it.isNotEmpty() }
            ?: ItemCategory.bundled()
    }

    @Serializable
    private data class CategoriesResponse(
        val ok: Boolean = false,
        val categories: List<ItemCategory> = emptyList(),
    )

    private companion object {
        const val KEY_CATEGORIES = "item.categories.v1"
        val categoryListSerializer = ListSerializer(ItemCategory.serializer())
    }
}
