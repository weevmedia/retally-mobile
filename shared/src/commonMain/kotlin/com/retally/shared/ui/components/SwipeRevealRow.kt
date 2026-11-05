/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.retally.shared.ui.theme.ReTallyTheme
import kotlinx.coroutines.launch

/** One action revealed by a swipe: an icon, a one-word label and the tint it draws in. */
data class SwipeAction(
    val icon: ImageVector,
    val label: String,
    val tint: Color,
    val background: Color,
    val onClick: () -> Unit,
)

/**
 * iOS-style swipe actions: drag the row to the left to reveal [actions] under its trailing
 * edge (each [actionWidth] wide), tap one to run it, tap the row or swipe back to close.
 * Release past half the reveal width (or a quick flick) snaps open, otherwise it springs
 * shut. Only one row should be open at a time — pass [open]/[onOpenChange] from the list so
 * opening a row closes the previous one. The row itself is drawn by [content]; the drag is
 * horizontal only, so vertical scrolling of the parent list is unaffected.
 */
@Composable
fun SwipeRevealRow(
    actions: List<SwipeAction>,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    actionWidth: Dp = 72.dp,
    shape: RoundedCornerShape = RoundedCornerShape(ReTallyTheme.dimensions.cardRadius),
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val revealPx = with(density) { (actionWidth * actions.size).toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    // The list decides which row is open; follow it (closing when another row opens).
    LaunchedEffect(open, revealPx) {
        offset.animateTo(if (open) -revealPx else 0f, spring(stiffness = 600f))
    }
    val settle: (Float) -> Unit = { velocity ->
        val shouldOpen = velocity < -600f || (velocity <= 600f && offset.value < -revealPx / 2)
        onOpenChange(shouldOpen)
        scope.launch { offset.animateTo(if (shouldOpen) -revealPx else 0f, spring(stiffness = 600f)) }
    }
    Box(modifier) {
        // Actions sit under the trailing edge, revealed as the row slides left. Only this
        // layer is clipped to the card shape so the card keeps its own shadow while sliding.
        Row(Modifier.matchParentSize().clip(shape), horizontalArrangement = Arrangement.End) {
            actions.forEach { action ->
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(actionWidth)
                        .background(action.background)
                        .clickable {
                            onOpenChange(false)
                            scope.launch { offset.animateTo(0f, spring(stiffness = 600f)) }
                            action.onClick()
                        },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(action.icon, contentDescription = action.label, tint = action.tint, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(action.label, style = ReTallyTheme.typography.pill, color = action.tint)
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = offset.value }
                .draggable(
                    state = rememberDraggableState { delta ->
                        val next = (offset.value + delta).coerceIn(-revealPx, 0f)
                        scope.launch { offset.snapTo(next) }
                    },
                    orientation = Orientation.Horizontal,
                    onDragStopped = { velocity -> settle(velocity) },
                )
                // While open, a tap anywhere on the row just closes it (no accidental action).
                .then(
                    if (open) {
                        Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            onOpenChange(false)
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            content()
        }
    }
}
