package com.retally.app.ui.settings

import com.retally.shared.core.ViewModel
import com.retally.shared.presentation.settings.SettingsContract.Action
import com.retally.shared.presentation.settings.SettingsContract.SideEffect
import com.retally.shared.presentation.settings.SettingsContract.State
import com.retally.shared.presentation.settings.SettingsContract.Store

/** Android ViewModel for Settings. Owns the shared [Store]. */
class SettingsViewModel(
    override val store: Store,
) : ViewModel<State, Action, SideEffect>()
