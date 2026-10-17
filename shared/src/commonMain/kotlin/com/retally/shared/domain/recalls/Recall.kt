package com.retally.shared.domain.recalls

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/** A purchase the recall may concern: generic item name from the shopper's receipts. */
@Serializable
data class RecallMatch(
    val item: String,
    /** YYYY-MM-DD of the most recent purchase. */
    val lastBought: String,
)

/** One food recall (FDA or USDA FSIS). Mirrors the `RecallDto` the `getRecalls` function returns. */
@Serializable
data class Recall(
    val id: String,
    /** "FDA" or "USDA". */
    val source: String,
    val title: String,
    val excerpt: String = "",
    val url: String,
    /** ISO-8601 instant. */
    val publishedAt: String,
    val classification: String? = null,
    /** Set when the recall mentions something the shopper bought recently. */
    val match: RecallMatch? = null,
)

/** Outcome of asking the backend for recalls. Never thrown — failures are values. */
sealed interface RecallsLoadResult {
    data class Success(val recalls: List<Recall>, val signedIn: Boolean) : RecallsLoadResult
    data class Failure(val reason: String) : RecallsLoadResult
}

interface RecallsRepository {
    /** Last recalls received, cached on the device so the screen opens instantly. */
    val recalls: StateFlow<List<Recall>>
    /** [refresh] asks the server to re-check FDA/USDA now instead of serving stored recalls. */
    suspend fun load(refresh: Boolean = false): RecallsLoadResult
}
