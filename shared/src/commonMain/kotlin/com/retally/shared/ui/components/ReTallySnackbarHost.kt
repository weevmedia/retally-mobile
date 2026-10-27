package com.retally.shared.ui.components

import com.retally.shared.core.strings.MessageTone
import com.retally.shared.core.strings.LocalStringResolver
import androidx.compose.runtime.remember
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The app-wide message bar: a green check before confirmations and a red cross before errors
 * (see [MessageTone]).
 * Used in every screen's Scaffold `snackbarHost` slot so confirmations
 * ("Deleted Walmart", "Favorites updated", ...) look the same everywhere.
 */
@Composable
fun ReTallySnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState, modifier) { data ->
        val resolver = LocalStringResolver.current
        val isError = remember(data.visuals.message) { MessageTone.isError(data.visuals.message, resolver) }
        Snackbar {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Red cross for errors, green check for confirmations.
                Icon(
                    imageVector = if (isError) Icons.Rounded.Cancel else Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = if (isError) ErrorRed else SuccessGreen,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = data.visuals.message,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** Bright enough to read on the dark snackbar in both themes. */
private val SuccessGreen = Color(0xFF34D399)
private val ErrorRed = Color(0xFFF87171)
