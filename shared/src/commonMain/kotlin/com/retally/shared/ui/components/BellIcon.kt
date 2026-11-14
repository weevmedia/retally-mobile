/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Notification bell (hanging loop on top, rounded dome flaring to a flat rim, clapper below),
 * recreated as a vector from the design asset: transparent background, crisp at any size,
 * and tinted like the other icons. 24x24 viewport.
 */
val BellIcon: ImageVector = ImageVector.Builder(
    name = "bell",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    val ink = SolidColor(Color.Black)
    // Hanging loop.
    path(fill = null, stroke = ink, strokeLineWidth = 1.6f) {
        moveTo(13.3f, 3.5f)
        arcTo(1.3f, 1.3f, 0f, isMoreThanHalf = true, isPositiveArc = false, 10.7f, 3.5f)
        arcTo(1.3f, 1.3f, 0f, isMoreThanHalf = true, isPositiveArc = false, 13.3f, 3.5f)
        close()
    }
    // Dome and flared rim.
    path(
        fill = null,
        stroke = ink,
        strokeLineWidth = 1.7f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(6.6f, 15f)
        lineTo(6.6f, 10.6f)
        curveTo(6.6f, 7.4f, 9f, 5.1f, 12f, 5.1f)
        curveTo(15f, 5.1f, 17.4f, 7.4f, 17.4f, 10.6f)
        lineTo(17.4f, 15f)
        lineTo(20.2f, 18.4f)
        lineTo(3.8f, 18.4f)
        close()
    }
    // Clapper: half circle under the rim.
    path(
        fill = null,
        stroke = ink,
        strokeLineWidth = 1.7f,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(9.7f, 18.4f)
        arcTo(2.3f, 2.3f, 0f, isMoreThanHalf = false, isPositiveArc = false, 14.3f, 18.4f)
    }
}.build()
