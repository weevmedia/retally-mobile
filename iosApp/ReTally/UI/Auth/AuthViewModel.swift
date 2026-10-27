import AuthenticationServices
import Combine
import CryptoKit
import Foundation
import Shared
import UIKit

/// Owns `AuthContract.Store`: guest entry finishes auth; email CONTINUE opens the OTP screen.
@MainActor
final class AuthViewModel: ObservableObject {
    let screen: ComposeScreen

    private let store: AuthContractStore
    private var sideEffects: Closeable?
    /// Kept alive while the Apple sheet is up (ASAuthorizationController holds its delegate weakly).
    private var appleSignIn: AppleSignInCoordinator?
    /// Kept alive while the Google web sheet is up.
    private var googleSignIn: GoogleSignInCoordinator?

    init(showGuestOption: Bool, onFinished: @escaping () -> Void, onEmailContinue: @escaping (String, String, Bool, String?, Bool) -> Void, onBack: @escaping () -> Void) {
        let store = IosDependencies.shared.getAuthStore()
        self.store = store
        store.dispatch(action: AuthContractAction.GuestOptionVisible(visible: showGuestOption))
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
            } else if let start = effect as? AuthContractSideEffect.StartProviderSignIn {
                // iOS offers Sign in with Apple and Google.
                if start.provider == .google {
                    self?.startGoogleSignIn()
                } else {
                    self?.startAppleSignIn()
                }
            }
        }
    }

    private func startAppleSignIn() {
        let coordinator = AppleSignInCoordinator { [weak self] result in
            guard let self else { return }
            self.appleSignIn = nil
            switch result {
            case let .success(idToken, rawNonce, displayName):
                self.store.dispatch(action: AuthContractAction.ProviderTokenReceived(
                    provider: .apple,
                    idToken: idToken,
                    nonce: rawNonce,
                    displayName: displayName
                ))
            case .cancelled:
                self.store.dispatch(action: AuthContractAction.ProviderSignInCancelled.shared)
            case .failed:
                self.store.dispatch(action: AuthContractAction.ProviderSignInFailed.shared)
            }
        }
        appleSignIn = coordinator
        coordinator.start()
    }

    private func startGoogleSignIn() {
        let coordinator = GoogleSignInCoordinator { [weak self] result in
            guard let self else { return }
            self.googleSignIn = nil
            switch result {
            case let .success(idToken):
                self.store.dispatch(action: AuthContractAction.ProviderTokenReceived(
                    provider: .google,
                    idToken: idToken,
                    nonce: nil,
                    displayName: nil // the backend reads the name from Google's token
                ))
            case .cancelled:
                self.store.dispatch(action: AuthContractAction.ProviderSignInCancelled.shared)
            case .failed:
                self.store.dispatch(action: AuthContractAction.ProviderSignInFailed.shared)
            }
        }
        googleSignIn = coordinator
        coordinator.start()
    }

    deinit {
        sideEffects?.close()
        screen.dispose()
        store.clear()
    }
}

// MARK: - Sign in with Apple

/// Runs the Sign in with Apple sheet and hands back Apple's identity token plus the raw nonce
/// (the backend checks the token carries its SHA-256, so a token can't be replayed). Apple
/// shares the user's name only on their very first sign-in, so it's passed along when present.
final class AppleSignInCoordinator: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    enum Result {
        case success(idToken: String, rawNonce: String, displayName: String?)
        case cancelled
        case failed
    }

    private let completion: (Result) -> Void
    private let rawNonce = AppleSignInCoordinator.randomNonce()

    init(completion: @escaping (Result) -> Void) {
        self.completion = completion
    }

    func start() {
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = AppleSignInCoordinator.sha256(rawNonce)
        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.presentationContextProvider = self
        controller.performRequests()
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let tokenData = credential.identityToken,
              let idToken = String(data: tokenData, encoding: .utf8) else {
            DispatchQueue.main.async { self.completion(.failed) }
            return
        }
        let name = credential.fullName.map {
            PersonNameComponentsFormatter.localizedString(from: $0, style: .default)
        }?.trimmingCharacters(in: .whitespaces)
        DispatchQueue.main.async {
            self.completion(.success(idToken: idToken, rawNonce: self.rawNonce, displayName: (name?.isEmpty ?? true) ? nil : name))
        }
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        let cancelled = (error as? ASAuthorizationError)?.code == .canceled
        if !cancelled { print("[ReTallyAppleSignIn] failed: \(error.localizedDescription)") }
        DispatchQueue.main.async { self.completion(cancelled ? .cancelled : .failed) }
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        return scenes.flatMap(\.windows).first(where: \.isKeyWindow) ?? ASPresentationAnchor()
    }

    private static func randomNonce(length: Int = 32) -> String {
        let charset = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var bytes = [UInt8](repeating: 0, count: length)
        if SecRandomCopyBytes(kSecRandomDefault, length, &bytes) != errSecSuccess {
            return UUID().uuidString + UUID().uuidString
        }
        return String(bytes.map { charset[Int($0) % charset.count] })
    }

    private static func sha256(_ input: String) -> String {
        SHA256.hash(data: Data(input.utf8)).map { String(format: "%02x", $0) }.joined()
    }
}

