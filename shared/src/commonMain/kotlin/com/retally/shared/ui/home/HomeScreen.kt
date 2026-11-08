/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.ui.home

import com.retally.shared.domain.budget.BudgetPace
import com.retally.shared.ui.components.AboveScanFab
import com.retally.shared.ui.components.BannerGapAboveScanFab
import com.retally.shared.ui.components.CircleIconButton
import com.retally.shared.ui.components.SwipeAction
import com.retally.shared.ui.components.SwipeRevealRow
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import com.retally.shared.ui.format.formatMonthYear
import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.str
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.retally.shared.ui.barcode.BarcodeModal
import com.retally.shared.ui.barcode.BarcodeGlyph
import com.retally.shared.ui.components.StoreIcon
import com.retally.shared.domain.update.UpdateStatus
import com.retally.shared.ui.update.UpdateAvailableDialog
import com.retally.shared.ui.update.UpdateRequiredScreen
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.sp
import com.retally.shared.domain.ai.Nudge
import com.retally.shared.domain.ai.NudgeReadStore
import com.retally.shared.domain.receipt.model.Receipt
import com.retally.shared.presentation.home.HomeContract
import com.retally.shared.ui.components.AdBannerSlot
import com.retally.shared.ui.components.AdFormat
import com.retally.shared.ui.components.Pill
import com.retally.shared.ui.components.SurfaceCard
import com.retally.shared.ui.components.ReTallySnackbarHost
import com.retally.shared.ui.format.formatCurrency
import com.retally.shared.ui.format.formatShortDate
import com.retally.shared.ui.theme.CappedTextScale
import com.retally.shared.ui.theme.ReTallyTheme
import kotlin.time.Clock
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/** User-facing copy for one-shot Home effects; null for effects the host handles itself. */
fun HomeContract.SideEffect.userMessage(): String? = when (this) {
    is HomeContract.SideEffect.ShowMessage -> message
    HomeContract.SideEffect.LaunchScanner,
    HomeContract.SideEffect.OpenDuplicates,
    HomeContract.SideEffect.OpenBudget,
    HomeContract.SideEffect.OpenNotifications,
    HomeContract.SideEffect.OpenReceipts,
    HomeContract.SideEffect.OpenSignIn,
    HomeContract.SideEffect.OpenShoppingList,
    is HomeContract.SideEffect.OpenScanReview,
    is HomeContract.SideEffect.OpenReceipt,
    is HomeContract.SideEffect.OpenHowItWorks,
    -> null
}

