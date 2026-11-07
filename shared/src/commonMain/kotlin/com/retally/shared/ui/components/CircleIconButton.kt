/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.retally.shared.ui.theme.ReTallyTheme

/**
 * Top-bar icon in a white (dark: card-coloured) circle. It lifts with a soft shadow while
 * pressed, and stays lifted when [selected] (e.g. a store that's a favourite). Used for every
 * top-bar action and back/close button so they all look the same.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CircleIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = ReTallyTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val elevation by animateDpAsState(
        targetValue = when {
            pressed -> 10.dp
            selected -> 6.dp
            else -> 1.dp
        },
        label = "circle-elevation",
    )
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "circle-scale")
    Box(
        modifier = modifier
            .padding(4.dp) // same 48dp footprint as an IconButton
            .size(CircleButtonSize)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(elevation, CircleShape, clip = false)
            .clip(CircleShape)
            .background(c.surface)
            .border(1.dp, c.ink.copy(alpha = 0.06f), CircleShape)
            .combinedClickable(
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

private val CircleButtonSize = 40.dp
