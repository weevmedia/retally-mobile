package com.retally.shared.ui.components

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StoreIconTest {

    @Test
    fun `bundled stores resolve to a logo case-insensitively`() {
        listOf(
            "Costco Wholesale",
            "COSTCO",
            "Walmart",
            "Wal-Mart Supercenter",
            "Target",
            "Trader Joe's",
            "TRADER JOES",
            "Whole Foods Market",
            "ALDI",
            "CVS/pharmacy",
            "Walgreens",
            "7-Eleven",
            "7 Eleven",
        ).forEach { name -> assertNotNull(storeLogoFor(name), "expected a logo for $name") }
    }

    @Test
    fun `kroger and unknown stores have no bundled logo`() {
        // Kroger's current mark isn't available as a freely reusable SVG, so it
        // uses the generic storefront fallback like any other unknown store.
        assertNull(storeLogoFor("Kroger"))
        assertNull(storeLogoFor("Joe's Corner Store"))
        assertNull(storeLogoFor(""))
    }
}
