import Combine
import Foundation
import Shared

/// Owns `AuthContract.Store`: guest entry finishes auth; email CONTINUE opens the OTP screen.
@MainActor
final class AuthViewModel: ObservableObject {
    let screen: ComposeScreen

    private let store: AuthContractStore
    private var sideEffects: Closeable?

    init(onFinished: @escaping () -> Void, onEmailContinue: @escaping (String, String, Bool, String?, Bool) -> Void, onBack: @escaping () -> Void) {
        let store = IosDependencies.shared.getAuthStore()
        self.store = store
        screen = IosScreens.shared.auth(store: store)

        sideEffects = store.collectSideEffect { [weak self] effect in
            if effect is AuthContractSideEffect.NavigateToMain {
                onFinished()
            } else if let otp = effect as? AuthContractSideEffect.NavigateToOtp {
                onEmailContinue(otp.email, otp.displayName, otp.isNewUser, otp.firstName, otp.codeAlreadySent)
            } else if effect is AuthContractSideEffect.NavigateBack {
                onBack()
            } else if let message = (effect as? AuthContractSideEffect.ShowMessage)?.message {
                self?.screen.showMessage(message: message)
            }
        }
    }

    deinit {
        sideEffects?.close()
        screen.dispose()
        store.clear()
    }
}