/**
 * Stateless Home screen: renders [state], reports intents through [dispatch]. Side effects
 * are the caller's job (snackbar via [snackbarHostState], navigation via the host).
 *
 * Layout: top bar with the scan action, the ad banner directly under it, then a savings
 * summary card followed by receipts grouped by month. Rows open the receipt on tap and
 * delete on swipe-left (with confirmation); the empty state carries the first-scan CTA.
 *
 * In debug builds a long-press on the scan icon adds a sample receipt (replaces the old "+").
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    state: HomeContract.State,
    dispatch: (HomeContract.Action) -> Unit,
    adsEnabled: Boolean,
    bannerAdUnitId: String,
    showDebugActions: Boolean,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    bottomContentInset: Dp = 0.dp,
    /** Space between the bottom of the content area and the floating banner slot. */
    floatingBannerBottomOffset: Dp = 10.dp,
) {
    val d = ReTallyTheme.dimensions
    // UI-only: which receipt's barcode is being shown full-screen for a store scanner.
    var barcodeReceipt by remember { mutableStateOf<Receipt?>(null) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(str(S.APP_NAME), style = ReTallyTheme.typography.appTitle, color = ReTallyTheme.colors.ink) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ReTallyTheme.colors.background),
                actions = {
                    // Scanning moved to the tab bar's centre FAB; the bell opens the 48-hour
                    // notification inbox. Debug builds: long-press adds a sample receipt.
                    NotificationsAction(
                        hasNotifications = state.hasNotifications,
                        newNotificationCount = state.newNotificationCount,
                        onClick = { dispatch(HomeContract.Action.NotificationsClicked) },
                        onLongClick = if (showDebugActions) ({ dispatch(HomeContract.Action.AddSampleReceipt) }) else null,
                    )
                    Spacer(Modifier.width(d.sm))
                },
            )
        },
        snackbarHost = { ReTallySnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        // Where the ad goes depends on the list: with no receipts the 320×50 strip floats just
        // above the scan button (matching Receipts); once there are receipts it moves inline
        // (guests: above "Recent receipts"; signed in: after the first receipt), so it reads as
        // part of the page instead of covering the last row. Not while receipts are still restoring.
        // Signed in with a single receipt and no budget card the page is short enough that
        // the strip can float too — an inline ad after the only receipt would just hang at
        // the bottom of the list.
        val shortSignedInHome = !state.account.isGuest && state.budgetFraction == null && state.receipts.size == 1
        val showFloatingStrip = adsEnabled && !state.isRestoring && (state.isEmpty || shortSignedInHome)
        val bannerReserve = if (showFloatingStrip) d.bannerHeight + d.md * 2 else 0.dp
        Box(Modifier.fillMaxSize()) {
            val required = state.update as? UpdateStatus.Required
            if (required != null) {
                // Below the backend's minimum: Home is replaced until the app is updated.
                UpdateRequiredScreen(
                    status = required,
                    onUpdate = { dispatch(HomeContract.Action.UpdateClicked) },
                    modifier = Modifier.padding(top = padding.calculateTopPadding()),
                )
            } else {
                HomeContent(
                    state = state,
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding(),
                        // bottomContentInset is host chrome Compose cannot see: on iOS the tab bar
                        // is a SwiftUI sibling and HomeView ignores the safe area, so the inset
                        // TabBarView adds never reaches here. Android injects it into the window
                        // insets instead and passes 0.
                        bottom = padding.calculateBottomPadding() + bannerReserve + bottomContentInset,
                    ),
                    onScan = { dispatch(HomeContract.Action.ScanClicked) },
                    onReceiptClick = { dispatch(HomeContract.Action.ReceiptClicked(it)) },
                    onBarcodeClick = { barcodeReceipt = it },
                    onDelete = { dispatch(HomeContract.Action.DeleteRequested(it)) },
                    onDuplicatesClick = { dispatch(HomeContract.Action.DuplicatesClicked) },
                    onSpentClick = { dispatch(HomeContract.Action.BudgetClicked) },
                    onNudgeClick = { dispatch(HomeContract.Action.NudgeClicked(it)) },
                    onHowItWorks = { dispatch(HomeContract.Action.HowItWorksClicked) },
                    onSignIn = { dispatch(HomeContract.Action.SignInClicked) },
                    onSeeAll = { dispatch(HomeContract.Action.SeeAllReceiptsClicked) },
                    // Inline (above Recent receipts) whenever the strip isn't floating.
                    adsEnabled = adsEnabled && !showFloatingStrip,
                    bannerAdUnitId = bannerAdUnitId,
                )
            }
            if (showFloatingStrip) {
                // Home: the ad sits 2dp above the scan FAB (Android; iOS uses fallbackBottom).
                // FloatingAdBanner has d.xs of its own vertical padding, so take it off.
                AboveScanFab(
                    fallbackBottom = padding.calculateBottomPadding() + bottomContentInset + floatingBannerBottomOffset,
                    gap = HOME_BANNER_GAP_ABOVE_SCAN_FAB - d.xs,
                ) {
                    FloatingAdBanner(adUnitId = bannerAdUnitId, enabled = true)
                }
            }
        }
    }

    barcodeReceipt?.let { receipt -> BarcodeModal(receipt = receipt, onDismiss = { barcodeReceipt = null }) }

    (state.update as? UpdateStatus.Available)?.let { available ->
        UpdateAvailableDialog(
            status = available,
            onUpdate = { dispatch(HomeContract.Action.UpdateClicked) },
            onLater = { dispatch(HomeContract.Action.UpdateDismissed) },
        )
    }

    if (state.backingUpCount > 0 || state.backedUpCount > 0) {
        SyncingReceiptsDialog(syncingCount = state.backingUpCount, syncedCount = state.backedUpCount)
    }

    if (state.pendingBackupPrompt > 0) {
        val n = state.pendingBackupPrompt
        AlertDialog(
            onDismissRequest = { dispatch(HomeContract.Action.PendingBackupDeclined) },
            title = { Text(if (n == 1) str(S.HOME_BACKUP_PROMPT_TITLE_ONE) else str(S.HOME_BACKUP_PROMPT_TITLE, n)) },
            text = { Text(str(if (n == 1) S.HOME_BACKUP_PROMPT_BODY_ONE else S.HOME_BACKUP_PROMPT_BODY, n)) },
            confirmButton = {
                TextButton(onClick = { dispatch(HomeContract.Action.PendingBackupConfirmed) }) {
                    Text(str(S.HOME_BACKUP_PROMPT_CONFIRM))
                }
            },
            dismissButton = {
                TextButton(onClick = { dispatch(HomeContract.Action.PendingBackupDeclined) }) {
                    Text(str(S.HOME_BACKUP_PROMPT_LATER))
                }
            },
        )
    }

    state.pendingDelete?.let { receipt ->
        AlertDialog(
            onDismissRequest = { dispatch(HomeContract.Action.DeleteCancelled) },
            title = { Text(str(S.HOME_DELETE_TITLE)) },
            text = { Text(str(S.HOME_DELETE_BODY, receipt.storeName, formatShortDate(receipt.purchaseDate))) },
            confirmButton = {
                TextButton(onClick = { dispatch(HomeContract.Action.DeleteConfirmed) }) {
                    Text(str(S.ACTION_DELETE), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { dispatch(HomeContract.Action.DeleteCancelled) }) { Text(str(S.ACTION_CANCEL)) }
            },
        )
    }
}

