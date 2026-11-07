/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.ui.recalls

import com.retally.shared.ui.components.CircleIconButton
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.retally.shared.ui.components.PlatformPullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.str
import com.retally.shared.domain.recalls.Recall
import com.retally.shared.presentation.recalls.RecallsContract
import com.retally.shared.presentation.recalls.RecallsContract.Action
import com.retally.shared.ui.components.EmptyState
import com.retally.shared.ui.components.InFeedSquareAd
import com.retally.shared.ui.components.PrimaryButton
import com.retally.shared.ui.components.ReTallySnackbarHost
import com.retally.shared.ui.components.SectionHeader
import com.retally.shared.ui.components.StickyBottomAd
import com.retally.shared.ui.components.SurfaceCard
import com.retally.shared.ui.format.formatShortDate
import com.retally.shared.ui.shopping.OpticalCenter
import com.retally.shared.ui.theme.ReTallyDimensions
import com.retally.shared.ui.theme.ReTallyTheme
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

/**
 * Food recalls (More › Food recalls). Cards show source, date, title and a short excerpt; a
 * tap opens the official FDA / USDA notice in the browser. Recalls that mention something the
 * shopper bought recently sit on top under "May affect you". When ads are on, an in-feed
 * square ad follows every [RECALLS_PER_AD] recalls and a sticky banner is pinned under the
 * feed ([StickyBottomAd]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecallsScreen(
    state: RecallsContract.State,
    dispatch: (Action) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    adsEnabled: Boolean = false,
    bannerAdUnitId: String = "",
) {
    val c = ReTallyTheme.colors
    LaunchedEffect(Unit) { dispatch(Action.Appeared) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(str(S.ALERTS_TITLE), style = ReTallyTheme.typography.display, color = c.ink) },
                navigationIcon = {
                    CircleIconButton(onClick = { dispatch(Action.CloseClicked) }) {
                        Icon(
                            Icons.Rounded.ChevronLeft,
                            contentDescription = str(S.ACTION_BACK),
                            tint = c.ink,
                            // The chevron glyph has wide built-in padding; 32dp reads like a normal back arrow.
                            modifier = Modifier.size(28.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background),
            )
        },
        snackbarHost = { ReTallySnackbarHost(snackbarHostState) },
        // Sticky banner under the feed; the list keeps scrolling behind it and the Scaffold
        // pads the content by its height so the last recall is never hidden.
        bottomBar = {
            StickyBottomAd(
                adUnitId = bannerAdUnitId,
                enabled = adsEnabled && bannerAdUnitId.isNotBlank() && !state.isLoading && !state.loadFailed,
            )
        },
        containerColor = c.background,
    ) { padding ->
        val content = Modifier.fillMaxSize().padding(padding)
        when {
            state.isLoading -> Box(content, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.primary)
            }
            state.loadFailed -> Box(content.padding(horizontal = ReTallyTheme.dimensions.screen), contentAlignment = OpticalCenter) {
                EmptyState(
                    icon = Icons.Rounded.Warning,
                    title = str(S.ALERTS_FAILED_TITLE),
                    body = str(S.ALERTS_FAILED_BODY),
                    iconSize = ReTallyDimensions.emptyStateIconLarge,
                    showIconDisc = false,
                    action = { PrimaryButton(str(S.SHOPPING_RETRY), onClick = { dispatch(Action.Refresh) }, icon = Icons.Rounded.Refresh) },
                )
            }
            // Pull down to re-check FDA / USDA (replaces the old refresh icon).
            else -> PlatformPullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { dispatch(Action.Refresh) },
                modifier = content,
            ) {
                RecallList(
                    state,
                    dispatch,
                    Modifier.fillMaxSize(),
                    adUnitId = bannerAdUnitId.takeIf { adsEnabled && it.isNotBlank() },
                )
            }
        }
    }
}

@Composable
private fun RecallList(
    state: RecallsContract.State,
    dispatch: (Action) -> Unit,
    modifier: Modifier,
    /** Null when ads are off. */
    adUnitId: String?,
) {
    val d = ReTallyTheme.dimensions
    val uri = LocalUriHandler.current
    val open: (Recall) -> Unit = { recall -> runCatching { uri.openUri(recall.url) } }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = d.screen, vertical = d.sm),
        verticalArrangement = Arrangement.spacedBy(d.md),
    ) {
        // Source / disclaimer note first, so it's read before any recall.
        if (state.recalls.isNotEmpty()) {
            item(key = "sources-note") {
                Text(
                    str(S.ALERTS_SOURCES_NOTE),
                    style = ReTallyTheme.typography.caption,
                    color = ReTallyTheme.colors.muted,
                    modifier = Modifier.padding(horizontal = d.xs),
                )
            }
        }
        if (state.isGuest) {
            item { SignInHint(onSignIn = { dispatch(Action.SignInClicked) }) }
        }
        if (state.matched.isNotEmpty()) {
            item { SectionHeader(str(S.ALERTS_SECTION_MATCHED), Modifier.padding(top = d.xs)) }
            itemsIndexed(state.matched, key = { _, it -> "m-${it.id}" }) { index, recall ->
                RecallCardWithAd(recall, position = index, adUnitId = adUnitId, onClick = { open(recall) })
            }
            item {
                Text(
                    str(S.ALERTS_MATCH_DISCLAIMER),
                    style = ReTallyTheme.typography.caption,
                    color = ReTallyTheme.colors.muted,
                    modifier = Modifier.padding(horizontal = d.xs),
                )
            }
        }
        if (state.others.isNotEmpty()) {
            item { SectionHeader(str(S.ALERTS_SECTION_LATEST), Modifier.padding(top = d.xs)) }
            val offset = state.matched.size
            itemsIndexed(state.others, key = { _, it -> it.id }) { index, recall ->
                RecallCardWithAd(recall, position = offset + index, adUnitId = adUnitId, onClick = { open(recall) })
            }
        }
        if (state.recalls.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.CheckCircle,
                    title = str(S.ALERTS_EMPTY_TITLE),
                    body = str(S.ALERTS_EMPTY_BODY),
                    modifier = Modifier.padding(top = d.xl),
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Recalls between in-feed ads. */
private const val RECALLS_PER_AD = 2

/**
 * A recall card, followed by an in-feed ad when it is the [RECALLS_PER_AD]th, 4th, … recall
 * in the whole list ([position] counts across both sections, starting at 0).
 */
@Composable
private fun RecallCardWithAd(recall: Recall, position: Int, adUnitId: String?, onClick: () -> Unit) {
    if (adUnitId == null || (position + 1) % RECALLS_PER_AD != 0) {
        RecallCard(recall, onClick)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(ReTallyTheme.dimensions.md)) {
        RecallCard(recall, onClick)
        InFeedSquareAd(adUnitId = adUnitId)
    }
}

@Composable
private fun RecallCard(recall: Recall, onClick: () -> Unit) {
    val c = ReTallyTheme.colors
    val d = ReTallyTheme.dimensions
    SurfaceCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(d.cardPadding)) {
            recall.match?.let { match ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(ReTallyDimensions.buttonRadius))
                        .background(c.warningTint)
                        .padding(horizontal = d.md, vertical = d.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Warning, contentDescription = null, tint = c.warningInk, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(d.sm))
                    Text(
                        str(S.ALERTS_MATCH, match.item, formatBoughtDate(match.lastBought)),
                        style = ReTallyTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = c.warningInk,
                    )
                }
                Spacer(Modifier.height(d.md))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourcePill(recall.source)
                Spacer(Modifier.width(d.sm))
                Text(formatPublished(recall.publishedAt), style = ReTallyTheme.typography.caption, color = c.muted)
                recall.classification?.takeIf { it.isNotBlank() }?.let {
                    Text(" · $it", style = ReTallyTheme.typography.caption, color = c.muted)
                }
            }
            Spacer(Modifier.height(d.sm))
            Text(
                recall.title,
                style = ReTallyTheme.typography.rowTitle.copy(fontWeight = FontWeight.Bold),
                color = c.ink,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (recall.excerpt.isNotBlank() && recall.excerpt != recall.title) {
                Spacer(Modifier.height(d.xs))
                Text(
                    recall.excerpt,
                    style = ReTallyTheme.typography.secondaryBody,
                    color = c.muted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(d.sm))
            Text(
                str(S.ALERTS_READ_MORE),
                style = ReTallyTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                color = c.primaryDark,
            )
        }
    }
}

