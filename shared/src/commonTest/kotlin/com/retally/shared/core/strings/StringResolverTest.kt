package com.retally.shared.core.strings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StringResolverTest {
    @Test
    fun placeholders_are_filled_positionally_and_sequentially() {
        assertEquals("Good evening, Mukesh", formatPlaceholders("Good evening, %1\$s", arrayOf("Mukesh")))
        assertEquals("Removed Milk from Costco.", formatPlaceholders("Removed %1\$s from %2\$s.", arrayOf("Milk", "Costco")))
        assertEquals("3 receipts", formatPlaceholders("%d receipts", arrayOf(3)))
        assertEquals("a then b", formatPlaceholders("%s then %s", arrayOf("a", "b")))
        assertEquals("plain", formatPlaceholders("plain", null))
    }

    @Test
    fun english_fallback_covers_every_identifier() {
        // Every key in the catalogue must have English copy, so a typo in an identifier fails here.
        val resolver = EnglishStringResolver
        val unresolved = EnglishStrings.keys.filter { resolver.getString(it) == it }
        assertTrue(unresolved.isEmpty(), "Missing English copy for: $unresolved")
        assertEquals("No receipts yet", resolver.getString(StringAssetIdentifiers.HOME_EMPTY_TITLE))
    }
}
