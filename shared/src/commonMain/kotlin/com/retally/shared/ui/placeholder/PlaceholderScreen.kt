package com.retally.shared.ui.placeholder

import com.retally.shared.core.strings.StringAssetIdentifiers as S
import com.retally.shared.core.strings.str
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.retally.shared.presentation.main.MainTab
import com.retally.shared.ui.components.EmptyState
import com.retally.shared.ui.theme.ReTallyTheme

/** Copy for the tabs that exist in the shell before their feature ships. */
enum class PlaceholderTab(val tab: MainTab, val icon: ImageVector, val bodyKey: String) {
    RECEIPTS(MainTab.RECEIPTS, Icons.AutoMirrored.Rounded.ReceiptLong, S.PLACEHOLDER_RECEIPTS_BODY),
    AI_LIST(MainTab.AI_LIST, Icons.Rounded.AutoAwesome, S.PLACEHOLDER_AI_LIST_BODY),
    MORE(MainTab.MORE, Icons.Rounded.MoreHoriz, S.PLACEHOLDER_MORE_BODY),
}

/** A tab that is present in the shell but not built yet: title bar plus a friendly empty state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceholderScreen(placeholder: PlaceholderTab, modifier: Modifier = Modifier) {
    val c = ReTallyTheme.colors
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(str(placeholder.tab.labelKey), style = ReTallyTheme.typography.display, color = c.ink) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background),
            )
        },
        containerColor = c.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            EmptyState(icon = placeholder.icon, title = str(S.GENERAL_COMING_SOON), body = str(placeholder.bodyKey))
        }
    }
}
