package com.retally.shared.ui.recalls

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.retally.shared.ui.components.PrimaryButton
import com.retally.shared.ui.components.ReTallySnackbarHost
import com.retally.shared.ui.components.SectionHeader
import com.retally.shared.ui.components.SurfaceCard
import com.retally.shared.ui.format.formatShortDate
import com.retally.shared.ui.theme.ReTallyDimensions
import com.retally.shared.ui.theme.ReTallyTheme
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

/**
 * Food recall alerts (Home bell). Cards show source, date, title and a short excerpt; a tap
 * opens the official FDA / USDA notice in the browser. Recalls that mention something the
 * shopper bought recently sit on top under "May affect you".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecallsScreen(
    state: RecallsContract.State,
    dispatch: (Action) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val c = ReTallyTheme.colors
    LaunchedEffect(Unit) { dispatch(Action.Appeared) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(str(S.ALERTS_TITLE), style = ReTallyTheme.typography.display, color = c.ink) },
                navigationIcon = {
                    IconButton(onClick = { dispatch(Action.CloseClicked) }) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = str(S.ACTION_BACK), tint = c.ink)
                    }
                },
                actions = {
                    IconButton(onClick = { dispatch(Action.Refresh) }, enabled = !state.isRefreshing && !state.isLoading) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = c.primary, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = str(S.SHOPPING_REFRESH), tint = c.ink)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background),
            )
        },
        snackbarHost = { ReTallySnackbarHost(snackbarHostState) },
        containerColor = c.background,
    ) { padding ->
        val content = Modifier.fillMaxSize().padding(padding)
        when {
            state.isLoading -> Box(content, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.primary)
            }
            state.loadFailed -> Box(content.padding(horizontal = ReTallyTheme.dimensions.screen), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Rounded.Warning,
                    title = str(S.ALERTS_FAILED_TITLE),
                    body = str(S.ALERTS_FAILED_BODY),
                    action = { PrimaryButton(str(S.SHOPPING_RETRY), onClick = { dispatch(Action.Refresh) }) },
                )
            }
            else -> RecallList(state, dispatch, content)
        }
    }
}

@Composable
private fun RecallList(state: RecallsContract.State, dispatch: (Action) -> Unit, modifier: Modifier) {
    val d = ReTallyTheme.dimensions
    val uri = LocalUriHandler.current
    val open: (Recall) -> Unit = { recall -> runCatching { uri.openUri(recall.url) } }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = d.screen, vertical = d.sm),
        verticalArrangement = Arrangement.spacedBy(d.md),
    ) {
        if (state.isGuest) {
            item { SignInHint(onSignIn = { dispatch(Action.SignInClicked) }) }
        }
        if (state.matched.isNotEmpty()) {
            item { SectionHeader(str(S.ALERTS_SECTION_MATCHED), Modifier.padding(top = d.xs)) }
            items(state.matched, key = { "m-${it.id}" }) { RecallCard(it, onClick = { open(it) }) }
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
            items(state.others, key = { it.id }) { RecallCard(it, onClick = { open(it) }) }
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
        } else {
            item {
                Text(
                    str(S.ALERTS_SOURCES_NOTE),
                    style = ReTallyTheme.typography.caption,
                    color = ReTallyTheme.colors.muted,
                    modifier = Modifier.padding(horizontal = d.xs),
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
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
            PrimaryButton(str(S.SHOPPING_SIGN_IN), onClick = onSignIn, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun formatPublished(iso: String): String =
    runCatching { formatShortDate(Instant.parse(iso)) }.getOrDefault("")

/** "2026-10-09" → "Oct 9, 2026" (a calendar date, so no time-zone shift). */
private fun formatBoughtDate(date: String): String =
    runCatching { formatShortDate(LocalDate.parse(date).atStartOfDayIn(TimeZone.UTC), TimeZone.UTC) }.getOrDefault(date)
