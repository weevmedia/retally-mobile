package com.retally.shared.ui.navigation

import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.str
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.DocumentScanner
import com.retally.shared.ui.components.ShoppingBasketIcon
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.retally.shared.presentation.main.MainTab
import com.retally.shared.ui.theme.ReTallyDimensions
import com.retally.shared.ui.theme.ReTallyTheme

/** Layout tokens for the tab bar; sizes the hosts must reserve are exposed here. */
object ReTallyTabBarDefaults {
    /** Height of the floating pill itself. */
    val barHeight: Dp = 72.dp
    /**
     * How far the scan FAB pokes out above the pill.
     *
     * 44 rather than 28: measured off the design, the green disc sits about a third into the
     * bar, not two thirds. The FAB is offset by `-fabOverhang + 4.dp`, so this value is what
     * decides the overlap — and [totalHeight] grows with it, which both hosts already read.
     */
    val fabOverhang: Dp = 44.dp
    /** Total height a host should reserve (pill + overhang + float margin), excluding system insets. */
    val totalHeight: Dp get() = barHeight + fabOverhang + bottomMargin
    val horizontalMargin: Dp = 12.dp
    /** Transparent gap below the pill so it floats with all four corners visible. */
    val bottomMargin: Dp = 12.dp
    /** Half the bar height: the bar is a stadium pill like the reference. */
    val cornerRadius: Dp = 36.dp
    val itemIconSize: Dp = 24.dp
    val fabSize: Dp = ReTallyDimensions.scanFabSize
    val fabIconSize: Dp = 26.dp
    /** White collar between the green disc and the shadow, so the FAB reads as a raised bump. */
    val fabRingWidth: Dp = 4.dp

    // -- Scan notch -------------------------------------------------------
    // Derived, never hand-typed: the iOS host draws a native glass pill behind this bar and
    // has to cut the identical notch. It reads these through `IosScreens`, so the two layers
    // cannot drift the way they did when Swift kept its own copy of the numbers.

    /** Radius of the circle subtracted from the pill's top edge. */
    val notchRadius: Dp get() = fabSize / 2 + fabRingWidth + 3.dp

    /**
     * Notch centre in pill coordinates, where 0 is the pill's top edge and negative is above
     * it. Follows the FAB, which is offset by `-fabOverhang + 4.dp`.
     */
    val notchCenterY: Dp get() = 4.dp + (fabSize + fabRingWidth * 2) / 2 - fabOverhang
}

/**
 * White Schwab-style bar: stadium pill, light pill highlight behind the selected tab,
 * brand emerald for the selected icon/label. Deliberately not theme-driven — the bar stays
 * white in both themes, like the reference.
 */
private object TabBarColors {
    val bar = Color.White
    val highlight = Color(0xFFF1F2F4)
    val selected = Color(0xFF10B981)
    val unselected = Color(0xFF6B7280)
}

/**
 * Bar background with a concave circular notch cut out of the top edge for the scan FAB.
 *
 * The cutout is centred on the FAB: [notchCenterY] is the FAB centre's y in bar coordinates
 * (negative = above the bar's top edge). [notchRadius] is the FAB's outer collar plus a
 * hairline gap, so the page background shows as a thin outline between the white ring and
 * the bar — the notch from the design. Without the gap the cutout edge would coincide with
 * the collar and stay invisible against the same white.
 */
private class NotchedBarShape(
    private val cornerRadius: Dp,
    private val notchCenterY: Dp,
    private val notchRadius: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val cr = with(density) { cornerRadius.toPx() }
        val nr = with(density) { notchRadius.toPx() }
        val cy = with(density) { notchCenterY.toPx() }
        val cx = size.width / 2f
        val bar = Path().apply {
            addRoundRect(RoundRect(Rect(0f, 0f, size.width, size.height), CornerRadius(cr, cr)))
        }
        val cutout = Path().apply {
            addOval(Rect(cx - nr, cy - nr, cx + nr, cy + nr))
        }
        return Outline.Generic(Path.combine(PathOperation.Difference, bar, cutout))
    }
}

/**
 * tab-bar with scan-fab: a floating rounded surface with Home · Receipts · [Scan] · Shopping List ·
 * More. The centre slot is the raised green FAB using the app's scan icon, labelled "Scan"
 * inside the bar. Shared by both platforms so the shell looks identical on iOS and Android.
 *
 * [bottomInset] is the system navigation-bar inset the host wants padded inside the surface.
 */