/** 320×50 banner floating above the tab bar — transparent, no card or shadow, so it sits
 * directly on the screen background. */
@Composable
private fun FloatingAdBanner(adUnitId: String, enabled: Boolean, modifier: Modifier = Modifier) {
    if (!enabled) return
    val d = ReTallyTheme.dimensions
    Box(
        modifier = modifier.padding(horizontal = d.lg),
        contentAlignment = Alignment.Center,
    ) {
        AdBannerSlot(
            adUnitId = adUnitId,
            enabled = true,
            modifier = Modifier.padding(vertical = d.xs),
        )
    }
}

/**
 * 320×50 banner with an "Advertisement" caption, transparent so it sits on the screen
 * background.
 *
 * Was a 300×250 medium rectangle, which ate ~200dp of the empty state and pushed the body
 * copy and the Scan button below the fold on shorter screens. Same unit id as the floating
 * strip; AdMob serves whichever size is requested.
 */
@Composable
internal fun InlineRectangleAd(adUnitId: String) {
    val d = ReTallyTheme.dimensions
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = d.lg),
        shape = RoundedCornerShape(d.cardRadius),
        color = Color.Transparent,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = str(S.GENERAL_ADVERTISEMENT),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = d.sm, bottom = d.xs),
            )
            AdBannerSlot(
                adUnitId = adUnitId,
                enabled = true,
                format = AdFormat.BANNER,
                modifier = Modifier.padding(bottom = d.md),
            )
        }
    }
}

/**
 * Bell in the top bar. The dot shows while the inbox holds anything from the last 48 hours;
 * the bell shakes only while there are *new* ones (arrived since the inbox was last opened).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotificationsAction(
    hasNotifications: Boolean,
    newNotificationCount: Int,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val c = ReTallyTheme.colors
    val bellAngle = remember { Animatable(0f) }
    // Keyed on the count: another new notification restarts the shake right away.
    LaunchedEffect(newNotificationCount) {
        if (newNotificationCount == 0) {
            bellAngle.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            for (angle in BELL_RING_ANGLES) bellAngle.animateTo(angle, tween(BELL_RING_STEP_MS))
            delay(BELL_RING_PAUSE_MS)
        }
    }
    CircleIconButton(onClick = onClick, onLongClick = onLongClick) {
        Icon(
            Icons.Outlined.Notifications,
            contentDescription = str(S.HOME_NOTIFICATIONS_A11Y),
            tint = c.ink,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    rotationZ = bellAngle.value
                    // Swing from the top of the bell, like a real one.
                    transformOrigin = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 0.1f)
                },
        )
        if (hasNotifications) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 9.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(c.primary),
            )
        }
    }
}

/** Home banner ad to the top of the scan FAB (Android). Other screens use [BannerGapAboveScanFab]. */
private val HOME_BANNER_GAP_ABOVE_SCAN_FAB = 2.dp

/** Bell shake: swing angles (degrees), time per swing, and the pause between shakes. */
private val BELL_RING_ANGLES = listOf(16f, -14f, 11f, -8f, 5f, -2f, 0f)
private const val BELL_RING_STEP_MS = 70
private const val BELL_RING_PAUSE_MS = 3_000L

