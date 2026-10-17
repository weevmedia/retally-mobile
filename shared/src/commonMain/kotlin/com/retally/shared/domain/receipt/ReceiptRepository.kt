package com.retally.shared.domain.receipt

import com.retally.shared.domain.receipt.model.Receipt
import com.retally.shared.domain.receipt.model.ReceiptItem
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock

/**
 * Local source of truth for receipts. The cloud is a mirror, never the primary store — the
 * app must work fully offline (a shopper checks a receipt in the parking lot).
 */
interface ReceiptRepository {
    fun observeReceipts(): Flow<List<Receipt>>
    suspend fun getReceipt(id: String): Receipt?
    suspend fun upsert(receipt: Receipt)
    suspend fun delete(id: String)
    suspend fun markSynced(ids: Collection<String>, syncedAt: kotlin.time.Instant)
    suspend fun deleteAll()
    /**
     * Deletes receipts older than [maxAgeDays]. Returns the image paths of deleted
     * receipts so the caller can delete the files. Used for guest FIFO retention.
     */
    suspend fun pruneOldReceipts(maxAgeDays: Int = 30): List<String> = emptyList()
}

/**
 * Sample data used by the debug "Add sample receipt" action so the Home list, duplicate
 * badges and totals can be exercised before the scanner ships (MVP scope: shell first).
 */
object SampleReceipts {
    fun make(clock: Clock = Clock.System, idFactory: () -> String): Receipt {
        val now = clock.now()
        val bread = ReceiptItem(id = idFactory(), itemNumber = "0041", name = "Sourdough Loaf", price = 5.49)
        return Receipt(
            id = idFactory(),
            storeName = "Green Market",
            purchaseDate = now,
            paymentInfo = "Visa •••• 4242",
            createdAt = now,
            receiptTotal = 28.65,
            items = listOf(
                ReceiptItem(id = idFactory(), itemNumber = "1123", name = "Organic Bananas", price = 2.19),
                bread,
                bread.copy(id = idFactory(), isDuplicate = true),
                ReceiptItem(id = idFactory(), itemNumber = "2208", name = "Oat Milk 1L", price = 3.99),
                ReceiptItem(id = idFactory(), itemNumber = "3390", name = "Paper Towels 6pk", price = 11.49),
            ),
        )
    }
}
