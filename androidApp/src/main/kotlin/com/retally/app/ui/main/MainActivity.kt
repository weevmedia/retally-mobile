package com.retally.app.ui.main

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
import com.retally.shared.presentation.main.MainContract
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
                    onScan = {
                        navigateToTab(navController, MainTab.HOME)
                        mainViewModel.dispatch(MainContract.Action.ScanClicked)
                    },
                    bottomInset = bottomInset,
                )
            }
        }

        // The tab bar floats over the fragments. While it is showing, replace the bottom system
        // inset the screens see with the bar's height so Scaffold pads content above it.
        ViewCompat.setOnApplyWindowInsetsListener(navHostView) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            if (!tabBar.isVisible) return@setOnApplyWindowInsetsListener insets
            val barPx = (ReTallyTabBarDefaults.totalHeight.value * resources.displayMetrics.density).toInt()
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
            val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
            val current = navHost.navController.currentDestination?.id
            if (current != null && current in TOP_LEVEL_DESTINATIONS) {
                openShoppingListOnHome = false
                navigateToTab(navHost.navController, MainTab.AI_LIST)
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

    /** Switch top-level tab like NavigationUI does: single-top, state saved/restored per tab. */
    private fun navigateToTab(navController: NavController, tab: MainTab) {
        val destination = tab.destinationId()
        if (navController.currentDestination?.id == destination) return
        val options = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setRestoreState(true)
            .setPopUpTo(navController.graph.startDestinationId, inclusive = false, saveState = true)
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