@Composable
internal fun HomeContent(
    state: HomeContract.State,
    contentPadding: PaddingValues,
    onScan: () -> Unit,
    onReceiptClick: (String) -> Unit,
    onDelete: (String) -> Unit,
    onBarcodeClick: (Receipt) -> Unit = {},
    onDuplicatesClick: () -> Unit = {},
    onSpentClick: () -> Unit = {},
    onNudgeClick: (String) -> Unit = {},
    onHowItWorks: () -> Unit = {},
    onSignIn: () -> Unit = {},
    onSeeAll: () -> Unit = {},
    adsEnabled: Boolean = false,
    bannerAdUnitId: String = "",
) {
    val d = ReTallyTheme.dimensions

    if (state.isEmpty && state.isRestoring) {
        RestoringReceipts(contentPadding)
        return
    }
    if (state.isEmpty) {
        EmptyHome(
            account = state.account,
            contentPadding = contentPadding,
            onScan = onScan,
            onHowItWorks = onHowItWorks,
            onSignIn = onSignIn,
            adsEnabled = adsEnabled,
            bannerAdUnitId = bannerAdUnitId,
        )
        return
    }

    // The receipt whose Delete action is revealed; opening one closes the rest.
    var swipedReceiptId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = d.screen,
            end = d.screen,
            top = contentPadding.calculateTopPadding() + d.sm,
            bottom = contentPadding.calculateBottomPadding() + d.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(d.md),
    ) {
        if (state.receipts.isNotEmpty()) {
            item { Greeting(state.account) }
            if (state.account.isGuest) {
                item { GuestBanner(onSignIn = onSignIn) }
            }
            item {
                // The month lives in the header, so the three tiles below can just say what they count.
                Text(
                    str(S.HOME_EXPENSE_OVERVIEW, formatMonthYear(Clock.System.now()).substringBefore(' ')),
                    style = ReTallyTheme.typography.sectionHeader,
                    color = ReTallyTheme.colors.ink,
                    modifier = Modifier.padding(top = ReTallyTheme.dimensions.sm),
                )
            }
            item { StatTiles(state, onSpentClick = onSpentClick, onReceiptsClick = onSeeAll) }
            // Server-built nudges (signed-in only); guests see the sign-in banner instead.
            if (!state.account.isGuest) {
                item { NudgeCarousel(state.nudges, onNudgeClick = onNudgeClick) }
            }
            // Only when this month has a budget: spent vs budget, coloured by pace (no title line).
            if (state.budgetFraction != null) {
                item { BudgetProgressCard(state, onClick = onSpentClick) }
            }
            // Inline 320×50 ad: guests see it above Recent receipts; signed-in users see it
            // after the first receipt, so their latest scan stays right under the header.
            val adAboveHeader = adsEnabled && state.account.isGuest
            val adAfterFirstReceipt = adsEnabled && !state.account.isGuest
            if (adAboveHeader) {
                item(key = "ad") { AdBannerSlot(adUnitId = bannerAdUnitId, enabled = true, modifier = Modifier.padding(top = d.sm)) }
            }
            item {
                RecentReceiptsHeader(
                    onSeeAll = onSeeAll,
                    modifier = Modifier.padding(top = d.sm),
                )
            }
            state.recentReceipts.take(HomeContract.State.RECENT_RECEIPT_COUNT).forEachIndexed { index, receipt ->
                item(key = receipt.id) {
                    SwipeToDeleteRow(
                        onDelete = { onDelete(receipt.id) },
                        open = swipedReceiptId == receipt.id,
                        onOpenChange = { open -> swipedReceiptId = if (open) receipt.id else swipedReceiptId.takeIf { it != receipt.id } },
                    ) {
                        ReceiptRow(
                            receipt = receipt,
                            onClick = { onReceiptClick(receipt.id) },
                            onBarcodeClick = { onBarcodeClick(receipt) },
                        )
                    }
                }
                if (index == 0 && adAfterFirstReceipt) {
                    item(key = "ad") { AdBannerSlot(adUnitId = bannerAdUnitId, enabled = true) }
                }
            }
        }

        if (state.isLoading) {
            item {
                Column(Modifier.fillMaxWidth().padding(d.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                }
            }
        }

    }
}

/**
 * Three stat tiles across the top of Home: spend for the current month, receipts held, and
 * duplicates. Each tile carries its own tinted gradient with a circular icon badge.
 *
 * Equal weights with [IntrinsicSize.Min] so all three match the tallest, keeping the row
 * level when one figure wraps.
 */
