package com.retally.shared.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Caps the user's font-size setting for *display* text — the hero figure, barcode digits,
 * the splash wordmark. Body text keeps scaling fully (Dynamic Type / Android font size are
 * respected everywhere else); these few oversized numerals would otherwise overflow their
 * cards at the largest accessibility sizes. 1.3× is the point where 40sp still fits a phone
 * card in one line.
 */
@Composable
fun CappedTextScale(maxFontScale: Float = 1.3f, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    if (density.fontScale <= maxFontScale) {
        content()
    } else {
        CompositionLocalProvider(
            LocalDensity provides Density(density = density.density, fontScale = maxFontScale),
            content = content,
        )
    }
}
