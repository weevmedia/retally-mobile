package com.retally.shared.domain.scan

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class ReceiptParserTest {

    private var counter = 0
    private val fixedClock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-09-14T12:00:00Z")
    }
    private val parser = ReceiptParser(clock = fixedClock, timeZone = TimeZone.UTC, idFactory = { "item-${counter++}" })

    private val walmart = listOf(
        "Walmart",
        "Save money. Live better.",
        "ST# 02345 OP# 009 TE# 12 TR# 0456",
        "GV WHOLE MILK 001234567890 3.48 X",
        "BANANAS 000000004011 1.27 N",
        "SOURDOUGH 007874212345 4.98 X",
        "SOURDOUGH 007874212345 4.98 X",
        "SUBTOTAL 14.71",
        "TAX 1 6.250% 0.56",
        "TOTAL 15.27",
        "VISA TEND 15.27",
        "Visa **** **** **** 4242",
        "CHANGE DUE 0.00",
        "# ITEMS SOLD 4",
        "09/13/26 18:42:11",
        "THANK YOU FOR SHOPPING",
    )

    /** Walmart prints a feedback URL and a "Wal-Mart" reprint above the real name. */
    private val walmartFeedbackHeader = listOf(
        "Give us feedback @ survey.walmart.com",
        "Thank you! ID #:7WT8R613N5BN",
        "Walmart",
        "Wal-Mart",
        "781 331-0063 Mgr. ED",
        "740 MIDDLE ST",
        "WEYMOUTH MA 02188",
        "ST# 03200 OP# 003067 TE# 06 TR# 00669",
        "GV WHOLE MILK 001234567890 3.48 X",
        "BANANAS 000000004011 1.27 N",
        "SUBTOTAL 4.75",
        "TOTAL 4.75",
    )

    /** Big Y prints its name only as a script logo above "SELF CHECKOUT", address on top. */
    private val bigYSelfCheckout = listOf(
        "10 WASHINGTON ST",
        "NORWELL, MA 02061",
        "STORE DIRECTOR- STEPHEN BEANE",
        "STORE NUMBER (781) 681-9322",
        "Big Y",
        "SELF CHECKOUT",
        "Credit Card Payment Information",
        "Card: VISA Amount: $13.65",
        "09/12/26 18:52",
        "Big Y Membership: 40096788151",
        "FCL CREAM CHEESE 2.99",
        "FC ORG MAC&CHEESE 1.99",
        "TOTAL 13.65",
        "Visa **** **** **** 5099",
        "Tell us about this visit to Big Y",
        "https://www.bigy.com/survey",
    )

    /** The script logo often doesn't OCR at all — the chain fingerprint must still win. */
    private val bigYLogoNotOcr = listOf(
        "10 WASHINGTON ST",
        "NORWELL, MA 02061",
        "STORE NUMBER (781) 681-9322",
        "SELF CHECKOUT",
        "Credit Card Payment Information",
        "Card: VISA Amount: $13.65",
        "09/12/26 18:52",
        "Big Y Membership: 40096788151",
        "FCL CREAM CHEESE 2.99",
        "TOTAL 13.65",
        "Visa **** **** **** 5099",
    )

    @Test
    fun `extracts store, items, payment and date`() {
        val receipt = parser.parse(walmart)

        assertEquals("Walmart", receipt.storeName)
        assertEquals(listOf("GV WHOLE MILK", "BANANAS", "SOURDOUGH", "SOURDOUGH"), receipt.items.map { it.name })
        assertEquals(listOf(3.48, 1.27, 4.98, 4.98), receipt.items.map { it.price })
        assertEquals("Visa •••• 4242", receipt.paymentInfo)
        assertEquals(Instant.parse("2026-09-13T18:42:00Z"), receipt.purchaseDate)
    }

    @Test
    fun `feedback url and reprinted name do not become the store name`() {
        val receipt = parser.parse(walmartFeedbackHeader)

        assertEquals("Walmart", receipt.storeName)
        assertEquals(listOf("GV WHOLE MILK", "BANANAS"), receipt.items.map { it.name })
    }

    @Test
    fun `big y logo header resolves to Big Y, not the address or self checkout`() {
        val receipt = parser.parse(bigYSelfCheckout)

        assertEquals("Big Y", receipt.storeName)
        assertEquals(listOf("FCL CREAM CHEESE", "FC ORG MAC&CHEESE"), receipt.items.map { it.name })
        assertEquals("Visa •••• 5099", receipt.paymentInfo)
        assertEquals(Instant.parse("2026-09-12T18:52:00Z"), receipt.purchaseDate)
    }

    @Test
    fun `big y is found even when the script logo does not OCR`() {
        val receipt = parser.parse(bigYLogoNotOcr)

        assertEquals("Big Y", receipt.storeName)
        assertEquals(
            "10 WASHINGTON ST\nNORWELL, MA 02061\nSTORE NUMBER (781) 681-9322",
            receipt.storeAddress,
        )
    }

    @Test
    fun `identical name and price is flagged as a duplicate, totals are not`() {
        val receipt = parser.parse(walmart)
        assertEquals(2, receipt.duplicateCount)
        assertTrue(receipt.items.filter { it.isDuplicate }.all { it.name.startsWith("SOURDOUGH") })
        assertTrue(receipt.items.none { it.name.contains("TOTAL") || it.name.contains("TAX") })
    }

    @Test
    fun `item numbers, commas and currency symbols are tolerated`() {
        val receipt = parser.parse(listOf("Green Market", "1123 Organic Bananas $2.19", "2208 Oat Milk 1L 3,99", "0041 Sourdough Loaf 5.49 T"))
        assertEquals(listOf("1123", "2208", "0041"), receipt.items.map { it.itemNumber })
        assertEquals(listOf(2.19, 3.99, 5.49), receipt.items.map { it.price })
        assertEquals(listOf("Organic Bananas", "Oat Milk 1L", "Sourdough Loaf"), receipt.items.map { it.name })
    }

    @Test
    fun `future dates and barcode-only lines are ignored`() {
        val receipt = parser.parse(listOf("Corner Shop", "12/25/2031", "0049000012345 1.99", "Cash 5.00"))
        assertNull(receipt.purchaseDate)
        assertTrue(receipt.items.isEmpty())
        assertEquals("Cash", receipt.paymentInfo)
    }

    @Test
    fun `iso and day-first dates are understood`() {
        assertEquals(Instant.parse("2026-01-05T00:00:00Z"), parser.parse(listOf("Shop", "2026-01-05")).purchaseDate)
        assertEquals(Instant.parse("2026-01-31T09:05:00Z"), parser.parse(listOf("Shop", "31/01/2026 9:05 AM")).purchaseDate)
        assertNotNull(parser.parse(listOf("Shop", "Date: 3/4/26 2:15 PM")).purchaseDate)
    }

    @Test
    fun `falls back gracefully when nothing is recognisable`() {
        val receipt = parser.parse(listOf("1234", "5678"))
        assertEquals("1234", receipt.storeName)
        assertTrue(receipt.items.isEmpty())
        assertNull(receipt.paymentInfo)
    }

    private val costcoSynthetic = listOf(
        "COSTCO WHOLESALE",
        "Frisco #1234",
        "2700 PRESTON RD",
        "FRISCO, TX 75034",
        "(972) 555-0100",
        "E 1234567 KS ORG BANANAS 1.99 E",
        "E 2345678 KS EGGS 24CT 6.49 E",
        "E 2345678 KS EGGS 24CT 6.49 E",
        "SUBTOTAL 14.97",
        "TAX 0.00",
        "**** TOTAL 14.97",
        "XXXXXXXXXXXX4242 VISA",
        "09/12/2026 14:03",
        "2100 1234 5678 9012 3456 7890",
        "Thank You!",
    )

    @Test
    fun `address lines under the store name are kept verbatim`() {
        val parsed = parser.parse(costcoSynthetic)
        assertEquals("COSTCO WHOLESALE", parsed.storeName)
        assertEquals("Frisco #1234\n2700 PRESTON RD\nFRISCO, TX 75034\n(972) 555-0100", parsed.storeAddress)
    }

    @Test
    fun `subtotal tax total and barcode are read from the footer`() {
        val parsed = parser.parse(costcoSynthetic)
        assertEquals(14.97, parsed.subtotal)
        assertEquals(0.0, parsed.tax)
        assertEquals(14.97, parsed.total)
        assertEquals("210012345678901234567890", parsed.barcode)
    }

    @Test
    fun `separate tax lines are summed and a missing barcode is null`() {
        val parsed = parser.parse(walmart)
        assertEquals(14.71, parsed.subtotal)
        assertEquals(0.56, parsed.tax)
        assertEquals(15.27, parsed.total)
        assertEquals(null, parsed.barcode) // item UPCs sit on item lines, never alone
        assertEquals("Save money. Live better.\nST# 02345 OP# 009 TE# 12 TR# 0456", parsed.storeAddress) // header shown as printed
    }

    /** Real Costco receipt (Dedham #319, 09/05/2026), as the OCR row-merger hands it over. */
    private val costcoDedham = listOf(
        "COSTCO", "WHOLESALE", "Dedham #319", "200 Legacy Blvd", "Dedham, MA 02026",
        "OE Member 111912144577",
        "2040045 BC BBQ VTY 18.99",
        "1193157 KS SEAWEED 9.99",
        "1560969 MADEGOODMINI 11.99",
        "1023755 KS EGGS 9.39",
        "E 3 WHOLE MILK 2.59",
        "1538864 STARBCREAMER 7.99",
        "27003 STRAWBERRIES 5.99",
        "5021 PREM. PLUMS 6.69",
        "1224482 SOFT PRETZEL 9.99",
        "2023205 ET BAGEL 8CT 4.99",
        "284601 KS ALMONDS 13.49",
        "331100 POTATOES 8.29",
        "522779 KS RD PEPPER 3.99",
        "7913 GREEN KIWI 7.29",
        "1135340 S.B ORG GALA 6.49",
        "1901577 KEN. RUNNEFT 18.99 A",
        "2049779 PMKN FLAX 5.49",
        "2059712 CHO 2OG YRGT 6.79",
        "1610750 KS ORG CSHW 11.49",
        "545345 KS PISTACHIO 10.99",
        "1801 MANDARINS 4.49",
        "647465 AVOCADOS 6.99",
        "24047 PAPAYA 4.49",
        "1491866 PUMPKIN SEED 10.99",
        "2003554 LUCKYSWEATER 16.99",
        "E 722650 MINI CUKES 6.99",
        "1698212 NMFISH 300CT 19.99 A",
        "E 0000389072 / 2040045 5.00-",
        "SUBTOTAL 248.59",
        "TAX 1.94",
        "250.53",
        "**** TOTAL",
        "XXXXXXXXXXXX5278 CHIP Read",
        "AID: A0000000031010",
        "Seq# 7254 App#: 13191D",
        "Costco Visa Resp: APPROVED",
        "APPROVED - Purchase",
        "AMOUNT: $250.53",
        "Costco Visa 250.53",
        "CHANGE 0.00",
        "A 6.25% Tax 1.94",
        "TOTAL TAX 1.94",
        "TOTAL NUMBER OF ITEMS SOLD = 31",
        "INSTANT SAVINGS $ 33.30",
        "09/05/2026 17:00 319 7 456 57",
        "21031900704562609051700",
        "OP#: 57 Name: Mo",
        "Thank You! Please Come Again",
        "Items Sold: 31",
        "OE 09/05/2026 17:00",
    )

    @Test
    fun costco_header_is_store_name_then_address_as_printed() {
        val parsed = parser.parse(costcoDedham)
        assertEquals("COSTCO WHOLESALE", parsed.storeName) // stacked logo words merge into the name
        assertEquals("Dedham #319\n200 Legacy Blvd\nDedham, MA 02026", parsed.storeAddress)
    }

    @Test
    fun costco_total_is_the_grand_total_not_total_tax() {
        val parsed = parser.parse(costcoDedham)
        assertEquals(250.53, parsed.total)
        assertEquals(248.59, parsed.subtotal)
        assertEquals(1.94, parsed.tax) // TOTAL TAX wins; the repeated tax lines are not summed
    }

    @Test
    fun costco_payment_is_issuer_method_and_last_four() {
        val parsed = parser.parse(costcoDedham)
        assertEquals("Costco Visa •••• 5278", parsed.paymentInfo)
    }

    @Test
    fun costco_items_keep_item_numbers_behind_the_tax_flag() {
        val parsed = parser.parse(costcoDedham)
        val cukes = parsed.items.single { it.name == "MINI CUKES" }
        assertEquals("722650", cukes.itemNumber)
        assertEquals(6.99, cukes.price)
        assertTrue(parsed.items.none { it.name.contains("Visa") || it.name.contains("AMOUNT") })
        assertEquals("21031900704562609051700", parsed.barcode)
        assertEquals(Instant.parse("2026-09-05T17:00:00Z"), parsed.purchaseDate)
    }

    @Test
    fun costco_milk_keeps_the_single_digit_item_number() {
        val parsed = parser.parse(costcoDedham)
        val milk = parsed.items.single { it.name == "WHOLE MILK" }
        assertEquals("3", milk.itemNumber) // "E 3 WHOLE MILK 2.59": the 3 is the item number
        assertEquals(2.59, milk.price)
    }

    @Test
    fun costco_dedham_every_line_matches_the_receipt() {
        val items = parser.parse(costcoDedham).items
        // 27 positive lines; the "5.00-" savings line is a negative adjustment, not an item.
        assertEquals(27, items.size)
        assertEquals(
            listOf(
                "2040045", "1193157", "1560969", "1023755", "3", "1538864", "27003",
                "5021", "1224482", "2023205", "284601", "331100", "522779", "7913",
                "1135340", "1901577", "2049779", "2059712", "1610750", "545345",
                "1801", "647465", "24047", "1491866", "2003554", "722650", "1698212",
            ),
            items.map { it.itemNumber },
        )
        assertEquals(
            listOf(
                "BC BBQ VTY", "KS SEAWEED", "MADEGOODMINI", "KS EGGS", "WHOLE MILK",
                "STARBCREAMER", "STRAWBERRIES", "PREM. PLUMS", "SOFT PRETZEL",
                "ET BAGEL 8CT", "KS ALMONDS", "POTATOES", "KS RD PEPPER",
                "GREEN KIWI", "S.B ORG GALA", "KEN. RUNNEFT", "PMKN FLAX",
                "CHO 2OG YRGT", "KS ORG CSHW", "KS PISTACHIO", "MANDARINS",
                "AVOCADOS", "PAPAYA", "PUMPKIN SEED", "LUCKYSWEATER", "MINI CUKES",
                "NMFISH 300CT",
            ),
            items.map { it.name },
        )
        assertEquals(
            listOf(
                18.99, 9.99, 11.99, 9.39, 2.59, 7.99, 5.99, 6.69, 9.99, 4.99, 13.49,
                8.29, 3.99, 7.29, 6.49, 18.99, 5.49, 6.79, 11.49, 10.99, 4.49, 6.99,
                4.49, 10.99, 16.99, 6.99, 19.99,
            ),
            items.map { it.price },
        )
        assertTrue(items.none { it.price < 0 }) // the savings line never becomes an item
    }

    @Test
    fun costco_total_amount_printed_above_the_label_is_found() {
        // Real Costco layout: the amount sits on its own line above "**** TOTAL".
        val parsed = parser.parse(
            listOf(
                "COSTCO WHOLESALE",
                "MILK 3.49",
                "SUBTOTAL 3.49",
                "TAX 0.00",
                "3.49",
                "**** TOTAL",
            ),
        )
        assertEquals(3.49, parsed.total)
    }

    @Test
    fun total_amount_split_onto_the_next_line_is_found() {
        val parsed = parser.parse(
            listOf(
                "SHOP",
                "MILK 3.49",
                "**** TOTAL",
                "3.49",
            ),
        )
        assertEquals(3.49, parsed.total)
    }

    @Test
    fun total_label_with_no_amount_nearby_stays_null() {
        val parsed = parser.parse(listOf("SHOP", "MILK 3.49", "**** TOTAL", "THANK YOU"))
        assertNull(parsed.total)
    }

    /** Real Whole Foods receipt (Hingham, 09/19/2026): indented annotations and the card line are not items. */
    private val wholeFoodsHingham = listOf(
        "WHOLE FOODS",
        "MARKET",
        "Hingham HGM 781-741-8050",
        "94 Derby St",
        "Hingham, MA 02043",
        "BC PLAIN CRM TOP YGRT \$5.49 F",
        "CRLOR APPLE CIDER 64FZ \$4.99 F",
        "LIME \$0.59 F",
        "ANNIE SHELLS AGED CHDDR \$1.86 F",
        "Reg \$2.49",
        "Savings with Prime (\$0.63)",
        "Subtotal: \$13.56",
        "Total Savings: -\$0.63",
        "Net Sales: \$12.93",
        "Total: \$12.93",
        "Sold Items: 4",
        "Paid:",
        "Amz Crdt Crd *6872 \$12.93",
        "Your Total Savings",
        "\$0.63",
        "09/19/2026 07:19 PM",
        "63011015990275475091920260",
    )

    @Test
    fun wholefoods_annotations_and_card_line_are_not_items() {
        val parsed = parser.parse(wholeFoodsHingham)
        assertEquals("WHOLE FOODS MARKET", parsed.storeName)
        assertEquals("94 Derby St\nHingham, MA 02043", parsed.storeAddress)
        assertEquals(
            listOf("BC PLAIN CRM TOP YGRT", "CRLOR APPLE CIDER 64FZ", "LIME", "ANNIE SHELLS AGED CHDDR"),
            parsed.items.map { it.name },
        )
        assertEquals(listOf(5.49, 4.99, 0.59, 1.86), parsed.items.map { it.price })
        assertEquals(13.56, parsed.subtotal)
        assertEquals(12.93, parsed.total) // "Total:", not "Total Savings:"
    }

    @Test
    fun tax_lines_before_total_are_summed_when_no_total_tax_line() {
        val parsed = parser.parse(listOf("SHOP", "ITEM A 10.00", "SUBTOTAL 10.00", "STATE TAX 0.50", "CITY TAX 0.25", "TOTAL 10.75", "CASH 20.00"))
        assertEquals(0.75, parsed.tax)
        assertEquals(10.75, parsed.total)
        assertEquals("Cash", parsed.paymentInfo)
    }

    @Test
    fun items_need_a_positive_price_and_take_the_item_number_from_wherever_it_is_printed() {
        val parsed = parser.parse(
            listOf(
                "SHOP",
                "E 2040045 BC BBQ VTY 18.99",          // Costco: flag, leading number
                "E 0000389072 / 2040045 5.00-",        // Costco discount: trailing minus → skipped
                "GV MILK 007874201234 F 2.98",         // Walmart: trailing UPC + tax code
                "212-01-0123 KITCHEN TOWEL 4.50",      // Target: dashed number
                "120 ML SERUM 9.99",                   // short leading number is part of the name
                "SOURDOUGH LOAF 5.49",                 // no item number at all
                "RETURN -3.00",                        // negative → skipped
                "TOTAL 41.95",
            ),
        )
        assertEquals(listOf("BC BBQ VTY", "GV MILK", "KITCHEN TOWEL", "120 ML SERUM", "SOURDOUGH LOAF"), parsed.items.map { it.name })
        assertEquals(listOf("2040045", "007874201234", "212-01-0123", null, null), parsed.items.map { it.itemNumber })
        assertEquals(listOf(18.99, 2.98, 4.50, 9.99, 5.49), parsed.items.map { it.price })
    }

    @Test
    fun `store name skips greetings and merges multi-line logos`() {
        val parsed = parser.parse(
            listOf(
                "WELCOME TO",
                "TRADER",
                "JOE'S",
                "MARKET",
                "1123 ORGANIC MILK 4.99",
                "TOTAL 4.99",
            ),
        )
        assertEquals("TRADER JOE'S MARKET", parsed.storeName)
        assertEquals(listOf("ORGANIC MILK"), parsed.items.map { it.name })
    }

    @Test
    fun `store names with numbers and symbols are picked up`() {
        val parsed = parser.parse(listOf("7-ELEVEN", "STORE #12345", "COFFEE 2.19", "TOTAL 2.19"))
        assertEquals("7-ELEVEN", parsed.storeName)
        assertEquals(listOf("COFFEE"), parsed.items.map { it.name })
    }

    @Test
    fun `phone numbers and reprint marks above the name are skipped`() {
        val parsed = parser.parse(listOf("(972) 555-0100", "COSTCO WHOLESALE", "Frisco #1234", "MILK 3.49"))
        assertEquals("COSTCO WHOLESALE", parsed.storeName)
        assertEquals(listOf("MILK"), parsed.items.map { it.name })
    }

    @Test
    fun `target receipt with logo-only header resolves the chain from the footer`() {
        val parsed = parser.parse(
            listOf(
                "Hanover - 781-499-1961",
                "1167 Washington St",
                "Hanover, Massachusetts 02339-1643",
                "09/18/2026 08:53 PM",
                "KITCHEN",
                "072088332 Chefman T \$18.59",
                "SUBTOTAL \$18.59",
                "T = MA TAX 6.25000 on \$18.59 \$1.16",
                "TOTAL \$19.75",
                "*9801 MERCH CARD PAYMENT \$19.08",
                "*3385 MASTERCARD CHARGE \$0.67",
                "YOU'RE SHOPPING SMARTER WITH",
                "TARGET CIRCLE",
                "Visit Target.com/Circle to see",
                "details and all of your benefits.",
                "REC#2-6261-2532-0171-9200-8",
                "informtarget.com",
            ),
        )
        // The bullseye logo doesn't OCR, so the header has no name: the "TARGET CIRCLE"
        // fingerprint later on wins, and the address is read from the top.
        assertEquals("Target", parsed.storeName)
        assertEquals("1167 Washington St\nHanover, Massachusetts 02339-1643", parsed.storeAddress)
        assertEquals(19.75, parsed.total)
    }

    @Test
    fun `walmart promo line above the name does not become the store name`() {
        val parsed = parser.parse(
            listOf(
                "Save money. Live better.",
                "Walmart",
                "123 Main St",
                "Springfield, IL 62701",
                "MILK 3.49",
                "TOTAL 3.49",
            ),
        )
        assertEquals("Walmart", parsed.storeName)
        assertEquals("123 Main St\nSpringfield, IL 62701", parsed.storeAddress)
    }

    @Test
    fun `receipts without a known chain keep the scanned name`() {
        val parsed = parser.parse(listOf("Green Market", "MILK 3.49", "TOTAL 3.49"))
        assertEquals("Green Market", parsed.storeName)
    }

    @Test
    fun `barcode at the top with dashes is recognized`() {
        val parsed = parser.parse(
            listOf(
                "2-6261-2532-0171-9200-8",
                "Hanover - 781-499-1961",
                "1167 Washington St",
                "09/18/2026 08:53 PM",
                "072088332 Chefman T \$18.59",
                "TOTAL \$19.75",
            ),
        )
        assertEquals("262612532017192008", parsed.barcode)
    }

    @Test
    fun `barcode with a short label prefix is recognized`() {
        val parsed = parser.parse(listOf("REC#2-6261-2532-0171-9200-8", "MILK 3.49", "TOTAL 3.49"))
        assertEquals("262612532017192008", parsed.barcode)
    }

    @Test
    fun `date time lines are never barcodes`() {
        val parsed = parser.parse(listOf("Shop", "09/13/26 18:42:11", "MILK 3.49", "TOTAL 3.49"))
        assertNull(parsed.barcode)
    }

    @Test
    fun `fees tips donations and coupons are never items`() {
        val parsed = parser.parse(
            listOf(
                "SHOP",
                "MILK 3.49",
                "Q-TIPS 3.99",
                "BOTTLE DEPOSIT 0.10",
                "SERVICE FEE 1.00",
                "TIP 2.00",
                "DONATION 1.00",
                "REBATE 3.00",
                "MFR CPN 1.00",
                "COUPON SAVINGS 2.00",
                "TAX1 0.25",
                "TOTAL 12.83",
            ),
        )
        assertEquals(listOf("MILK", "Q-TIPS"), parsed.items.map { it.name })
        assertEquals(listOf(3.49, 3.99), parsed.items.map { it.price })
    }
}
