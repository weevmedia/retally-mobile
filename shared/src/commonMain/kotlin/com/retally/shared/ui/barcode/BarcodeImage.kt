package com.retally.shared.ui.barcode

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.retally.shared.ui.theme.CappedTextScale
import com.retally.shared.ui.theme.ReTallyTheme
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A Code 128 barcode drawn from [value], with the digits printed underneath — the picture at
 * the bottom of a receipt, re-created from the OCR'd number so a store scanner can read it.
 * Always black on white regardless of theme; scanners need the contrast.
 */
@Composable
fun BarcodeImage(
    value: String,
    modifier: Modifier = Modifier,
    barHeight: Dp = 72.dp,
) {
    val widths = remember(value) { runCatching { Code128.moduleWidths(value) }.getOrNull() }
    Column(
        modifier = modifier
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (widths != null) {
            val totalModules = widths.sum() + QUIET_ZONE * 2
            Canvas(Modifier.fillMaxWidth().height(barHeight)) {
                val module = size.width / totalModules
                var x = QUIET_ZONE * module
                var bar = true
                for (w in widths) {
                    if (bar) {
                        drawRect(color = Color.Black, topLeft = Offset(x, 0f), size = Size(w * module, size.height))
                    }
                    x += w * module
                    bar = !bar
                }
            }
        }
        CappedTextScale(maxFontScale = 1.2f) {
            Text(
                text = value.chunked(4).joinToString(" "),
                style = ReTallyTheme.typography.amount.copy(letterSpacing = 2.sp),
                color = Color.Black,
                maxLines = 1,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

private const val QUIET_ZONE = 10
