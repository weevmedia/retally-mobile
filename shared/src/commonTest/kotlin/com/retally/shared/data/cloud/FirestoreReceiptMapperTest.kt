package com.retally.shared.data.cloud

import com.retally.shared.domain.receipt.model.Receipt
import com.retally.shared.domain.receipt.model.ReceiptItem
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class FirestoreReceiptMapperTest {

    private val mapper = FirestoreReceiptMapper(timeZone = TimeZone.UTC)
    private val purchase = Instant.parse("2026-09-12T18:41:00Z")
    private val bread = ReceiptItem(id = "li-2", itemNumber = "0041", name = "Sourdough Loaf", price = 5.49)
    private val receipt = Receipt(
        id = "rcpt-1",
        storeName = "Green Market #12",
        purchaseDate = purchase,
        paymentInfo = "Visa •••• 4242",
        createdAt = purchase,
        items = listOf(
            ReceiptItem(id = "li-1", name = "Organic Bananas", price = 2.19),
            bread,
            bread.copy(id = "li-3", isDuplicate = true),
        ),
    )

    @Test
    fun `receipt document carries every rules-validated field`() {
        val doc = mapper.receiptDocument(receipt, currency = "USD", deviceId = "dev-1", appVersion = "1.0 (1)")

        assertEquals("Green Market #12", doc["storeName"])
        assertEquals("green market 12", doc["merchantNameNormalized"])
        assertEquals("2026-09-12", doc["purchaseDateLocal"])
        assertEquals("USD", doc["currency"])
        assertEquals(13.17, doc["total"])
        assertEquals(3, doc["lineItemCount"])
        assertEquals("card", doc["paymentMethod"])
        assertEquals("scan", doc["source"])
        assertEquals(1, doc["schemaVersion"])

        @Suppress("UNCHECKED_CAST")
        val analysis = doc["analysis"] as Map<String, Any?>
        assertEquals(1, analysis["duplicateCount"])
        assertEquals(5.49, analysis["overchargeAmount"])
        assertEquals(listOf("duplicate_line"), analysis["flags"])

        @Suppress("UNCHECKED_CAST")
        val ocr = doc["ocr"] as Map<String, Any?>
        assertEquals(true, ocr["piiScrubbed"])
        assertTrue((doc["storeName"] as String).length <= 120)
    }

    @Test
    fun `payment info never leaves the device as anything but a category`() {
        assertEquals("card", mapper.paymentMethod("Visa •••• 4242"))
        assertEquals("card", mapper.paymentMethod("MASTERCARD DEBIT"))
        assertEquals("mobile", mapper.paymentMethod("Apple Pay"))
        assertEquals("cash", mapper.paymentMethod("CASH TEND 20.00"))
        assertEquals("unknown", mapper.paymentMethod(null))
        assertEquals("unknown", mapper.paymentMethod("gift"))
    }

    @Test
    fun `line items are denormalized and link duplicates to their original`() {
        val docs = receipt.items.mapIndexed { i, item -> mapper.lineItemDocument("uid-1", receipt, item, i, "USD") }

        assertEquals("uid-1", docs[0]["uid"])
        assertEquals("rcpt-1", docs[0]["receiptId"])
        assertEquals(0, docs[0]["position"])
        assertEquals("Organic Bananas", docs[0]["rawDescription"])
        assertEquals("organic bananas", docs[0]["normalizedName"])
        assertEquals(2.19, docs[0]["unitPrice"])
        assertEquals("each", docs[0]["unit"])
        assertEquals(false, docs[0]["userCorrected"])

        assertNull(docs[1]["duplicateOfLineId"])
        assertEquals(true, docs[2]["isDuplicate"])
        assertEquals("li-2", docs[2]["duplicateOfLineId"])
    }

    @Test
    fun `merchant normalization strips punctuation and case`() {
        assertEquals("wal mart supercenter", mapper.normalizeMerchant("WAL-MART  Supercenter!"))
        assertEquals("h e b", mapper.normalizeMerchant("H-E-B"))
    }
}