@Composable
fun ReTallyTabBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
    /**
     * iOS only: draw the bar translucent so the native glass behind it (Liquid Glass on
     * iOS 26+, ultra-thin blur on older) shows through. Android keeps the opaque bar.
     */
    translucentBar: Boolean = false,
) {
    val t = ReTallyTabBarDefaults
    // The theme's shadow token (8% black) is too faint to read as the design's soft outline.
    val barShadow = Color(0x26000000)
    // FAB centre in bar coordinates. The cutout is the collar plus a hairline gap so
    // the notch edge stays visible against the white ring.
    // The scan FAB (and its notch and label) is hidden on the Shopping List tab. The centre
    // slot keeps its width so the other tabs don't shift under the user's finger.
    val showScan = selected != MainTab.AI_LIST
    val notchShape: Shape = if (showScan) {
        NotchedBarShape(
            cornerRadius = t.cornerRadius,
            notchCenterY = t.notchCenterY,
            notchRadius = t.notchRadius,
        )
    } else {
        RoundedCornerShape(t.cornerRadius)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = t.fabOverhang, bottom = t.bottomMargin), // FAB overhang above; float above the screen edge below
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = t.horizontalMargin)
                .shadow(
                    elevation = 10.dp,
                    shape = notchShape,
                    clip = false,
                    ambientColor = barShadow,
                    spotColor = barShadow,
                )
                .clip(notchShape)
                .background(if (translucentBar) TabBarColors.bar.copy(alpha = 0.5f) else TabBarColors.bar)
                .padding(bottom = bottomInset)
                .height(t.barHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabItem(MainTab.HOME, Icons.Rounded.Home, selected == MainTab.HOME, onSelect, Modifier.weight(1f))
            TabItem(MainTab.RECEIPTS, Icons.AutoMirrored.Rounded.ReceiptLong, selected == MainTab.RECEIPTS, onSelect, Modifier.weight(1f))
            // Centre slot: only the "Scan" label sits in the bar; the FAB is drawn above.
            Column(
                modifier = Modifier.weight(1f).height(t.barHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (showScan) {
                    Text(str(S.TAB_SCAN), style = ReTallyTheme.typography.caption, color = TabBarColors.unselected)
                    Spacer(Modifier.height(14.dp))
                }
            }
            TabItem(MainTab.AI_LIST, ShoppingBasketIcon, selected == MainTab.AI_LIST, onSelect, Modifier.weight(1f))
            TabItem(MainTab.MORE, Icons.Rounded.MoreHoriz, selected == MainTab.MORE, onSelect, Modifier.weight(1f))
        }
        if (showScan) {
            ScanFab(
                onClick = onScan,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = -t.fabOverhang + 4.dp),
            )
        }
    }
}

@Composable
private fun TabItem(
    tab: MainTab,
    icon: ImageVector,
    selected: Boolean,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (selected) TabBarColors.selected else TabBarColors.unselected
    Box(
        modifier = modifier
            .height(ReTallyTabBarDefaults.barHeight)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = { onSelect(tab) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Selected tab sits in a lighter pill, like the reference.
        Column(
            modifier = Modifier
                .background(
                    if (selected) TabBarColors.highlight else Color.Transparent,
                    CircleShape,
                )
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val label = str(tab.labelKey)
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(ReTallyTabBarDefaults.itemIconSize))
            Spacer(Modifier.height(4.dp))
            Text(label, style = ReTallyTheme.typography.caption, color = tint)
        }
    }
}

/**
 * scan-fab: raised primary disc inside a white collar, so the bar appears to bulge around it.
 *
 * Two circles, and the order matters. The outer one is [ReTallyTabBarDefaults.fabRingWidth]
 * wider on each side, filled with `surface` (white) — it carries the shadow, and the white ring
 * now stands out against the dark bar. Above the bar the shadow falls on the page background
 * and reads as the grey edge around the bump.
 *
 * An earlier version tinted the collar with `primaryTint`, which stayed visible against the
 * white bar and read as an arc slicing through the Scan slot. Keep the collar `surface`.
 */
@Composable
private fun ScanFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = ReTallyTheme.colors
    val t = ReTallyTabBarDefaults
    Box(
        modifier = modifier
            .size(t.fabSize + t.fabRingWidth * 2)
            .shadow(6.dp, CircleShape, clip = false, ambientColor = c.shadow, spotColor = c.shadow)
            .clip(CircleShape)
            .background(c.surface),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(t.fabSize)
                .clip(CircleShape)
                .background(c.primary)
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.DocumentScanner,
                contentDescription = str(S.TAB_SCAN_RECEIPT_A11Y),
                tint = c.onPrimary,
                modifier = Modifier.size(t.fabIconSize),
            )
        }
    }
}
