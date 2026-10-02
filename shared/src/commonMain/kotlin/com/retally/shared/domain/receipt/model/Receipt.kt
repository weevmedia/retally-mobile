package com.retally.shared.domain.receipt.model

import com.retally.shared.domain.receipt.DuplicateMatcher
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Instant

/**
 * A scanned receipt. Mirrors the SwiftData model of the original iOS app so a future
 * migration can map 1:1.
 */
@Serializable
data class Receipt(
    val id: String,
    val storeName: String,
    /** Address lines exactly as printed under the store name, joined with newlines. */
    val storeAddress: String? = null,
    @Serializable(with = InstantIsoSerializer::class)
    val purchaseDate: Instant,
    /** "Visa •••• 4242", "Cash", or null when the parser could not tell. */
    val paymentInfo: String? = null,
    val items: List<ReceiptItem> = emptyList(),
    /** Printed SUBTOTAL / TAX / TOTAL when the parser found them; null otherwise. */
    val subtotal: Double? = null,
    val tax: Double? = null,
    val receiptTotal: Double? = null,
    /** Digits of the barcode printed at the bottom (transaction / return code). */
    val barcode: String? = null,
    /** Absolute path of the scanned page image in the app's private storage, when kept. */
    val imagePath: String? = null,
    @Serializable(with = InstantIsoSerializer::class)
    val createdAt: Instant,
    /** Set once the receipt has been accepted by the cloud backend. */
    @Serializable(with = InstantIsoSerializer::class)
    val syncedAt: Instant? = null,
) {
    /** Sum of the printed line prices — a diagnostic, never the receipt total. */
    val itemsTotal: Double get() = items.sumOf { it.price }

    /**
     * What was charged: the printed TOTAL, and only the printed TOTAL. Never computed
     * from the items — discounts, quantities and missed lines make the item sum
     * unreliable. Null when the parser could not find the total line.
     */
    val total: Double? get() = receiptTotal

    /** Line items flagged as charged twice that the shopper has not confirmed as distinct. */
    val duplicates: List<ReceiptItem> get() = items.filter { it.isDuplicate && !it.keptAsDistinct }

    /**
     * Money the shopper likely overpaid: for each group of matching lines, every copy beyond
     * the first. Two identical lines → one line's price; three → two. Lines are grouped
     * the way [com.retally.shared.domain.receipt.DuplicateMatcher] matches them — by item
     * number when both lines have one, otherwise by normalised name — at the same price.
     */
    val overchargeAmount: Double
        get() = duplicates
            .groupBy { (it.itemNumber?.let(DuplicateMatcher::normalizeItemNumber).orEmpty().ifEmpty { it.name.lowercase().filter { c -> c.isLetterOrDigit() } }) to it.price }
            .values
            .sumOf { group -> group.drop(1).sumOf { it.price } }
}

@Serializable
data class ReceiptItem(
    val id: String,
    val itemNumber: String? = null,
    val name: String,
    val price: Double,
    /** Flagged by [com.retally.shared.domain.receipt.DuplicateMatcher] as charged twice on this receipt. */
    val isDuplicate: Boolean = false,
    /**
     * The shopper chose "Keep both" for this line: it really was bought twice. The matcher
     * skips it from then on and it no longer counts toward the overcharge.
     */
    val keptAsDistinct: Boolean = false,
    /** "Keep both" chosen for a cross-receipt repeat involving this line; hides it from that list. */
    val keptAcrossReceipts: Boolean = false,
)

/** ISO-8601 wire format for [kotlin.time.Instant] ("2026-09-13T14:03:00Z"). */
object InstantIsoSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("com.retally.InstantIso", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}
