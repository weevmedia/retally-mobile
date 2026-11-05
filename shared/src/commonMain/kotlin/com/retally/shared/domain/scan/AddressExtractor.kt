/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.domain.scan

/**
 * The address block a receipt prints under the store name, split into the parts the
 * "Edit receipt details" page shows as street / city / state / ZIP.
 */
data class ParsedAddress(
    val street: String,
    val city: String,
    val state: String,
    val zip: String,
)

/**
 * Best-effort split of a printed address block. Pure and US-only: receipts here print US
 * addresses ("123 Main St\nEdison, NJ 08817"), and the shapes they actually print are
 * handled: "City, ST ZIP", "City ST ZIP", "City, ST" (no ZIP), "City ZIP" (no state),
 * full state names ("New Jersey"), ZIP+4, multi-line streets, single-line street+city,
 * and a trailing phone line, which is dropped before parsing. Lines printed below the
 * address (store number, phone, slogan) are cut off at the last state + ZIP line. Two-letter tokens are only taken as a state when they
 * are a real US state code, so "Main St" never loses its "St".
 *
 * Anything it cannot place stays in [ParsedAddress.street], so no printed text is ever
 * lost — the shopper can always fix the fields by hand. This replaces the ad-hoc split
 * that lived in `StoreEdit`; the parser itself is untouched (`parse()` stays pure and
 * keeps the address block verbatim).
 */
object AddressExtractor {

    fun extract(address: String): ParsedAddress {
        var lines = address.lines().map { it.trim() }.filter { it.isNotEmpty() }
        // A phone line printed under the address ("781-499-1961") is header junk, not an
        // address part: drop trailing lines that are nothing but a 10-digit number.
        while (lines.size > 1 && lines.last().filter { it.isDigit() }.length == 10 &&
            lines.last().all { it.isDigit() || it == '-' || it == ' ' || it == '(' || it == ')' || it == '.' }
        ) {
            lines = lines.dropLast(1)
        }
        var text = lines.joinToString("\n")
        if (text.isEmpty()) return ParsedAddress("", "", "", "")

        // A receipt line printed below the address (store number, phone, slogan) must not
        // hijack the parse: keep everything up to the last line that actually ends with a
        // state + ZIP, and drop the rest. Without this, a trailing "ST# ..." line donates
        // its own numbers as the ZIP and the city/state come out wrong.
        val addressLines = text.lines()
        val lastAddressLine = addressLines.indexOfLast { isAddressLine(it) }
        if (lastAddressLine >= 0) {
            text = addressLines.take(lastAddressLine + 1).joinToString("\n")
        }

        var zip = ""
        Regex("""\b(\d{5}(?:-\d{4})?)\s*$""").find(text)?.let { match ->
            zip = match.groupValues[1]
            text = text.substring(0, match.range.first).trimEnd(',', ' ', '\n')
        }

        var state = ""
        // Full state name first, longest first: "West Virginia" must win over "Virginia".
        val lower = text.lowercase()
        for ((name, code) in STATE_NAMES) {
            if (lower.endsWith(name)) {
                val start = text.length - name.length
                if (start == 0 || text[start - 1] == ',' || text[start - 1] == ' ' || text[start - 1] == '\n') {
                    state = code
                    text = text.substring(0, start).trimEnd(',', ' ', '\n')
                    break
                }
            }
        }
        if (state.isEmpty()) {
            Regex("""[,\s]([A-Za-z]{2})\.?\s*$""").find(text)?.let { match ->
                val code = match.groupValues[1].uppercase()
                if (code in STATE_CODES) {
                    state = code
                    text = text.substring(0, match.range.first).trimEnd(',', ' ', '\n')
                }
            }
        }

        val (street, city) = when {
            '\n' in text -> text.substringBeforeLast('\n').trim() to
                text.substringAfterLast('\n').trim().trimEnd(',')
            ',' in text -> text.substringBeforeLast(',').trim() to
                text.substringAfterLast(',').trim()
            else -> splitSingleLine(text, zip)
        }
        return ParsedAddress(street.trim(), city.trim(), state, zip)
    }

