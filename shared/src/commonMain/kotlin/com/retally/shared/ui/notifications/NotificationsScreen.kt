/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.ui.notifications

import com.retally.shared.ui.components.CircleIconButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.str
import com.retally.shared.domain.notifications.AppNotification
import com.retally.shared.domain.notifications.AppNotificationKind
import com.retally.shared.presentation.notifications.NotificationsContract
import com.retally.shared.presentation.notifications.NotificationsContract.Action
import com.retally.shared.ui.components.EmptyState
import com.retally.shared.ui.components.IconBadge
import com.retally.shared.ui.components.ReTallySnackbarHost
import com.retally.shared.ui.components.SurfaceCard
import com.retally.shared.ui.theme.ReTallyTheme
import kotlin.time.Clock

/**
 * The Home bell: notifications ReTally raised in the last 48 hours. A tap opens what the
 * notification was about; X dismisses it; "Clear all" empties the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    state: NotificationsContract.State,
    dispatch: (Action) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val c = ReTallyTheme.colors
    val d = ReTallyTheme.dimensions
    LaunchedEffect(Unit) { dispatch(Action.Appeared) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(str(S.NOTIFICATIONS_TITLE), style = ReTallyTheme.typography.display, color = c.ink) },
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
                actions = {
                    if (!state.isEmpty) {
                        TextButton(onClick = { dispatch(Action.ClearAllClicked) }) {
                            Text(str(S.NOTIFICATIONS_CLEAR_ALL), color = c.primaryDark)
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
        if (state.isEmpty) {
            // Centred on the whole screen, not just the area under the top bar: this Box starts
            // below the bar, so shift up by half its height (same as the Receipts empty state).
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .offset(y = -padding.calculateTopPadding() / 2)
                    .padding(horizontal = d.screen),
                contentAlignment = Alignment.Center,
            ) {
                // A large bare bell, no tinted disc behind it.
                EmptyState(
                    icon = Icons.Outlined.NotificationsNone,
                    title = str(S.NOTIFICATIONS_EMPTY_TITLE),
                    body = str(S.NOTIFICATIONS_EMPTY_BODY),
                    iconSize = EMPTY_BELL_SIZE,
                    showIconDisc = false,
                )
            }
        } else {
            val now = Clock.System.now().toEpochMilliseconds()
            LazyColumn(
                modifier = content,
                contentPadding = PaddingValues(horizontal = d.screen, vertical = d.sm),
                verticalArrangement = Arrangement.spacedBy(d.md),
            ) {
                // The 48-hour note sits above the list so it's seen before scrolling.
                item(key = "retention-note") {
                    Text(
                        str(S.NOTIFICATIONS_RETENTION_NOTE),
                        style = ReTallyTheme.typography.caption,
                        color = c.muted,
                        modifier = Modifier.padding(horizontal = d.xs),
                    )
                }
                items(state.notifications, key = { it.id }) { notification ->
                    NotificationCard(
                        notification = notification,
                        nowMillis = now,
                        onClick = { dispatch(Action.NotificationClicked(notification.id)) },
                        onDismiss = { dispatch(Action.DismissClicked(notification.id)) },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: AppNotification,
    nowMillis: Long,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = ReTallyTheme.colors
    val d = ReTallyTheme.dimensions
    SurfaceCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(start = d.cardPadding, top = d.cardPadding, bottom = d.cardPadding),
            verticalAlignment = Alignment.Top,
        ) {
            IconBadge(
                icon = when (notification.kind) {
                    AppNotificationKind.NEARBY_STORE -> Icons.Rounded.LocationOn
                    AppNotificationKind.BUDGET -> Icons.Rounded.Savings
                },
                tint = c.primaryDark,
                background = c.primaryTint,
            )
            Spacer(Modifier.width(d.md))
            Column(Modifier.weight(1f)) {
                Text(
                    notification.title,
                    style = ReTallyTheme.typography.rowTitle.copy(fontWeight = FontWeight.SemiBold),
                    color = c.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (notification.body.isNotBlank()) {
                    Spacer(Modifier.height(d.xs))
                    Text(
                        notification.body,
                        style = ReTallyTheme.typography.secondaryBody,
                        color = c.muted,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(d.xs))
                Text(
                    relativeTime(notification.postedAtMillis, nowMillis),
                    style = ReTallyTheme.typography.caption,
                    color = c.muted,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = str(S.NOTIFICATIONS_DISMISS_A11Y),
                    tint = c.muted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** "Just now" · "12 min ago" · "5 h ago" · "Yesterday" — entries never outlive 48 hours. */
@Composable
private fun relativeTime(postedAt: Long, now: Long): String {
    val minutes = ((now - postedAt).coerceAtLeast(0) / 60_000).toInt()
    return when {
        minutes < 1 -> str(S.NOTIFICATIONS_TIME_NOW)
        minutes < 60 -> str(S.NOTIFICATIONS_TIME_MINUTES, minutes)
        minutes < 24 * 60 -> str(S.NOTIFICATIONS_TIME_HOURS, minutes / 60)
        else -> str(S.NOTIFICATIONS_TIME_YESTERDAY)
    }
}

/** "You're all caught up" bell. */
private val EMPTY_BELL_SIZE = 96.dp
