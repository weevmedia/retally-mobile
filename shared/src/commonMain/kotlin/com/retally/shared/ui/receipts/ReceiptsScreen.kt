package com.retally.shared.ui.receipts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.str
import com.retally.shared.presentation.receipts.ReceiptsContract
import com.retally.shared.ui.components.AdBannerSlot
import com.retally.shared.ui.components.AdFormat
import com.retally.shared.ui.components.EmptyState
import com.retally.shared.ui.components.SectionHeader
import com.retally.shared.ui.components.ReTallySnackbarHost
import com.retally.shared.ui.format.formatMonthYearShort
import com.retally.shared.ui.format.formatShortDate
import com.retally.shared.ui.home.ReceiptRow
import com.retally.shared.ui.home.SwipeToDeleteRow
import com.retally.shared.ui.theme.ReTallyTheme
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Receipts tab: every receipt, newest first, behind a month filter and paged in as the list
 * scrolls. Rows are Home's [ReceiptRow] inside [SwipeToDeleteRow] so the two lists stay
 * identical — Home just caps itself at the newest two.
 *
 * Stateless: [state] renders, intents go out through [dispatch]; the host owns navigation and
 * the snackbar.
 *
 * @param bottomContentInset chrome the host draws over the screen that Compose cannot see —
 *   on iOS the floating tab bar (see `HomeScreen`). Android injects it into window insets.
 * @param adsEnabled live ad eligibility from the shell (remote config + consent).
 * @param bannerAdUnitId AdMob unit id for the in-feed square ads shown after every 5th receipt.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReceiptsScreen(
    state: ReceiptsContract.State,
    dispatch: (ReceiptsContract.Action) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    bottomContentInset: Dp = 0.dp,
    adsEnabled: Boolean = false,
    bannerAdUnitId: String = "",
) {
    val d = ReTallyTheme.dimensions
    val c = ReTallyTheme.colors

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(str(S.TAB_RECEIPTS), style = ReTallyTheme.typography.display, color = c.ink) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background),
            )
        },
        snackbarHost = { ReTallySnackbarHost(snackbarHostState) },
        containerColor = c.background,
    ) { padding ->
        val bottomInset = padding.calculateBottomPadding() + bottomContentInset
        // 5+ receipts: a 300×250 square ad inline after every 5th receipt. No ads while loading.
        val showInFeedAd = adsEnabled && !state.isLoading && state.visible.size >= IN_FEED_AD_MIN_RECEIPTS

        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            if (state.months.size > 1) {
                MonthFilterRow(
                    months = state.months,
                    selected = state.selectedMonth,
                    onSelect = { dispatch(ReceiptsContract.Action.MonthSelected(it)) },
                )
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when {
                    state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                    state.isEmpty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                            title = str(S.RECEIPTS_EMPTY_TITLE),
                            body = str(S.RECEIPTS_EMPTY_BODY),
                        )
                    }

                    state.isFilteredEmpty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                            title = str(S.RECEIPTS_FILTER_EMPTY_TITLE),
                            body = str(S.RECEIPTS_FILTER_EMPTY_BODY),
                        )
                    }

                    else -> ReceiptsList(
                        state = state,
                        dispatch = dispatch,
                        bottomInset = bottomInset,
                        showAd = showInFeedAd,
                        adUnitId = bannerAdUnitId,
                    )
                }
            }
        }
    }

    state.pendingDelete?.let { receipt ->
        AlertDialog(
            onDismissRequest = { dispatch(ReceiptsContract.Action.DeleteCancelled) },
            title = { Text(str(S.HOME_DELETE_TITLE)) },
            text = { Text(str(S.HOME_DELETE_BODY, receipt.storeName, formatShortDate(receipt.purchaseDate))) },
            confirmButton = {
                TextButton(onClick = { dispatch(ReceiptsContract.Action.DeleteConfirmed) }) {
                    Text(str(S.ACTION_DELETE), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { dispatch(ReceiptsContract.Action.DeleteCancelled) }) { Text(str(S.ACTION_CANCEL)) }
            },
        )
    }
}

