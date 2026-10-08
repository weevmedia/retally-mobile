package com.retally.shared.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Raw brand palette — the hex values from the design system, one object per appearance,
 * mirroring Sidearm's `CMPLightPalette` / `CMPDarkPalette`. Screens never read these
 * directly; they go through the semantic [ReTallyColors] tokens below so a colour can be
 * re-pointed in one place.
 */
object ReTallyLightPalette {
    val primary = Color(0xFF10B981)       // CTAs, active tab, scan FAB
    val primaryDark = Color(0xFF047857)   // pressed states, headers
    val primaryTint = Color(0xFFD1FAE5)   // soft primary fill (synced badge, empty-state disc)
    val surface = Color(0xFFFFFFFF)       // cards, sheets
    val background = Color(0xFFEDEFF1)    // app background (light grey; white cards sit on this)
    val ink = Color(0xFF111827)           // headings, body
    val muted = Color(0xFF6B7280)         // secondary text, hints
    val warning = Color(0xFFF59E0B)       // DUPLICATE pills
    val warningTint = Color(0xFFFEF3C7)   // amber row tint
    val warningInk = Color(0xFF92400E)    // text on the amber tint
    val success = Color(0xFF10B981)       // cloud-synced checks
    val danger = Color(0xFFEF4444)        // delete, errors
    val dangerTint = Color(0xFFFEE2E2)
    val divider = Color(0xFFE5E7EB)       // 1pt list dividers, card hairlines
    val shadow = Color(0x14000000)        // 8% black
}

object ReTallyDarkPalette {
    val primary = Color(0xFF34D399)
    val primaryDark = Color(0xFF059669)
    val primaryTint = Color(0xFF064E3B)
    val surface = Color(0xFF1F2937)
    val background = Color(0xFF111827)
    val ink = Color(0xFFF9FAFB)
    val muted = Color(0xFF9CA3AF)
    val warning = Color(0xFFFBBF24)
    val warningTint = Color(0xFF3B2F0B)
    val warningInk = Color(0xFFFDE68A)
    val success = Color(0xFF34D399)
    val danger = Color(0xFFF87171)
    val dangerTint = Color(0xFF4C1D1D)
    val divider = Color(0xFF374151)
    val shadow = Color(0x33000000)
}

/**
 * Semantic colour tokens available as `ReTallyTheme.colors.*`. Names follow the design
 * system's token column (Primary, Ink, Muted, Warning…) rather than Material roles, so a
 * designer's "Muted" is `colors.muted` in code.
 */
@Immutable
data class ReTallyColors(
    val primary: Color,
    val primaryDark: Color,
    val primaryTint: Color,
    val onPrimary: Color,
    val surface: Color,
    val background: Color,
    val ink: Color,
    val muted: Color,
    val warning: Color,
    val warningTint: Color,
    val warningInk: Color,
    val success: Color,
    val danger: Color,
    val dangerTint: Color,
    val divider: Color,
    val shadow: Color,
) {
    companion object {
        val Light = ReTallyColors(
            primary = ReTallyLightPalette.primary,
            primaryDark = ReTallyLightPalette.primaryDark,
            primaryTint = ReTallyLightPalette.primaryTint,
            onPrimary = Color.White,
            surface = ReTallyLightPalette.surface,
            background = ReTallyLightPalette.background,
            ink = ReTallyLightPalette.ink,
            muted = ReTallyLightPalette.muted,
            warning = ReTallyLightPalette.warning,
            warningTint = ReTallyLightPalette.warningTint,
            warningInk = ReTallyLightPalette.warningInk,
            success = ReTallyLightPalette.success,
            danger = ReTallyLightPalette.danger,
            dangerTint = ReTallyLightPalette.dangerTint,
            divider = ReTallyLightPalette.divider,
            shadow = ReTallyLightPalette.shadow,
        )
        val Dark = ReTallyColors(
            primary = ReTallyDarkPalette.primary,
            primaryDark = ReTallyDarkPalette.primaryDark,
            primaryTint = ReTallyDarkPalette.primaryTint,
            onPrimary = Color(0xFF052E22),
            surface = ReTallyDarkPalette.surface,
            background = ReTallyDarkPalette.background,
            ink = ReTallyDarkPalette.ink,
            muted = ReTallyDarkPalette.muted,
            warning = ReTallyDarkPalette.warning,
            warningTint = ReTallyDarkPalette.warningTint,
            warningInk = ReTallyDarkPalette.warningInk,
            success = ReTallyDarkPalette.success,
            danger = ReTallyDarkPalette.danger,
            dangerTint = ReTallyDarkPalette.dangerTint,
            divider = ReTallyDarkPalette.divider,
            shadow = ReTallyDarkPalette.shadow,
        )
    }
}

val LocalReTallyColors = staticCompositionLocalOf { ReTallyColors.Light }
