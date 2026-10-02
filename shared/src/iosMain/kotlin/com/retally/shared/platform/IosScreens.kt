package com.retally.shared.platform

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retally.shared.presentation.duplicates.DuplicatesContract
import com.retally.shared.presentation.receipts.ReceiptsContract
import com.retally.shared.presentation.home.HomeContract
import com.retally.shared.presentation.main.MainContract
import com.retally.shared.presentation.scan.ScanReviewContract
import com.retally.shared.presentation.settings.SettingsContract
import com.retally.shared.presentation.splash.SplashContract
import com.retally.shared.presentation.onboarding.OnboardingContract
import com.retally.shared.presentation.auth.AuthContract
import com.retally.shared.presentation.auth.OtpContract
import com.retally.shared.ui.duplicates.DuplicatesScreen
import com.retally.shared.ui.home.HomeScreen
import com.retally.shared.ui.navigation.ReTallyTabBar
import com.retally.shared.ui.navigation.ReTallyTabBarDefaults
import com.retally.shared.ui.placeholder.PlaceholderScreen
import com.retally.shared.ui.receipts.ReceiptsScreen
import com.retally.shared.ui.placeholder.PlaceholderTab
import com.retally.shared.ui.scan.ScanReviewScreen
import com.retally.shared.ui.settings.SettingsScreen
import com.retally.shared.ui.splash.SplashScreen
import com.retally.shared.ui.onboarding.OnboardingScreen
import com.retally.shared.ui.auth.AuthScreen
import com.retally.shared.ui.auth.OtpScreen
import com.retally.shared.ui.theme.ReTallyTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/**
 * A Compose screen packaged for a Swift `View`/`ViewModel` pair: the UIViewController to
 * embed plus a handle for the one-shot messages the Swift ViewModel receives as side effects.
 * Call [dispose] from the ViewModel's `deinit`.
 */