/**
 * The scrolling receipt list. With 5+ receipts a square ad is inserted inline after every
 * 5th receipt and scrolls with the list like any other row.
 */
@Composable
private fun ReceiptsList(
    state: ReceiptsContract.State,
    dispatch: (ReceiptsContract.Action) -> Unit,
    bottomInset: Dp,
    showAd: Boolean,
    adUnitId: String,
) {
    val d = ReTallyTheme.dimensions
    val listState = rememberLazyListState()

    // Infinite scroll: ask for another page once the last rendered row is close to the end.
    val atEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= listState.layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { atEnd }
            .distinctUntilChanged()
            .collect { if (it) dispatch(ReceiptsContract.Action.LoadMore) }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = d.screen,
            end = d.screen,
            top = d.sm,
            bottom = bottomInset + d.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(d.md),
    ) {
            // Month headers only when the list spans more than one month; with a
            // filter on, every row is the same month and the chip already says which.
            val grouped = state.visible.groupBy { formatMonthYearShort(it.purchaseDate) }
            var receiptIndex = 0
            grouped.forEach { (month, receipts) ->
                if (state.selectedMonth == null) {
                    item(key = "month-$month") {
                        SectionHeader(month, modifier = Modifier.padding(top = d.sm))
                    }
                }
                receipts.forEach { receipt ->
                    item(key = receipt.id) {
                        SwipeToDeleteRow(onDelete = { dispatch(ReceiptsContract.Action.DeleteRequested(receipt.id)) }) {
                            ReceiptRow(
                                receipt = receipt,
                                onClick = { dispatch(ReceiptsContract.Action.ReceiptClicked(receipt.id)) },
                                onBarcodeClick = { dispatch(ReceiptsContract.Action.ReceiptClicked(receipt.id)) },
                            )
                        }
                    }
                    receiptIndex++
                    if (showAd && receiptIndex % IN_FEED_AD_EVERY == 0) {
                        item(key = "in-feed-ad-$receiptIndex") {
                            InFeedSquareAd(adUnitId = adUnitId)
                        }
                    }
                }
            }

            if (state.canLoadMore) {
                item(key = "loading-more") {
                    Box(Modifier.fillMaxWidth().padding(d.md), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
}

/**
 * In-feed 300×250 square ad with an "Advertisement" caption, shown inline after every 5th
 * receipt. It scrolls with the list like any other row.
 */
@Composable
private fun InFeedSquareAd(adUnitId: String, modifier: Modifier = Modifier) {
    val d = ReTallyTheme.dimensions
    Surface(
        modifier = modifier.fillMaxWidth(),
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
                format = AdFormat.MEDIUM_RECTANGLE,
                modifier = Modifier.padding(bottom = d.sm),
            )
        }
    }
}

/** Horizontally scrolling month chips: All, then every month that has receipts, newest first. */
@Composable
private fun MonthFilterRow(
    months: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    val d = ReTallyTheme.dimensions
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = d.screen, vertical = d.sm),
        horizontalArrangement = Arrangement.spacedBy(d.sm),
    ) {
        MonthChip(label = str(S.RECEIPTS_FILTER_ALL), selected = selected == null, onClick = { onSelect(null) })
        months.forEach { month ->
            MonthChip(label = month, selected = selected == month, onClick = { onSelect(month) })
        }
    }
}

@Composable
private fun MonthChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val d = ReTallyTheme.dimensions
    val c = ReTallyTheme.colors
    Text(
        text = label,
        style = ReTallyTheme.typography.body.copy(
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        ),
        color = if (selected) c.onPrimary else c.ink,
        modifier = Modifier
            .clip(RoundedCornerShape(d.controlRadius))
            .background(if (selected) c.primary else c.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = d.md, vertical = d.sm),
        maxLines = 1,
    )
}

/** Rows from the end at which the next page is requested. */
private const val LOAD_MORE_THRESHOLD = 3

/**
 * In-feed square ads repeat after every this many receipts, starting once the visible
 * list holds at least [IN_FEED_AD_MIN_RECEIPTS].
 */
private const val IN_FEED_AD_MIN_RECEIPTS = 5
private const val IN_FEED_AD_EVERY = 5
