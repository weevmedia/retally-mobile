/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

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
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

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
            val recalls = withinWindow(result.recalls)
            settings.putString(KEY_RECALLS, json.encodeToString(LIST_SERIALIZER, recalls))
            _recalls.value = recalls
            RecallsLoadResult.Success(result.recalls, result.signedIn)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("[ReTallyRecalls] request failed: ${e.message}")
            RecallsLoadResult.Failure(e.message ?: "network")
        }
    }

    override fun clearCache() {
        settings.remove(KEY_RECALLS)
        _recalls.value = emptyList()
    }

    /** Last saved list, minus anything that has aged out of the 60-day window since it was saved. */
    private fun loadCached(): List<Recall> =
        settings.getStringOrNull(KEY_RECALLS)?.let { raw ->
            runCatching { json.decodeFromString(LIST_SERIALIZER, raw) }.getOrNull()
        }?.let(::withinWindow) ?: emptyList()

    /**
     * Keeps recalls published in the last [WINDOW_DAYS] days — the same window the server
     * uses — so a phone that stays offline stops showing old notices. Unparseable dates are
     * kept rather than silently hidden.
     */
    private fun withinWindow(list: List<Recall>): List<Recall> {
        val cutoff = Clock.System.now() - WINDOW_DAYS.days
        return list.filter { recall ->
            runCatching { Instant.parse(recall.publishedAt) >= cutoff }.getOrDefault(true)
        }
    }

    companion object {
        const val DEFAULT_URL = "https://us-east1-retally-c5212.cloudfunctions.net/getRecalls"
        private const val KEY_RECALLS = "recalls.cache.v1"
        /** Matches the backend's window and the "Recalls from the last 60 days" note. */
        private const val WINDOW_DAYS = 60
        private val LIST_SERIALIZER = ListSerializer(Recall.serializer())
    }
}
