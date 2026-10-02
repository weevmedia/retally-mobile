package com.retally.shared.ui.duplicates

import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.str
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.retally.shared.domain.receipt.CrossReceiptDuplicate
import com.retally.shared.domain.receipt.model.Receipt
import com.retally.shared.presentation.duplicates.DuplicatesContract
import com.retally.shared.ui.components.DuplicateBadge
import com.retally.shared.ui.components.EmptyState
import com.retally.shared.ui.components.IconBadge
import com.retally.shared.ui.components.ListDivider
import com.retally.shared.ui.components.SecondaryButton
import com.retally.shared.ui.components.SectionHeader
import com.retally.shared.ui.components.SurfaceCard
import com.retally.shared.ui.format.formatCurrency
import com.retally.shared.ui.format.formatShortDate
import com.retally.shared.ui.theme.ReTallyDimensions
import com.retally.shared.ui.theme.ReTallyTheme

/**
 * "Duplicates found" — the filtered list opened from the Home stat card. Two sections:
 * items repeated across receipts (milk bought Sep 12 and Sep 15) with Keep both / Remove one,
 * and receipts that still carry an unresolved in-receipt double charge (tap to open them).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(
    state: DuplicatesContract.State,
    dispatch: (DuplicatesContract.Action) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val d = ReTallyTheme.dimensions
    val c = ReTallyTheme.colors
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(str(S.DUPLICATES_TITLE), style = ReTallyTheme.typography.display, color = c.ink) },
                navigationIcon = {
                    IconButton(onClick = { dispatch(DuplicatesContract.Action.CloseClicked) }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = str(S.ACTION_BACK), tint = c.ink)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = c.background,
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.primary)
            }
            state.isEmpty -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Rounded.CheckCircle,
                    title = str(S.DUPLICATES_EMPTY_TITLE),
                    body = str(S.DUPLICATES_EMPTY_BODY),
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = d.screen, end = d.screen,
                    top = padding.calculateTopPadding() + d.sm,
                    bottom = padding.calculateBottomPadding() + d.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(d.md),
            ) {
                if (state.crossReceipt.isNotEmpty()) {
                    item { SectionHeader(str(S.DUPLICATES_ACROSS_HEADER, state.crossReceipt.size)) }
                    items(state.crossReceipt, key = { it.key }) { dup ->
                        CrossReceiptCard(
                            dup = dup,
                            onKeepBoth = { dispatch(DuplicatesContract.Action.KeepBoth(dup.key)) },
                            onRemoveOne = { dispatch(DuplicatesContract.Action.RemoveOneRequested(dup.key)) },
                            onReceiptClick = { dispatch(DuplicatesContract.Action.ReceiptClicked(it)) },
                        )
                    }
                }
                if (state.inReceipt.isNotEmpty()) {
                    item { SectionHeader(str(S.DUPLICATES_ON_RECEIPT_HEADER, state.inReceipt.sumOf { it.duplicates.size }), modifier = Modifier.padding(top = d.sm)) }
                    items(state.inReceipt, key = { it.id }) { receipt ->
                        InReceiptRow(receipt = receipt, onClick = { dispatch(DuplicatesContract.Action.ReceiptClicked(receipt.id)) })
                    }
                }
            }
        }
    }

    state.pendingRemoval?.let { dup ->
        AlertDialog(
            onDismissRequest = { dispatch(DuplicatesContract.Action.RemoveOneCancelled) },
            shape = RoundedCornerShape(d.cardRadius),
            containerColor = c.surface,
            title = { Text(str(S.RECEIPT_REMOVE_ONE_TITLE, dup.name), style = ReTallyTheme.typography.title, color = c.ink) },
            text = {
                Text(
                    str(S.DUPLICATES_REMOVE_ONE_BODY, formatShortDate(dup.latest.purchaseDate), dup.latest.storeName, formatCurrency(dup.price)),
                    style = ReTallyTheme.typography.body,
                    color = c.muted,
                )
            },
            confirmButton = {
                TextButton(onClick = { dispatch(DuplicatesContract.Action.RemoveOneConfirmed) }) {
                    Text(str(S.ACTION_REMOVE), style = ReTallyTheme.typography.button, color = c.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { dispatch(DuplicatesContract.Action.RemoveOneCancelled) }) {
                    Text(str(S.ACTION_CANCEL), style = ReTallyTheme.typography.button, color = c.ink)
                }
            },
        )
    }
}

/** One repeated item with every purchase listed (oldest first) and the two resolutions. */
@Composable
private fun CrossReceiptCard(
    dup: CrossReceiptDuplicate,
    onKeepBoth: () -> Unit,
    onRemoveOne: () -> Unit,
    onReceiptClick: (String) -> Unit,
) {
    val d = ReTallyTheme.dimensions
    val c = ReTallyTheme.colors
    SurfaceCard(modifier = Modifier.fillMaxWidth(), containerColor = c.warningTint) {
        Column(Modifier.padding(d.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon = Icons.Rounded.Repeat, tint = c.warning, background = c.warning.copy(alpha = 0.18f))
                Spacer(Modifier.width(d.md))
                Column(Modifier.weight(1f)) {
                    Text(dup.name, style = ReTallyTheme.typography.body, color = c.ink)
                    Text(
                        str(S.DUPLICATES_BOUGHT_TIMES, dup.count, daysBetween(dup)),
                        style = ReTallyTheme.typography.caption,
                        color = c.warningInk,
                    )
                }
                Spacer(Modifier.width(d.sm))
                Text(formatCurrency(dup.price), style = ReTallyTheme.typography.amount, color = c.warningInk)
            }
            Spacer(Modifier.height(d.sm))
            DuplicateBadge()
            Spacer(Modifier.height(d.sm))
            ListDivider()
            dup.occurrences.forEach { occ ->
                TextButton(
                    onClick = { onReceiptClick(occ.receiptId) },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = d.xs),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Receipt, contentDescription = null, tint = c.muted, modifier = Modifier.size(ReTallyDimensions.iconSmall))
                    Spacer(Modifier.width(d.sm))
                    Text(
                        "${formatShortDate(occ.purchaseDate)} · ${occ.storeName}",
                        style = ReTallyTheme.typography.caption,
                        color = c.ink,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(d.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(d.sm)) {
                SecondaryButton(
                    text = str(S.ACTION_KEEP_BOTH),
                    onClick = onKeepBoth,
                    modifier = Modifier.weight(1f).height(ReTallyDimensions.buttonHeightCompact),
                )
                SecondaryButton(
                    text = str(S.ACTION_REMOVE_ONE),
                    onClick = onRemoveOne,
                    modifier = Modifier.weight(1f).height(ReTallyDimensions.buttonHeightCompact),
                    contentColor = c.danger,
                )
            }
        }
    }
}

