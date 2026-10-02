package com.retally.shared.data.cloud

import com.retally.shared.core.Platform
import com.retally.shared.domain.cloud.CloudConfig
import com.retally.shared.domain.cloud.CloudSyncResult
import com.retally.shared.domain.cloud.CloudSyncService
import com.retally.shared.domain.receipt.model.Receipt
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlin.time.Clock

@Serializable
internal data class UploadReceiptsRequest(
    val platform: String,
    val appVersion: String,
    val deviceId: String,
    val receipts: List<Receipt>,
)

@Serializable
internal data class UploadReceiptsResponse(
    val accepted: List<String> = emptyList(),
)

/**
 * Ktor-backed implementation of the proposed `POST {baseUrl}/v1/receipts/batch` contract.
 *
 * `baseUrl` is injected from [CloudConfig]; an empty value means "no backend yet" and
 * [isConfigured] turns the sync UI into an informational placeholder rather than a failing
 * button. The device id is an installation-scoped random id (no PII, no advertising id).
 */
internal class KtorCloudSyncService(
    private val client: HttpClient,
    private val config: CloudConfig,
    private val platform: Platform,
    private val deviceIdProvider: () -> String,
    private val clock: Clock = Clock.System,
) : CloudSyncService {

    override val isConfigured: Boolean get() = config.baseUrl.isNotBlank()

    override suspend fun upload(receipts: List<Receipt>): CloudSyncResult {
        if (!isConfigured) {
            return CloudSyncResult.Failure(message = "Cloud sync is not configured yet.", retryable = false)
        }
        if (receipts.isEmpty()) {
            return CloudSyncResult.Success(syncedIds = emptyList(), syncedAt = clock.now())
        }
        return try {
            val response = client.post("${config.baseUrl.trimEnd('/')}/v1/receipts/batch") {
                contentType(ContentType.Application.Json)
                config.apiKey?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                setBody(
                    UploadReceiptsRequest(
                        platform = platform.name,
                        appVersion = platform.appVersion,
                        deviceId = deviceIdProvider(),
                        receipts = receipts,
                    ),
                )
            }
            if (response.status.isSuccess()) {
                val body = response.body<UploadReceiptsResponse>()
                CloudSyncResult.Success(syncedIds = body.accepted, syncedAt = clock.now())
            } else {
                CloudSyncResult.Failure(
                    message = "Server answered ${response.status.value}.",
                    retryable = response.status.value >= 500,
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            CloudSyncResult.Failure(message = t.message ?: "Could not reach the server.", retryable = true)
        }
    }
}

/**
 * Stand-in used until a backend URL is configured **and** in debug builds that want a green
 * path: pretends the upload took a moment and accepted everything, so the Settings screen's
 * "Back up now" flow can be demoed end-to-end.
 */
internal class SimulatedCloudSyncService(
    private val clock: Clock = Clock.System,
) : CloudSyncService {
    override val isConfigured: Boolean = true

    override suspend fun upload(receipts: List<Receipt>): CloudSyncResult {
        delay(SIMULATED_LATENCY_MS)
        return CloudSyncResult.Success(syncedIds = receipts.map { it.id }, syncedAt = clock.now())
    }

    private companion object {
        const val SIMULATED_LATENCY_MS = 900L
    }
}

