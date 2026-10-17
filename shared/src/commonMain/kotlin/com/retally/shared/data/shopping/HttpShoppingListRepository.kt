package com.retally.shared.data.shopping

import com.retally.shared.domain.auth.AuthRepository
import com.retally.shared.domain.shopping.ShoppingList
import com.retally.shared.domain.shopping.ShoppingListLoadResult
import com.retally.shared.domain.shopping.ShoppingListRepository
import com.retally.shared.domain.shopping.normalizeStoreName
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
private data class CallableRequest(val data: GetShoppingListRequest)

@Serializable
private data class GetShoppingListRequest(val storeName: String? = null, val refresh: Boolean = false)

@Serializable
private data class GetShoppingListResult(val status: String = "ok", val lists: List<ShoppingList> = emptyList())

/** On-device edits for one store's list. */
@Serializable
private data class StoreEdits(
    val checked: Set<String> = emptySet(),
    val dismissed: Set<String> = emptySet(),
    val added: List<String> = emptyList(),
    /** generatedAt of the list the dismissals belong to; a new list resets them. */
    val listGeneratedAt: String = "",
)

/**
 * Calls the `getShoppingList` callable Cloud Function over HTTPS (callable protocol:
 * `{"data": …}` in, `{"result": …}` / `{"error": …}` out, Firebase ID token as Bearer) and
 * caches the lists plus the shopper's edits in [Settings].
 */
