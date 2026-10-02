package com.retally.shared.ui.barcode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Code128Test {

    @Test
    fun `even digit strings use subset C with the standard checksum`() {
        // Start C(105) + 12·1 + 34·2 + 56·3 = 353; 353 mod 103 = 44
        assertEquals(listOf(105, 12, 34, 56, 44), Code128.symbols("123456"))
    }

    @Test
    fun `odd digit strings lead with one subset B digit then switch to C`() {
        val symbols = Code128.symbols("12345")
        assertEquals(104, symbols[0])           // Start B
        assertEquals('1'.code - 32, symbols[1]) // "1" in subset B
        assertEquals(99, symbols[2])            // Code C
        assertEquals(listOf(23, 45), symbols.subList(3, 5))
    }

    @Test
    fun `text uses subset B`() {
        val symbols = Code128.symbols("AB")
        assertEquals(listOf(104, 33, 34), symbols.dropLast(1))
    }

    @Test
    fun `module widths start with a bar and end with the stop pattern`() {
        val widths = Code128.moduleWidths("123456")
        // 5 symbols × 6 elements + 7-element stop
        assertEquals(5 * 6 + 7, widths.size)
        assertEquals(listOf(2, 3, 3, 1, 1, 1, 2), widths.takeLast(7))
        assertTrue(widths.all { it in 1..4 })
        // Every symbol is 11 modules wide; stop is 13.
        assertEquals(5 * 11 + 13, widths.sum())
    }
}
