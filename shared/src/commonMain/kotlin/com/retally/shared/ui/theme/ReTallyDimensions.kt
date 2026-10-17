package com.retally.shared.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Layout tokens from the design system, as a single object like Sidearm's `CMPDimensions`.
 * Everything sits on the 8pt grid (4pt half-steps for tight gaps). Components reference
 * these by name; a number that appears in a screen file is a bug.
 */
object ReTallyDimensions {
    // Grid
    val grid: Dp = 8.dp
    val gridHalf: Dp = 4.dp
    val spaceXS: Dp = 4.dp
    val spaceSM: Dp = 8.dp
    val spaceMD: Dp = 12.dp
    val spaceLG: Dp = 16.dp
    val spaceXL: Dp = 24.dp
    val spaceXXL: Dp = 32.dp

    // Screen
    /** Horizontal inset for screen content. */
    val screenPadding: Dp = 20.dp

    // Cards: 16pt radius, 16pt padding, soft shadow
    val cardRadius: Dp = 16.dp
    val cardPadding: Dp = 16.dp
    /** Elevation that approximates the spec's y=2 / blur=12 / 8% black shadow. */
    val cardShadowElevation: Dp = 2.dp

    // Buttons: 12–14pt radius, 52pt primary height
    val buttonRadius: Dp = 12.dp
    val buttonRadiusLarge: Dp = 14.dp
    val buttonHeight: Dp = 52.dp
    val buttonHeightCompact: Dp = 40.dp

    // List rows: 64pt min height, 1pt dividers
    val rowMinHeight: Dp = 64.dp
    val dividerThickness: Dp = 1.dp

    // Pills / badges
    val pillRadius: Dp = 999.dp
    val pillPaddingHorizontal: Dp = 8.dp
    val pillPaddingVertical: Dp = 3.dp
    val pillIconSize: Dp = 12.dp

    // Icons & badges
    val iconSmall: Dp = 16.dp
    val iconMedium: Dp = 22.dp
    val iconBadgeSize: Dp = 40.dp
    val emptyStateDisc: Dp = 88.dp
    val emptyStateIcon: Dp = 40.dp
    /** Larger empty-state artwork, for the shopping list tab. */
    val emptyStateIconLarge: Dp = 128.dp

    /**
     * Height of the Home empty-state illustration. 150 rather than the original 220: with the
     * guest banner shown, the taller artwork pushed the Scan button and the ad slot off the
     * bottom of the screen.
     */
    val emptyIllustration: Dp = 150.dp

    // Scan FAB in the tab bar
    val scanFabSize: Dp = 56.dp

    /** Standard AdMob banner height; the slot reserves it so content never jumps. */
    val bannerHeight: Dp = 50.dp
    val controlRadius: Dp = 12.dp
}
