//
//  Copyright (c) 2026-2027 Weev Media. All rights reserved.
//

import FirebaseAppCheck
import FirebaseCore
import Foundation

/// Firebase App Check on iOS: proves to the backend that a call comes from the genuine
/// ReTally build. App Attest in real builds (DeviceCheck on iOS < 14); the debug provider in
/// DEBUG / simulator, whose token is printed to the Xcode console once and must be registered
/// in the Firebase console → App Check → the iOS app → "Manage debug tokens".
///
/// `install()` has to run BEFORE `FirebaseApp.configure()`. Afterwards Firestore and Storage
/// attach the token on their own; the shared Kotlin HTTP client gets it through `token(_:)`.
enum AppCheckSetup {
    static func install() {
        AppCheck.setAppCheckProviderFactory(ReTallyAppCheckProviderFactory())
    }

    /// Current App Check token for the shared HTTP client; nil when none can be minted.
    static func token(_ completion: @escaping (String?) -> Void) {
        AppCheck.appCheck().token(forcingRefresh: false) { token, _ in
            completion(token?.token)
        }
    }
}

private final class ReTallyAppCheckProviderFactory: NSObject, AppCheckProviderFactory {
    func createProvider(with app: FirebaseApp) -> AppCheckProvider? {
        #if DEBUG || targetEnvironment(simulator)
        return AppCheckDebugProvider(app: app)
        #else
        if #available(iOS 14.0, *) {
            return AppAttestProvider(app: app)
        }
        return DeviceCheckProvider(app: app)
        #endif
    }
}
