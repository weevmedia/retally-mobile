//
//  Copyright (c) 2026-2027 Weev Media. All rights reserved.
//

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
                OnboardingView(onFinished: { viewModel.onboardingFinished(showGuestOption: $0) })
                    // Fades in after the splash; "Get Started" pushes it off to the left.
                    .transition(.asymmetric(insertion: .opacity, removal: .move(edge: .leading)))
            }
            if viewModel.showAuth {
                AuthView(showGuestOption: !viewModel.authFromSettings && viewModel.authShowsGuestOption, onFinished: viewModel.authFinished, onEmailContinue: viewModel.authEmailContinue, onBack: viewModel.authBack)
                    // Slides in from the right (after "Get Started", or Log in from the app);
                    // fades away once signed in.
                    .transition(.asymmetric(insertion: .move(edge: .trailing), removal: .opacity))
                    .zIndex(1)
            }
            if viewModel.showOtp {
                OtpView(email: viewModel.otpEmail, displayName: viewModel.otpDisplayName, isNewUser: viewModel.otpIsNewUser, accountFirstName: viewModel.otpAccountFirstName, codeAlreadySent: viewModel.otpCodeAlreadySent, onFinished: viewModel.otpFinished, onBack: viewModel.otpBack)
                    .transition(.opacity)
                    // Above the sign-in screen (which has zIndex 1 for its slide-in).
                    .zIndex(2)
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
