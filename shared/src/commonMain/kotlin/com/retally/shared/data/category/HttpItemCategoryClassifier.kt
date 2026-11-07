/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.data.category

import com.retally.shared.core.log.debugLog
import com.retally.shared.domain.auth.AuthRepository
import com.retally.shared.domain.category.ItemCategoryClassifier
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

@Serializable
private data class CategorizeCallableRequest(val data: CategorizeRequest)

@Serializable
private data class CategorizeRequest(val store: String, val items: List<String>)

/** Calls the `categorizeItems` callable Cloud Function. Works for guests and signed-in users. */
class HttpItemCategoryClassifier(
    private val http: HttpClient,
    private val auth: AuthRepository,
    private val json: Json,
    private val functionUrl: String = DEFAULT_URL,
) : ItemCategoryClassifier {

    override suspend fun classify(storeName: String, itemNames: List<String>): Map<String, String>? {
        // Send cleaned names, but answer with the caller's exact names: callers look results up
        // by `item.name`. OCR differs per platform (iOS Vision can leave trailing / non-breaking
        // spaces where ML Kit doesn't), so a trimmed key used to miss and the item stayed
        // "Uncategorized" on iOS while the same receipt worked on Android.
        val originalsByKey = itemNames
            .filter { normalize(it).isNotEmpty() }
            .groupBy { normalize(it).lowercase() }
        val names = originalsByKey.values.map { normalize(it.first()) }
        if (names.isEmpty()) return emptyMap()
        val token = try {
            auth.getIdToken(forceRefresh = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: run {
            // No Firebase user yet (guest sign-in not finished): items keep their keyword guess.
            log("skipped: no Firebase ID token")
            return null
        }
        val out = mutableMapOf<String, String>()
        for (chunk in names.chunked(MAX_PER_CALL)) {
            try {
                val response = http.post(functionUrl) {
                    timeout { requestTimeoutMillis = 60_000; socketTimeoutMillis = 60_000 }
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(CategorizeCallableRequest(CategorizeRequest(storeName.take(80), chunk)))
                }
                val body = response.bodyAsText()
                if (!response.status.isSuccess()) {
                    log("HTTP ${response.status.value}: ${body.take(200)}")
                    return out.takeIf { it.isNotEmpty() }
                }
                val categories = runCatching {
                    (json.parseToJsonElement(body).jsonObject["result"] as? JsonObject)
                        ?.get("categories") as? JsonObject
                }.getOrNull() ?: continue
                for ((name, value) in categories) {
                    val id = (value as? JsonPrimitive)?.contentOrNull ?: continue
                    // Match the server's key back to every original spelling of that name.
                    originalsByKey[normalize(name).lowercase()]?.forEach { original -> out[original] = id }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("request failed: ${e.message}")
                return out.takeIf { it.isNotEmpty() }
            }
        }
        val missed = itemNames.filter { it !in out && normalize(it).isNotEmpty() }
        log("${out.size}/${itemNames.size} item(s) categorised" + if (missed.isEmpty()) "" else " — no category for: ${missed.take(10)}")
        return out
    }

    /** Trims and collapses whitespace, including non-breaking spaces from OCR. */
    private fun normalize(name: String): String =
        name.replace('\u00A0', ' ').replace(WHITESPACE, " ").trim()

    private fun log(message: String) = debugLog("ReTallyCategories") { "$message" }

    companion object {
        const val DEFAULT_URL = "https://us-east1-retally-c5212.cloudfunctions.net/categorizeItems"
        /** The server takes up to 120 lines per call. */
        private const val MAX_PER_CALL = 100
        private val WHITESPACE = Regex("\\s+")
    }
}
