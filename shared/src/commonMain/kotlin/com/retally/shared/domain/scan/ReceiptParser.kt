package com.retally.shared.domain.scan

import com.retally.shared.core.newId
import com.retally.shared.domain.receipt.DuplicateMatcher
import com.retally.shared.domain.receipt.model.ReceiptItem
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** A receipt as understood from OCR text, before the shopper confirms and saves it. */
data class ParsedReceipt(
    val storeName: String,
    /** Address lines printed under the store name, joined with newlines; null if none. */
    val storeAddress: String? = null,
    val purchaseDate: Instant?,
    /** "Visa •••• 4242", "Cash", or null when the parser could not tell. */
    val paymentInfo: String?,
    val items: List<ParsedItem>,
    val subtotal: Double? = null,
    val tax: Double? = null,
    /** The printed TOTAL, when found. */
    val total: Double? = null,
    /** Digits of the barcode printed at the bottom, when found. */
    val barcode: String? = null,
) {
    val duplicateCount: Int get() = items.count { it.isDuplicate }
}

data class ParsedItem(
    val id: String,
    val itemNumber: String?,
    val name: String,
    val price: Double,
    val isDuplicate: Boolean = false,
)

/**
 * Heuristic parser for OCR'd receipt lines (port of the original iOS `ReceiptParser`):
 * extracts the store name, purchase date, payment method and priced line items, then flags
 * identical name+price pairs as likely double charges. Pure Kotlin so both apps parse the
 * same receipt the same way and it is unit-testable without a camera.
 */
