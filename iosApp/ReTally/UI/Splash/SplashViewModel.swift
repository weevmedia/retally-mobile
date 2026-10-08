import Combine
import Foundation
import Shared

/// Owns `SplashContract.Store`: warms the ads SDK behind the brand animation and reports
/// the hand-off. Screen rendering is the shared Compose splash.
@MainActor
final class SplashViewModel: ObservableObject {
    let store: SplashContractStore
    let screen: ComposeScreen

    private var sideEffects: Closeable?

    init(onFinished: @escaping (Bool, Bool) -> Void) {
        let store = IosDependencies.shared.getSplashStore()
        self.store = store
        screen = IosScreens.shared.splash(store: store, mainStore: IosDependencies.shared.getMainStore())

        sideEffects = store.collectSideEffect { effect in
            if let navigate = effect as? SplashContractSideEffect.NavigateToMain {
                onFinished(navigate.adsReady, navigate.isFirstLaunch)
            }
        }
        store.dispatch(action: SplashContractAction.Start.shared)
    }

    deinit {
        sideEffects?.close()
        screen.dispose()
        store.clear()
    }
}
