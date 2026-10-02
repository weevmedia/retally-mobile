package com.retally.shared.domain.receipt

import com.retally.shared.domain.receipt.model.ReceiptItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DuplicateMatcherTest {

    private fun item(id: String, name: String, price: Double, kept: Boolean = false, itemNumber: String? = null) =
        ReceiptItem(id = id, itemNumber = itemNumber, name = name, price = price, keptAsDistinct = kept)

    @Test
    fun identical_name_and_price_match() {
        assertTrue(DuplicateMatcher.isSameItem(item("a", "Sourdough Loaf", 5.49), item("b", "SOURDOUGH  LOAF", 5.49)))
    }

    @Test
    fun ocr_noise_within_one_or_two_characters_still_matches() {
        assertTrue(DuplicateMatcher.isSimilarName("WHOLE MILK", "WH0LE MILK"))       // O → 0
        assertTrue(DuplicateMatcher.isSimilarName("KS ALMONDS", "KS ALMOND"))        // dropped letter
        assertTrue(DuplicateMatcher.isSimilarName("MADEGOODMINI", "MADEG00DMINI"))   // two edits on 12 chars
    }

    @Test
    fun different_products_do_not_match() {
        assertFalse(DuplicateMatcher.isSimilarName("MANDARINS", "AVOCADOS"))
        assertFalse(DuplicateMatcher.isSimilarName("3 EGGS", "3 EGGO"))               // short names must be exact
        assertFalse(DuplicateMatcher.isSimilarName("GREEN KIWI", "GREEN KIWI 2LB"))   // length gap beyond tolerance
    }

    @Test
    fun a_different_price_is_never_a_duplicate() {
        assertFalse(DuplicateMatcher.isSameItem(item("a", "Whole Milk", 2.59), item("b", "Whole Milk", 2.99)))
    }

    @Test
    fun flag_marks_every_member_of_a_group_and_leaves_singles_alone() {
        val flagged = DuplicateMatcher.flag(
            listOf(item("1", "Whole Milk", 2.59), item("2", "Bananas", 1.19), item("3", "WH0LE MILK", 2.59), item("4", "Whole Milk", 2.59)),
        )
        assertEquals(listOf(true, false, true, true), flagged.map { it.isDuplicate })
    }

    @Test
    fun kept_items_are_skipped_and_their_partner_is_unflagged() {
        val flagged = DuplicateMatcher.flag(
            listOf(item("1", "Whole Milk", 2.59, kept = true), item("2", "Whole Milk", 2.59)),
        )
        assertEquals(listOf(false, false), flagged.map { it.isDuplicate })
    }

    @Test
    fun levenshtein_counts_edits() {
        assertEquals(0, DuplicateMatcher.levenshtein("abc", "abc", limit = 3))
        assertEquals(1, DuplicateMatcher.levenshtein("abc", "abd", limit = 3))
        assertEquals(2, DuplicateMatcher.levenshtein("kitten", "sittin", limit = 3))
        assertTrue(DuplicateMatcher.levenshtein("abcdef", "zzzzzz", limit = 1) > 1) // early exit still exceeds
    }

    @Test
    fun same_item_number_matches_despite_ocr_mangled_names() {
        assertTrue(
            DuplicateMatcher.isSameItem(
                item("a", "KS ALMONDS", 13.49, itemNumber = "284601"),
                item("b", "KS ALM0NDS", 13.49, itemNumber = "284601"),
            ),
        )
    }

    @Test
    fun dashed_item_numbers_match_their_plain_form() {
        assertTrue(
            DuplicateMatcher.isSameItem(
                item("a", "KITCHEN TOWEL", 4.50, itemNumber = "212-01-0123"),
                item("b", "KITCHEN TOWEL", 4.50, itemNumber = "212010123"),
            ),
        )
    }

    @Test
    fun different_item_numbers_never_match_despite_identical_names() {
        assertFalse(
            DuplicateMatcher.isSameItem(
                item("a", "KS ALMONDS", 13.49, itemNumber = "284601"),
                item("b", "KS ALMONDS", 13.49, itemNumber = "284602"),
            ),
        )
    }

    @Test
    fun name_fallback_applies_when_either_side_lacks_an_item_number() {
        assertTrue(
            DuplicateMatcher.isSameItem(
                item("a", "WHOLE MILK", 2.59, itemNumber = "000003"),
                item("b", "WHOLE MILK", 2.59),
            ),
        )
        assertFalse(
            DuplicateMatcher.isSameItem(
                item("a", "WHOLE MILK", 2.59, itemNumber = "000003"),
                item("b", "SKIM MILK", 2.59),
            ),
        )
    }
}
