package com.retally.shared.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/**
 * Typography system for ReTally — same shape as Sidearm's `CMPTypography`: one object of
 * named, documented text styles that screens reference by role, never by size.
 *
 * Fonts are the platform system faces (design system: Inter / SF Pro), so the app feels native on each side without
 * shipping font files: [FontFamily.Default] resolves to **SF Pro** on iOS (Display for 20pt
 * and above, Text below — the OS picks the optical size) and **Roboto** on Android.
 *
 * Scale (size / weight) — the design system's five anchors plus supporting styles:
 * ```
 * appTitle        40  Bold        the "ReTally" wordmark in the Home top bar
 * display         28  Bold        screen titles ("My Receipts")
 * title           20  SemiBold    card headers, store names
 * body            16  Regular     item rows
 * caption         13  Regular     hints, timestamps
 * pill            11  Bold        badges, uppercase (DUPLICATE, SYNCED TO CLOUD)
 * sectionHeader   18  SemiBold    group headings
 * rowTitle        16  SemiBold    store names in lists
 * secondaryBody   14  Regular     meta lines
 * price           24  Bold        receipt total (tabular)
 * amount          16  Medium      line-item prices (tabular)
 * heroAmount      40  ExtraBold   the "Caught so far" figure on Home
 * button          16  SemiBold    52pt buttons
 * link            18  Regular     underlined inline links ("See how it works")
 * ```
 * Numbers use tabular figures (`tnum`) so columns of prices align.
 */
object ReTallyTypography {

    /** SF Pro on iOS, Roboto on Android. */
    val fontFamily: FontFamily = FontFamily.Default

    private val base = TextStyle(
        fontFamily = fontFamily,
        lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None),
    )

    /** Tabular digits so amounts line up in columns. */
    private const val TABULAR = "tnum"

    // ── Design-system scale ───────────────────────────────────────────────────────────────
    /**
     * App title 40 Bold — the "ReTally" wordmark in the Home top bar only.
     *
     * Deliberately separate from [display]: the Home greeting and every other screen title
     * stay at 28, so bumping the wordmark does not drag them along.
     */
    val appTitle: TextStyle = base.copy(fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.6).sp)

    /** Display 28 Bold — screen titles ("My Receipts"). */
    val display: TextStyle = base.copy(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp)

    /** Title 20 SemiBold — card headers, store names. */
    val title: TextStyle = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp)

    /** Body 16 Regular — item rows. */
    val body: TextStyle = base.copy(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp)

    /** Caption 13 Regular — hints ("You buy this every 6 days"), timestamps. */
    val caption: TextStyle = base.copy(fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp)

    /** Pill 11 Bold uppercase — badges (DUPLICATE, SYNCED TO CLOUD). Callers uppercase the text. */
    val pill: TextStyle = base.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp)

    // ── Supporting styles (same families/weights, sizes between the anchors above) ────────
    /** Section Header 18 SemiBold — group headings. */
    val sectionHeader: TextStyle = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp)

    /** Row title 16 SemiBold — store names in lists. */
    val rowTitle: TextStyle = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp)

    /** Secondary body 14 Regular — meta lines, descriptions. */
    val secondaryBody: TextStyle = base.copy(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)

    /** Overline 12 Medium, tracked — section labels like "SEPTEMBER 2026". */
    val overline: TextStyle = base.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.8.sp)

    /** Price / total 24 Bold, tabular. */
    val price: TextStyle = base.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, fontFeatureSettings = TABULAR)

    /** Amount 16 Medium, tabular — line-item prices and subtotal/tax rows. */
    val amount: TextStyle = base.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp, fontFeatureSettings = TABULAR)

    /** Hero amount 40 ExtraBold, tabular — the headline figure on Home. */
    val heroAmount: TextStyle = base.copy(fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-1).sp, fontFeatureSettings = TABULAR)

    /**
     * Link 18 Regular, underlined — inline text links such as "See how it works".
     *
     * Carries its own underline so call sites do not `.copy()` a decoration onto `body`,
     * which is what they did before and is how the size drifted from the design.
     */
    val link: TextStyle = base.copy(
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        textDecoration = TextDecoration.Underline,
    )

    /** Button 16 SemiBold — 52pt primary buttons. */
    val button: TextStyle = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp)

    // ── Aliases kept so existing call sites map onto the design-system anchors ────────────
    val largeTitle: TextStyle get() = display
    val screenTitle: TextStyle get() = title
    val storeName: TextStyle get() = title
    val label: TextStyle get() = pill

    /**
     * Material mapping so `MaterialTheme.typography.*` and Material components (buttons,
     * dialogs, snackbars) render on the same scale.
     */
    val material: Typography = Typography(
        displayLarge = heroAmount,
        displayMedium = display,
        headlineLarge = display,
        headlineMedium = title,
        headlineSmall = title,
        titleLarge = title,
        titleMedium = rowTitle,
        titleSmall = rowTitle.copy(fontSize = 14.sp, lineHeight = 20.sp),
        bodyLarge = body,
        bodyMedium = secondaryBody,
        bodySmall = caption,
        labelLarge = button,
        labelMedium = overline,
        labelSmall = pill,
    )
}