@Composable
private fun StatTiles(
    state: HomeContract.State,
    onSpentClick: () -> Unit = {},
    onReceiptsClick: () -> Unit = {},
) {
    val d = ReTallyTheme.dimensions
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(d.md),
    ) {
        StatTile(
            label = str(S.HOME_STAT_SPENT),
            value = formatCurrency(state.monthToDateTotal),
            icon = Icons.Rounded.AttachMoney,
            palette = StatPalette(
                top = Color(0xFFC9EDD6),
                bottom = Color(0xFF8BD9AC),
                badge = Color(0xFF2F9E63),
            ),
            onClick = onSpentClick,
            modifier = Modifier.weight(1f),
            hint = str(S.BUDGET_TAP_HINT),
        )
        StatTile(
            label = str(S.HOME_STAT_RECEIPTS),
            value = state.monthReceiptCount.toString(),
            icon = Icons.Rounded.Receipt,
            palette = StatPalette(
                top = Color(0xFFC9D6EA),
                bottom = Color(0xFF9DB6D3),
                badge = Color(0xFF5B7BA6),
            ),
            onClick = onReceiptsClick,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = str(S.HOME_STAT_DUPLICATES),
            value = state.monthDuplicatesCount.toString(),
            icon = Icons.Rounded.Layers,
            palette = StatPalette(
                top = Color(0xFFF3DCB0),
                bottom = Color(0xFFE5BA6E),
                badge = Color(0xFFC98F2E),
            ),
            modifier = Modifier.weight(1f),
        )
    }
}

/** Tinted gradient + badge colors for one stat tile. */
private data class StatPalette(val top: Color, val bottom: Color, val badge: Color)

/**
 * This month's budget on Home: what it tracks ("Grocery only" / "All spending", no "Budget ·"
 * prefix) and "$412.50 of $600.00", then a bar coloured by
 * pace (green on track, amber heading over, red over) and what's left. Shown only when a budget
 * is set for the current month; tapping opens the Budget screen.
 */
@Composable
private fun BudgetProgressCard(state: HomeContract.State, onClick: () -> Unit) {
    val budget = state.budgetAmount ?: return
    val fraction = state.budgetFraction ?: return
    val d = ReTallyTheme.dimensions
    val c = ReTallyTheme.colors
    val barColor = when (state.budgetStatus) {
        BudgetPace.OVER -> c.danger
        BudgetPace.WARNING -> c.warning
        else -> c.primary
    }
    val spent = state.budgetSpent
    SurfaceCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(d.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // What the budget tracks ("Grocery" / "All spending") — no "Budget ·" prefix.
                Text(
                    str(if (state.budgetScope.allSpending) S.BUDGET_TRACK_ALL else S.HOME_BUDGET_SCOPE_GROCERY),
                    style = ReTallyTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = c.ink,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(d.sm))
                // Shrinks to fit so large amounts never wrap or clip.
                BasicText(
                    str(S.HOME_BUDGET_SPENT_OF, formatCurrency(spent), formatCurrency(budget)),
                    style = ReTallyTheme.typography.body.copy(color = c.ink, fontWeight = FontWeight.Bold, textAlign = TextAlign.End),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 17.sp, stepSize = 0.5.sp),
                    modifier = Modifier.weight(1.3f),
                )
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = c.muted,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.height(d.sm))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(barColor.copy(alpha = 0.18f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(barColor),
                )
            }
            Spacer(Modifier.height(d.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (spent <= budget) str(S.HOME_BUDGET_LEFT, formatCurrency(budget - spent))
                    else str(S.HOME_BUDGET_OVER, formatCurrency(spent - budget)),
                    style = ReTallyTheme.typography.caption,
                    color = if (spent > budget) c.danger else c.muted,
                    modifier = Modifier.weight(1f),
                )
                // Month-end at today's pace, in the bar's colour (green / amber / red).
                state.budgetProjected?.let { projected ->
                    Text(
                        str(S.HOME_BUDGET_PROJECTED, formatCurrency(projected)),
                        style = ReTallyTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = barColor,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    icon: ImageVector,
    palette: StatPalette,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    hint: String? = null,
) {
    val d = ReTallyTheme.dimensions
    val c = ReTallyTheme.colors
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(d.cardRadius))
            .background(Brush.verticalGradient(listOf(palette.top, palette.bottom)))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(d.md),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(palette.badge),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(d.sm))
        Text(
            label,
            style = ReTallyTheme.typography.caption,
            color = c.ink,
            maxLines = 2,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(d.xs))
        CappedTextScale {
            // Shrinks as the number grows ($84.10 → $12,480.55) so the full value always fits.
            BasicText(
                value,
                style = ReTallyTheme.typography.price.copy(color = c.ink, fontWeight = FontWeight.Bold, fontSize = 22.sp),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 22.sp, stepSize = 0.5.sp),
            )
        }
        // Tap hint (e.g. Spent tile → budget screen): one line, shrinking to fit the tile's
        // width (the three tiles get narrow on small phones), with no trailing chevron.
        if (hint != null) {
            Spacer(Modifier.height(d.xs))
            BasicText(
                hint,
                style = ReTallyTheme.typography.caption.copy(
                    color = c.ink.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                ),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = 10.sp, stepSize = 0.5.sp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** "Recent receipts" with the "See all" link through to the Receipts tab. */
@Composable
private fun RecentReceiptsHeader(onSeeAll: () -> Unit, modifier: Modifier = Modifier) {
    val c = ReTallyTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            str(S.HOME_RECENT_TITLE),
            style = ReTallyTheme.typography.sectionHeader,
            color = c.ink,
            modifier = Modifier.weight(1f),
        )
        Text(
            str(S.HOME_SEE_ALL),
            style = ReTallyTheme.typography.body,
            color = c.primaryDark,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSeeAll,
                )
                .padding(ReTallyTheme.dimensions.xs),
        )
    }
}

