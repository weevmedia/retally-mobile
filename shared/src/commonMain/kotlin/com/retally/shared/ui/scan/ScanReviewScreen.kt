/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.ui.scan

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import com.retally.shared.ui.format.formatShortDate
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.Canvas
import com.retally.shared.ui.components.CircleIconButton
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
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import com.retally.shared.domain.category.ItemCategory
import com.retally.shared.domain.receipt.model.Receipt
import com.retally.shared.domain.receipt.model.ReceiptItem
import com.retally.shared.domain.receipt.model.ScanStatus
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
import com.retally.shared.ui.components.SwipeRevealRow
import com.retally.shared.ui.components.SwipeAction
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
    // While the receipt is being read: nothing but the scan animation, centred on a blank page
    // (no top bar, no content).
    if (state.isLoading) {
        Box(
            modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ScanCookingAnimation(isAiMode = state.isAiParsing)
                // The slower AI read gets a line of copy so the wait doesn't feel stuck.
                androidx.compose.animation.AnimatedVisibility(visible = state.isAiParsing) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 20.dp, start = 40.dp, end = 40.dp),
                    ) {
                        Text(
                            str(S.RECEIPT_AI_READING_TITLE),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            str(S.RECEIPT_AI_READING_BODY),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        return
    }
    // Hoisted above the Scaffold so the image viewer overlays the top bar too.
    var showPhoto by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(str(S.RECEIPT_TITLE), style = ReTallyTheme.typography.display) },
                navigationIcon = {
                    CircleIconButton(onClick = { dispatch(ScanReviewContract.Action.CloseClicked) }) {
                        Icon(Icons.Rounded.Close, contentDescription = str(S.ACTION_CLOSE))
                    }
                },
                actions = {
                    // Delete and favorite only make sense for a saved receipt: hidden while
                    // loading and on the "no receipt found" result.
                    val hasReceipt = state.receipt != null && !state.nothingFound
                    if (hasReceipt) {
                        CircleIconButton(onClick = { dispatch(ScanReviewContract.Action.DeleteClicked) }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = str(S.ACTION_DELETE),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    if (hasReceipt) CircleIconButton(
                        onClick = { dispatch(ScanReviewContract.Action.FavoriteStoreToggled) },
                        selected = state.isFavoriteStore,
                    ) {
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
                    SaveStatus(state, dispatch)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { ReTallySnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val receipt = state.receipt
        when {
            receipt == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = str(S.RECEIPT_NO_ITEMS_TITLE),
                    body = str(S.RECEIPT_NO_ITEMS_BODY),
                    illustration = { NoItemsIllustration() },
                    action = {
                        PrimaryButton(text = str(S.RECEIPT_BACK_TO_RECEIPTS), onClick = { dispatch(ScanReviewContract.Action.CloseClicked) }, icon = Icons.AutoMirrored.Rounded.ArrowBack)
                    },
                )
            }
            else -> ReceiptBody(
                receipt = receipt,
                contentPadding = padding,
                categories = state.categories,
                onKeepBoth = { dispatch(ScanReviewContract.Action.KeepBoth(it)) },
                onRemoveOne = { dispatch(ScanReviewContract.Action.RemoveOneRequested(it)) },
                onEditItem = { dispatch(ScanReviewContract.Action.ItemEditOpened(it)) },
                onPhotoClick = { showPhoto = true },
                onEditStore = { dispatch(ScanReviewContract.Action.EditStoreClicked) },
                onShare = { dispatch(ScanReviewContract.Action.ShareClicked) },
                onSave = { dispatch(ScanReviewContract.Action.SaveToPhotosClicked) },
            )
        }
    }
    if (state.confirmingDelete) {
        state.receipt?.let { receipt ->
            AlertDialog(
                onDismissRequest = { dispatch(ScanReviewContract.Action.DeleteCancelled) },
                title = { Text(str(S.HOME_DELETE_TITLE)) },
                text = { Text(str(S.HOME_DELETE_BODY, receipt.storeName, formatShortDate(receipt.purchaseDate))) },
                confirmButton = {
                    TextButton(onClick = { dispatch(ScanReviewContract.Action.DeleteConfirmed) }) {
                        Text(str(S.ACTION_DELETE), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dispatch(ScanReviewContract.Action.DeleteCancelled) }) { Text(str(S.ACTION_CANCEL)) }
                },
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
    // "Edit store" page: slides in over the receipt (top bar included) with its own Back.
    // Keeps the last draft while sliding out, after the store has cleared it.
    val lastStoreEdit = remember { mutableStateOf<ScanReviewContract.StoreEdit?>(null) }
    state.storeEdit?.let { if (lastStoreEdit.value != it) lastStoreEdit.value = it }
    androidx.compose.animation.AnimatedVisibility(
        visible = state.storeEdit != null,
        enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { it }),
        exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { it }),
    ) {
        (state.storeEdit ?: lastStoreEdit.value)?.let { edit ->
            EditStorePage(edit = edit, dispatch = dispatch, modifier = Modifier.fillMaxSize())
        }
    }
    // "Edit item" sheet (swipe a line → Edit): slides up over the receipt.
    state.itemEdit?.let { edit ->
        EditItemSheet(edit = edit, categories = state.categories, dispatch = dispatch)
    }
    // Full-screen image viewer overlay — drawn above the Scaffold so the receipt
    // top bar (X / Receipt / heart) is hidden while viewing. Back dismisses only this.
    androidx.compose.animation.AnimatedVisibility(
        visible = showPhoto,
        enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) +
            androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) +
            androidx.compose.animation.fadeOut(),
    ) {
        state.receipt?.imagePath?.let {
            ReceiptImageViewer(
                imagePath = it,
                onDismiss = { showPhoto = false },
                onShare = { dispatch(ScanReviewContract.Action.ShareClicked) },
                onSave = { dispatch(ScanReviewContract.Action.SaveToPhotosClicked) },
            )
        }
    }
    if (state.showSignInToSync) {
        SignInToSyncDialog(dispatch)
    }
    } // Close root Box
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

