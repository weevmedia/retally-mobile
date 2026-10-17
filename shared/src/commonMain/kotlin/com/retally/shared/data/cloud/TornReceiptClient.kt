package com.retally.shared.data.cloud

import com.retally.shared.domain.auth.AuthRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One line item from the Gemini response. */
@Serializable
data class TornReceiptItemDto(
    val name: String,
    val price: Double,
    val confidence: Double = 1.0,
)

/** Structured receipt from the parseTornReceipt Cloud Function. */
@Serializable
data class TornReceiptDto(
    val storeName: String? = null,
    val storeNameConfidence: Double = 0.0,
    val purchaseDate: String? = null, // YYYY-MM-DD
    val purchaseDateConfidence: Double = 0.0,
    val items: List<TornReceiptItemDto> = emptyList(),
    val subtotal: Double? = null,
    val subtotalConfidence: Double = 0.0,
    val tax: Double? = null,
    val taxConfidence: Double = 0.0,
    val total: Double? = null,
    val totalConfidence: Double = 0.0,
    val paymentInfo: String? = null,
)

@Serializable
private data class ParseTornReceiptRequest(
    val imageBase64: String,
    val mimeType: String = "image/jpeg",
)

@Serializable
private data class ParseTornReceiptResponse(
    val ok: Boolean,
    val receipt: TornReceiptDto? = null,
    val error: String? = null,
)

/** Result of the AI fallback call. Never throws — failures are [Failure]. */
sealed interface TornReceiptResult {
    data class Success(val receipt: TornReceiptDto) : TornReceiptResult
    data class Failure(val reason: String) : TornReceiptResult
}

/**
 * Calls the parseTornReceipt Cloud Function (Gemini fallback for torn/damaged receipts).
 *
 * The function URL is region + project based:
 * `https://us-east1-<project>.cloudfunctions.net/parseTornReceipt`
 */
class TornReceiptClient(
    private val http: HttpClient,
    private val auth: AuthRepository,
    private val functionUrl: String,
) {
    suspend fun parse(imageBase64: String, mimeType: String = "image/jpeg"): TornReceiptResult {
        val idToken = try {
            auth.getIdToken()
        } catch (e: Exception) {
            null
        } ?: return TornReceiptResult.Failure("not-signed-in")

        return try {
            val response = http.post(functionUrl) {
                // Gemini needs time: cold start (5-10s) + image upload + AI processing.
                // 60s covers the worst case; the global 15s default is too short.
                timeout {
                    requestTimeoutMillis = 60_000
                    connectTimeoutMillis = 10_000
                }
                header(HttpHeaders.Authorization, "Bearer $idToken")
                contentType(ContentType.Application.Json)
                setBody(ParseTornReceiptRequest(imageBase64, mimeType))
            }
            if (!response.status.isSuccess()) {
                return TornReceiptResult.Failure("http-${response.status.value}")
            }
            // Log the raw JSON for debugging — shows exactly what Gemini returned.
            val rawJson: String = response.body()
            println("[ReTallyScan] gemini-raw: $rawJson")
            val body = try {
                kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    .decodeFromString<ParseTornReceiptResponse>(rawJson)
            } catch (e: Exception) {
                return TornReceiptResult.Failure("json-parse-error: ${e.message}")
            }
            if (body.ok && body.receipt != null) {
                TornReceiptResult.Success(body.receipt)
            } else {
                TornReceiptResult.Failure(body.error ?: "unknown")
            }
        } catch (e: Exception) {
            TornReceiptResult.Failure(e.message ?: "network-error")
        }
    }

    companion object {
        /** Default URL for the retally-c5212 project, us-east1 region. */
        const val DEFAULT_URL = "https://us-east1-retally-c5212.cloudfunctions.net/parseTornReceipt"
    }
}
