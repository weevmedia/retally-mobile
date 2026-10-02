package com.retally.shared.ui.maps

/**
 * URL that opens the platform's maps app searching for [query] (a store name plus its
 * printed address). Apple Maps on iOS, Google Maps on Android — each falls back to the
 * browser when the app is missing, so the link never dead-ends.
 */
expect fun mapsSearchUrl(query: String): String

/** Store name and printed address as one maps query: "COSTCO WHOLESALE, 200 Legacy Blvd, Dedham, MA 02026". */
fun mapsQuery(storeName: String, storeAddress: String?): String =
    (listOf(storeName) + storeAddress.orEmpty().lines())
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(", ")

/** RFC 3986 percent-encoding for a query component (no java.net in common code). */
internal fun encodeUriComponent(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val c = byte.toInt() and 0xFF
        val ch = c.toChar()
        if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
            append(ch)
        } else {
            append('%').append(HEX[c shr 4]).append(HEX[c and 0x0F])
        }
    }
}

private const val HEX = "0123456789ABCDEF"