/** Hero stat: money caught, with receipts checked and duplicate count beneath. */
@Suppress("UnusedPrivateMember") // kept for the Receipts tab; Home shows StatTiles now
@Composable
private fun SummaryCard(state: HomeContract.State) {
    val d = ReTallyTheme.dimensions
    val colors = MaterialTheme.colorScheme
    val hasDuplicates = state.duplicateCount > 0
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(d.cardRadius))
            .background(
                Brush.linearGradient(
                    listOf(colors.primary, colors.primary.copy(alpha = 0.82f)),
                ),
            )
            .padding(d.xl),
    ) {
        Column {
            Text(
                if (hasDuplicates) str(S.HOME_SUMMARY_CAUGHT) else str(S.HOME_SUMMARY_ALL_CLEAR),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onPrimary.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(d.xs))
            CappedTextScale {
                Text(
                    if (hasDuplicates) formatCurrency(state.totalOvercharge) else formatCurrency(0.0),
                    style = ReTallyTheme.typography.heroAmount,
                    color = colors.onPrimary,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(d.md))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d.sm)) {
                Icon(
                    if (hasDuplicates) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    (if (state.receipts.size == 1) str(S.HOME_SUMMARY_RECEIPTS_ONE, 1) else str(S.HOME_SUMMARY_RECEIPTS_MANY, state.receipts.size)) +
                        " · " +
                        (if (state.duplicateCount == 1) str(S.HOME_SUMMARY_DUPLICATES_ONE, 1) else str(S.HOME_SUMMARY_DUPLICATES_MANY, state.duplicateCount)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onPrimary.copy(alpha = 0.9f),
                )
            }
        }
    }
}

/**
 * One nudge card: kind icon, bold title with — when the server sent one — the key figure
 * in a pill right after it ("$1.80 less", "+22%"), then the detail at full width. Tappable (with a chevron)
 * when the nudge points somewhere. With no nudges yet, a "still learning" placeholder holds
 * the slot.
 */
