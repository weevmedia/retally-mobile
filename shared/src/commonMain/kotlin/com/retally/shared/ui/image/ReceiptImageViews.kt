/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.ui.image

import androidx.compose.material.icons.rounded.Close
import com.retally.shared.ui.components.CircleIconButton
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.str
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
 * The app's white circle buttons float over the photo: X on the left, Share and Save on the right. The
 * photo sits below it as a paper card (rounded, soft shadow), with a short pinch-to-zoom hint.
 * No heart icon here (heart is only on the receipt page).
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
    Box(Modifier.fillMaxSize()) {
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
                // At 1x a one-finger drag is left alone, so the Column's native scroll (with
                // fling) handles it — smooth like any list. Pinch (two fingers) zooms; once
                // zoomed, drags pan the image instead and scrolling pauses.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState, enabled = scale <= 1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // The photo starts right under the status bar; the buttons float on top of it.
                    Spacer(Modifier.statusBarsPadding())
                    Image(
                        bitmap = bitmap,
                        contentDescription = str(S.PHOTO_RECEIPT_A11Y),
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .widthIn(max = 420.dp)
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp)
                            // Paper card: soft shadow, rounded corners, hairline edge. Zoom stays
                            // inside the card (the clip comes before the zoom layer).
                            .shadow(10.dp, RoundedCornerShape(16.dp), clip = false)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, colors.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                            // Gestures sit before graphicsLayer so pans are in screen pixels.
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = { scale = 1f; offset = Offset.Zero },
                                    // Tap no longer dismisses — use the X button to close.
                                )
                            }
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    do {
                                        val event = awaitPointerEvent()
                                        val fingers = event.changes.count { it.pressed }
                                        if (fingers >= 2 || scale > 1f) {
                                            val newScale = (scale * event.calculateZoom()).coerceIn(1f, 6f)
                                            scale = newScale
                                            offset = if (newScale > 1f) {
                                                // Keep the zoomed image covering its slot.
                                                val maxX = size.width * (newScale - 1f) / 2f
                                                val maxY = size.height * (newScale - 1f) / 2f
                                                val moved = offset + event.calculatePan()
                                                Offset(moved.x.coerceIn(-maxX, maxX), moved.y.coerceIn(-maxY, maxY))
                                            } else {
                                                Offset.Zero
                                            }
                                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                    )
                }
            } // Close CloudImageState.Loaded
        } // Close when
        // One-time gesture hint, fades out after a few seconds.
        var showHint by remember { mutableStateOf(true) }
        val loaded = imageState is CloudImageState.Loaded
        androidx.compose.runtime.LaunchedEffect(loaded) {
            if (!loaded) return@LaunchedEffect
            kotlinx.coroutines.delay(3_000) // count from when the photo is actually on screen
            showHint = false
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = showHint && loaded,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp),
        ) {
            Text(
                str(S.PHOTO_VIEWER_HINT),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    } // Close content Box
    // Floating buttons over the photo (like a photo viewer): X on the left, Share and Save on
    // the right.
    Row(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The app's own white circle buttons, same icons as before, floating over the photo.
        CircleIconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colors.onSurface)
        }
        Spacer(Modifier.weight(1f))
        CircleIconButton(onClick = onShare) {
            Icon(Icons.Rounded.Share, contentDescription = "Share", tint = colors.onSurface)
        }
        CircleIconButton(onClick = onSave) {
            Icon(Icons.Rounded.Download, contentDescription = "Save to Photos", tint = colors.onSurface)
        }
    }
    }
}

