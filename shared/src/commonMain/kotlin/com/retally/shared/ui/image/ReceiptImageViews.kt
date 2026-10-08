package com.retally.shared.ui.image

import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.str
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Round thumbnail of the scanned page with the caption below the circle; the "original"
 * the shopper can pull up for a cashier. Renders nothing when the image file is missing.
 */
@Composable
fun ReceiptThumbnail(imagePath: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bitmap = rememberLocalImage(imagePath, maxDimension = 400) ?: return
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = str(S.PHOTO_ORIGINAL_A11Y),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .border(2.dp, colors.primary, CircleShape),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            str(S.PHOTO_ORIGINAL),
            style = MaterialTheme.typography.titleSmall,
            color = colors.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            str(S.PHOTO_TAP_TO_VIEW),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Full-screen photo viewer: black backdrop, pinch to zoom, drag to pan, double-tap to reset,
 * close via the X or a single tap on the backdrop.
 */
@Composable
fun ReceiptImageViewer(imagePath: String, onDismiss: () -> Unit) {
    val bitmap = rememberLocalImage(imagePath, maxDimension = 2400)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = str(S.PHOTO_RECEIPT_A11Y),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                        .transformable(transform)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = { scale = 1f; offset = Offset.Zero },
                                onTap = { if (scale == 1f) onDismiss() },
                            )
                        },
                )
            } else {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Rounded.Image, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(str(S.PHOTO_MISSING), color = Color.White.copy(alpha = 0.8f))
                    Spacer(Modifier.weight(1f))
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp)
                    .background(Color.White.copy(alpha = 0.15f), CircleShape),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = str(S.ACTION_CLOSE), tint = Color.White)
            }
        }
    }
}
