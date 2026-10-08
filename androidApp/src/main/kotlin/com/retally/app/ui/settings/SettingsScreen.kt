package com.retally.app.ui.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import com.retally.shared.presentation.settings.SettingsContract.Action
import com.retally.shared.presentation.settings.SettingsContract.State
import com.retally.shared.ui.settings.SettingsScreen as SharedSettingsScreen

/** Android entry composable for Settings; renders the shared Compose Multiplatform screen. */
@Composable
fun SettingsScreen(
    state: State,
    dispatch: (Action) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    SharedSettingsScreen(state = state, dispatch = dispatch, snackbarHostState = snackbarHostState)
}
