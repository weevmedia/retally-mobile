/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.ui.components

import androidx.compose.animation.core.animate
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.Job
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
 * edge, tap one to run it, tap the row or swipe back to close. Only one row should be open at
 * a time — pass [open]/[onOpenChange] from the list so opening a row closes the previous one.
 * The drag is horizontal only, so vertical scrolling of the parent list is unaffected.
 *
 * Tuned to feel like UIKit's swipe actions:
 *  - the row follows the finger 1:1 (a plain float state, no coroutine per drag frame),
 *  - dragging past the full reveal rubber-bands instead of hitting a wall,
 *  - the action area grows with the finger, as on iOS, rather than sitting fixed-width,
 *  - release projects the fling (position + velocity) to pick open/closed, and the settle
 *    spring starts from the finger's own velocity so there is no hitch at release,
 *  - the list's open/closed bookkeeping never restarts a settle that is already running.
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
    val scope = rememberCoroutineScope()

    var offset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val motion = remember { SwipeMotion() }

    fun settleTo(target: Float, velocity: Float = 0f) {
        motion.job?.cancel()
        motion.target = target
        motion.job = scope.launch {
            animate(
                initialValue = offset,
                targetValue = target,
                initialVelocity = velocity,
                animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
            ) { value, _ -> offset = value }
        }
    }

    // The list decides which row is open (closing this one when another opens). Skip when
    // this row is already heading there, so a running settle keeps its velocity.
    LaunchedEffect(open, revealPx) {
        val target = if (open) -revealPx else 0f
        if (!dragging && motion.target != target) settleTo(target)
    }

    val revealed = (-offset).coerceAtLeast(0f)
    Box(modifier) {
        // Actions sit under the trailing edge and widen with the finger. Only this layer is
        // clipped to the card shape so the card keeps its own shadow while sliding.
        if (revealed > 0.5f) {
            Row(
                Modifier
                    .matchParentSize()
                    .clip(shape),
                horizontalArrangement = Arrangement.End,
            ) {
                Row(
                    Modifier
                        .fillMaxHeight()
                        .width(with(density) { revealed.toDp() }),
                ) {
                    actions.forEach { action ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(action.background)
                                .clickable {
                                    onOpenChange(false)
                                    settleTo(0f)
                                    action.onClick()
                                },
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Fades in over the first part of the reveal, like iOS.
                            val alpha = (revealed / revealPx).coerceIn(0f, 1f)
                            Icon(
                                action.icon,
                                contentDescription = action.label,
                                tint = action.tint,
                                modifier = Modifier.size(22.dp).graphicsLayer { this.alpha = alpha },
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                action.label,
                                style = ReTallyTheme.typography.pill,
                                color = action.tint,
                                maxLines = 1,
                                modifier = Modifier.graphicsLayer { this.alpha = alpha },
                            )
                        }
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = offset }
                .draggable(
                    state = rememberDraggableState { delta ->
                        // Past the full reveal the row resists (rubber band); it never moves
                        // right of its resting place.
                        val resisted = if (offset < -revealPx && delta < 0f) delta * RUBBER_BAND else delta
                        offset = (offset + resisted).coerceIn(-revealPx * MAX_OVERDRAG, 0f)
                    },
                    orientation = Orientation.Horizontal,
                    onDragStarted = {
                        motion.job?.cancel()
                        motion.target = null
                        dragging = true
                    },
                    onDragStopped = { velocity ->
                        dragging = false
                        // Where a free fling would come to rest, as UIKit decides it.
                        val projected = offset + velocity * PROJECTION_SECONDS
                        val shouldOpen = projected < -revealPx / 2
                        val target = if (shouldOpen) -revealPx else 0f
                        settleTo(target, velocity)
                        onOpenChange(shouldOpen)
                    },
                )
                // While open, a tap anywhere on the row just closes it (no accidental action).
                .then(
                    if (open) {
                        Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            onOpenChange(false)
                            settleTo(0f)
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

/** The running settle animation and where it is heading (null while the finger is down). */
private class SwipeMotion {
    var job: Job? = null
    var target: Float? = 0f
}

/** Share of the finger's movement applied once the row is past the full reveal. */
private const val RUBBER_BAND = 0.25f
/** How far past the full reveal the rubber band can stretch, as a multiple of it. */
private const val MAX_OVERDRAG = 1.5f
/** Fling projection window: about how far iOS lets a released swipe travel. */
private const val PROJECTION_SECONDS = 0.12f
