package com.retally.shared.ui.barcode

import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.str
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.retally.shared.domain.receipt.model.Receipt
import com.retally.shared.ui.format.formatDateTime

/**
 * "Boarding-pass" style barcode sheet: a dimmed backdrop, one bright white card with the
 * receipt's barcode drawn large enough for a register scanner, and nothing else to read.
 * Tapping anywhere closes it — the shopper is holding the phone out to a cashier.
 */
@Composable
fun BarcodeModal(receipt: Receipt, onDismiss: () -> Unit) {
    val code = receipt.barcode ?: return
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.92f, animationSpec = tween(220)),
                exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.95f),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(24.dp, RoundedCornerShape(28.dp), clip = false)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color.White)
                            .padding(top = 24.dp, bottom = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            receipt.storeName.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF3A4D49),
                            letterSpacing = MaterialTheme.typography.labelLarge.letterSpacing * 1.5,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            formatDateTime(receipt.purchaseDate),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF7A8A86),
                        )
                        Spacer(Modifier.height(20.dp))
                        // Perforated "tear line" — the boarding-pass cue that this is scannable.
                        TearLine()
                        Spacer(Modifier.height(16.dp))
                        BarcodeImage(value = code, barHeight = 128.dp, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        Text(
                            str(S.BARCODE_SHOW_TO_SERVICE),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF7A8A86),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        str(S.BARCODE_TAP_TO_CLOSE),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

/** Dashed separator across the card with notches at both edges. */
@Composable
private fun TearLine() {
    Canvas(Modifier.fillMaxWidth().height(20.dp)) {
        val notch = 10.dp.toPx()
        val y = size.height / 2
        drawCircle(Color.Black.copy(alpha = 0.78f), radius = notch, center = Offset(0f, y))
        drawCircle(Color.Black.copy(alpha = 0.78f), radius = notch, center = Offset(size.width, y))
        var x = notch + 8.dp.toPx()
        val dash = 6.dp.toPx()
        val gap = 5.dp.toPx()
        while (x < size.width - notch - 8.dp.toPx()) {
            drawRect(Color(0xFFDDE5E2), topLeft = Offset(x, y - 1.dp.toPx()), size = Size(dash, 2.dp.toPx()))
            x += dash + gap
        }
    }
}

/**
 * Tiny barcode glyph for list rows — a handful of bars, drawn so it reads as "barcode" at
 * 16 dp without shipping an icon asset.
 */
@Composable
fun BarcodeGlyph(tint: Color, modifier: Modifier = Modifier.size(16.dp)) {
    Canvas(modifier) {
        val widths = listOf(2, 1, 2, 1, 1, 2, 1, 2, 1, 1)   // thick/thin rhythm, sums to 14 units
        val unit = size.width / 16f
        var x = unit
        widths.forEachIndexed { i, w ->
            if (i % 2 == 0) {
                drawRect(tint, topLeft = Offset(x, 0f), size = Size(w * unit, size.height))
            }
            x += w * unit
        }
    }
}