/** A receipt with unresolved in-receipt double charges; resolve them on the receipt itself. */
@Composable
private fun InReceiptRow(receipt: Receipt, onClick: () -> Unit) {
    val d = ReTallyTheme.dimensions
    val c = ReTallyTheme.colors
    SurfaceCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(d.cardPadding), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = Icons.Rounded.Warning, tint = c.warning, background = c.warningTint)
            Spacer(Modifier.width(d.md))
            Column(Modifier.weight(1f)) {
                Text(receipt.storeName, style = ReTallyTheme.typography.rowTitle, color = c.ink)
                Text(
                    "${formatShortDate(receipt.purchaseDate)} · ${if (receipt.duplicates.size == 1) str(S.DUPLICATES_DOUBLE_CHARGES_ONE) else str(S.DUPLICATES_DOUBLE_CHARGES_MANY, receipt.duplicates.size)}",
                    style = ReTallyTheme.typography.caption,
                    color = c.muted,
                )
            }
            Spacer(Modifier.width(d.sm))
            Text("+${formatCurrency(receipt.overchargeAmount)}", style = ReTallyTheme.typography.amount, color = c.warning)
        }
    }
}

private fun daysBetween(dup: CrossReceiptDuplicate): Int =
    (dup.latest.purchaseDate - dup.earliest.purchaseDate).inWholeDays.toInt().coerceAtLeast(1)