/** Guest tapped the cloud icon: say why it has no tick and offer sign-in. */
@Composable
private fun SignInToSyncDialog(dispatch: (ScanReviewContract.Action) -> Unit) {
    val c = ReTallyTheme.colors
    AlertDialog(
        onDismissRequest = { dispatch(ScanReviewContract.Action.SignInToSyncDismissed) },
        shape = RoundedCornerShape(ReTallyTheme.dimensions.cardRadius),
        containerColor = c.surface,
        title = { Text(str(S.RECEIPT_SIGN_IN_TO_SYNC_TITLE), style = ReTallyTheme.typography.title, color = c.ink) },
        text = { Text(str(S.RECEIPT_SIGN_IN_TO_SYNC_BODY), style = ReTallyTheme.typography.body, color = c.muted) },
        confirmButton = {
            TextButton(onClick = { dispatch(ScanReviewContract.Action.SignInToSyncConfirmed) }) {
                Text(str(S.RECEIPT_SIGN_IN_TO_SYNC_CONFIRM), style = ReTallyTheme.typography.button, color = c.primaryDark)
            }
        },
        dismissButton = {
            TextButton(onClick = { dispatch(ScanReviewContract.Action.SignInToSyncDismissed) }) {
                Text(str(S.HOME_BACKUP_PROMPT_LATER), style = ReTallyTheme.typography.button, color = c.ink)
            }
        },
    )
}

/**
 * Save status in the top bar, in the same circle as the other actions: a spinner while
 * saving, then an outlined cloud — with a small green tick badge once a cloud copy exists.
 * Tapping the badge-less cloud sends a guest to sign in (receipts only sync for accounts) or
 * retries the upload for a signed-in user; the store decides which.
 */