@Composable
private fun NudgeCard(
    nudge: Nudge?,
    readStore: NudgeReadStore,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = ReTallyTheme.dimensions
    val c = ReTallyTheme.colors
    val readIds by readStore.readIds.collectAsState()
    val isUnread = nudge != null && nudge.id !in readIds
    val tappable = nudge?.action != null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(d.cardRadius))
            .background(c.primaryTint)
            .then(if (tappable && nudge != null) Modifier.clickable { onClick(nudge.id) } else Modifier)
            .padding(d.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                nudgeIcon(nudge?.kind),
                contentDescription = null,
                tint = c.primaryDark,
                modifier = Modifier.size(32.dp),
            )
            // GIF-style attention dot on nudges the user hasn't seen yet.
            if (isUnread) {
                PulsingDot(Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp))
            }
        }
        Spacer(Modifier.width(d.md))
        Column(Modifier.weight(1f)) {
            if (nudge == null) {
                Text(str(S.HOME_AI_INSIGHT_LEARNING), style = ReTallyTheme.typography.body, color = c.ink)
            } else {
                // Title with the key figure pill at its end, so the body below gets the card's
                // full width.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        nudge.title,
                        style = ReTallyTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                        color = c.ink,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    nudge.highlight?.takeIf { it.isNotBlank() }?.let { figure ->
                        Spacer(Modifier.width(d.sm))
                        Text(
                            figure,
                            style = ReTallyTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                            color = c.onPrimary,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(c.primaryDark)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                // No line limits: title and body always show in full.
                Text(
                    nudge.body,
                    style = ReTallyTheme.typography.secondaryBody,
                    color = c.ink.copy(alpha = 0.8f),
                )
            }
        }
        if (tappable) {
            Spacer(Modifier.width(d.xs))
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = c.primaryDark,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Icon per nudge kind; unknown kinds from a newer server get the sparkle. */
private fun nudgeIcon(kind: String?): ImageVector = when (kind) {
    "cheaper_elsewhere" -> Icons.Rounded.AttachMoney
    "price_creep" -> Icons.AutoMirrored.Rounded.TrendingUp
    "running_low" -> Icons.Rounded.ShoppingCart
    "double_charge" -> Icons.Rounded.Layers
    "spending_pace" -> Icons.Rounded.Speed
    "price_drop" -> Icons.AutoMirrored.Rounded.TrendingDown
    else -> Icons.Rounded.AutoAwesome
}

/**
 * Nudge carousel: every firing nudge becomes a swipeable card with breadcrumb dots.
 * Swiping is infinite in both directions — past the last nudge wraps to the first.
 * With a single nudge (or none) this degrades to the plain [NudgeCard]: no pager, no dots.
 *
 * A nudge counts as "read" once it has been on screen a moment: fresh nudges pulse the
 * attention dot until then, already-seen ones never do.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NudgeCarousel(
    nudges: List<Nudge>,
    onNudgeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    readStore: NudgeReadStore = koinInject(),
) {
    val d = ReTallyTheme.dimensions
    LaunchedEffect(nudges) {
        if (nudges.isNotEmpty()) {
            delay(NUDGE_READ_DELAY_MS)
            readStore.markRead(nudges.map { it.id })
        }
    }
    if (nudges.size <= 1) {
        NudgeCard(nudges.firstOrNull(), readStore, onNudgeClick, modifier)
        return
    }
    val count = nudges.size
    // Infinite both ways: a huge virtual page count, started on a multiple of `count`,
    // so virtualPage % count always lands on a real nudge.
    val virtualPages = Int.MAX_VALUE
    val startPage = virtualPages / 2 - (virtualPages / 2 % count)
    val pagerState = rememberPagerState(initialPage = startPage) { virtualPages }
    Column(modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            NudgeCard(nudges[page % count], readStore, onNudgeClick)
        }
        Spacer(Modifier.height(d.sm))
        BreadcrumbDots(count = count, current = pagerState.currentPage % count)
    }
}

/** How long a nudge must be on screen before it counts as seen. */
private const val NUDGE_READ_DELAY_MS = 2500L

/**
 * GIF-style attention dot: an infinite scale-and-fade pulse marking a nudge the user
 * hasn't seen yet. Amber with a white ring so it pops against the green card.
 */
@Composable
private fun PulsingDot(modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "nudge-unread")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "dot-scale",
    )
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "dot-alpha",
    )
    Box(
        modifier
            .size(10.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clip(CircleShape)
            .background(Color(0xFFF59E0B))
            .border(2.dp, Color.White, CircleShape),
    )
}

/** Page-indicator dots under the nudge carousel; only shown when more than one nudge fires. */
@Composable
private fun BreadcrumbDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val c = ReTallyTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val active = i == current
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (active) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (active) c.primaryDark else c.ink.copy(alpha = 0.25f)),
            )
        }
    }
}

/**
 * Swipe left to reveal Delete — the same iOS-style reveal (same red surface and icon) the
 * receipt page uses on its line items, so the two lists feel alike. Tapping the row opens
 * the receipt, so there is no Edit action. The host confirms before removing. Pass
 * [open]/[onOpenChange] from the list so only one row is open at a time.
 */
