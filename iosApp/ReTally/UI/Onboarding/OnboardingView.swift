import SwiftUI

/// First launch only: value-prop pager between splash and sign-in.
struct OnboardingView: View {
    @StateObject private var viewModel: OnboardingViewModel

    init(onFinished: @escaping () -> Void) {
        _viewModel = StateObject(wrappedValue: OnboardingViewModel(onFinished: onFinished))
    }

    var body: some View {
        ComposeHostView(controller: viewModel.screen.viewController)
            .ignoresSafeArea() // Compose pads for the system bars itself.
    }
}
