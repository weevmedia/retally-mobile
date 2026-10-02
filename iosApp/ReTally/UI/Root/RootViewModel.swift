import Combine
import Foundation

/// App phase: splash until the ads SDK has reported, then onboarding (first launch only),
/// then sign-in, then the OTP code entry, then the tab bar.
@MainActor
final class RootViewModel: ObservableObject {
    /// nil = still on splash. Set once by the splash with whether banners may load.
    @Published private(set) var adsReady: Bool?
    /// First launch: the onboarding pager sits between splash and sign-in.
    @Published private(set) var showOnboarding = false
    /// First launch: the sign-in screen sits between onboarding and the tab bar.
    @Published private(set) var showAuth = false
    /// True when the sign-in overlay was opened from the More tab (guest → log in) instead
    /// of the first-launch flow — Back then dismisses instead of re-showing onboarding.
    @Published private(set) var authFromSettings = false
    /// First launch: the 6-digit email code entry sits between sign-in and the tab bar.
    @Published private(set) var showOtp = false
    /// The validated email the OTP screen is verifying.
    @Published private(set) var otpEmail = ""
    /// "First Last" from the auth name form (new users) — travels to verifyOtp.
    @Published private(set) var otpDisplayName = ""
    /// Whether the OTP email belongs to a new account — drives the post-verification
    /// branch only (straight Home vs "Welcome back"). Never shown pre-verification.
    @Published private(set) var otpIsNewUser = false
    /// First name from the account lookup — shown only after verification.
    @Published private(set) var otpAccountFirstName: String?
    /// The auth screen already sent the code via requestOtp — don't re-send on entry.
    @Published private(set) var otpCodeAlreadySent = false

    func splashFinished(adsReady: Bool, isFirstLaunch: Bool) {
        self.adsReady = adsReady
        self.showOnboarding = isFirstLaunch
    }

    func onboardingFinished() {
        showOnboarding = false
        showAuth = true
    }

    func authFinished() {
        showAuth = false
        authFromSettings = false
    }

    /// More tab → guest Log in: show the sign-in overlay over the tab bar.
    func showAuthFromSettings() {
        authFromSettings = true
        showAuth = true
    }

    /// Back chevron on the sign-in email step: dismiss auth and re-show onboarding.
    /// When opened from the More tab there is no onboarding to return to — just dismiss.
    func authBack() {
        showAuth = false
        if authFromSettings {
            authFromSettings = false
        } else {
            showOnboarding = true
        }
    }

    /// Email CONTINUE on the sign-in screen: overlay the OTP entry, keeping auth underneath
    /// so Back returns to fix the email. The code was already sent by requestOtp, so the
    /// OTP screen must not re-send it. displayName/isNewUser/accountFirstName drive the
    /// OTP verification and the post-verification branch.
    func authEmailContinue(email: String, displayName: String, isNewUser: Bool, accountFirstName: String?, codeAlreadySent: Bool) {
        otpEmail = email
        otpDisplayName = displayName
        otpIsNewUser = isNewUser
        otpAccountFirstName = accountFirstName
        otpCodeAlreadySent = codeAlreadySent
        showOtp = true
    }

    func otpFinished() {
        showOtp = false
        showAuth = false
        authFromSettings = false
    }

    func otpBack() {
        showOtp = false
    }
}

extension Notification.Name {
    /// Posted by the More tab's ViewModel when a guest taps Log in.
    static let retallyShowAuth = Notification.Name("retallyShowAuth")
}
