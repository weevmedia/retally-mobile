/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.ui.components

import com.retally.shared.domain.store.iconKeyOf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Store logos come from the server per store (no bundled logos); what the app owns is the
 * key a receipt's store name maps to, which is also the server's document id.
 */
class StoreIconTest {

    @Test
    fun messyReceiptHeadersMapToTheStoreKey() {
        assertEquals("walmart", iconKeyOf("WALMART SUPERCENTER #2813"))
        assertEquals("costco", iconKeyOf("Costco Wholesale"))
        assertEquals("traderjoes", iconKeyOf("Trader Joe's"))
        assertEquals("7eleven", iconKeyOf("7-Eleven"))
        assertEquals("stopshop", iconKeyOf("Stop & Shop"))
    }

    @Test
    fun blankNameIsUnknown() {
        assertEquals("unknown", iconKeyOf(""))
        assertEquals("unknown", iconKeyOf("#12"))
    }
}
