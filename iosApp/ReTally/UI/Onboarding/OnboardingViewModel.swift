import Combine
import Foundation
import Shared

/// Owns `OnboardingContract.Store` and reports the single exit (Get Started / Have an account).
@MainActor
final class OnboardingViewModel: ObservableObject {
    let screen: ComposeScreen

    private let store: OnboardingContractStore
    private var sideEffects: Closeable?

    init(onFinished: @escaping () -> Void) {
        let store = IosDependencies.shared.getOnboardingStore()
        self.store = store
        screen = IosScreens.shared.onboarding(store: store)

        sideEffects = store.collectSideEffect { effect in
            if effect is OnboardingContractSideEffect.NavigateToMain {
                onFinished()
            }
        }
    }

    deinit {
        sideEffects?.close()
        screen.dispose()
        store.clear()
    }
}