class ComposeScreen internal constructor(
    val viewController: UIViewController,
    private val snackbar: SnackbarHostState,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun showMessage(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    fun dispose() = scope.cancel()
}

/**
 * Factories for the shared Compose screens, one per Swift screen folder (`UI/Home/HomeView`
 * hosts [home], and so on). State is observed inside Compose straight from the store the
 * Swift ViewModel owns; the ViewModel only handles side effects (navigation, camera, links).
 */
object IosScreens {

    fun splash(store: SplashContract.Store, mainStore: MainContract.Store): ComposeScreen = screen { _ ->
        val state by store.observeState().collectAsStateWithLifecycle()
        val shell by mainStore.observeState().collectAsStateWithLifecycle()
        SplashScreen(
            progress = state.progress,
            adsEnabled = shell.adsEnabled,
            bannerAdUnitId = IosDependencies.getAdsConfig().homeBannerUnitId,
        )
    }

    /** First launch only: value-prop pager between splash and the tab bar. */
    fun onboarding(store: OnboardingContract.Store): ComposeScreen = screen { _ ->
        OnboardingScreen(dispatch = store::dispatch)
    }

    /** First launch only: sign-in between onboarding and the tab bar. */
    fun auth(store: AuthContract.Store): ComposeScreen = screen { snackbar ->
        val state by store.observeState().collectAsStateWithLifecycle()
        AuthScreen(state = state, dispatch = store::dispatch, snackbarHostState = snackbar)
    }

    /** First launch only: 6-digit email code entry between sign-in and the tab bar. */
    fun otp(store: OtpContract.Store): ComposeScreen = screen { snackbar ->
        val state by store.observeState().collectAsStateWithLifecycle()
        OtpScreen(state = state, dispatch = store::dispatch, snackbarHostState = snackbar)
    }

    fun home(store: HomeContract.Store, mainStore: MainContract.Store): ComposeScreen = screen { snackbar ->
        val state by store.observeState().collectAsStateWithLifecycle()
        val shell by mainStore.observeState().collectAsStateWithLifecycle()
        HomeScreen(
            state = state,
            dispatch = store::dispatch,
            adsEnabled = shell.adsEnabled,
            bannerAdUnitId = IosDependencies.getAdsConfig().homeBannerUnitId,
            showDebugActions = IosDependencies.showDebugActions,
            snackbarHostState = snackbar,
        )
    }

    fun settings(store: SettingsContract.Store): ComposeScreen = screen { snackbar ->
        val state by store.observeState().collectAsStateWithLifecycle()
        SettingsScreen(state = state, dispatch = store::dispatch, snackbarHostState = snackbar)
    }

    /** Receipts tab: every receipt, month-filtered and paged. */
    fun receipts(store: ReceiptsContract.Store, mainStore: MainContract.Store): ComposeScreen = screen { snackbar ->
        val state by store.observeState().collectAsStateWithLifecycle()
        val shell by mainStore.observeState().collectAsStateWithLifecycle()
        ReceiptsScreen(
            state = state,
            dispatch = store::dispatch,
            snackbarHostState = snackbar,
            adsEnabled = shell.adsEnabled,
            bannerAdUnitId = IosDependencies.getAdsConfig().homeBannerUnitId,
        )
    }

    /** A placeholder tab (AI List) until its feature ships. */
    fun placeholder(tab: PlaceholderTab): ComposeScreen = screen { _ -> PlaceholderScreen(tab) }

    /**
     * The shared tab bar with the scan FAB. Sized by [tabBarHeightPoints] plus the bottom
     * safe-area inset the Swift host passes in; taps are dispatched straight to [store].
     */
    fun tabBar(store: MainContract.Store, bottomInsetPoints: Double): ComposeScreen = screen(transparent = true) { _ ->
        val state by store.observeState().collectAsStateWithLifecycle()
        ReTallyTabBar(
            selected = state.selectedTab,
            onSelect = { store.dispatch(MainContract.Action.SelectTab(it)) },
            onScan = { store.dispatch(MainContract.Action.ScanClicked) },
            bottomInset = bottomInsetPoints.dp,
            // The Swift host draws native glass behind the bar (see TabBarGlassBackground).
            translucentBar = true,
        )
    }

    /** Height (in points) the Swift host must give the tab bar, excluding the safe-area inset. */
    val tabBarHeightPoints: Double get() = ReTallyTabBarDefaults.totalHeight.value.toDouble()

    // -- Tab bar geometry for the native glass layer ----------------------
    // `TabBarGlassBackground` draws a native Liquid Glass pill behind the shared Compose bar
    // and must match it exactly. These expose the Kotlin tokens so Swift never keeps its own
    // copy; 1 pt == 1 dp on iOS.

    /** Pill height, excluding the FAB overhang, the float margin and the safe-area inset. */
    val tabBarPillHeightPoints: Double get() = ReTallyTabBarDefaults.barHeight.value.toDouble()

    /** Inset from each screen edge to the pill. */
    val tabBarHorizontalMarginPoints: Double get() = ReTallyTabBarDefaults.horizontalMargin.value.toDouble()

    /** How far the scan FAB pokes out above the pill. */
    val tabBarFabOverhangPoints: Double get() = ReTallyTabBarDefaults.fabOverhang.value.toDouble()

    /** Transparent gap below the pill so it floats clear of the screen edge. */
    val tabBarBottomMarginPoints: Double get() = ReTallyTabBarDefaults.bottomMargin.value.toDouble()

    /** Radius of the scan notch cut into the pill's top edge. */
    val tabBarNotchRadiusPoints: Double get() = ReTallyTabBarDefaults.notchRadius.value.toDouble()

    /** Notch centre relative to the pill's top edge; negative sits above the edge. */
    val tabBarNotchCenterYPoints: Double get() = ReTallyTabBarDefaults.notchCenterY.value.toDouble()

    fun duplicates(store: DuplicatesContract.Store): ComposeScreen = screen { snackbar ->
        val state by store.observeState().collectAsStateWithLifecycle()
        DuplicatesScreen(state = state, dispatch = store::dispatch, snackbarHostState = snackbar)
    }

    fun scanReview(store: ScanReviewContract.Store): ComposeScreen = screen { snackbar ->
        val state by store.observeState().collectAsStateWithLifecycle()
        ScanReviewScreen(state = state, dispatch = store::dispatch, snackbarHostState = snackbar)
    }

    private fun screen(transparent: Boolean = false, content: @Composable (SnackbarHostState) -> Unit): ComposeScreen {
        val snackbar = SnackbarHostState()
        val controller = ComposeUIViewController {
            val preferences = IosDependencies.getPreferences()
            val prefs by preferences.observe().collectAsStateWithLifecycle(preferences.current)
            ReTallyTheme(themeMode = prefs.themeMode) {
                if (transparent) {
                    content(snackbar)
                } else {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        content(snackbar)
                    }
                }
            }
        }
        return ComposeScreen(controller, snackbar)
    }
}
