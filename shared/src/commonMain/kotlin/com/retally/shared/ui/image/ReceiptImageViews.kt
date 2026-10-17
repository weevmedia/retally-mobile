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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Share
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

/**
 * Round thumbnail of the scanned page with the caption below the circle; the "original"
 * the shopper can pull up for a cashier. Renders nothing when the image file is missing.
 * Shows a downloading indicator while fetching from cloud.
 */
@Composable
fun ReceiptThumbnail(imagePath: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val imageState = rememberReceiptImage(imagePath, maxDimension = 400)
    val colors = MaterialTheme.colorScheme
    when (imageState) {
        is CloudImageState.Loading, is CloudImageState.Downloading -> {
            // Show a placeholder with downloading indicator.
            Column(
                modifier = modifier
                    .clip(RoundedCornerShape(16.dp))
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = colors.primary,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (imageState is CloudImageState.Downloading) "Downloading…" else str(S.PHOTO_ORIGINAL),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onBackground,
                    textAlign = TextAlign.Center,
                )
            }
        }
        is CloudImageState.Missing -> return
        is CloudImageState.Loaded -> {
            val bitmap = imageState.bitmap
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
        } // Close CloudImageState.Loaded
    } // Close when
}

/**
 * Full-screen photo viewer: app background, receipt-narrow image centered like a paper
 * receipt, vertical scroll for long receipts, pinch to zoom with pan when zoomed,
 * double-tap to reset. Scroll is disabled while zoomed so gestures never fight.
 * Share, Save, X grouped top-right — no top bar, no title. No heart icon here
 * (heart is only on the receipt page).
 * Slides up from the bottom (wrap in AnimatedVisibility at the call site).
 */
@Composable
fun ReceiptImageViewer(
    imagePath: String,
    onDismiss: () -> Unit,
    onShare: () -> Unit = {},
    onSave: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val imageState = rememberReceiptImage(imagePath, maxDimension = 2400)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val colors = MaterialTheme.colorScheme

    Box(
        modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        when (imageState) {
            is CloudImageState.Loading, is CloudImageState.Downloading -> {
                // Show downloading indicator in the center.
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = colors.primary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (imageState is CloudImageState.Downloading) "Downloading…" else "Loading…",
                        color = colors.onBackground,
                    )
                }
            }
            is CloudImageState.Missing -> {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.weight(1f))
                    androidx.compose.material.icons.Icons.Rounded.Image.let {
                        Icon(it, contentDescription = null, tint = colors.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(48.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(str(S.PHOTO_MISSING), color = colors.onSurfaceVariant.copy(alpha = 0.8f))
                    Spacer(Modifier.weight(1f))
                }
            }
            is CloudImageState.Loaded -> {
                val bitmap = imageState.bitmap
                val scrollState = rememberScrollState()
                // Zoom state: transformable handles pinch + pan. The vertical scroll is
                // only enabled at 1x — once zoomed, all gestures go to pan/zoom. This
                // avoids the gesture fight where detectTransformGestures consumed
                // single-finger drags and scroll worked intermittently.
                val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                    val newScale = (scale * zoomChange).coerceIn(1f, 6f)
                    scale = newScale
                    offset = if (newScale > 1f) offset + panChange else Offset.Zero
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState, enabled = scale <= 1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = str(S.PHOTO_RECEIPT_A11Y),
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .widthIn(max = 420.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 76.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            }
                            .transformable(transformState)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = { scale = 1f; offset = Offset.Zero },
                                    // Tap no longer dismisses — use the X button to close.
                                )
                            },
                    )
                }
            } // Close CloudImageState.Loaded
        } // Close when
        // Share, Save, X grouped top-right. Floating over the image, no top bar.
        // Tap on the image no longer dismisses — X is the only way to close.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = onShare,
                    modifier = Modifier.background(colors.surfaceVariant.copy(alpha = 0.85f), CircleShape),
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = "Share", tint = colors.onSurface)
                }
                IconButton(
                    onClick = onSave,
                    modifier = Modifier.background(colors.surfaceVariant.copy(alpha = 0.85f), CircleShape),
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = "Save to Photos", tint = colors.onSurface)
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.background(colors.surfaceVariant.copy(alpha = 0.85f), CircleShape),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colors.onSurface)
                }
            }
        }
    }
}
