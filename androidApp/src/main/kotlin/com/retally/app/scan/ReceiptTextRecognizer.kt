/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.app.scan

import android.graphics.Rect
import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

/**
 * ML Kit OCR for receipt pages. Returns text lines in reading order, merging fragments that
 * sit on the same printed row (item name on the left, price on the right) so the shared
 * `ReceiptParser` sees "SOURDOUGH LOAF 5.49" as one line — the same treatment the iOS
 * `ReceiptTextRecognizer` gives Vision output.
 */
class ReceiptTextRecognizer(private val context: Context) {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val barcodeScanner by lazy { BarcodeScanning.getClient() }

    suspend fun recognizeLines(pages: List<Uri>): List<String> = withContext(Dispatchers.Default) {
        pages.flatMap { uri ->
            val image = InputImage.fromFilePath(context, uri)
            mergeRows(recognizer.process(image).await()) + barcodes(image)
        }
    }

    /** One page's lines plus the box around all its text (image pixels), for cropping to the receipt. */
    data class Page(val lines: List<String>, val textBounds: Rect?)

    suspend fun recognizePage(uri: Uri): Page = withContext(Dispatchers.Default) {
        val image = InputImage.fromFilePath(context, uri)
        val text = recognizer.process(image).await()
        Page(mergeRows(text) + barcodes(image), receiptTextBounds(text))
    }

    /**
     * Barcode payloads read from the bars themselves, not OCR'd digits: text OCR on the
     * small digits under a top-of-receipt barcode often drops or misreads them, which is
     * why Walmart/Shaw's top barcodes came and went. Numeric payloads join as their own
     * lines, last, so the shared parser's barcode detector picks them up without ever
     * touching the store address at the top. A failed scan just yields no payloads.
     */
    private suspend fun barcodes(image: InputImage): List<String> = try {
        barcodeScanner.process(image).await().mapNotNull { it.rawValue }
            .filter { payload ->
                val digits = payload.filter { it.isDigit() }
                digits.length >= 12 && payload.all { it.isDigit() || it.isWhitespace() || it == '-' }
            }
            .map { payload -> payload.filter { it.isDigit() } }
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * The box around the receipt's printed text, robust to strays: only real lines (2+ letters or
     * digits) count, and only lines that overlap the receipt's main column — so text from
     * another paper lying next to the receipt, or a misread speck on the tablecloth, can't
     * stretch the crop sideways. The column is the band between the typical line's left and
     * right edges (medians); it then grows to take in lines within a short gap (the price
     * column), but not text from a separate paper further away.
     */
    private fun receiptTextBounds(text: Text): Rect? {
        val boxes = text.textBlocks.flatMap { it.lines }
            .filter { line -> line.text.count { it.isLetterOrDigit() } >= 2 }
            .mapNotNull { it.boundingBox }
        if (boxes.size < 3) return null
        fun median(values: List<Int>): Int = values.sorted()[values.size / 2]
        val bandLeft = median(boxes.map { it.left })
        val bandRight = median(boxes.map { it.right })
        if (bandRight <= bandLeft) return null
        // Grow from the column: the price column sits a little to the right of the item names, so
        // take in lines within a short gap of what's kept; a separate paper further away stays out.
        val maxGap = (bandRight - bandLeft) / 4
        val receipt = boxes.filter { it.right > bandLeft && it.left < bandRight }.toMutableList()
        if (receipt.size < 3) return null
        var left = receipt.minOf { it.left }
        var right = receipt.maxOf { it.right }
        var grew = true
        while (grew) {
            grew = false
            for (box in boxes) {
                if (box in receipt) continue
                val gap = when {
                    box.left > right -> box.left - right
                    box.right < left -> left - box.right
                    else -> 0
                }
                if (gap <= maxGap) {
                    receipt += box
                    left = minOf(left, box.left)
                    right = maxOf(right, box.right)
                    grew = true
                }
            }
        }
        return Rect(
            receipt.minOf { it.left },
            receipt.minOf { it.top },
            receipt.maxOf { it.right },
            receipt.maxOf { it.bottom },
        )
    }

    private fun mergeRows(text: Text): List<String> {
        data class Fragment(val text: String, val top: Int, val bottom: Int, val left: Int) {
            val midY get() = (top + bottom) / 2f
            val height get() = bottom - top
        }
        val fragments = text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            val value = line.text.trim()
            if (value.isEmpty()) null else Fragment(value, box.top, box.bottom, box.left)
        }.sortedBy { it.midY }

        val rows = mutableListOf<MutableList<Fragment>>()
        for (fragment in fragments) {
            val row = rows.lastOrNull()
            val anchor = row?.first()
            if (anchor != null && abs(anchor.midY - fragment.midY) < max(anchor.height, fragment.height) * 0.5f) {
                row.add(fragment)
            } else {
                rows.add(mutableListOf(fragment))
            }
        }
        return rows.map { row -> row.sortedBy { it.left }.joinToString(" ") { it.text } }
    }
}
