/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.ui

import com.retally.shared.ui.format.formatCurrency
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatCurrencyTest {
    @Test
    fun `US conventions by default`() {
        assertEquals("$12.30", formatCurrency(12.3))
        assertEquals("$1,234,567.89", formatCurrency(1234567.891))
        assertEquals("-$0.50", formatCurrency(-0.5))
        assertEquals("$0.00", formatCurrency(0.0))
    }

    @Test
    fun `a currency no launch market uses is never shown as dollars`() {
        assertEquals("EUR 12.30", formatCurrency(12.3, "EUR"))
        assertEquals("$12.30", formatCurrency(12.3, "usd"))
    }
}
