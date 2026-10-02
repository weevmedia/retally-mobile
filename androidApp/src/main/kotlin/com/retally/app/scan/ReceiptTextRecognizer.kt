package com.retally.app.scan

import android.content.Context
import android.net.Uri
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

    suspend fun recognizeLines(pages: List<Uri>): List<String> = withContext(Dispatchers.Default) {
        pages.flatMap { uri ->
            val image = InputImage.fromFilePath(context, uri)
            mergeRows(recognizer.process(image).await())
        }
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
