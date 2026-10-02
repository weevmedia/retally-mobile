package com.retally.shared.ui.scan

import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.str
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import com.retally.shared.ui.barcode.BarcodeModal
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.retally.shared.domain.receipt.model.Receipt
import com.retally.shared.domain.receipt.model.ReceiptItem
import com.retally.shared.presentation.scan.ScanReviewContract
import com.retally.shared.ui.barcode.BarcodeImage
import com.retally.shared.ui.image.ReceiptImageViewer
import com.retally.shared.ui.image.ReceiptThumbnail
import com.retally.shared.ui.maps.mapsQuery
import com.retally.shared.ui.maps.mapsSearchUrl
import com.retally.shared.ui.components.EmptyState
import com.retally.shared.ui.components.SectionHeader
import com.retally.shared.ui.components.IconBadge
import androidx.compose.foundation.lazy.items
import com.retally.shared.ui.components.SurfaceCard
import com.retally.shared.ui.components.DuplicateBadge
import com.retally.shared.ui.components.ListDivider
import com.retally.shared.ui.components.PrimaryButton
import com.retally.shared.ui.components.SecondaryButton
import com.retally.shared.ui.components.ReTallySnackbarHost
import com.retally.shared.ui.theme.ReTallyDimensions
import com.retally.shared.ui.format.formatCurrency
import com.retally.shared.ui.format.formatDateTime
import com.retally.shared.ui.theme.ReTallyTheme

/**
 * Receipt view, shown right after a scan (already saved) and when a receipt is opened from
 * Home. Reads top to bottom like the paper receipt: store + address, the double-charge
 * verdict, every line item, totals with tax, payment, and the barcode re-drawn at the bottom.
 * Tapping a line item toggles its double-charge flag (corrections re-save automatically).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanReviewScreen(
    state: ScanReviewContract.State,
    dispatch: (ScanReviewContract.Action) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(str(S.RECEIPT_TITLE), style = ReTallyTheme.typography.display) },
                navigationIcon = {
                    IconButton(onClick = { dispatch(ScanReviewContract.Action.CloseClicked) }) {
                        Icon(Icons.Rounded.Close, contentDescription = str(S.ACTION_CLOSE))
                    }
                },
                actions = {
                    IconButton(onClick = { dispatch(ScanReviewContract.Action.FavoriteStoreToggled) }) {
                        if (state.isFavoriteStore) {
                            Icon(
                                Icons.Rounded.Favorite,
                                contentDescription = str(S.RECEIPT_UNFAVORITE_A11Y),
                                tint = Color(0xFFEF4444),
                            )
                        } else {
                            Icon(
                                Icons.Rounded.FavoriteBorder,
                                contentDescription = str(S.RECEIPT_FAVORITE_A11Y),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    SaveStatus(state)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { ReTallySnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val receipt = state.receipt
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            receipt == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = str(S.RECEIPT_NO_ITEMS_TITLE),
                    body = str(S.RECEIPT_NO_ITEMS_BODY),
                    action = {
                        PrimaryButton(text = str(S.RECEIPT_BACK_TO_RECEIPTS), onClick = { dispatch(ScanReviewContract.Action.CloseClicked) })
                    },
                )
            }
            else -> ReceiptBody(
                receipt = receipt,
                contentPadding = padding,
                onToggleDuplicate = { dispatch(ScanReviewContract.Action.ItemDuplicateToggled(it)) },
                onKeepBoth = { dispatch(ScanReviewContract.Action.KeepBoth(it)) },
                onRemoveOne = { dispatch(ScanReviewContract.Action.RemoveOneRequested(it)) },
            )
        }
    }
    state.pendingRemoval?.let { item ->
        RemoveOneDialog(
            item = item,
            onConfirm = { dispatch(ScanReviewContract.Action.RemoveOneConfirmed) },
            onDismiss = { dispatch(ScanReviewContract.Action.RemoveOneCancelled) },
        )
    }
}

/** "Remove one" always confirms first — the app never deletes a line on its own. */
@Composable
private fun RemoveOneDialog(item: ReceiptItem, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = ReTallyTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(ReTallyTheme.dimensions.cardRadius),
        containerColor = c.surface,
        title = { Text(str(S.RECEIPT_REMOVE_ONE_TITLE, item.name), style = ReTallyTheme.typography.title, color = c.ink) },
        text = {
            Text(
                str(S.RECEIPT_REMOVE_ONE_BODY, formatCurrency(item.price)),
                style = ReTallyTheme.typography.body,
                color = c.muted,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(str(S.ACTION_REMOVE), style = ReTallyTheme.typography.button, color = c.danger) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(str(S.ACTION_CANCEL), style = ReTallyTheme.typography.button, color = c.ink) }
        },
    )
}

