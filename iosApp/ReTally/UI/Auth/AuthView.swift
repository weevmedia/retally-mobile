import SwiftUI

/// First launch only: sign-in between onboarding and the tab bar.
struct AuthView: View {
    @StateObject private var viewModel: AuthViewModel

    init(onFinished: @escaping () -> Void, onEmailContinue: @escaping (String, String, Bool, String?, Bool) -> Void, onBack: @escaping () -> Void) {
        _viewModel = StateObject(wrappedValue: AuthViewModel(onFinished: onFinished, onEmailContinue: onEmailContinue, onBack: onBack))
    }

    var body: some View {
        ComposeHostView(controller: viewModel.screen.viewController)
            .ignoresSafeArea() // Compose pads for the system bars itself.
    }
}
