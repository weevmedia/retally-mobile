package com.retally.shared.domain.receipt

import com.retally.shared.domain.receipt.model.ReceiptItem
import kotlin.math.abs
import kotlin.math.min

/**
 * Decides when two line items are "the same thing charged twice".
 *
 * Rule: the **item number** wins when both lines have one — "2040045" and "2040045"
 * are the same product even when OCR mangled the name (dashes and spaces are ignored,
 * so "212-01-0123" matches "212010123"). Only when at least one line has no item
 * number (small shops print none) does the matcher fall back to identical or
 * near-identical names (design system §5): names are normalised (lower-case,
 * alphanumerics only) and allowed a small edit distance that scales with length,
 * because OCR mangles a character or two ("WHOLE MILK" / "WH0LE MILK",
 * "KS ALMONDS" / "KS ALMOND").
 *
 * The price must always match to the cent — a different price is a different product
 * or a legitimate second purchase, never a double charge.
 */
object DuplicateMatcher {

    /** Lower-case alphanumerics only: "Whole  Milk, 1L" → "wholemilk1l". */
    fun normalize(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    /** Digits only: "212-01-0123" → "212010123". Blank when there is no real number. */
    fun normalizeItemNumber(number: String): String = number.filter { it.isDigit() }

    /**
     * True when [a] and [b] are the same item at the same price. Both item numbers
     * present → the numbers decide; otherwise the names decide.
     */
    fun isSameItem(a: ReceiptItem, b: ReceiptItem): Boolean {
        if (abs(a.price - b.price) >= 0.005) return false
        val aNumber = a.itemNumber?.let(::normalizeItemNumber).orEmpty()
        val bNumber = b.itemNumber?.let(::normalizeItemNumber).orEmpty()
        if (aNumber.isNotEmpty() && bNumber.isNotEmpty()) return aNumber == bNumber
        return isSimilarName(a.name, b.name)
    }

    /**
     * Names match when their normalised forms are equal, or differ by at most
     * `max(1, length / 6)` edits (1 edit up to 11 chars, 2 up to 17, 3 up to 23…).
     * Very short names (< 4 chars) must match exactly — "3 EGGS" vs "3 EGGO" is not noise.
     */
    fun isSimilarName(a: String, b: String): Boolean {
        val x = normalize(a)
        val y = normalize(b)
        if (x == y) return true
        if (x.isEmpty() || y.isEmpty()) return false
        val shorter = min(x.length, y.length)
        if (shorter < 4) return false
        val allowed = maxOf(1, shorter / 6)
        if (abs(x.length - y.length) > allowed) return false
        return levenshtein(x, y, limit = allowed) <= allowed
    }

    /**
     * Flags every item that has at least one other [isSameItem] match in the list, skipping
     * items the shopper has already confirmed as distinct ([ReceiptItem.keptAsDistinct]).
     * Items are grouped greedily so A~B and B~C put all three in one group.
     */
    fun flag(items: List<ReceiptItem>): List<ReceiptItem> {
        val groupOf = IntArray(items.size) { -1 }
        var nextGroup = 0
        for (i in items.indices) {
            if (items[i].keptAsDistinct) continue
            for (j in 0 until i) {
                if (items[j].keptAsDistinct) continue
                if (isSameItem(items[i], items[j])) {
                    if (groupOf[j] == -1) groupOf[j] = nextGroup++
                    groupOf[i] = groupOf[j]
                    break
                }
            }
        }
        val sizes = groupOf.filter { it >= 0 }.groupingBy { it }.eachCount()
        return items.mapIndexed { index, item ->
            val g = groupOf[index]
            val duplicate = g >= 0 && (sizes[g] ?: 0) > 1
            if (item.isDuplicate == duplicate) item else item.copy(isDuplicate = duplicate)
        }
    }

    /** Levenshtein distance with an early exit once [limit] is exceeded. */
    internal fun levenshtein(a: String, b: String, limit: Int): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            var rowMin = curr[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
                if (curr[j] < rowMin) rowMin = curr[j]
            }
            if (rowMin > limit) return rowMin
            val t = prev; prev = curr; curr = t
        }
        return prev[b.length]
    }
}
