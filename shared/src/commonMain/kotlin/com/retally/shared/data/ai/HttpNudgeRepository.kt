package com.retally.shared.data.ai

import com.retally.shared.domain.ai.Nudge
import com.retally.shared.domain.ai.NudgeRepository
import com.retally.shared.domain.auth.AuthRepository
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.time.Clock

@Serializable
private data class NudgesCallableRequest(val data: NudgesRequest)

@Serializable
private data class NudgesRequest(val refresh: Boolean = false)

@Serializable
private data class GetNudgesResult(
    val status: String = "ok",
    val nudges: List<Nudge> = emptyList(),
)

/**
 * Calls the `getNudges` callable Cloud Function (built in the same server pass as the Smart
 * List) and caches the answer in [Settings]. Signed-in shoppers only; for guests the cache is
 * emptied and nothing is requested.
 */
class HttpNudgeRepository(
    private val http: HttpClient,
    private val auth: AuthRepository,
    private val settings: Settings,
    private val json: Json,
    private val functionUrl: String = DEFAULT_URL,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : NudgeRepository {

    private val _nudges = MutableStateFlow(loadCached())
    override val nudges: StateFlow<List<Nudge>> = _nudges.asStateFlow()

    private val lock = Mutex()
    private var lastFetchAt: Long? = null

    override suspend fun refresh(force: Boolean) {
        if (auth.isAnonymous) {
            if (_nudges.value.isNotEmpty()) clearCache()
            return
        }
        lock.withLock {
            val last = lastFetchAt
            if (!force && last != null && now() - last < MIN_INTERVAL_MS) return
            lastFetchAt = now()
        }
        val token = try {
            auth.getIdToken(forceRefresh = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return
        try {
            val response = http.post(functionUrl) {
                // A rebuild runs the Smart List's Gemini pass too; allow for a cold start.
                timeout { requestTimeoutMillis = 120_000; socketTimeoutMillis = 120_000 }
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(NudgesCallableRequest(NudgesRequest(refresh = false)))
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                log("HTTP ${response.status.value}: ${body.take(200)}")
                lastFetchAt = null // retry on the next appearance
                return
            }
            val result = runCatching { json.parseToJsonElement(body).jsonObject["result"] as? JsonObject }.getOrNull()
                ?.let { json.decodeFromJsonElement(GetNudgesResult.serializer(), it) }
                ?: return log("bad response")
            val list = result.nudges.sortedByDescending { it.priority }
            settings.putString(KEY_NUDGES, json.encodeToString(LIST_SERIALIZER, list))
            _nudges.value = list
            log("${list.size} nudge(s), status=${result.status}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("request failed: ${e.message}")
            lastFetchAt = null
        }
    }

    override fun clearCache() {
        settings.remove(KEY_NUDGES)
        _nudges.value = emptyList()
        lastFetchAt = null
    }

    private fun loadCached(): List<Nudge> =
        settings.getStringOrNull(KEY_NUDGES)?.let { raw ->
            runCatching { json.decodeFromString(LIST_SERIALIZER, raw) }.getOrNull()
        } ?: emptyList()

    private fun log(message: String) = println("[ReTallyNudges] $message")

    companion object {
        const val DEFAULT_URL = "https://us-east1-retally-c5212.cloudfunctions.net/getNudges"
        private const val KEY_NUDGES = "nudges.cache.v1"
        /** Home re-asks at most this often; the server itself refreshes daily and on new receipts. */
        private const val MIN_INTERVAL_MS = 30L * 60 * 1000
        private val LIST_SERIALIZER = ListSerializer(Nudge.serializer())
    }
}
