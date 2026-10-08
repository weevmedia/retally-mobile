package com.retally.shared.ui.home

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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.retally.shared.ui.barcode.BarcodeModal
import com.retally.shared.ui.barcode.BarcodeGlyph
import com.retally.shared.ui.components.StoreIcon
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retally.shared.domain.ai.Nudge
import com.retally.shared.domain.ai.NudgeReadStore
import com.retally.shared.domain.receipt.model.Receipt
import com.retally.shared.presentation.home.HomeContract
import com.retally.shared.ui.components.AdBannerSlot
import com.retally.shared.ui.components.AdFormat
import com.retally.shared.ui.components.Pill
import com.retally.shared.ui.components.SurfaceCard
import com.retally.shared.ui.components.IconBadge
import com.retally.shared.ui.components.ReTallySnackbarHost
import com.retally.shared.ui.format.formatCurrency
import com.retally.shared.ui.format.formatMonthShort
import com.retally.shared.ui.format.formatShortDate
import com.retally.shared.ui.theme.CappedTextScale
import com.retally.shared.ui.theme.ReTallyDimensions
import com.retally.shared.ui.theme.ReTallyTheme
import kotlin.time.Clock
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/** User-facing copy for one-shot Home effects; null for effects the host handles itself. */
fun HomeContract.SideEffect.userMessage(): String? = when (this) {
    is HomeContract.SideEffect.ShowMessage -> message
    HomeContract.SideEffect.LaunchScanner,
    HomeContract.SideEffect.OpenDuplicates,
    HomeContract.SideEffect.OpenReceipts,
    HomeContract.SideEffect.OpenSignIn,
    is HomeContract.SideEffect.OpenScanReview,
    is HomeContract.SideEffect.OpenReceipt,
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
                    // Scanning moved to the tab bar's centre FAB; the bell is the notifications
                    // entry point (placeholder). Debug builds: long-press adds a sample receipt.
                    NotificationsAction(
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
        // The banner floats over the list at the bottom (above the tab bar); the list reserves
        // the same height underneath so the last receipt is never hidden behind it.
        // With receipts: a 320×50 strip floats over the list. Empty: the same 320×50 banner
        // sits inline under the call-to-action instead (see HomeContent).
        val showFloatingStrip = adsEnabled && state.receipts.isNotEmpty()
        val bannerReserve = if (showFloatingStrip) d.bannerHeight + d.md * 2 else 0.dp
        Box(Modifier.fillMaxSize()) {
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
                onHowItWorks = { dispatch(HomeContract.Action.HowItWorksClicked) },
                onSignIn = { dispatch(HomeContract.Action.SignInClicked) },
                onSeeAll = { dispatch(HomeContract.Action.SeeAllReceiptsClicked) },
                adsEnabled = adsEnabled,
                bannerAdUnitId = bannerAdUnitId,
            )
            FloatingAdBanner(
                adUnitId = bannerAdUnitId,
                enabled = showFloatingStrip,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = padding.calculateBottomPadding() + bottomContentInset + d.md),
            )
        }
    }

    barcodeReceipt?.let { receipt -> BarcodeModal(receipt = receipt, onDismiss = { barcodeReceipt = null }) }

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

