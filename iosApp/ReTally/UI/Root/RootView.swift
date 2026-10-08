import SwiftUI

struct RootView: View {
    @StateObject private var viewModel = RootViewModel()

    var body: some View {
        ZStack {
            if let adsReady = viewModel.adsReady {
                TabBarView(adsReady: adsReady)
                    .transition(.opacity)
            } else {
                SplashView(onFinished: viewModel.splashFinished)
                    .transition(.opacity)
            }
            if viewModel.showOnboarding {
                OnboardingView(onFinished: viewModel.onboardingFinished)
                    .transition(.opacity)
            }
            if viewModel.showAuth {
                AuthView(onFinished: viewModel.authFinished, onEmailContinue: viewModel.authEmailContinue, onBack: viewModel.authBack)
                    .transition(.opacity)
            }
            if viewModel.showOtp {
                OtpView(email: viewModel.otpEmail, displayName: viewModel.otpDisplayName, isNewUser: viewModel.otpIsNewUser, accountFirstName: viewModel.otpAccountFirstName, codeAlreadySent: viewModel.otpCodeAlreadySent, onFinished: viewModel.otpFinished, onBack: viewModel.otpBack)
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: viewModel.adsReady == nil)
        .animation(.easeInOut(duration: 0.25), value: viewModel.showOnboarding)
        .animation(.easeInOut(duration: 0.25), value: viewModel.showAuth)
        .animation(.easeInOut(duration: 0.25), value: viewModel.showOtp)
        .onReceive(NotificationCenter.default.publisher(for: .retallyShowAuth)) { _ in
            viewModel.showAuthFromSettings()
        }
    }
}
