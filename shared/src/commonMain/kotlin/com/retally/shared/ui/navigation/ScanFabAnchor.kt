package com.retally.shared.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Where the tab bar's scan FAB currently sits, in window pixels. The Android tab bar is a
 * separate ComposeView from the screens, so screens that need to line up with the FAB (the
 * Home floating banner) read it from here instead of re-deriving it from insets.
 *
 * Null when no Compose tab bar is showing (iOS draws its own in SwiftUI).
 */
object ScanFabAnchor {
    var topInWindowPx: Float? by mutableStateOf(null)
}
