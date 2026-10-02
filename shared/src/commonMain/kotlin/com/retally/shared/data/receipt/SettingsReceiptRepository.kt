package com.retally.shared.data.receipt

import com.retally.shared.domain.receipt.ReceiptRepository
import com.retally.shared.domain.receipt.model.Receipt
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/**
 * JSON-in-Settings persistence. Receipts are small (tens of KB for a heavy user), so a single
 * serialized list keeps the storage story identical on both platforms without pulling in a
 * database. Swap for SQLDelight once search/filter across thousands of receipts is a feature.
 */
internal class SettingsReceiptRepository(
    private val settings: Settings,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ReceiptRepository {

    private val receipts = MutableStateFlow(load())

    override fun observeReceipts(): Flow<List<Receipt>> = receipts.asStateFlow()

    override suspend fun getReceipt(id: String): Receipt? = receipts.value.firstOrNull { it.id == id }

    override suspend fun upsert(receipt: Receipt) = mutate { current ->
        current.filterNot { it.id == receipt.id } + receipt
    }

    override suspend fun delete(id: String) = mutate { current -> current.filterNot { it.id == id } }

    override suspend fun markSynced(ids: Collection<String>, syncedAt: kotlin.time.Instant) = mutate { current ->
        current.map { if (it.id in ids) it.copy(syncedAt = syncedAt) else it }
    }

    override suspend fun deleteAll() = mutate { emptyList() }

    private suspend fun mutate(block: (List<Receipt>) -> List<Receipt>) {
        val next = block(receipts.value).sortedByDescending { it.purchaseDate }
        withContext(ioDispatcher) {
            settings.putString(KEY_RECEIPTS, json.encodeToString(ListSerializer, next))
        }
        receipts.value = next
    }

    private fun load(): List<Receipt> {
        val raw = settings.getStringOrNull(KEY_RECEIPTS) ?: return emptyList()
        return runCatching { json.decodeFromString(ListSerializer, raw) }
            .getOrDefault(emptyList())
            .sortedByDescending { it.purchaseDate }
    }

    private companion object {
        const val KEY_RECEIPTS = "receipts.v1"
        val ListSerializer = ListSerializer(Receipt.serializer())
    }
}

