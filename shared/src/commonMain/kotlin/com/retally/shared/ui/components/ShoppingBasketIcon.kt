package com.retally.shared.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Shopping basket with a sparkle, recreated as a vector from the design asset so it stays
 * crisp at every size and tints like the other icons. 24x24 viewport: draw it at 24dp in
 * the tab bar and 48dp in the empty state.
 */
val ShoppingBasketIcon: ImageVector = ImageVector.Builder(
    name = "shopping_basket_sparkle",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    val ink = SolidColor(Color.Black)
    // Basket rim: thick rounded bar.
    path(fill = null, stroke = ink, strokeLineWidth = 3f, strokeLineCap = StrokeCap.Round) {
        moveTo(2.5f, 9.2f)
        lineTo(21.5f, 9.2f)
    }
    // Basket body: open trapezoid under the rim.
    path(
        fill = null,
        stroke = ink,
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(2.9f, 11f)
        lineTo(4.6f, 18.9f)
        quadTo(4.8f, 19.9f, 5.8f, 19.9f)
        lineTo(18.2f, 19.9f)
        quadTo(19.2f, 19.9f, 19.4f, 18.9f)
        lineTo(21.1f, 11f)
    }
    // Basket slats.
    path(fill = null, stroke = ink, strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round) {
        moveTo(11.5f, 12.2f)
        lineTo(11.5f, 15.6f)
        moveTo(15.5f, 12.2f)
        lineTo(15.5f, 15.6f)
    }
    // Handles, rising from the rim and leaning inward.
    path(fill = null, stroke = ink, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
        moveTo(7.8f, 8.6f)
        lineTo(10f, 3.8f)
        moveTo(16.2f, 8.6f)
        lineTo(14f, 3.8f)
    }
    // Sparkle: four-point star, top right.
    path(fill = ink) {
        moveTo(19.75f, 0.3f)
        quadTo(20.3f, 2.05f, 22.1f, 2.6f)
        quadTo(20.3f, 3.15f, 19.75f, 4.9f)
        quadTo(19.2f, 3.15f, 17.4f, 2.6f)
        quadTo(19.2f, 2.05f, 19.75f, 0.3f)
        close()
    }
}.build()
