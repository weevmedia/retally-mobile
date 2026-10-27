package com.retally.shared.domain.ai

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * One Home nudge, built on the server (`getNudges`, see the backend's nudges/compute.ts) from
 * the shopper's purchase history across stores. Server-driven: the app renders whatever kinds
 * arrive with one generic card, so new kinds need no app release. Every figure in [title],
 * [body] and [highlight] is computed server-side from real receipts.
 */
@Serializable
data class Nudge(
    /** Stable while the same fact holds — "seen" tracking ([NudgeReadStore]) keys on it. */
    val id: String,
    /** "cheaper_elsewhere", "price_creep", "running_low", "double_charge", "spending_pace", … */
    val kind: String,
    val title: String,
    val body: String,
    /** The key figure shown large on the card ("$1.80 less", "+22%"); null for none. */
    val highlight: String? = null,
    /** What a tap opens; null = informational only. */
    val action: NudgeAction? = null,
    val priority: Int = 0,
)

/** Tap target for a nudge. Unknown [type]s from a newer server are treated as no action. */
@Serializable
data class NudgeAction(
    /** "shopping_list" | "receipt" | "budget". */
    val type: String,
    val storeName: String? = null,
    val receiptId: String? = null,
)

/**
 * Home nudges for signed-in shoppers, cached on the device so Home shows them instantly and
 * offline. Guests have none (the server needs a synced account, like the Smart List).
 */
interface NudgeRepository {
    /** Last nudges from the server, highest priority first. */
    val nudges: StateFlow<List<Nudge>>

    /**
     * Fetches fresh nudges unless the last fetch is under ~30 minutes old ([force] skips that).
     * Never throws; on failure the cached nudges stay.
     */
    suspend fun refresh(force: Boolean = false)

    /** Drops the cache — they describe the account's purchases (sign-out, delete). */
    fun clearCache()
}
