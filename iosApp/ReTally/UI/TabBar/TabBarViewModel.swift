import Combine
import Foundation
import Shared

/// Owns the app-wide `MainContract.Store` (a Koin single): ad eligibility decided on the
/// splash and the selected tab. The shared Compose tab bar dispatches `SelectTab` /
/// `ScanClicked` into the store; this class mirrors the store's selection back into
/// SwiftUI's `TabView` so the page switches.
@MainActor
final class TabBarViewModel: ObservableObject {
    let store: MainContractStore
    let adsEnabled: Bool

    @Published private(set) var selectedTab: MainTab = .home
    private(set) var tabBar: ComposeScreen?

    private var stateCollector: Closeable?

    init(adsReady: Bool) {
        store = IosDependencies.shared.getMainStore()
        adsEnabled = adsReady && IosDependencies.shared.getAdsConfig().enabled
        store.dispatch(action: MainContractAction.Init(adsEnabled: adsEnabled))
        stateCollector = store.collectState { [weak self] state in
            guard let self, self.selectedTab != state.selectedTab else { return }
            self.selectedTab = state.selectedTab
        }
    }

    /// Built lazily once the safe-area inset is known, so the bar pads itself for the home indicator.
    func tabBarScreen(bottomInset: CGFloat) -> ComposeScreen {
        if let tabBar { return tabBar }
        let screen = IosScreens.shared.tabBar(store: store, bottomInsetPoints: Double(bottomInset))
        tabBar = screen
        return screen
    }

    /// Scan button: the store selects Home and raises the scan request the Home store listens for.
    func scan() {
        store.dispatch(action: MainContractAction.ScanClicked())
    }

    /// Keeps the store in sync when the native `TabView` changes the selection.
    func select(_ tab: MainTab) {
        guard tab != selectedTab else { return }
        store.dispatch(action: MainContractAction.SelectTab(tab: tab))
    }

    var tabBarHeight: CGFloat { CGFloat(IosScreens.shared.tabBarHeightPoints) }

    deinit {
        stateCollector?.close()
        tabBar?.dispose()
    }
}