/** "Saving…" spinner → "Saved" tick in the top bar; quiet after a moment. */
@Composable
private fun SaveStatus(state: ScanReviewContract.State) {
    val d = ReTallyTheme.dimensions
    when {
        state.isSaving -> Row(Modifier.padding(end = d.md), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(d.xs))
            Text(str(S.RECEIPT_SAVING), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.savedAt != null -> Row(Modifier.padding(end = d.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(d.xs))
            Text(str(S.RECEIPT_SAVED), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ReceiptBody(
    receipt: Receipt,
    contentPadding: PaddingValues,
    onToggleDuplicate: (String) -> Unit,
    onKeepBoth: (String) -> Unit,
    onRemoveOne: (String) -> Unit,
) {
    val d = ReTallyTheme.dimensions
    var showBarcode by remember { mutableStateOf(false) }
    var showPhoto by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = d.screen, end = d.screen,
            top = contentPadding.calculateTopPadding() + d.sm,
            bottom = contentPadding.calculateBottomPadding() + d.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(d.md),
    ) {
        item { StoreHeader(receipt, onPhotoClick = { showPhoto = true }) }
        item { DoubleChargeVerdict(receipt) }

        item { SectionHeader(str(S.RECEIPT_ITEMS_HEADER, receipt.items.size), modifier = Modifier.padding(top = d.sm)) }
        items(receipt.items, key = { it.id }) { item ->
            LineItemCard(
                item = item,
                onClick = { onToggleDuplicate(item.id) },
                onKeepBoth = { onKeepBoth(item.id) },
                onRemoveOne = { onRemoveOne(item.id) },
            )
        }

        // The totals card only exists when the parser found something to print: subtotal,
        // tax, total or payment. A receipt with none of those shows no card at all.
        if (receipt.subtotal != null || receipt.tax != null || receipt.receiptTotal != null || receipt.paymentInfo != null) {
            item { TotalsCard(receipt) }
        }

        receipt.barcode?.let { code ->
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(d.sm))
                    BarcodeImage(
                        value = code,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(d.cardRadius))
                            .clickable { showBarcode = true },
                    )
                    Spacer(Modifier.height(d.xs))
                    Text(
                        str(S.RECEIPT_BARCODE_HINT),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Breathing room so the last content scrolls fully into view above the nav bar.
        item { Spacer(Modifier.height(d.xxl)) }
    }

    if (showBarcode) {
        BarcodeModal(receipt = receipt, onDismiss = { showBarcode = false })
    }
    if (showPhoto) {
        receipt.imagePath?.let { ReceiptImageViewer(imagePath = it, onDismiss = { showPhoto = false }) }
    }
}

/**
 * Store name and address on the left, the scanned receipt photo pinned to the extreme
 * right beside the date/time row. The map pin sits left of the first address line and
 * opens the address in Maps; then date/time.
 * Payment is shown with the totals, not here.
 */
@Composable
private fun StoreHeader(receipt: Receipt, onPhotoClick: () -> Unit) {
    val d = ReTallyTheme.dimensions
    val colors = MaterialTheme.colorScheme
    val muted = colors.onSurfaceVariant
    val uriHandler = LocalUriHandler.current
    val openMaps: () -> Unit = {
        runCatching { uriHandler.openUri(mapsSearchUrl(mapsQuery(receipt.storeName, receipt.storeAddress))) }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = d.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                receipt.storeName,
                style = ReTallyTheme.typography.storeName,
                color = colors.onBackground,
            )
            receipt.storeAddress?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }?.takeIf { it.isNotEmpty() }?.let { lines ->
                Spacer(Modifier.height(d.xs))
                Row(
                    modifier = Modifier.clickable(onClick = openMaps),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Rounded.Place,
                        contentDescription = str(S.RECEIPT_OPEN_IN_MAPS_A11Y),
                        tint = colors.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(d.xs))
                    Column {
                        lines.forEach { line ->
                            Text(line, style = ReTallyTheme.typography.secondaryBody, color = muted)
                        }
                    }
                }
            }
            Spacer(Modifier.height(d.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = muted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(d.xs))
                Text(formatDateTime(receipt.purchaseDate), style = ReTallyTheme.typography.secondaryBody, color = muted)
            }
        }
        receipt.imagePath?.let { path ->
            Spacer(Modifier.width(d.md))
            ReceiptThumbnail(
                imagePath = path,
                onClick = onPhotoClick,
                modifier = Modifier.align(Alignment.Bottom),
            )
        }
    }
}

