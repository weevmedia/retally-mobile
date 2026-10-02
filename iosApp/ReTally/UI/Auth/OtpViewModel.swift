import Combine
import Foundation
import Shared

/// Owns `OtpContract.Store`: requests the 6-digit code on entry, verifies it, finishes auth.
@MainActor
final class OtpViewModel: ObservableObject {
    let screen: ComposeScreen

    private let store: OtpContractStore
    private var sideEffects: Closeable?

    init(email: String, displayName: String, isNewUser: Bool, accountFirstName: String?, codeAlreadySent: Bool = false, onFinished: @escaping () -> Void, onBack: @escaping () -> Void) {
        let store = IosDependencies.shared.getOtpStore()
        self.store = store
        screen = IosScreens.shared.otp(store: store)
        // codeAlreadySent: the auth screen already sent the code via requestOtp, so no re-send.
        // displayName (First Last) goes to verifyOtp for new users; isNewUser and
        // accountFirstName drive the post-verification branch only.
        store.dispatch(action: OtpContractAction.Started(email: email, displayName: displayName, isNewUser: isNewUser, accountFirstName: accountFirstName, codeAlreadySent: codeAlreadySent))

        sideEffects = store.collectSideEffect { [weak self] effect in
            switch effect {
            case is OtpContractSideEffect.NavigateToMain:
                onFinished()
            case is OtpContractSideEffect.NavigateBack:
                onBack()
            case let message as OtpContractSideEffect.ShowMessage:
                self?.screen.showMessage(message: message.message)
            default:
                break
            }
        }
    }

    deinit {
        sideEffects?.close()
        screen.dispose()
        store.clear()
    }
}