/** Filled circular scan button in the top bar — the screen's single primary action. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotificationsAction(onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    val c = ReTallyTheme.colors
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Notifications,
            contentDescription = str(S.HOME_NOTIFICATIONS_A11Y),
            tint = c.ink,
            modifier = Modifier.size(24.dp),
        )
        // Unread dot (placeholder until notifications exist).
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(c.primary),
        )
    }
}

@Composable
internal fun HomeContent(
    state: HomeContract.State,
    contentPadding: PaddingValues,
    onScan: () -> Unit,
    onReceiptClick: (String) -> Unit,
    onDelete: (String) -> Unit,
    onBarcodeClick: (Receipt) -> Unit = {},
    onDuplicatesClick: () -> Unit = {},
    onHowItWorks: () -> Unit = {},
    onSignIn: () -> Unit = {},
    onSeeAll: () -> Unit = {},
    adsEnabled: Boolean = false,
    bannerAdUnitId: String = "",
) {
    val d = ReTallyTheme.dimensions

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
            item { StatTiles(state) }
            item { NudgeCarousel(state.allNudges) }
            item { SpendingBreakdown(state.spendingByCategory) }
            item {
                RecentReceiptsHeader(
                    onSeeAll = onSeeAll,
                    modifier = Modifier.padding(top = d.sm),
                )
            }
            items(
                state.recentReceipts.take(HomeContract.State.RECENT_RECEIPT_COUNT),
                key = { it.id },
            ) { receipt ->
                SwipeToDeleteRow(onDelete = { onDelete(receipt.id) }) {
                    ReceiptRow(
                        receipt = receipt,
                        onClick = { onReceiptClick(receipt.id) },
                        onBarcodeClick = { onBarcodeClick(receipt) },
                    )
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
private fun StatTiles(state: HomeContract.State) {
    val d = ReTallyTheme.dimensions
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(d.md),
    ) {
        StatTile(
            label = str(S.HOME_STAT_SPENT, formatMonthShort(Clock.System.now())),
            value = formatCurrency(state.monthToDateTotal),
            icon = Icons.Rounded.AttachMoney,
            palette = StatPalette(
                top = Color(0xFFC9EDD6),
                bottom = Color(0xFF8BD9AC),
                badge = Color(0xFF2F9E63),
            ),
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = str(S.HOME_STAT_RECEIPTS),
            value = state.receipts.size.toString(),
            icon = Icons.Rounded.Receipt,
            palette = StatPalette(
                top = Color(0xFFC9D6EA),
                bottom = Color(0xFF9DB6D3),
                badge = Color(0xFF5B7BA6),
            ),
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = str(S.HOME_STAT_DUPLICATES),
            value = state.duplicatesFoundCount.toString(),
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

@Composable
private fun StatTile(
    label: String,
    value: String,
    icon: ImageVector,
    palette: StatPalette,
    modifier: Modifier = Modifier,
) {
    val d = ReTallyTheme.dimensions
    val c = ReTallyTheme.colors
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(d.cardRadius))
            .background(Brush.verticalGradient(listOf(palette.top, palette.bottom)))
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
        )
        Spacer(Modifier.height(d.xs))
        CappedTextScale {
            Text(value, style = ReTallyTheme.typography.price, color = c.ink, maxLines = 1)
        }
    }
}

/**
 * This month's item spend grouped by the server-pushed categories: one labeled
 * row per category with a proportional bar. Hidden when nothing is categorized
 * yet (or there are no receipts).
 */
@Composable
private fun SpendingBreakdown(spending: List<HomeContract.CategorySpend>) {
    if (spending.isEmpty()) return
    val d = ReTallyTheme.dimensions
    val c = ReTallyTheme.colors
    val max = spending.maxOf { it.total }.coerceAtLeast(1.0)
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(d.cardPadding),
            verticalArrangement = Arrangement.spacedBy(d.sm),
        ) {
            Text(str(S.HOME_SPENDING_TITLE), style = ReTallyTheme.typography.title, color = c.ink)
            spending.forEach { row ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            row.category.name,
                            style = ReTallyTheme.typography.body,
                            color = c.ink,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            formatCurrency(row.total),
                            style = ReTallyTheme.typography.amount,
                            color = c.ink,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(c.muted.copy(alpha = 0.18f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((row.total / max).toFloat().coerceIn(0f, 1f))
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(c.primary),
                        )
                    }
                }
            }
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
 * AI nudge card: one nudge from the on-device engine, each type with its own icon.
 * Until history is thick enough for any nudge, a "still learning" placeholder holds the slot.
 */
