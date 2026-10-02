package com.retally.shared.data.cloud

import com.retally.shared.domain.receipt.model.Receipt
import com.retally.shared.domain.receipt.model.ReceiptItem
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.Timestamp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Maps the app's [Receipt] onto the Firestore documents defined in `backend/firestore/SCHEMA.md`
 * (schema v1). Every field the security rules validate is produced here, so a rules rejection
 * means the schema and this mapper drifted — fix both together.
 *
 * PII: the client never uploads card numbers. `paymentInfo` ("Visa •••• 4242") is reduced to
 * a category, and the OCR text is not retained by the current model, so `ocr.rawText` is
 * empty with `piiScrubbed = true`.
 */
class FirestoreReceiptMapper(
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }

    fun receiptDocument(
        receipt: Receipt,
        currency: String,
        deviceId: String,
        appVersion: String,
    ): Map<String, Any?> {
        val duplicates = receipt.duplicates
        val total = (receipt.total ?: receipt.itemsTotal).round2()
        val tax = (receipt.tax ?: 0.0).round2()
        return mapOf(
            "storeName" to receipt.storeName.take(120),
            "storeAddress" to receipt.storeAddress?.take(300),
            "merchantId" to null,
            "merchantNameNormalized" to normalizeMerchant(receipt.storeName),
            "purchaseDate" to receipt.purchaseDate.toTimestamp(),
            "purchaseDateLocal" to receipt.purchaseDate.toLocalDateString(),
            "currency" to currency,
            "subtotal" to (receipt.subtotal ?: receipt.itemsTotal).round2(),
            "tax" to tax,
            "total" to total,
            "barcode" to receipt.barcode,
            "lineItemCount" to receipt.items.size,
            "paymentMethod" to paymentMethod(receipt.paymentInfo),
            "analysis" to mapOf(
                "duplicateCount" to duplicates.size,
                "overchargeAmount" to receipt.overchargeAmount.round2(),
                "flags" to if (duplicates.isEmpty()) emptyList() else listOf("duplicate_line"),
                "confidence" to 1.0,
            ),
            "categoryTotals" to mapOf("other" to total),
            "image" to mapOf("storagePath" to null, "thumbnailPath" to null),
            "ocr" to mapOf(
                "rawText" to "",
                "engine" to "mlkit",
                "appVersion" to appVersion,
                "piiScrubbed" to true,
            ),
            "source" to "scan",
            "deviceId" to deviceId,
            "createdAt" to receipt.createdAt.toTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp,
            "deletedAt" to null,
            "schemaVersion" to SCHEMA_VERSION,
        )
    }

    fun lineItemDocument(
        uid: String,
        receipt: Receipt,
        item: ReceiptItem,
        position: Int,
        currency: String,
    ): Map<String, Any?> {
        val duplicateOf = if (item.isDuplicate) {
            receipt.items.take(position).firstOrNull { it.name == item.name && !it.isDuplicate }?.id
        } else {
            null
        }
        val price = item.price.round2()
        return mapOf(
            "uid" to uid,
            "receiptId" to receipt.id,
            "merchantId" to null,
            "purchaseDate" to receipt.purchaseDate.toTimestamp(),
            "position" to position,
            "rawDescription" to item.name.take(200),
            "itemNumber" to item.itemNumber,
            "normalizedName" to item.name.trim().lowercase(),
            "productId" to null,
            "category" to "other",
            "brand" to null,
            "quantity" to 1.0,
            "unit" to "each",
            "unitPrice" to price,
            "lineTotal" to price,
            "discount" to 0.0,
            "taxable" to null,
            "isDuplicate" to item.isDuplicate,
            "duplicateOfLineId" to duplicateOf,
            "userCorrected" to false,
            "currency" to currency,
            "createdAt" to receipt.createdAt.toTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp,
        )
    }

    /** First-sync profile document; rules only allow the listed keys on create. */
    fun newUserDocument(locale: String, currency: String): Map<String, Any?> = mapOf(
        "displayName" to null,
        "email" to null,
        "photoUrl" to null,
        "locale" to locale,
        "currency" to currency,
        "timeZone" to timeZone.id,
        "settings" to mapOf("cloudBackup" to true, "aiPersonalization" to false, "adsPersonalized" to false),
        "stats" to mapOf(
            "receiptCount" to 0, "lineItemCount" to 0, "totalSpent" to 0.0, "totalOvercharge" to 0.0, "lastReceiptAt" to null,
        ),
        "createdAt" to FieldValue.serverTimestamp,
        "updatedAt" to FieldValue.serverTimestamp,
        "schemaVersion" to SCHEMA_VERSION,
    )

    // ---- helpers ----

    internal fun paymentMethod(paymentInfo: String?): String {
        val p = paymentInfo?.lowercase() ?: return "unknown"
        return when {
            listOf("apple pay", "google pay", "samsung pay", "paypal", "venmo").any { it in p } -> "mobile"
            listOf("visa", "master", "amex", "american express", "discover", "card", "credit", "debit").any { it in p } -> "card"
            "cash" in p -> "cash"
            else -> "unknown"
        }
    }

    internal fun normalizeMerchant(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun Instant.toTimestamp(): Timestamp =
        Timestamp(seconds = epochSeconds, nanoseconds = nanosecondsOfSecond)

    private fun Instant.toLocalDateString(): String {
        val d = toLocalDateTime(timeZone).date
        return "${d.year}-${d.month.number.toString().padStart(2, '0')}-${d.day.toString().padStart(2, '0')}"
    }

    private fun Double.round2(): Double = kotlin.math.round(this * 100) / 100
}