// MARK: - Sign in with Google (iOS)

/// Google sign-in without the Google SDK: the standard OAuth flow for installed apps, run in
/// Apple's secure web sheet (ASWebAuthenticationSession) with PKCE, then the code is exchanged
/// for Google's ID token, which the backend verifies.
///
/// Needs the iOS OAuth client from Firebase: once Google sign-in is enabled, the downloaded
/// GoogleService-Info.plist carries CLIENT_ID and REVERSED_CLIENT_ID (the redirect scheme).
final class GoogleSignInCoordinator: NSObject, ASWebAuthenticationPresentationContextProviding {
    enum Result {
        case success(idToken: String)
        case cancelled
        case failed
    }

    private let completion: (Result) -> Void
    private var session: ASWebAuthenticationSession?
    private let verifier = GoogleSignInCoordinator.randomString(length: 64)
    private let state = GoogleSignInCoordinator.randomString(length: 24)

    init(completion: @escaping (Result) -> Void) {
        self.completion = completion
    }

    func start() {
        guard let path = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist"),
              let plist = NSDictionary(contentsOfFile: path),
              let clientId = plist["CLIENT_ID"] as? String,
              let scheme = plist["REVERSED_CLIENT_ID"] as? String else {
            print("[ReTallyGoogleSignIn] CLIENT_ID missing: enable Google sign-in in Firebase and update GoogleService-Info.plist")
            finish(.failed)
            return
        }
        let redirect = "\(scheme):/oauth2redirect"
        var components = URLComponents(string: "https://accounts.google.com/o/oauth2/v2/auth")!
        components.queryItems = [
            URLQueryItem(name: "client_id", value: clientId),
            URLQueryItem(name: "redirect_uri", value: redirect),
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "scope", value: "openid email profile"),
            URLQueryItem(name: "code_challenge", value: GoogleSignInCoordinator.challenge(for: verifier)),
            URLQueryItem(name: "code_challenge_method", value: "S256"),
            URLQueryItem(name: "state", value: state),
            URLQueryItem(name: "prompt", value: "select_account"),
        ]
        guard let url = components.url else { finish(.failed); return }

        let session = ASWebAuthenticationSession(url: url, callbackURLScheme: scheme) { [weak self] callback, error in
            guard let self else { return }
            if let error = error as? ASWebAuthenticationSessionError, error.code == .canceledLogin {
                self.finish(.cancelled)
                return
            }
            guard error == nil, let callback,
                  let items = URLComponents(url: callback, resolvingAgainstBaseURL: false)?.queryItems,
                  items.first(where: { $0.name == "state" })?.value == self.state,
                  let code = items.first(where: { $0.name == "code" })?.value else {
                self.finish(.failed)
                return
            }
            self.exchange(code: code, clientId: clientId, redirect: redirect)
        }
        session.presentationContextProvider = self
        session.prefersEphemeralWebBrowserSession = false // reuse the user's Google session in Safari
        self.session = session
        if !session.start() { finish(.failed) }
    }

    /// Trades the one-time code for tokens (no client secret for iOS clients; PKCE proves it's us).
    private func exchange(code: String, clientId: String, redirect: String) {
        var request = URLRequest(url: URL(string: "https://oauth2.googleapis.com/token")!)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        let form: [String: String] = [
            "client_id": clientId,
            "code": code,
            "code_verifier": verifier,
            "grant_type": "authorization_code",
            "redirect_uri": redirect,
        ]
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        request.httpBody = form.map { key, value in
            "\(key)=\(value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value)"
        }.joined(separator: "&").data(using: .utf8)

        URLSession.shared.dataTask(with: request) { [weak self] data, _, error in
            guard let self else { return }
            guard error == nil, let data,
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let idToken = json["id_token"] as? String else {
                print("[ReTallyGoogleSignIn] token exchange failed")
                self.finish(.failed)
                return
            }
            self.finish(.success(idToken: idToken))
        }.resume()
    }

    private func finish(_ result: Result) {
        DispatchQueue.main.async { self.completion(result) }
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        return scenes.flatMap(\.windows).first(where: \.isKeyWindow) ?? ASPresentationAnchor()
    }

    private static func randomString(length: Int) -> String {
        let charset = Array("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~")
        var bytes = [UInt8](repeating: 0, count: length)
        _ = SecRandomCopyBytes(kSecRandomDefault, length, &bytes)
        return String(bytes.map { charset[Int($0) % charset.count] })
    }

    /// PKCE S256: base64url(SHA-256(verifier)), no padding.
    private static func challenge(for verifier: String) -> String {
        Data(SHA256.hash(data: Data(verifier.utf8)))
            .base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}