@Composable
private fun SourcePill(source: String) {
    val c = ReTallyTheme.colors
    Text(
        source.uppercase(),
        style = ReTallyTheme.typography.pill,
        color = c.primaryDark,
        modifier = Modifier
            .clip(RoundedCornerShape(ReTallyDimensions.pillRadius))
            .background(c.primaryTint)
            .padding(horizontal = ReTallyDimensions.pillPaddingHorizontal, vertical = ReTallyDimensions.pillPaddingVertical),
    )
}

@Composable
private fun SignInHint(onSignIn: () -> Unit) {
    val c = ReTallyTheme.colors
    val d = ReTallyTheme.dimensions
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(d.cardPadding)) {
            Text(str(S.ALERTS_SIGN_IN_TITLE), style = ReTallyTheme.typography.rowTitle, color = c.ink)
            Spacer(Modifier.height(d.xs))
            Text(str(S.ALERTS_SIGN_IN_BODY), style = ReTallyTheme.typography.secondaryBody, color = c.muted)
            Spacer(Modifier.height(d.md))
            PrimaryButton(str(S.SHOPPING_SIGN_IN), onClick = onSignIn, modifier = Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Rounded.Login)
        }
    }
}

private fun formatPublished(iso: String): String =
    runCatching { formatShortDate(Instant.parse(iso)) }.getOrDefault("")

/** "2026-10-09" → "Oct 9, 2026" (a calendar date, so no time-zone shift). */
private fun formatBoughtDate(date: String): String =
    runCatching { formatShortDate(LocalDate.parse(date).atStartOfDayIn(TimeZone.UTC), TimeZone.UTC) }.getOrDefault(date)
