package com.retally.shared.data.recalls

import com.retally.shared.domain.auth.AuthRepository
import com.retally.shared.domain.recalls.Recall
import com.retally.shared.domain.recalls.RecallsLoadResult
import com.retally.shared.domain.recalls.RecallsRepository
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@Serializable
private data class RecallsCallableRequest(val data: RecallsRequest)

@Serializable
private data class RecallsRequest(val refresh: Boolean = false)

@Serializable
private data class GetRecallsResult(
    val signedIn: Boolean = false,
    val recalls: List<Recall> = emptyList(),
)

/**
 * Calls the `getRecalls` callable Cloud Function (callable protocol over HTTPS) and caches the
 * last result in [Settings]. Recalls are public, so any Firebase user (guest included) may
 * call it; only signed-in shoppers get "you may have bought this" matches.
 */
class HttpRecallsRepository(
    private val http: HttpClient,
    private val auth: AuthRepository,
    private val settings: Settings,
    private val json: Json,
    private val functionUrl: String = DEFAULT_URL,
) : RecallsRepository {

    private val _recalls = MutableStateFlow(loadCached())
    override val recalls: StateFlow<List<Recall>> = _recalls.asStateFlow()

    override suspend fun load(refresh: Boolean): RecallsLoadResult {
        val idToken = try {
            auth.getIdToken()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return try {
            val response = http.post(functionUrl) {
                // A cold start may also run the first FDA/USDA sync.
                timeout { requestTimeoutMillis = 60_000; socketTimeoutMillis = 60_000 }
                if (idToken != null) header(HttpHeaders.Authorization, "Bearer $idToken")
                contentType(ContentType.Application.Json)
                setBody(RecallsCallableRequest(RecallsRequest(refresh)))
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                println("[ReTallyRecalls] HTTP ${response.status.value}: ${body.take(200)}")
                return RecallsLoadResult.Failure("http-${response.status.value}")
            }
            val result = runCatching { json.parseToJsonElement(body).jsonObject["result"] as? JsonObject }.getOrNull()
                ?.let { json.decodeFromJsonElement(GetRecallsResult.serializer(), it) }
                ?: return RecallsLoadResult.Failure("bad-response")
            settings.putString(KEY_RECALLS, json.encodeToString(LIST_SERIALIZER, result.recalls))
            _recalls.value = result.recalls
            RecallsLoadResult.Success(result.recalls, result.signedIn)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("[ReTallyRecalls] request failed: ${e.message}")
            RecallsLoadResult.Failure(e.message ?: "network")
        }
    }

    private fun loadCached(): List<Recall> =
        settings.getStringOrNull(KEY_RECALLS)?.let { raw ->
            runCatching { json.decodeFromString(LIST_SERIALIZER, raw) }.getOrNull()
        } ?: emptyList()

    companion object {
        const val DEFAULT_URL = "https://us-east1-retally-c5212.cloudfunctions.net/getRecalls"
        private const val KEY_RECALLS = "recalls.cache.v1"
        private val LIST_SERIALIZER = ListSerializer(Recall.serializer())
    }
}