class ReceiptParser(
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val idFactory: () -> String = { newId() },
) {

    fun parse(lines: List<String>): ParsedReceipt {
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
        val chain = findKnownChain(clean)
        val storeIndex = detectStoreNameIndex(clean, chain)
        val (scannedName, nameLinesConsumed) = storeNameParts(clean, storeIndex)
        // The logo often doesn't OCR (Target's bullseye), but the chain name is printed later
        // ("TARGET CIRCLE", "target.com"): trust the fingerprint then, and read the address
        // from the top of the receipt instead of from the misdetected name.
        val (storeName, addressStart) =
            if (chain != null && (!scannedName.contains(chain.keyword, ignoreCase = true) || chain.exactName)) {
                chain.name to 0
            } else {
                scannedName to storeIndex + nameLinesConsumed
            }
        return ParsedReceipt(
            storeName = storeName,
            storeAddress = detectStoreAddress(clean, addressStart),
            purchaseDate = detectDate(clean),
            paymentInfo = detectPaymentInfo(clean),
            items = flagDuplicates(detectItems(clean)),
            subtotal = detectAmount(clean, SUBTOTAL),
            tax = detectTax(clean),
            total = detectAmount(clean, TOTAL, preferLast = true),
            barcode = detectBarcode(clean),
        )
    }

    // Store name: a known chain's printed name wins when it appears near the top (Walmart
    // prints "Save money. Live better." above WALMART); otherwise the first prominent,
    // mostly-alphabetic line, skipping the greeting/phone/"STORE #" junk some chains print
    // above it ("WELCOME TO" / "STORE #123").
    private fun detectStoreNameIndex(lines: List<String>, chain: KnownChain?): Int {
        if (chain != null) {
            // The printed name, not the feedback URL: "survey.walmart.com" must not win.
            lines.take(MAX_STORE_NAME_SCAN).forEachIndexed { index, line ->
                if (chain.namePattern.containsMatchIn(line)) return index
            }
        }
        for ((index, line) in lines.take(MAX_STORE_NAME_SCAN).withIndex()) {
            if (isStoreNameLine(line)) return index
        }
        return 0
    }

    /** First known chain fingerprinted in the header/footer area, if any. */
    private fun findKnownChain(lines: List<String>): KnownChain? {
        val head = lines.take(MAX_CHAIN_SCAN)
        return KNOWN_CHAINS.firstOrNull { chain -> head.any { chain.fingerprint.containsMatchIn(it) } }
    }

    private fun isStoreNameLine(line: String): Boolean {
        if (STORE_NAME_SKIP.containsMatchIn(line)) return false
        if (URL_LIKE.containsMatchIn(line)) return false
        if (TRAILING_PRICE.containsMatchIn(line)) return false
        if (NUMERIC_DATE.containsMatchIn(line) || ISO_DATE.containsMatchIn(line)) return false
        val letters = line.count { it.isLetter() }
        return letters >= 3 && letters * 2 >= line.length
    }

    /**
     * Logos often OCR as one word per row ("COSTCO" / "WHOLESALE", "TRADER" / "JOE'S" /
     * "MARKET"). Keep consuming lines that look like name continuations — mostly letters,
     * no digits, prices, dates or address punctuation — up to [MAX_STORE_NAME_LINES], so the
     * header reads "COSTCO WHOLESALE" and the address still starts at "Dedham #319".
     * Returns the name text plus how many lines were consumed: a reprinted duplicate
     * ("Wal-Mart" under "Walmart") is consumed but not glued onto the name.
     */
    private fun storeNameParts(lines: List<String>, storeIndex: Int): Pair<String, Int> {
        if (storeIndex >= lines.size) return "Unknown Store" to 0
        val parts = mutableListOf(lines[storeIndex])
        val seen = mutableSetOf(lines[storeIndex].nameKey())
        var consumed = 1
        while (consumed < MAX_STORE_NAME_LINES) {
            val next = lines.getOrNull(storeIndex + consumed) ?: break
            if (!isStoreNameContinuation(next)) break
            consumed++
            val key = next.nameKey()
            if (key !in seen) {
                seen += key
                parts += next
            }
        }
        return parts.joinToString(" ").ifBlank { "Unknown Store" } to consumed
    }

    /** Case-insensitive letters-only key: "Walmart" and "Wal-Mart" are the same name. */
    private fun String.nameKey(): String = lowercase().filter { it.isLetter() }

    private fun isStoreNameContinuation(line: String): Boolean {
        if (line.length > MAX_STORE_NAME_LINE_LEN) return false
        if (line.any { it.isDigit() || it == '#' || it == ',' || it == '.' || it == ':' }) return false
        if (STORE_NAME_SKIP.containsMatchIn(line)) return false
        if (TRAILING_PRICE.containsMatchIn(line)) return false
        if (NUMERIC_DATE.containsMatchIn(line) || ISO_DATE.containsMatchIn(line)) return false
        val letters = line.count { it.isLetter() }
        return letters >= 2 && letters * 2 >= line.length
    }

    /**
     * Address = everything printed under the store name up to the first item, date or receipt
     * keyword (store number, street, "CITY, ST 12345", phone…), capped at [MAX_ADDRESS_LINES].
     * Kept verbatim — it is shown exactly as printed. Lines that are only punctuation/dashes
     * are skipped.
     */
    private fun detectStoreAddress(lines: List<String>, nameEndIndex: Int): String? {
        val out = mutableListOf<String>()
        for (line in lines.drop(nameEndIndex)) {
            // A phone line above the address ("Hanover - 781-499-1961") is header junk, not
            // the address: skip it while nothing is collected yet instead of letting the
            // date regex mistake it for a date and end the address block.
            if (out.isEmpty() && PHONE.containsMatchIn(line)) continue
            val isNoise = ITEM.matches(line) || NUMERIC_DATE.containsMatchIn(line) || ISO_DATE.containsMatchIn(line) ||
                NON_ITEM.containsMatchIn(line) || TRAILING_PRICE.containsMatchIn(line)
            if (isNoise) break
            if (line.any { it.isLetterOrDigit() }) out += line
            if (out.size >= MAX_ADDRESS_LINES) break
        }
        return out.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    /** Last (or first) line matching [label], with its printed amount. */
    private fun detectAmount(lines: List<String>, label: Regex, preferLast: Boolean = false): Double? {
        val matches = lines.mapIndexedNotNull { index, line ->
            if (!label.containsMatchIn(line)) return@mapIndexedNotNull null
            amountNear(lines, index, line)
        }
        return if (preferLast) matches.lastOrNull() else matches.firstOrNull()
    }

    /**
     * The amount for a totals label: on the same line when printed ("TOTAL 250.53"),
     * otherwise a bare price on the line directly above or below — Costco prints the
     * total amount on its own line above "**** TOTAL".
     */
    private fun amountNear(lines: List<String>, index: Int, line: String): Double? {
        price(line)?.let { return it }
        for (i in listOf(index - 1, index + 1)) {
            if (i in lines.indices) barePrice(lines[i])?.let { return it }
        }
        return null
    }

    /** A line that is nothing but a price ("250.53", "$ 33.30") — never an item row. */
    private fun barePrice(line: String): Double? {
        if (!BARE_PRICE.matches(line.trim())) return null
        return price(line)
    }

    /**
     * Tax as printed. A "TOTAL TAX" line wins outright; otherwise the "TAX … 1.23" lines between
     * SUBTOTAL and TOTAL are summed (state + local print separately). Tax lines *after* the
     * TOTAL (Costco's "A 6.25% Tax" summary block) repeat the same amounts and are ignored.
     */
    private fun detectTax(lines: List<String>): Double? {
        lines.firstOrNull { TOTAL_TAX.containsMatchIn(it) }?.let { line -> price(line)?.let { return it } }
        val totalIndex = lines.indexOfLast { TOTAL.containsMatchIn(it) }.takeIf { it >= 0 } ?: lines.size
        val taxes = lines.take(totalIndex).mapNotNull { line ->
            if (!TAX.containsMatchIn(line) || SUBTOTAL.containsMatchIn(line)) return@mapNotNull null
            price(line)
        }
        return taxes.takeIf { it.isNotEmpty() }?.sum()?.let { kotlin.math.round(it * 100) / 100 }
    }

    private fun price(line: String): Double? =
        TRAILING_PRICE.find(line)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()

    /**
     * The return/transaction barcode: a line that is (almost) nothing but digits — spaces and
     * dashes allowed, with an optional short label like "REC#" — wherever it is printed, top
     * or bottom. The longest digit run wins; scanning bottom-up breaks ties in favour of the
     * lower code. Date/time lines ("09/13/26 18:42:11") can never match: slashes and colons
     * are not allowed.
     */
    private fun detectBarcode(lines: List<String>): String? =
        lines.asReversed().asSequence()
            .filter { line -> BARCODE_LINE.matches(line) }
            .map { line -> line.filter { it.isDigit() } }
            .filter { it.length >= 12 }
            .maxByOrNull { it.length }

    private fun detectDate(lines: List<String>): Instant? {
        val text = lines.joinToString("\n")
        val latestAllowed = clock.now() + 1.days // receipts are printed in the past
        val candidates = buildList {
            NUMERIC_DATE.findAll(text).forEach { m ->
                val (a, b, c) = m.destructured
                var y = c.toInt(); if (y < 100) y += 2000
                val first = a.toInt(); val second = b.toInt()
                // US receipts print M/D/Y; fall back to D/M/Y when the first number cannot be a month.
                val (month, day) = if (first > 12) second to first else first to second
                val date = runCatching { LocalDate(y, month, day) }.getOrNull() ?: return@forEach
                add(date.atTime(timeAfter(text, m.range.last + 1)).toInstant(timeZone))
            }
            ISO_DATE.findAll(text).forEach { m ->
                val (y, mo, d) = m.destructured
                val date = runCatching { LocalDate(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull() ?: return@forEach
                add(date.atTime(timeAfter(text, m.range.last + 1)).toInstant(timeZone))
            }
        }
        return candidates.firstOrNull { it <= latestAllowed }
    }

    /** A "14:03" / "2:03 PM" immediately after a date belongs to it; otherwise midnight. */
    private fun timeAfter(text: String, from: Int): LocalTime {
        val m = TIME.find(text.substring(from.coerceAtMost(text.length)).take(16)) ?: return LocalTime(0, 0)
        var hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].toInt()
        val meridiem = m.groupValues[3].uppercase()
        if (meridiem == "PM" && hour < 12) hour += 12
        if (meridiem == "AM" && hour == 12) hour = 0
        return runCatching { LocalTime(hour, minute) }.getOrDefault(LocalTime(0, 0))
    }

    /**
     * "Costco Visa •••• 5278": the payment method (with its issuer prefix when printed, e.g.
     * "Costco Visa", "Chase Debit") plus the last four digits of the masked card number, which
     * card terminals print on their own line ("XXXXXXXXXXXX5278 CHIP Read"). Either half may
     * be missing; null when neither is found.
     */
    private fun detectPaymentInfo(lines: List<String>): String? {
        var method: String? = null
        for (line in lines) {
            val m = PAYMENT.find(line) ?: continue
            val prefix = m.groupValues[1].takeIf { it.isNotEmpty() && !PAYMENT_PREFIX_STOP.matches(it) }
            val name = m.groupValues[2].lowercase().split(' ').filter { it.isNotEmpty() }
                .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
            val candidate = listOfNotNull(prefix, name).joinToString(" ")
            // Prefer the mention that carries an issuer prefix ("Costco Visa" over a bare "Visa").
            if (method == null || (prefix != null && !method.contains(' '))) method = candidate
        }
        val last4 = lines.firstNotNullOfOrNull { line -> MASKED_CARD.find(line)?.groupValues?.get(1) }
        return when {
            method != null && last4 != null -> "$method •••• $last4"
            method != null -> method
            last4 != null -> "Card •••• $last4"
            else -> null
        }
    }

    /**
     * A line is an item only when it ends in a plain price (no minus sign, so discount and
     * refund lines such as "0000389072 / 2040045 5.00-" are skipped). The item number is
     * whichever the receipt prints, if any:
     *  - leading, after an optional tax flag: Costco "E 2040045 BC BBQ VTY 18.99", Target "212-01-0123 …";
     *  - trailing UPC before the price: Walmart "GV MILK 007874201234 F 2.98";
     *  - none at all (small shops): "SOURDOUGH LOAF 5.49".
     * A leading run of 4+ digits is always the item number; a shorter one only when it
     * follows a tax flag (Costco "E 3 WHOLE MILK 2.59" → item number "3"). Anything
     * else ("120 ML SERUM") stays part of the name.
     */
    private fun detectItems(lines: List<String>): List<ParsedItem> = lines.mapNotNull { line ->
        if (NON_ITEM.containsMatchIn(line)) return@mapNotNull null
        if (MASKED_CARD_FRAGMENT.containsMatchIn(line)) return@mapNotNull null
        val m = ITEM.find(line) ?: return@mapNotNull null
        val flag = m.groups[1]?.value
        val leading = m.groups[2]?.value
        var name = m.groups[3]?.value?.trim().orEmpty()
        val digits = leading?.count { it.isDigit() } ?: 0
        var itemNumber: String? = null
        if (digits >= 4 || (flag != null && digits in 1..3)) {
            itemNumber = leading
        } else if (leading != null) {
            // A short leading number with no tax flag ("120 ML SERUM") is part of the name.
            name = "$leading $name"
        }
        if (itemNumber == null) {
            TRAILING_ITEM_NUMBER.find(name)?.let { t ->
                itemNumber = t.groupValues[1]
                name = name.removeRange(t.range).trim()
            }
        }
        val price = m.groups[4]?.value?.replace(',', '.')?.toDoubleOrNull() ?: return@mapNotNull null
        // A "name" without letters is usually a barcode fragment, not an item.
        if (name.isEmpty() || name.none { it.isLetter() }) return@mapNotNull null
        ParsedItem(id = idFactory(), itemNumber = itemNumber, name = name, price = price)
    }

    companion object {
        /**
         * Chains whose logo often doesn't OCR (Target's bullseye) or that print promo lines
         * above the name: [name] is the display name, [fingerprint] matches the chain anywhere
         * it is printed, [keyword] is the fragment a detected name must mention, and
         * [namePattern] matches only the printed chain name — never inside a domain, so
         * "survey.walmart.com" can't win the store-name hunt. [exactName] forces [name]
         * verbatim whenever the chain is fingerprinted: for chains whose name is only ever
         * the logo (Big Y), the OCR line may read "Big Y SELF CHECKOUT".
         */
        private data class KnownChain(
            val name: String,
            val keyword: String,
            val fingerprint: Regex,
            val namePattern: Regex,
            val exactName: Boolean = false,
        )

        private val KNOWN_CHAINS = listOf(
            KnownChain("Target", "target", Regex("""(?i)\bTARGET\b|target\.com"""), Regex("""(?i)\bTARGET\b(?!\.[a-z])""")),
            KnownChain("Walmart", "walmart", Regex("""(?i)\bWAL-?MART\b|walmart\.com"""), Regex("""(?i)\bWAL-?MART\b(?!\.[a-z])""")),
            KnownChain("Costco", "costco", Regex("""(?i)\bCOSTCO\b|costco\.com"""), Regex("""(?i)\bCOSTCO\b(?!\.[a-z])""")),
            KnownChain("Trader Joe's", "trader joe", Regex("""(?i)\bTRADER\s+JOE'?S\b|traderjoes\.com"""), Regex("""(?i)\bTRADER\s+JOE'?S\b(?!\.[a-z])""")),
            KnownChain("Whole Foods", "whole foods", Regex("""(?i)\bWHOLE\s+FOODS\b|wholefoodsmarket\.com"""), Regex("""(?i)\bWHOLE\s+FOODS\b(?!\.[a-z])""")),
            KnownChain("Aldi", "aldi", Regex("""(?i)\bALDI\b|aldi\.(?:com|us)"""), Regex("""(?i)\bALDI\b(?!\.[a-z])""")),
            KnownChain("CVS", "cvs", Regex("""(?i)\bCVS\b|cvs\.com"""), Regex("""(?i)\bCVS\b(?!\.[a-z])""")),
            KnownChain("Walgreens", "walgreens", Regex("""(?i)\bWALGREENS\b|walgreens\.com"""), Regex("""(?i)\bWALGREENS\b(?!\.[a-z])""")),
            KnownChain("7-Eleven", "7-eleven", Regex("""(?i)\b7-?ELEVEN\b|7-eleven\.com"""), Regex("""(?i)\b7-?ELEVEN\b(?!\.[a-z])""")),
            KnownChain("Kroger", "kroger", Regex("""(?i)\bKROGER\b|kroger\.com"""), Regex("""(?i)\bKROGER\b(?!\.[a-z])""")),
            // Big Y prints its name only as a script logo (often missed by OCR) above
            // "SELF CHECKOUT", with the address on top: the fingerprint catches the
            // "Big Y Membership" line and bigy.com instead. The name pattern skips the
            // membership line, which is never the printed store name.
            KnownChain(
                "Big Y", "big y",
                Regex("""(?i)\bBIG\s+Y\b|bigy\.com"""),
                Regex("""(?i)\bBIG\s+Y\b(?!\.[a-z])(?!\s+MEMBERSHIP\b)"""),
                exactName = true,
            ),
        )

        /** A run of digits shaped like a phone number, anywhere in the line. */
        private val PHONE = Regex("""\b\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4}\b""")

        /**
         * Lines containing these words are receipt plumbing, never items. Whole-word matching
         * keeps "CASHEWS" and "Q-TIPS" alive; TAX also matches "TAX1"/"TAX 2" rate labels.
         */
        private val NON_ITEM = Regex(
            """(?i)\b(?:TOTAL|SUB-?TOTAL|NET SALES|TAX\s?\d*|CHANGE|TENDER|TEND|BALANCE|PAYMENT|AMOUNT|CASH ?BACK|CASH|REFUND|REG|""" +
                """SAVINGS|DISCOUNT|COUPON|CPN|LOYALTY|POINTS|REWARDS?|REBATE|REDEEM\w*|VISA|MASTER ?CARD|AMEX|AMERICAN EXPRESS|DISCOVER|""" +
                """DEBIT|CREDIT|APPROVED|AUTH\w*|ACCOUNT|ITEMS SOLD|QTY|INVOICE|MEMBER|THANKS?|THANK YOU|CHECKOUT|""" +
                """FEES?|SURCHARGE|GRATUITY|DONATION|VOID|DEPOSIT|CRV|(?<!-)\bTIPS?\b(?!-))\b""",
        )
        /** A masked card fragment ("*6872") marks a payment line, never an item. */
        private val MASKED_CARD_FRAGMENT = Regex("""[Xx*•]\d{4}\b""")

        /** Optional issuer word ("Costco", "Chase") followed by the payment method. */
        private val PAYMENT = Regex(
            """(?i)(?:\b([A-Z][A-Za-z]{2,})\s+)?\b(VISA|MASTER\s?CARD|AMEX|AMERICAN EXPRESS|DISCOVER|DEBIT|CREDIT|CASH|APPLE PAY|GIFT CARD)\b""",
        )

        /** Words that precede a method word but are not an issuer name. */
        private val PAYMENT_PREFIX_STOP = Regex("""(?i)TOTAL|CHANGE|TENDER|TEND|APPROVED|RESP|PAYMENT|AMOUNT|CARD|WITH|BY|ON|THE""")

        /**
         * Header junk some chains print above the real store name — greetings, reprint marks,
         * "STORE #" lines, phone numbers, feedback/survey invitations and self-checkout
         * labels. Skipped when hunting for the name.
         */
        private val STORE_NAME_SKIP = Regex(
            """(?i)\b(?:WELCOME TO|REPRINT|DUPLICATE|COPY|STORE\s*#|TEL|PHONE|FAX|FEEDBACK|SURVEY|THANK YOU|THANKS|SELF-?\s?CHECKOUT)\b|^\s*\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4}\s*$""",
        )

        /** A URL or email address is never the store's printed name. */
        private val URL_LIKE = Regex("""(?i)(?:[\w.-]+@[\w.-]+\.\w+|\b\w+\.(?:com|net|org|us|io|co)\b)""")

        /** Masked card number: a run of X, * or • followed by the last four digits. */
        private val MASKED_CARD = Regex("""(?:[Xx*•]{4,}|\d{0,4}[Xx*•]{2,})\s?(\d{4})\b""")

        /** "TOTAL TAX 1.94" — the printed tax summary, when present. */
        private val TOTAL_TAX = Regex("""(?i)\bTOTAL\s+TAX\b""")

        /**
         * "[tax flag] [item number] item name price[ tax code]". The price must be
         * positive — a leading or trailing minus marks a discount/refund line, which is
         * not an item. The item-number group accepts digits with dashes ("212-01-0123");
         * [detectItems] decides what counts as one.
         */
        private val ITEM = Regex("""^(?:([A-Z])\s+)?(?:(\d[\d-]*)\s+)?(.+?)\s+\$?(\d{1,4}[.,]\d{2})(?:\s?[A-Za-z*])?$""")

        /** UPC printed after the name (8+ digits, optional tax code letter) — Walmart style. */
        private val TRAILING_ITEM_NUMBER = Regex("""\s(\d{8,})(?:\s[A-Z])?$""")

        private const val MAX_ADDRESS_LINES = 5
        private const val MAX_STORE_NAME_SCAN = 8
        private const val MAX_CHAIN_SCAN = 40
        private const val MAX_STORE_NAME_LINES = 3
        private const val MAX_STORE_NAME_LINE_LEN = 24

        private val SUBTOTAL = Regex("""(?i)\bSUB\s?-?\s?TOTAL\b""")
        /** The grand total line — not "TOTAL TAX", "TOTAL SAVINGS" or "TOTAL NUMBER OF ITEMS". */
        private val TOTAL = Regex("""(?i)^\s*\**\s*TOTAL\b(?!\s*(?:TAX|SAVINGS|DISCOUNT|NUMBER|ITEMS|QTY))""")
        private val TAX = Regex("""(?i)\bTAX\b""")
        private val TRAILING_PRICE = Regex("""\$?\s?(-?\d{1,5}[.,]\d{2})\s?[A-Za-z*]?\s*$""")
        /** A line holding only a price, e.g. Costco's total amount above "**** TOTAL". */
        private val BARE_PRICE = Regex("""^\$?\s?-?\d{1,5}[.,]\d{2}$""")
        /** "REC#2-6261-2532-0171-9200-8": optional short label, then digits with spaces/dashes. */
        private val BARCODE_LINE = Regex("""(?i)^(?:[A-Z]{1,6}#\s?)?[\d \-]{12,}$""")

        private val NUMERIC_DATE = Regex("""\b(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{2}|\d{4})\b""")
        private val ISO_DATE = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")
        private val TIME = Regex("""\s*(\d{1,2}):(\d{2})(?::\d{2})?\s*([AaPp][Mm])?""")

        /**
         * Marks every item with a near-identical name at the same price (see [DuplicateMatcher]).
         * Runs on [ParsedItem] by round-tripping through [ReceiptItem] so both layers share one rule.
         */
        fun flagDuplicates(items: List<ParsedItem>): List<ParsedItem> {
            val flagged = DuplicateMatcher.flag(items.map { ReceiptItem(id = it.id, itemNumber = it.itemNumber, name = it.name, price = it.price) })
            return items.zip(flagged) { parsed, item -> parsed.copy(isDuplicate = item.isDuplicate) }
        }
    }
}