@Composable
private fun NudgeCard(nudge: Nudge?, readStore: NudgeReadStore, modifier: Modifier = Modifier) {
    val d = ReTallyTheme.dimensions
    val c = ReTallyTheme.colors
    val readIds by readStore.readIds.collectAsState()
    val isUnread = nudge != null && nudge.id !in readIds
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(d.cardRadius))
            .background(c.primaryTint)
            .padding(d.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                when (nudge) {
                    is Nudge.PriceSpike -> Icons.Rounded.AttachMoney
                    is Nudge.GroceryRunOverdue -> Icons.Rounded.Receipt
                    is Nudge.DoubleBought -> Icons.Rounded.Layers
                    is Nudge.SavingsRecap -> Icons.Rounded.CheckCircle
                    is Nudge.RunningLow, null -> Icons.Rounded.AutoAwesome
                },
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
        Text(
            if (nudge != null) {
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(nudgeTitle(nudge))
                    }
                    append("\n")
                    append(nudgeBody(nudge))
                }
            } else {
                AnnotatedString(str(S.HOME_AI_INSIGHT_LEARNING))
            },
            style = ReTallyTheme.typography.body,
            color = c.ink,
        )
    }
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
        NudgeCard(nudges.firstOrNull(), readStore, modifier)
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
            NudgeCard(nudges[page % count], readStore)
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

@Composable
private fun nudgeTitle(nudge: Nudge): String = when (nudge) {
    is Nudge.RunningLow -> str(S.HOME_NUDGE_RUNNING_LOW_TITLE)
    is Nudge.PriceSpike -> str(S.HOME_NUDGE_PRICE_SPIKE_TITLE)
    is Nudge.GroceryRunOverdue -> str(S.HOME_NUDGE_GROCERY_OVERDUE_TITLE)
    is Nudge.DoubleBought -> str(S.HOME_NUDGE_DOUBLE_BOUGHT_TITLE)
    is Nudge.SavingsRecap -> str(S.HOME_NUDGE_SAVINGS_TITLE)
}

@Composable
private fun nudgeBody(nudge: Nudge): String = when (nudge) {
    is Nudge.RunningLow ->
        str(S.HOME_NUDGE_RUNNING_LOW_BODY, nudge.itemName, nudge.intervalDays)
    is Nudge.PriceSpike ->
        str(S.HOME_NUDGE_PRICE_SPIKE_BODY, nudge.itemName, formatCurrency(nudge.lastPrice), formatCurrency(nudge.usualPrice))
    is Nudge.GroceryRunOverdue ->
        str(S.HOME_NUDGE_GROCERY_OVERDUE_BODY, nudge.daysSinceLastTrip, nudge.usualIntervalDays)
    is Nudge.DoubleBought ->
        str(S.HOME_NUDGE_DOUBLE_BOUGHT_BODY, nudge.itemName, nudge.receiptCount)
    is Nudge.SavingsRecap ->
        str(S.HOME_NUDGE_SAVINGS_BODY, formatCurrency(nudge.monthToDate), formatCurrency(nudge.usualMonthly))
}

/** Swipe left to reveal a red delete surface; the host confirms before removing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeToDeleteRow(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val d = ReTallyTheme.dimensions
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onDelete()
            false // never remove the row here; the store does after confirmation
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(d.cardRadius))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = d.xl),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = str(S.ACTION_DELETE), tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
        content = { content() },
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
                        append(" · ${receipt.items.size} item${if (receipt.items.size == 1) "" else "s"}")
                    }
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (receipt.duplicates.isNotEmpty()) {
                        Pill(
                            text = "+${formatCurrency(receipt.overchargeAmount)} · ${receipt.duplicates.size} dup",
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
            Spacer(Modifier.width(d.sm))
            // Trailing slot: the barcode when the receipt has one (tap → full-screen for the
            // register scanner), otherwise the plain chevron. The total sits in the meta line.
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
            } else {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