/** The reason the app exists: either the duplicated lines with the amount, or an all-clear. */
@Composable
private fun DoubleChargeVerdict(receipt: Receipt) {
    val d = ReTallyTheme.dimensions
    val colors = MaterialTheme.colorScheme
    val duplicates = receipt.duplicates
    val c = ReTallyTheme.colors
    val container = if (duplicates.isEmpty()) c.primaryTint else c.warningTint
    val content = if (duplicates.isEmpty()) c.primaryDark else c.warningInk

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(d.cardRadius))
            .background(container)
            .padding(d.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (duplicates.isEmpty()) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                contentDescription = null,
                tint = content,
            )
            Spacer(Modifier.width(d.sm))
            Text(
                text = when {
                    duplicates.isEmpty() -> str(S.RECEIPT_NO_DOUBLE_CHARGES)
                    duplicates.size == 1 -> str(S.RECEIPT_DOUBLE_CHARGE_ONE, formatCurrency(receipt.overchargeAmount))
                    else -> str(S.RECEIPT_DOUBLE_CHARGE_MANY, duplicates.size, formatCurrency(receipt.overchargeAmount))
                },
                style = ReTallyTheme.typography.title,
                color = content,
            )
        }
        if (duplicates.isEmpty()) {
            Spacer(Modifier.height(d.xs))
            Text(
                str(S.RECEIPT_EVERY_LINE_ONCE),
                style = MaterialTheme.typography.bodyMedium,
                color = content.copy(alpha = 0.85f),
            )
        } else {
            Spacer(Modifier.height(d.sm))
            duplicates.forEach { item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(item.name, style = MaterialTheme.typography.bodyMedium, color = content, modifier = Modifier.weight(1f))
                    Text(formatCurrency(item.price), style = ReTallyTheme.typography.amount, color = content)
                }
            }
            Spacer(Modifier.height(d.xs))
            Text(
                str(S.RECEIPT_SHOW_CUSTOMER_SERVICE, formatCurrency(receipt.overchargeAmount)),
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.85f),
            )
        }
    }
}

/**
 * item-row: status badge, name, item number, price. The **duplicate variant** tints the card
 * amber, shows the DUPLICATE pill with "Same item scanned twice", and offers Keep both /
 * Remove one. Tapping the row toggles the flag by hand (the parser is a heuristic).
 */
@Composable
private fun LineItemCard(
    item: ReceiptItem,
    onClick: () -> Unit,
    onKeepBoth: () -> Unit,
    onRemoveOne: () -> Unit,
) {
    val d = ReTallyTheme.dimensions
    val c = ReTallyTheme.colors
    val flagged = item.isDuplicate && !item.keptAsDistinct
    SurfaceCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (flagged) c.warningTint else c.surface,
    ) {
        Column(Modifier.padding(d.cardPadding)) {
            Row(Modifier.heightIn(min = d.rowMinHeight - d.cardPadding * 2), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    icon = if (flagged) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
                    tint = if (flagged) c.warning else c.primaryDark,
                    background = if (flagged) c.warning.copy(alpha = 0.18f) else c.primaryTint,
                )
                Spacer(Modifier.width(d.md))
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = ReTallyTheme.typography.body, color = c.ink)
                    // Item number always on its own line under the name; receipts without one just omit it.
                    item.itemNumber?.let {
                        Text(str(S.RECEIPT_ITEM_NUMBER, it), style = ReTallyTheme.typography.caption, color = c.muted)
                    }
                    if (item.keptAsDistinct) {
                        Text(str(S.RECEIPT_KEPT_BOUGHT_TWICE), style = ReTallyTheme.typography.caption, color = c.muted)
                    }
                }
                Spacer(Modifier.width(d.sm))
                Text(
                    formatCurrency(item.price),
                    style = ReTallyTheme.typography.amount,
                    color = if (flagged) c.warningInk else c.ink,
                )
            }
            if (flagged) {
                Spacer(Modifier.height(d.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DuplicateBadge()
                    Spacer(Modifier.width(d.sm))
                    Text(str(S.RECEIPT_SAME_ITEM_TWICE), style = ReTallyTheme.typography.caption, color = c.warningInk)
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
}

/**
 * Subtotal, tax and total exactly as printed — never computed from the items. A row is
 * simply omitted when the parser didn't find that line on the receipt.
 */
@Composable
private fun TotalsCard(receipt: Receipt) {
    val d = ReTallyTheme.dimensions
    val colors = MaterialTheme.colorScheme
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(d.cardPadding), verticalArrangement = Arrangement.spacedBy(d.xs)) {
            receipt.subtotal?.let { TotalRow(str(S.RECEIPT_SUBTOTAL), it) }
            receipt.tax?.let { TotalRow(str(S.RECEIPT_TAX), it) }
            receipt.receiptTotal?.let { total ->
                if (receipt.subtotal != null || receipt.tax != null) {
                    ListDivider(Modifier.padding(vertical = d.xs))
                }
                TotalRow(str(S.RECEIPT_TOTAL), total, emphasize = true)
                if (receipt.overchargeAmount > 0) {
                    TotalRow(str(S.RECEIPT_SHOULD_HAVE_BEEN), total - receipt.overchargeAmount, strike = false, color = colors.primary)
                }
            }
            receipt.paymentInfo?.let { payment ->
                ListDivider(Modifier.padding(vertical = d.xs))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CreditCard, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(d.xs))
                    Text(str(S.RECEIPT_PAID_WITH), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(payment, style = ReTallyTheme.typography.amount, color = colors.onSurface)
                }
            }
        }
    }
}

@Composable
private fun TotalRow(label: String, amount: Double, emphasize: Boolean = false, strike: Boolean = false, color: Color? = null) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = color ?: if (emphasize) colors.onSurface else colors.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            formatCurrency(amount),
            style = if (emphasize) ReTallyTheme.typography.price else ReTallyTheme.typography.amount,
            color = color ?: colors.onSurface,
            textDecoration = if (strike) TextDecoration.LineThrough else null,
        )
    }
}
