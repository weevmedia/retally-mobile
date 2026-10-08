package com.retally.shared.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.retally.shared.core.strings.EnglishStringResolver
import com.retally.shared.core.strings.LocalStringResolver
import com.retally.shared.core.strings.StringResolver
import org.koin.mp.KoinPlatform
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.retally.shared.domain.preferences.ThemeMode

/**
 * Material colour scheme derived from the semantic tokens, so Material components (buttons,
 * dialogs, snackbars, top bars) pick up the brand without per-call colours. Screens prefer
 * `ReTallyTheme.colors.*` for anything the design system names explicitly.
 */
private fun ReTallyColors.toMaterial(dark: Boolean): ColorScheme = if (!dark) {
    lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryTint,
        onPrimaryContainer = primaryDark,
        secondary = muted,
        onSecondary = Color.White,
        tertiary = warning,
        tertiaryContainer = warningTint,
        onTertiaryContainer = warningInk,
        error = danger,
        onError = Color.White,
        errorContainer = dangerTint,
        onErrorContainer = Color(0xFF991B1B),
        background = background,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = background,
        onSurfaceVariant = muted,
        outline = divider,
        outlineVariant = divider,
    )
} else {
    darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryTint,
        onPrimaryContainer = Color(0xFFA7F3D0),
        secondary = muted,
        onSecondary = ink,
        tertiary = warning,
        tertiaryContainer = warningTint,
        onTertiaryContainer = warningInk,
        error = danger,
        onError = Color(0xFF450A0A),
        errorContainer = dangerTint,
        onErrorContainer = Color(0xFFFECACA),
        background = background,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = Color(0xFF273244),
        onSurfaceVariant = muted,
        outline = divider,
        outlineVariant = divider,
    )
}

/** Spacing scale as a composition-local view over [ReTallyDimensions] (kept for existing call sites). */
@Immutable
data class Dimensions(
    val xs: androidx.compose.ui.unit.Dp = ReTallyDimensions.spaceXS,
    val sm: androidx.compose.ui.unit.Dp = ReTallyDimensions.spaceSM,
    val md: androidx.compose.ui.unit.Dp = ReTallyDimensions.spaceMD,
    val lg: androidx.compose.ui.unit.Dp = ReTallyDimensions.spaceLG,
    val xl: androidx.compose.ui.unit.Dp = ReTallyDimensions.spaceXL,
    val xxl: androidx.compose.ui.unit.Dp = ReTallyDimensions.spaceXXL,
    val screen: androidx.compose.ui.unit.Dp = ReTallyDimensions.screenPadding,
    val cardRadius: androidx.compose.ui.unit.Dp = ReTallyDimensions.cardRadius,
    val cardPadding: androidx.compose.ui.unit.Dp = ReTallyDimensions.cardPadding,
    val controlRadius: androidx.compose.ui.unit.Dp = ReTallyDimensions.buttonRadius,
    val buttonHeight: androidx.compose.ui.unit.Dp = ReTallyDimensions.buttonHeight,
    val rowMinHeight: androidx.compose.ui.unit.Dp = ReTallyDimensions.rowMinHeight,
    val bannerHeight: androidx.compose.ui.unit.Dp = ReTallyDimensions.bannerHeight,
    val emptyIllustration: androidx.compose.ui.unit.Dp = ReTallyDimensions.emptyIllustration,
)

val LocalDimensions = staticCompositionLocalOf { Dimensions() }

object ReTallyTheme {
    val dimensions: Dimensions
        @Composable get() = LocalDimensions.current

    /** Semantic colour tokens (Primary, Ink, Muted, Warning…) for the current appearance. */
    val colors: ReTallyColors
        @Composable get() = LocalReTallyColors.current

    /** Named text styles — see [ReTallyTypography] for the scale. */
    val typography: ReTallyTypography get() = ReTallyTypography
}

@Composable
fun ReTallyTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    // The app follows the design system's single (light) colour theme regardless of the OS
    // appearance; [themeMode] is accepted for source compatibility and ignored.
    @Suppress("UNUSED_VARIABLE") val ignored = themeMode
    val dark = false
    val colors = ReTallyColors.Light
    val strings = remember { runCatching { KoinPlatform.getKoin().get<StringResolver>() }.getOrDefault(EnglishStringResolver) }
    CompositionLocalProvider(LocalReTallyColors provides colors, LocalStringResolver provides strings) {
        MaterialTheme(
            colorScheme = colors.toMaterial(dark),
            typography = ReTallyTypography.material,
            content = content,
        )
    }
}
