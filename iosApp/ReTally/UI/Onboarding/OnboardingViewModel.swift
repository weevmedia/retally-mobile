//
//  Copyright (c) 2026-2027 Weev Media. All rights reserved.
//

import Combine
import Foundation
import Shared

/// Owns `OnboardingContract.Store` and reports the single exit (Get Started / Have an account).
@MainActor
final class OnboardingViewModel: ObservableObject {
    let screen: ComposeScreen

    private let store: OnboardingContractStore
    private var sideEffects: Closeable?

    init(onFinished: @escaping (Bool) -> Void) {
        let store = IosDependencies.shared.getOnboardingStore()
        self.store = store
        screen = IosScreens.shared.onboarding(store: store)

        sideEffects = store.collectSideEffect { effect in
            if let next = effect as? OnboardingContractSideEffect.NavigateToMain {
                onFinished(next.showGuestOption) // false for "I already have an account"
            }
        }
    }

    deinit {
        sideEffects?.close()
        screen.dispose()
        store.clear()
    }
}