@Composable
private fun SaveStatus(state: ScanReviewContract.State, dispatch: (ScanReviewContract.Action) -> Unit) {
    when {
        state.isSaving -> CircleIconButton(onClick = {}, modifier = Modifier.padding(start = 8.dp)) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        // Any receipt on screen (new or reopened) shows its cloud state, so a guest can tap
        // through to sign in from an old receipt too.
        state.receipt != null -> {
            val inCloud = state.receipt.syncedAt != null
            CircleIconButton(
                onClick = { dispatch(ScanReviewContract.Action.CloudStatusClicked) },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Icon(
                    Icons.Outlined.Cloud,
                    contentDescription = str(if (inCloud) S.RECEIPT_SYNCED_A11Y else S.RECEIPT_NOT_SYNCED_A11Y),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                if (inCloud) {
                    // Green tick badge at the cloud's corner, ringed in the circle's surface colour.
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                            .size(16.dp)
                            .background(ReTallyTheme.colors.surface, CircleShape)
                            .padding(1.5.dp)
                            .background(SyncedGreen, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(10.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Badge green for "synced" — matches the success green used across the receipt page. */
private val SyncedGreen = Color(0xFF16A34A)

@Composable
private fun ReceiptBody(
    receipt: Receipt,
    contentPadding: PaddingValues,
    categories: List<ItemCategory>,
    onKeepBoth: (String) -> Unit,
    onRemoveOne: (String) -> Unit,
    onEditItem: (String) -> Unit,
    onPhotoClick: () -> Unit,
    onEditStore: () -> Unit = {},
    onShare: () -> Unit = {},
    onSave: () -> Unit = {},
) {
    val d = ReTallyTheme.dimensions
    var showBarcode by remember { mutableStateOf(false) }
    // The line whose swipe actions (Edit / Delete) are showing; opening one closes the rest.
    var swipedItemId by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = d.screen, end = d.screen,
            top = contentPadding.calculateTopPadding() + d.sm,
            bottom = contentPadding.calculateBottomPadding() + d.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(d.md),
    ) {
        item { StoreHeader(receipt, onPhotoClick = onPhotoClick, onEditStore = onEditStore) }
        // Torn/faded receipt notice — shown when the scan was incomplete.
        if (receipt.scanStatus != ScanStatus.COMPLETE) {
            item { PartialScanBanner(receipt) }
        }
        item { DoubleChargeVerdict(receipt) }

        item { SectionHeader(str(S.RECEIPT_ITEMS_HEADER, receipt.items.size), modifier = Modifier.padding(top = d.sm)) }
        items(receipt.items, key = { it.id }) { item ->
            val c = ReTallyTheme.colors
            SwipeRevealRow(
                actions = listOf(
                    SwipeAction(
                        icon = Icons.Rounded.Edit,
                        label = str(S.ACTION_EDIT),
                        tint = c.onPrimary,
                        background = c.primary,
                        onClick = { onEditItem(item.id) },
                    ),
                    SwipeAction(
                        icon = Icons.Outlined.Delete,
                        label = str(S.ACTION_DELETE),
                        tint = c.onPrimary,
                        background = c.danger,
                        onClick = { onRemoveOne(item.id) },
                    ),
                ),
                open = swipedItemId == item.id,
                onOpenChange = { open -> swipedItemId = if (open) item.id else swipedItemId.takeIf { it != item.id } },
                modifier = Modifier.animateItem(),
            ) {
                LineItemCard(
                    item = item,
                    categoryName = categories.firstOrNull { it.id == item.categoryId }?.name ?: item.categoryId,
                    onKeepBoth = { onKeepBoth(item.id) },
                    onRemoveOne = { onRemoveOne(item.id) },
                )
            }
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
    // Note: the full-screen image viewer is hoisted to ScanReviewScreen (above the
    // Scaffold) so it covers the receipt top bar while viewing.
}

/**
 * Banner shown when a receipt scan was incomplete (torn, faded, or cut off).
 * Uses the approved copy — never asks for another retake.
 */
@Composable
private fun PartialScanBanner(receipt: Receipt) {
    val colors = MaterialTheme.colorScheme
    val message = when (receipt.scanStatus) {
        ScanStatus.PARTIAL -> "This receipt looks torn or faded. We saved what we could read."
        ScanStatus.MANUAL_ENTRY -> "We couldn't read this receipt. Enter the details below."
        ScanStatus.COMPLETE -> return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.tertiaryContainer.copy(alpha = 0.6f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Rounded.Warning,
            contentDescription = null,
            tint = colors.onTertiaryContainer,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onTertiaryContainer,
            )
            if (receipt.missingFields.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Missing: " + receipt.missingFields.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onTertiaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/**
 * Store name and address on the left. The map pin sits left of the first address line and
 * opens the address in Maps; then date/time.
 * Payment is shown with the totals, not here.
 */
@Composable
private fun StoreHeader(receipt: Receipt, onPhotoClick: () -> Unit, onEditStore: () -> Unit) {
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
            // Name with a pencil right after it: opens "Edit store" to fix a misread name/address.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    receipt.storeName,
                    style = ReTallyTheme.typography.storeName,
                    color = colors.onBackground,
                    modifier = Modifier.weight(1f, fill = false),
                )
                IconButton(onClick = onEditStore, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = str(S.RECEIPT_EDIT_STORE_A11Y),
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
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
internal fun DoubleChargeVerdict(receipt: Receipt) {
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
internal fun LineItemCard(
    item: ReceiptItem,
    categoryName: String,
    onKeepBoth: () -> Unit,
    onRemoveOne: () -> Unit,
) {
    val d = ReTallyTheme.dimensions
    val c = ReTallyTheme.colors
    val flagged = item.isDuplicate && !item.keptAsDistinct
    SurfaceCard(
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
                    // Spending category chip (read-only here; changed on the Edit item page).
                    Spacer(Modifier.height(4.dp))
                    Text(
                        categoryName,
                        style = ReTallyTheme.typography.caption,
                        color = c.primaryDark,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.primaryTint)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
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
                        icon = Icons.Rounded.Check,
                        onClick = onKeepBoth,
                        modifier = Modifier.weight(1f).height(ReTallyDimensions.buttonHeightCompact),
                    )
                    SecondaryButton(
                        text = str(S.ACTION_REMOVE_ONE),
                        icon = Icons.Outlined.Delete,
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

/**
 * "No items found": a blank receipt (empty dashed lines where items should be) with a
 * magnifying glass over it. Large, centred, no background disc.
 */
@Composable
private fun NoItemsIllustration() {
    val c = ReTallyTheme.colors
    val paper = c.primary
    val faint = c.muted.copy(alpha = 0.45f)
    val lens = c.ink
    Canvas(Modifier.size(150.dp)) {
        val u = size.minDimension / 150f
        val stroke = Stroke(width = 5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // Receipt: top at y=18, zig-zag bottom at y≈128, x 25..97 (shifted left so receipt +
        // glass sit centred together).
        val left = 25f * u; val right = 97f * u; val top = 18f * u; val bottom = 120f * u
        val teeth = 6
        val tooth = (right - left) / teeth
        val body = Path().apply {
            moveTo(left, top)
            lineTo(right, top)
            lineTo(right, bottom)
            for (i in 0 until teeth) {
                val x0 = right - i * tooth
                lineTo(x0 - tooth / 2f, bottom + 8f * u)
                lineTo(x0 - tooth, bottom)
            }
            close()
        }
        drawPath(body, paper, style = stroke)
        // Empty item lines: dashed, faded — nothing to read.
        val dash = PathEffect.dashPathEffect(floatArrayOf(7f * u, 7f * u))
        listOf(40f to 0.8f, 56f to 0.55f, 72f to 0.8f, 88f to 0.45f).forEach { (y, w) ->
            drawLine(
                faint,
                Offset(left + 12f * u, y * u),
                Offset(left + 12f * u + (right - left - 24f * u) * w, y * u),
                strokeWidth = 4f * u,
                cap = StrokeCap.Round,
                pathEffect = dash,
            )
        }
        // Magnifying glass, bottom-right, overlapping the receipt.
        val center = Offset(91f * u, 98f * u)
        val radius = 22f * u
        drawCircle(c.background, radius = radius + 4f * u, center = center) // clears the lines under the lens
        drawCircle(lens, radius = radius, center = center, style = Stroke(width = 6f * u))
        drawLine(
            lens,
            Offset(center.x + radius * 0.72f, center.y + radius * 0.72f),
            Offset(center.x + radius * 1.55f, center.y + radius * 1.55f),
            strokeWidth = 9f * u,
            cap = StrokeCap.Round,
        )
        // A small "x" in the lens: searched, found nothing.
        val k = radius * 0.38f
        drawLine(lens, Offset(center.x - k, center.y - k), Offset(center.x + k, center.y + k), strokeWidth = 4.5f * u, cap = StrokeCap.Round)
        drawLine(lens, Offset(center.x + k, center.y - k), Offset(center.x - k, center.y + k), strokeWidth = 4.5f * u, cap = StrokeCap.Round)
    }
}