@Composable
internal fun SwipeToDeleteRow(
    onDelete: () -> Unit,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val c = ReTallyTheme.colors
    SwipeRevealRow(
        actions = listOf(
            SwipeAction(
                icon = Icons.Outlined.Delete,
                label = str(S.ACTION_DELETE),
                tint = c.onPrimary,
                background = c.danger,
                onClick = onDelete,
            ),
        ),
        open = open,
        onOpenChange = onOpenChange,
        modifier = modifier,
        content = content,
    )
}

@Composable
internal fun ReceiptRow(receipt: Receipt, onClick: () -> Unit, onBarcodeClick: () -> Unit) {
    val d = ReTallyTheme.dimensions
    SurfaceCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = d.lg, vertical = d.md), verticalAlignment = Alignment.CenterVertically) {
            StoreIcon(receipt.storeName)
            Spacer(Modifier.width(d.md))
            Column(Modifier.weight(1f)) {
                Text(receipt.storeName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d.sm)) {
                    // The total is the printed total; a receipt whose total line was never
                    // found shows no amount rather than a guessed one.
                    val meta = buildString {
                        append(formatShortDate(receipt.purchaseDate))
                        receipt.total?.let { append(" · ${formatCurrency(it)}") }
                    }
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (receipt.duplicates.isNotEmpty()) {
                        val duplicateCount = receipt.duplicates.size
                        Pill(
                            text = if (duplicateCount == 1) "1 duplicate" else "$duplicateCount duplicates",
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
            Spacer(Modifier.width(d.sm))
            // Trailing slot: the barcode when the receipt has one (tap → full-screen for the
            // register scanner); otherwise nothing — the row itself is still tappable.
            if (receipt.barcode != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable(onClick = onBarcodeClick),
                    contentAlignment = Alignment.Center,
                ) {
                    BarcodeGlyph(tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/**
 * Sync modal, shown right after "Back up" on the sign-in prompt: a cloud icon that bobs and
 * pulses while the receipts upload, flipping to a cloud-tick with "N receipts synced" for a
 * moment when done. Not dismissable — the upload is short and the Home list changes under it.
 */
@Composable
private fun SyncingReceiptsDialog(syncingCount: Int, syncedCount: Int) {
    val c = ReTallyTheme.colors
    val d = ReTallyTheme.dimensions
    val done = syncedCount > 0
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(d.cardRadius))
                .background(c.surface)
                .padding(horizontal = d.cardPadding, vertical = d.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (done) {
                Icon(
                    Icons.Rounded.CloudDone,
                    contentDescription = null,
                    tint = c.primary,
                    modifier = Modifier.size(72.dp),
                )
            } else {
                // "Uploading" motion: a gentle bob plus a soft pulse, looping until done.
                val transition = rememberInfiniteTransition(label = "sync-cloud")
                val bob by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = -8f,
                    animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                    label = "sync-bob",
                )
                val pulse by transition.animateFloat(
                    initialValue = 0.55f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                    label = "sync-pulse",
                )
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = c.primary.copy(alpha = 0.25f),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(96.dp),
                    )
                    Icon(
                        Icons.Rounded.CloudUpload,
                        contentDescription = null,
                        tint = c.primary,
                        modifier = Modifier
                            .size(56.dp)
                            .graphicsLayer { translationY = bob; alpha = pulse },
                    )
                }
            }
            Spacer(Modifier.height(d.lg))
            Text(
                when {
                    !done -> str(S.HOME_SYNCING_TITLE)
                    syncedCount == 1 -> str(S.HOME_SYNCED_TITLE_ONE)
                    else -> str(S.HOME_SYNCED_TITLE, syncedCount)
                },
                style = ReTallyTheme.typography.title,
                color = c.ink,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(d.xs))
            Text(
                if (done) str(S.HOME_SYNCED_BODY) else str(S.HOME_SYNCING_BODY, syncingCount),
                style = ReTallyTheme.typography.secondaryBody,
                color = c.muted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/** Fresh install, signed in: the account's receipts are on their way down from the cloud. */
@Composable
private fun RestoringReceipts(contentPadding: PaddingValues) {
    val c = ReTallyTheme.colors
    val d = ReTallyTheme.dimensions
    Column(
        Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = d.screen),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.CircularProgressIndicator(color = c.primary)
        Spacer(Modifier.height(d.lg))
        Text(
            str(S.HOME_RESTORING_TITLE),
            style = ReTallyTheme.typography.title,
            color = c.ink,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(d.xs))
        Text(
            str(S.HOME_RESTORING_BODY),
            style = ReTallyTheme.typography.secondaryBody,
            color = c.muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
