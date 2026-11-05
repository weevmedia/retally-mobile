/*
 * Copyright (c) 2026-2027 Weev Media. All rights reserved.
 */

package com.retally.app.ui.main

import com.retally.shared.core.crash.CrashReporting
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.core.graphics.Insets
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.retally.app.R
import com.retally.app.location.NearbyNotifications
import com.retally.shared.domain.shopping.ShoppingListRepository
import com.retally.shared.presentation.main.MainTab
import com.retally.shared.ui.navigation.ReTallyTabBar
import com.retally.shared.ui.navigation.ReTallyTabBarDefaults
import com.retally.shared.ui.theme.ReTallyTheme
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Single-activity shell. Screens are Fragments in `res/navigation/nav_graph.xml`; this class
 * hosts the shared Compose tab bar (with the scan FAB), routes its taps to the NavController
 * and mirrors the selected destination into [MainViewModel].
 */
class MainActivity : AppCompatActivity() {

    private val mainViewModel: MainViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        // System splash (Android 12+ icon animation) hands off to SplashFragment, which owns
        // the branded moment and the ads warm-up. Keep the system one short.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Keep the nav bar fully transparent on 3-button navigation too, so each screen's own
        // background shows behind it (no white system scrim over the green splash/onboarding).
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        // One colour theme (the design system's light palette): never switch to DayNight's dark variant.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        setContentView(R.layout.activity_main)

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHost.navController
        val tabBar = findViewById<ComposeView>(R.id.tab_bar)
        val navHostView = findViewById<android.view.View>(R.id.nav_host)

        tabBar.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        tabBar.setContent {
            val state by mainViewModel.store.observeState().collectAsStateWithLifecycle()
            val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            ReTallyTheme {
                ReTallyTabBar(
                    selected = state.selectedTab,
                    onSelect = { tab -> navigateToTab(navController, tab) },
                    // Open the camera over the current tab (Receipts stays Receipts) instead of
                    // jumping to Home first. A fresh scan lands on the Receipts list afterwards.
                    onScan = { openScanner(navController) },
                    bottomInset = bottomInset,
                    bottomMargin = ReTallyTabBarDefaults.marginAboveSystemBar(bottomInset),
                    translucentBar = true,
                )
            }
        }

        // The tab bar floats over the fragments. While it is showing, replace the bottom system
        // inset the screens see with the bar's height so Scaffold pads content above it.
        ViewCompat.setOnApplyWindowInsetsListener(navHostView) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            if (!tabBar.isVisible) return@setOnApplyWindowInsetsListener insets
            val density = resources.displayMetrics.density
            val navInsetDp = (bars.bottom / density).dp
            val barPx = (ReTallyTabBarDefaults.totalHeightAbove(navInsetDp).value * density).toInt()
            WindowInsetsCompat.Builder(insets)
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(bars.left, bars.top, bars.right, bars.bottom + barPx))
                .build()
        }

        consumeShoppingStoreExtra(intent)
        navController.addOnDestinationChangedListener { _: NavController, destination: NavDestination, _ ->
            // A "you're near {store}" tap: once splash hands off to Home, jump to the list.
            if (openShoppingListOnHome && destination.id == R.id.homeFragment) {
                openShoppingListOnHome = false
                navigateToTab(navController, MainTab.AI_LIST)
            }
            // Which screen a crash happened on ("homeFragment", "scanReviewFragment"…).
            CrashReporting.setValue("screen", runCatching { resources.getResourceEntryName(destination.id) }.getOrDefault("unknown"))
            // Splash sits on brand green: white status/nav icons there, dark icons elsewhere.
            val onGreen = destination.id == R.id.splashFragment
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !onGreen
                isAppearanceLightNavigationBars = !onGreen
            }
            val showBar = destination.id in TOP_LEVEL_DESTINATIONS
            if (tabBar.isVisible != showBar) {
                tabBar.isVisible = showBar
                ViewCompat.requestApplyInsets(navHostView)
            }
            destination.toMainTab()?.let(mainViewModel::selectTab)
        }
    }

    private val shoppingLists: ShoppingListRepository by inject()
    private var openShoppingListOnHome = false

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (consumeShoppingStoreExtra(intent)) {
            val navController = (supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
            // Already past splash / onboarding (Home is in the back stack): go to the Smart List
            // now, from whatever screen is open — a receipt, the camera, Budget… Otherwise the
            // destination listener does it as soon as Home appears.
            val signedInShell = runCatching { navController.getBackStackEntry(R.id.homeFragment) }.isSuccess
            if (signedInShell) {
                openShoppingListOnHome = false
                navigateToTab(navController, MainTab.AI_LIST)
            }
        }
    }

    /** Hands a notification's store to the Shopping List screen; true when one was present. */
    private fun consumeShoppingStoreExtra(intent: Intent?): Boolean {
        val store = intent?.getStringExtra(NearbyNotifications.EXTRA_SHOPPING_STORE) ?: return false
        intent.removeExtra(NearbyNotifications.EXTRA_SHOPPING_STORE)
        shoppingLists.pendingStoreName = store
        openShoppingListOnHome = true
        return true
    }

    private fun openScanner(navController: NavController) {
        if (navController.currentDestination?.id == R.id.customScannerFragment) return
        navController.navigate(
            R.id.customScannerFragment,
            null,
            NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setEnterAnim(androidx.navigation.ui.R.anim.nav_default_enter_anim)
                .setExitAnim(androidx.navigation.ui.R.anim.nav_default_exit_anim)
                .setPopEnterAnim(androidx.navigation.ui.R.anim.nav_default_pop_enter_anim)
                .setPopExitAnim(androidx.navigation.ui.R.anim.nav_default_pop_exit_anim)
                .build(),
        )
    }

    /** Switch top-level tab like NavigationUI does: single-top, state saved/restored per tab. */
    private fun navigateToTab(navController: NavController, tab: MainTab) {
        val destination = tab.destinationId()
        if (navController.currentDestination?.id == destination) return
        val options = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setRestoreState(true)
            // Home is the root of the signed-in back stack (splash / onboarding are popped), so
            // popping to it — not the graph's start (splash, no longer on the stack) — keeps one
            // screen per tab instead of piling tabs on top of each other.
            .setPopUpTo(R.id.homeFragment, inclusive = false, saveState = true)
            .build()
        navController.navigate(destination, null, options)
    }

    private fun MainTab.destinationId(): Int = when (this) {
        MainTab.HOME -> R.id.homeFragment
        MainTab.RECEIPTS -> R.id.receiptsFragment
        MainTab.AI_LIST -> R.id.aiListFragment
        MainTab.MORE -> R.id.settingsFragment
    }

    private fun NavDestination.toMainTab(): MainTab? = when (id) {
        R.id.homeFragment -> MainTab.HOME
        R.id.receiptsFragment -> MainTab.RECEIPTS
        R.id.aiListFragment -> MainTab.AI_LIST
        R.id.settingsFragment -> MainTab.MORE
        else -> null
    }

    private companion object {
        /** Destinations that show the tab bar. Detail/flow screens are not listed and hide it. */
        val TOP_LEVEL_DESTINATIONS = setOf(R.id.homeFragment, R.id.receiptsFragment, R.id.aiListFragment, R.id.settingsFragment, R.id.budgetFragment)
    }
}
