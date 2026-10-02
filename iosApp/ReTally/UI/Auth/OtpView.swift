import SwiftUI

/// First launch only: 6-digit email code entry between sign-in and the tab bar.
struct OtpView: View {
    @StateObject private var viewModel: OtpViewModel

    init(email: String, displayName: String, isNewUser: Bool, accountFirstName: String?, codeAlreadySent: Bool = false, onFinished: @escaping () -> Void, onBack: @escaping () -> Void) {
        _viewModel = StateObject(wrappedValue: OtpViewModel(email: email, displayName: displayName, isNewUser: isNewUser, accountFirstName: accountFirstName, codeAlreadySent: codeAlreadySent, onFinished: onFinished, onBack: onBack))
    }

    var body: some View {
        ComposeHostView(controller: viewModel.screen.viewController)
            .ignoresSafeArea() // Compose pads for the system bars itself.
    }
}
