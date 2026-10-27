import SwiftUI

/// First launch only: sign-in between onboarding and the tab bar.
struct AuthView: View {
    @StateObject private var viewModel: AuthViewModel

    /// `showGuestOption`: true on the first-launch sign-in only; opened from inside the app the
    /// user is already a guest, so "Continue as guest" is hidden.
    init(showGuestOption: Bool, onFinished: @escaping () -> Void, onEmailContinue: @escaping (String, String, Bool, String?, Bool) -> Void, onBack: @escaping () -> Void) {
        _viewModel = StateObject(wrappedValue: AuthViewModel(showGuestOption: showGuestOption, onFinished: onFinished, onEmailContinue: onEmailContinue, onBack: onBack))
    }

    var body: some View {
        ComposeHostView(controller: viewModel.screen.viewController)
            .ignoresSafeArea() // Compose pads for the system bars itself.
    }
}
