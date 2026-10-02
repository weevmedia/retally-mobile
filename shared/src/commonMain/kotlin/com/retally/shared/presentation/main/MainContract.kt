package com.retally.shared.presentation.main

import com.retally.shared.core.Effect
import com.retally.shared.core.strings.StringAssetIdentifiers
import com.retally.shared.domain.ads.AdsSession
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Bottom-navigation destinations, in on-screen order. Scan is not a tab — it is the centre
 * FAB, which fires [MainContract.Action.ScanClicked]. `SETTINGS` is kept as an alias for the
 * More tab so existing routes keep working.
 */
enum class MainTab(val labelKey: String) {
    HOME(StringAssetIdentifiers.TAB_HOME),
    RECEIPTS(StringAssetIdentifiers.TAB_RECEIPTS),
    AI_LIST(StringAssetIdentifiers.TAB_AI_LIST),
    MORE(StringAssetIdentifiers.TAB_MORE);

    companion object {
        val SETTINGS: MainTab get() = MORE
    }
}

/**
 * The scan FAB lives in the shell but the scanner flow belongs to Home. The shell publishes a
 * request here and [com.retally.shared.presentation.home.HomeContract.Store] turns it into its
 * own `LaunchScanner` side effect, so both platforms reuse the existing camera plumbing.
 */
object ScanRequests {
    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val flow: SharedFlow<Unit> get() = requests
    fun request() { requests.tryEmit(Unit) }
}

/**
 * Shell for the signed-in experience: owns which tab is selected and whether ads may render.
 * Deliberately tiny — feature logic lives in each tab's own store.
 */
interface MainContract {

    data class State(
        val selectedTab: MainTab = MainTab.HOME,
        val adsEnabled: Boolean = false,
    ) : com.retally.shared.core.State

    sealed class Action : com.retally.shared.core.Action {
        /** Seed from the splash's answer; [AdsSession] keeps the flag current afterwards. */
        data class Init(val adsEnabled: Boolean) : Action()
        data class SelectTab(val tab: MainTab) : Action()
        /** Centre FAB tapped: select Home and ask it to open the scanner. */
        data object ScanClicked : Action()
    }

    sealed class SideEffect : Effect

    class Store(adsSession: AdsSession) : com.retally.shared.core.Store<State, Action, SideEffect>() {
        override fun initState() = State()

        init {
            adsSession.adsEnabled
                .onEach { enabled -> currentState = currentState.copy(adsEnabled = enabled) }
                .launchIn(this)
        }

        override fun dispatch(action: Action) {
            when (action) {
                is Action.Init -> if (action.adsEnabled) currentState = currentState.copy(adsEnabled = true)
                is Action.SelectTab -> currentState = currentState.copy(selectedTab = action.tab)
                Action.ScanClicked -> {
                    currentState = currentState.copy(selectedTab = MainTab.HOME)
                    ScanRequests.request()
                }
            }
        }
    }
}
