package com.retally.shared.ui.components

import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.str
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.shadow
import com.retally.shared.ui.theme.ReTallyDimensions
import com.retally.shared.ui.theme.ReTallyTheme

/** Section label used above groups on Home and Settings. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = ReTallyTheme.typography.overline,
        color = ReTallyTheme.colors.muted,
        modifier = modifier.padding(horizontal = ReTallyTheme.dimensions.xs),
    )
}

/**
 * Card: 16pt radius, surface fill, soft shadow (y≈2, 8% black). Content supplies its own
 * 16pt padding via [ReTallyTheme.dimensions.cardPadding] so edge-to-edge rows stay possible.
 */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = ReTallyTheme.colors.surface,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(ReTallyTheme.dimensions.cardRadius)
    val shadowColor = ReTallyTheme.colors.shadow
    val shadowed = modifier.shadow(
        elevation = ReTallyDimensions.cardShadowElevation,
        shape = shape,
        clip = false,
        ambientColor = shadowColor,
        spotColor = shadowColor,
    )
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = shadowed, shape = shape, colors = colors, elevation = elevation) { content() }
    } else {
        Card(modifier = shadowed, shape = shape, colors = colors, elevation = elevation) { content() }
    }
}

/** Badge: 11pt Bold uppercase on a tinted pill — DUPLICATE, SYNCED TO CLOUD. */
@Composable
fun Pill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(containerColor)
            .padding(horizontal = ReTallyDimensions.pillPaddingHorizontal, vertical = ReTallyDimensions.pillPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReTallyTheme.dimensions.xs),
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(ReTallyDimensions.pillIconSize))
        }
        Text(
            text = text.uppercase(),
            style = ReTallyTheme.typography.pill,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** badge/duplicate — amber pill used on item rows and receipt rows. */
@Composable
fun DuplicateBadge(text: String? = null, modifier: Modifier = Modifier) {
    val c = ReTallyTheme.colors
    Pill(text = text ?: str(S.BADGE_DUPLICATE), containerColor = c.warning, contentColor = Color.White, modifier = modifier, icon = Icons.Rounded.Warning)
}

/** badge/synced — green pill shown once a receipt is mirrored to the cloud. */
@Composable
fun SyncedBadge(modifier: Modifier = Modifier) {
    val c = ReTallyTheme.colors
    Pill(text = str(S.BADGE_SYNCED_TO_CLOUD), containerColor = c.primaryTint, contentColor = c.primaryDark, modifier = modifier, icon = Icons.Rounded.CloudDone)
}

/** btn/primary — 52pt, 12pt radius, Primary fill; pressed state darkens to Primary dark. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val c = ReTallyTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Button(
        onClick = onClick,
        modifier = modifier.height(ReTallyDimensions.buttonHeight),
        enabled = enabled,
        shape = RoundedCornerShape(ReTallyDimensions.buttonRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (pressed) c.primaryDark else c.primary,
            contentColor = c.onPrimary,
            disabledContainerColor = c.divider,
            disabledContentColor = c.muted,
        ),
        interactionSource = interaction,
        contentPadding = PaddingValues(horizontal = ReTallyDimensions.spaceXL),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(ReTallyDimensions.iconMedium))
            Spacer(Modifier.width(ReTallyTheme.dimensions.sm))
        }
        Text(text, style = ReTallyTheme.typography.button)
    }
}

/** btn/secondary — 52pt outlined, Ink text on Surface. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    contentColor: Color = ReTallyTheme.colors.ink,
) {
    val c = ReTallyTheme.colors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(ReTallyDimensions.buttonHeight),
        enabled = enabled,
        shape = RoundedCornerShape(ReTallyDimensions.buttonRadius),
        border = BorderStroke(ReTallyDimensions.dividerThickness, c.divider),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = c.surface, contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = ReTallyDimensions.spaceXL),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(ReTallyDimensions.iconMedium))
            Spacer(Modifier.width(ReTallyTheme.dimensions.sm))
        }
        Text(text, style = ReTallyTheme.typography.button)
    }
}

/** 1pt divider in the design-system Divider colour. */
@Composable
fun ListDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = ReTallyDimensions.dividerThickness, color = ReTallyTheme.colors.divider)
}

/** Icon in a tinted circle, used as the leading element of list rows. */
@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color,
    background: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(ReTallyDimensions.iconBadgeSize)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(ReTallyDimensions.iconMedium))
    }
}

/** Empty-state block: illustration, headline, supporting copy, optional action. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = ReTallyDimensions.emptyStateIcon,
    action: (@Composable () -> Unit)? = null,
    /** False draws the bare icon with no tinted disc behind it. */
    showIconDisc: Boolean = true,
    /** Bold supporting copy. */
    bodyBold: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(ReTallyTheme.dimensions.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showIconDisc) {
            Box(
                modifier = Modifier
                    .size(ReTallyDimensions.emptyStateDisc)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(iconSize),
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ReTallyTheme.colors.ink,
                modifier = Modifier.size(iconSize),
            )
        }
        Spacer(Modifier.height(ReTallyTheme.dimensions.lg))
        Text(title, style = ReTallyTheme.typography.title, color = ReTallyTheme.colors.ink)
        Spacer(Modifier.height(ReTallyTheme.dimensions.sm))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bodyBold) FontWeight.Bold else null,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(ReTallyTheme.dimensions.xl))
            action()
        }
    }
}

/** Two-column key/value line inside a card. */
@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier, emphasize: Boolean = false) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(ReTallyTheme.dimensions.sm).weight(1f))
        Text(
            value,
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = if (emphasize) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}
