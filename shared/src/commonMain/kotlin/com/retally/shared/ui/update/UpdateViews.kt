/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.shared.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.str
import com.retally.shared.domain.update.UpdateStatus
import com.retally.shared.ui.components.PrimaryButton
import com.retally.shared.ui.theme.ReTallyTheme

/**
 * Soft update prompt shown over Home: a newer build is on the store, the shopper can take
 * it now or continue ("Not now" hides it until the next release).
 */
@Composable
fun UpdateAvailableDialog(
    status: UpdateStatus.Available,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
) {
    val c = ReTallyTheme.colors
    AlertDialog(
        // A stray tap outside must not count as "Not now" (that hides the prompt for the whole
        // release); only the buttons — or the back gesture — answer it.
        onDismissRequest = onLater,
        properties = DialogProperties(dismissOnClickOutside = false),
        shape = RoundedCornerShape(ReTallyTheme.dimensions.cardRadius),
        containerColor = c.surface,
        icon = { Icon(Icons.Rounded.Download, contentDescription = null, tint = c.primaryDark) },
        title = { Text(str(S.UPDATE_TITLE, status.version), style = ReTallyTheme.typography.title, color = c.ink) },
        text = {
            Text(
                status.message.ifBlank { str(S.UPDATE_BODY) },
                style = ReTallyTheme.typography.body,
                color = c.muted,
            )
        },
        confirmButton = {
            TextButton(onClick = onUpdate) {
                Text(str(S.UPDATE_ACTION), style = ReTallyTheme.typography.button, color = c.primaryDark)
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) {
                Text(str(S.UPDATE_LATER), style = ReTallyTheme.typography.button, color = c.muted)
            }
        },
    )
}

/**
 * Replaces Home when this build is below the backend's minimum: nothing to dismiss and no
 * hand-off to the app; the only way forward is the store.
 */
@Composable
fun UpdateRequiredScreen(
    status: UpdateStatus.Required,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = ReTallyTheme.dimensions
    val c = ReTallyTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .background(c.background)
            .padding(horizontal = d.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(c.primaryTint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Download, contentDescription = null, tint = c.primaryDark, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(d.lg))
        Text(str(S.UPDATE_REQUIRED_TITLE), style = ReTallyTheme.typography.display, color = c.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(d.sm))
        Text(
            str(S.UPDATE_REQUIRED_BODY, status.version),
            style = ReTallyTheme.typography.body,
            color = c.muted,
            textAlign = TextAlign.Center,
        )
        if (status.message.isNotBlank()) {
            Spacer(Modifier.height(d.sm))
            Text(status.message, style = ReTallyTheme.typography.secondaryBody, color = c.muted, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(d.xl))
        PrimaryButton(text = str(S.UPDATE_ACTION), onClick = onUpdate, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.SystemUpdate)
    }
}