class HttpShoppingListRepository(
    private val http: HttpClient,
    private val auth: AuthRepository,
    private val settings: Settings,
    private val json: Json,
    private val functionUrl: String = DEFAULT_URL,
) : ShoppingListRepository {

    private val _lists = MutableStateFlow(loadCachedLists())
    override val lists: StateFlow<List<ShoppingList>> = _lists.asStateFlow()

    override suspend fun load(storeName: String?, refresh: Boolean): ShoppingListLoadResult {
        // Only a genuinely anonymous (or signed-out) user is a guest. A server-side rejection of a
        // signed-in user is an auth problem to retry, not a reason to show the sign-in screen.
        if (auth.isAnonymous) return ShoppingListLoadResult.SignInRequired
        val first = call(storeName, refresh, forceTokenRefresh = false)
        if (first !is CallOutcome.AuthRejected) return first.toResult()
        // Cached ID token may be stale (e.g. right after a custom-token sign-in): mint a new one, retry once.
        log("auth rejected (${first.reason}); retrying with a fresh ID token")
        return when (val second = call(storeName, refresh, forceTokenRefresh = true)) {
            is CallOutcome.AuthRejected -> {
                log("auth rejected again (${second.reason})")
                ShoppingListLoadResult.Failure("auth-${second.reason}")
            }
            else -> second.toResult()
        }
    }

    private sealed interface CallOutcome {
        data class Done(val result: ShoppingListLoadResult) : CallOutcome
        data class AuthRejected(val reason: String) : CallOutcome
    }

    private fun CallOutcome.toResult(): ShoppingListLoadResult = when (this) {
        is CallOutcome.Done -> result
        is CallOutcome.AuthRejected -> ShoppingListLoadResult.Failure("auth-$reason")
    }

    private suspend fun call(storeName: String?, refresh: Boolean, forceTokenRefresh: Boolean): CallOutcome {
        val idToken = try {
            auth.getIdToken(forceRefresh = forceTokenRefresh)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return CallOutcome.AuthRejected("no-token")

        return try {
            val response = http.post(functionUrl) {
                // A rebuild runs Gemini once per store; cold start + AI can take ~30s.
                timeout { requestTimeoutMillis = 120_000; socketTimeoutMillis = 120_000 }
                header(HttpHeaders.Authorization, "Bearer $idToken")
                contentType(ContentType.Application.Json)
                setBody(CallableRequest(GetShoppingListRequest(storeName?.takeIf { it.isNotBlank() }, refresh)))
            }
            val body = response.bodyAsText()
            val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            if (!response.status.isSuccess()) {
                val message = (root?.get("error") as? JsonObject)?.get("message")?.jsonPrimitive?.content
                log("HTTP ${response.status.value}: ${message ?: body.take(200)}")
                return if (message == "sign-in-required" || response.status.value == 401) {
                    CallOutcome.AuthRejected(message ?: "http-401")
                } else {
                    CallOutcome.Done(ShoppingListLoadResult.Failure("http-${response.status.value}"))
                }
            }
            val result = root?.get("result")
                ?.let { json.decodeFromJsonElement(GetShoppingListResult.serializer(), it) }
                ?: return CallOutcome.Done(ShoppingListLoadResult.Failure("bad-response"))
            when (result.status) {
                "guest" -> CallOutcome.AuthRejected("server-says-guest")
                else -> {
                    saveLists(result.lists)
                    CallOutcome.Done(ShoppingListLoadResult.Success(result.lists, noHistory = result.status == "no-history"))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("request failed: ${e.message}")
            CallOutcome.Done(ShoppingListLoadResult.Failure(e.message ?: "network"))
        }
    }

    private fun log(message: String) = println("[ReTallyShopping] $message")

    override var pendingStoreName: String? = null

    override fun cachedListFor(storeName: String): ShoppingList? {
        val key = normalizeStoreName(storeName)
        return _lists.value.firstOrNull { it.storeKey == key }
            ?: _lists.value.firstOrNull { key.isNotEmpty() && (it.storeKey.startsWith(key) || key.startsWith(it.storeKey)) }
    }

    // ---- edits ----

    override fun checkedItems(storeKey: String): Set<String> = edits(storeKey).checked

    override fun setChecked(storeKey: String, item: String, checked: Boolean) = updateEdits(storeKey) {
        it.copy(checked = if (checked) it.checked + item else it.checked - item)
    }

    override fun dismiss(storeKey: String, item: String) = updateEdits(storeKey) {
        it.copy(dismissed = it.dismissed + item, checked = it.checked - item, added = it.added - item)
    }

    override fun dismissedItems(storeKey: String): Set<String> = edits(storeKey).dismissed

    override fun addedItems(storeKey: String): List<String> = edits(storeKey).added

    override fun addItem(storeKey: String, item: String) {
        val clean = item.trim().replaceFirstChar { it.uppercase() }
        if (clean.isEmpty()) return
        updateEdits(storeKey) {
            if (it.added.any { a -> a.equals(clean, ignoreCase = true) }) it
            else it.copy(added = it.added + clean, dismissed = it.dismissed - clean)
        }
    }

    override fun finishShopping(storeKey: String) = updateEdits(storeKey) {
        it.copy(checked = emptySet(), added = it.added.filterNot { a -> a in it.checked })
    }

    // ---- persistence ----

    private fun loadCachedLists(): List<ShoppingList> =
        settings.getStringOrNull(KEY_LISTS)?.let { raw ->
            runCatching { json.decodeFromString(LISTS_SERIALIZER, raw) }.getOrNull()
        } ?: emptyList()

    private fun saveLists(lists: List<ShoppingList>) {
        // Keep lists for stores the server did not return this time (e.g. a single-store refresh).
        val merged = (lists + _lists.value.filterNot { old -> lists.any { it.storeKey == old.storeKey } })
            .sortedByDescending { it.generatedAt }
        settings.putString(KEY_LISTS, json.encodeToString(LISTS_SERIALIZER, merged))
        _lists.value = merged
        // A rebuilt list starts with no dismissals; checks and added items survive mid-trip refreshes.
        val all = allEdits().toMutableMap()
        for (list in lists) {
            val e = all[list.storeKey] ?: continue
            if (e.listGeneratedAt != list.generatedAt) {
                all[list.storeKey] = e.copy(dismissed = emptySet(), listGeneratedAt = list.generatedAt)
            }
        }
        settings.putString(KEY_EDITS, json.encodeToString(EDITS_SERIALIZER, all))
    }

    private fun allEdits(): Map<String, StoreEdits> =
        settings.getStringOrNull(KEY_EDITS)?.let { raw ->
            runCatching { json.decodeFromString(EDITS_SERIALIZER, raw) }.getOrNull()
        } ?: emptyMap()

    private fun edits(storeKey: String): StoreEdits = allEdits()[storeKey] ?: StoreEdits()

    private fun updateEdits(storeKey: String, block: (StoreEdits) -> StoreEdits) {
        val all = allEdits().toMutableMap()
        val current = all[storeKey]
            ?: StoreEdits(listGeneratedAt = _lists.value.firstOrNull { it.storeKey == storeKey }?.generatedAt.orEmpty())
        all[storeKey] = block(current)
        settings.putString(KEY_EDITS, json.encodeToString(EDITS_SERIALIZER, all))
    }

    companion object {
        const val DEFAULT_URL = "https://us-east1-retally-c5212.cloudfunctions.net/getShoppingList"
        private const val KEY_LISTS = "shopping.lists.v1"
        private const val KEY_EDITS = "shopping.edits.v1"
        private val LISTS_SERIALIZER = ListSerializer(ShoppingList.serializer())
        private val EDITS_SERIALIZER = MapSerializer(String.serializer(), StoreEdits.serializer())
    }
}