    /**
     * One line left, e.g. "740 MIDDLE ST WEYMOUTH" after the state/ZIP came off: split
     * after the street suffix (or PO BOX number) so the city isn't swallowed into the
     * street. Falls back to the city-only reading ("Edison 08817") and then to keeping
     * the whole line as the street, so nothing is ever lost.
     */
    private fun splitSingleLine(text: String, zip: String): Pair<String, String> {
        STREET_LINE.find(text)?.let { match ->
            val street = match.groupValues[1].ifEmpty { match.groupValues[3] }.trim()
            val city = match.groupValues[2].ifEmpty { match.groupValues[4] }.trim().trimEnd(',')
            if (street.isNotEmpty() && city.isNotEmpty()) return street to city
        }
        if (zip.isNotEmpty() && text.none { it.isDigit() }) return "" to text.trim()
        return text.trim() to ""
    }

    /** "740 MIDDLE ST ..." or "PO BOX 123 ...": street part, then the city after it. */
    private val STREET_LINE = Regex(
        """^(\d+\s+.*\b(?:STREET|AVENUE|BOULEVARD|DRIVE|LANE|COURT|HIGHWAY|PLACE|TERRACE|ST|AVE|RD|BLVD|DR|LN|CT|WAY|HWY|PL|TER)\b\.?)\s+(.+)$|^(P\.?O\.?\s+BOX\s+\d+)\s+(.+)$""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * True when a line ends with a state + ZIP ("WEYMOUTH MA 02188", "Edison, NJ 08817"):
     * the ZIP must be preceded by a validated 2-letter code or a full state name, so a
     * store-number line ("ST# 03200 ... TR# 00669") never qualifies on its own digits.
     */
    private fun isAddressLine(line: String): Boolean {
        val zipMatch = Regex("""\b\d{5}(?:-\d{4})?\s*$""").find(line) ?: return false
        val before = line.substring(0, zipMatch.range.first).trimEnd()
        val token = before.substringAfterLast(' ').substringAfterLast(',').trim()
        if (token.length == 2 && token.uppercase() in STATE_CODES) return true
        val lower = before.lowercase()
        return STATE_NAMES.any { (name, _) -> lower.endsWith(name) }
    }

    private val STATE_CODES = setOf(
        "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "DC", "FL", "GA", "HI", "ID",
        "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD", "MA", "MI", "MN", "MS", "MO",
        "MT", "NE", "NV", "NH", "NJ", "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA",
        "RI", "SC", "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY",
    )

    private val STATE_NAMES = listOf(
        "district of columbia" to "DC",
        "new hampshire" to "NH",
        "new jersey" to "NJ",
        "new mexico" to "NM",
        "new york" to "NY",
        "north carolina" to "NC",
        "north dakota" to "ND",
        "rhode island" to "RI",
        "south carolina" to "SC",
        "south dakota" to "SD",
        "west virginia" to "WV",
        "alabama" to "AL",
        "alaska" to "AK",
        "arizona" to "AZ",
        "arkansas" to "AR",
        "california" to "CA",
        "colorado" to "CO",
        "connecticut" to "CT",
        "delaware" to "DE",
        "florida" to "FL",
        "georgia" to "GA",
        "hawaii" to "HI",
        "idaho" to "ID",
        "illinois" to "IL",
        "indiana" to "IN",
        "iowa" to "IA",
        "kansas" to "KS",
        "kentucky" to "KY",
        "louisiana" to "LA",
        "maine" to "ME",
        "maryland" to "MD",
        "massachusetts" to "MA",
        "michigan" to "MI",
        "minnesota" to "MN",
        "mississippi" to "MS",
        "missouri" to "MO",
        "montana" to "MT",
        "nebraska" to "NE",
        "nevada" to "NV",
        "ohio" to "OH",
        "oklahoma" to "OK",
        "oregon" to "OR",
        "pennsylvania" to "PA",
        "tennessee" to "TN",
        "texas" to "TX",
        "utah" to "UT",
        "vermont" to "VT",
        "virginia" to "VA",
        "washington" to "WA",
        "wisconsin" to "WI",
        "wyoming" to "WY",
    ).sortedByDescending { it.first.length }
}
