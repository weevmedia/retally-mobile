package com.retally.shared.data.receipt

import com.retally.shared.db.ReTallyDatabase
import com.retally.shared.db.Receipt as ReceiptRow
import com.retally.shared.domain.receipt.ReceiptRepository
import com.retally.shared.domain.receipt.model.Receipt
import com.retally.shared.domain.receipt.model.ReceiptItem
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
 * SQLDelight-backed receipts. Replaces the old JSON-in-Settings store: every receipt
 * is a row in the shared `retally.db`, line items ride along as a JSON column, and
 * photos stay as files in app-private storage with only the path in the table.
 *
 * The [ReceiptRepository] interface is unchanged, so Home, Receipts, cloud backup
 * and the geofence directory all keep working without edits.
 *
 * First run after the Settings-JSON era migrates the old `receipts.v1` blob into
 * the table once, then drops the key so it can never re-seed.
 */
internal class DbReceiptRepository(
    private val database: ReTallyDatabase,
    private val settings: Settings,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ReceiptRepository {

    private val receipts = MutableStateFlow(loadAll())

    override fun observeReceipts(): Flow<List<Receipt>> = receipts.asStateFlow()

    override suspend fun getReceipt(id: String): Receipt? = withContext(ioDispatcher) {
        ensureMigrated()
        database.receiptQueries.selectById(id).executeAsOneOrNull()?.toReceipt()
    }

    override suspend fun upsert(receipt: Receipt) = mutate {
        ensureMigrated()
        database.receiptQueries.insertOrReplace(
            id = receipt.id,
            storeName = receipt.storeName,
            storeAddress = receipt.storeAddress,
            purchaseDate = receipt.purchaseDate.toEpochMilliseconds(),
            paymentInfo = receipt.paymentInfo,
            itemsJson = json.encodeToString(receiptItemListSerializer, receipt.items),
            subtotal = receipt.subtotal,
            tax = receipt.tax,
            receiptTotal = receipt.receiptTotal,
            barcode = receipt.barcode,
            imagePath = receipt.imagePath,
            createdAt = receipt.createdAt.toEpochMilliseconds(),
            syncedAt = receipt.syncedAt?.toEpochMilliseconds(),
        )
        loadAll()
    }

    override suspend fun delete(id: String) = mutate {
        ensureMigrated()
        database.receiptQueries.deleteById(id)
        loadAll()
    }

    override suspend fun markSynced(ids: Collection<String>, syncedAt: Instant) {
        if (ids.isEmpty()) return
        mutate {
            ensureMigrated()
            database.receiptQueries.markSynced(
                syncedAt = syncedAt.toEpochMilliseconds(),
                ids = ids,
            )
            loadAll()
        }
    }

    override suspend fun deleteAll() = mutate {
        ensureMigrated()
        database.receiptQueries.deleteAll()
        loadAll()
    }

    private suspend fun mutate(block: () -> List<Receipt>) {
        receipts.value = withContext(ioDispatcher) { block() }
    }

    private fun loadAll(): List<Receipt> {
        ensureMigrated()
        return database.receiptQueries.selectAll().executeAsList().map { it.toReceipt() }
    }

    private fun ReceiptRow.toReceipt(): Receipt = Receipt(
        id = id,
        storeName = store_name,
        storeAddress = store_address,
        purchaseDate = Instant.fromEpochMilliseconds(purchase_date),
        paymentInfo = payment_info,
        items = json.decodeFromString(receiptItemListSerializer, items_json),
        subtotal = subtotal,
        tax = tax,
        receiptTotal = receipt_total,
        barcode = barcode,
        imagePath = image_path,
        createdAt = Instant.fromEpochMilliseconds(created_at),
        syncedAt = synced_at?.let(Instant::fromEpochMilliseconds),
    )

    /**
     * One-time move of the pre-SQLite `receipts.v1` Settings JSON into the
     * `receipt` table. Runs lazily on first DB access; the Settings key is
     * removed afterwards so it can never re-seed.
     */
    private var migrationDone = false

    private fun ensureMigrated() {
        if (migrationDone) return
        migrationDone = true
        val queries = database.receiptQueries
        if (queries.selectAll().executeAsList().isNotEmpty()) {
            settings.remove(KEY_RECEIPTS)
            return
        }
        val raw = settings.getStringOrNull(KEY_RECEIPTS) ?: return
        val seeded = runCatching {
            json.decodeFromString(receiptListSerializer, raw)
        }.getOrDefault(emptyList())
        if (seeded.isNotEmpty()) {
            queries.transaction {
                seeded.forEach { receipt ->
                    queries.insertOrReplace(
                        id = receipt.id,
                        storeName = receipt.storeName,
                        storeAddress = receipt.storeAddress,
                        purchaseDate = receipt.purchaseDate.toEpochMilliseconds(),
                        paymentInfo = receipt.paymentInfo,
                        itemsJson = json.encodeToString(receiptItemListSerializer, receipt.items),
                        subtotal = receipt.subtotal,
                        tax = receipt.tax,
                        receiptTotal = receipt.receiptTotal,
                        barcode = receipt.barcode,
                        imagePath = receipt.imagePath,
                        createdAt = receipt.createdAt.toEpochMilliseconds(),
                        syncedAt = receipt.syncedAt?.toEpochMilliseconds(),
                    )
                }
            }
        }
        settings.remove(KEY_RECEIPTS)
    }

    private companion object {
        /** Legacy pre-SQLite blob; read once by [ensureMigrated], then removed. */
        const val KEY_RECEIPTS = "receipts.v1"
        val receiptListSerializer = ListSerializer(Receipt.serializer())
        val receiptItemListSerializer = ListSerializer(ReceiptItem.serializer())
    }
}
