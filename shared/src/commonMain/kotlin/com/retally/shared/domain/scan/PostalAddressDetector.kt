/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.domain.scan

/**
 * Finds the postal address inside a block of OCR'd receipt-header text using the
 * platform's own address detector — iOS `NSDataDetector` (returns street / city /
 * state / ZIP components directly) and Android's system `TextClassifier` (returns the
 * address span, which [AddressExtractor] then splits). Both run on-device, need no
 * model download and no API key.
 *
 * The detectors were trained on messy real-world text, so unlike line heuristics they
 * cope with every chain's own header layout: phone numbers before the street, store
 * numbers between the street and the city, "SELF CHECKOUT" lines, addresses on one
 * line or three. Returns null when no address is recognised; callers fall back to the
 * regex split. Never throws.
 */
expect suspend fun detectPostalAddress(text: String): ParsedAddress?

/**
 * Turns the raw header of a receipt into one canonical address block, so the receipt
 * shows, edits and geocodes the same clean text no matter how the store printed it:
 *
 * ```
 * 450 Providence Hwy
 * Dedham, MA 02026
 * ```
 *
 * Order of trust: the platform detector over the whole header (it sees lines the
 * line-by-line parser may have cut off), then [AddressExtractor] over the parser's own
 * address block, then the block as printed when neither finds a state or ZIP.
 */
object StoreAddressNormalizer {
    /** How much of the top of the receipt the detector gets to look at. */
    private const val HEADER_LINES = 14

    suspend fun normalize(ocrLines: List<String>, parsedBlock: String?): String? {
        val header = ocrLines.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(HEADER_LINES)
            .joinToString("\n")
        val detected = if (header.isNotBlank()) {
            runCatching { detectPostalAddress(header) }.getOrNull()?.takeIf { it.isUsable }
        } else {
            null
        }
        val parsed = detected
            ?: parsedBlock?.let { AddressExtractor.extract(it) }?.takeIf { it.isUsable }
            ?: return parsedBlock
        return format(parsed)
    }

    /**
     * Street on the first line, "City, ST ZIP" on the second; missing parts are omitted.
     * Header junk the regex fallback may leave in the street ("Dedham #319", "Store 1234",
     * a phone number) is dropped; a real second street line ("Suite 5") is kept.
     */
    fun format(a: ParsedAddress): String {
        val street = a.street.lines()
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotEmpty() && !STREET_JUNK.containsMatchIn(it) }
            .joinToString(", ")
        val cityState = listOf(a.city.trim(), a.state.trim().uppercase()).filter { it.isNotEmpty() }.joinToString(", ")
        val second = listOf(cityState, a.zip.trim()).filter { it.isNotEmpty() }.joinToString(" ")
        return listOf(street, second).filter { it.isNotEmpty() }.joinToString("\n")
    }

    private val STREET_JUNK = Regex("""#\s*\d+|^store\b|\(?\d{3}\)?[ .-]?\d{3}[ .-]\d{4}""", RegexOption.IGNORE_CASE)

    /** A detection is only worth replacing the printed block when it pins down the town. */
    private val ParsedAddress.isUsable: Boolean
        get() = zip.isNotEmpty() || (city.isNotEmpty() && state.isNotEmpty())
}
