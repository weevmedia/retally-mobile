//
//  Copyright (c) 2026-2027 Weev Media. All rights reserved.
//

import SwiftUI

struct RootView: View {
    @StateObject private var viewModel = RootViewModel()

    var body: some View {
        ZStack {
            if viewModel.adsReady == nil {
                SplashView(onFinished: viewModel.splashFinished)
                    .transition(.opacity)
            } else if let adsReady = viewModel.adsReady, !viewModel.firstLaunchFlowActive {
                TabBarView(adsReady: adsReady)
                    .transition(.opacity)
            } else {
                // First launch: nothing under onboarding / sign-in. Mounting the tab bar here
                // would start Home early, and Home's update check puts its dialog on top of
                // the onboarding pager (Compose dialogs present above every SwiftUI layer).
                Color(.systemBackground)
                    .ignoresSafeArea()
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
            if viewModel.showStoreAlerts {
                StoreAlertsView(onFinished: viewModel.storeAlertsFinished, onBack: viewModel.storeAlertsBack)
                    // Fades in over the sign-in screen's exit; fades away into the tab bar.
                    .transition(.opacity)
                    .zIndex(3)
            }
            if viewModel.showOtp {
                OtpView(email: viewModel.otpEmail, displayName: viewModel.otpDisplayName, isNewUser: viewModel.otpIsNewUser, accountFirstName: viewModel.otpAccountFirstName, codeAlreadySent: viewModel.otpCodeAlreadySent, onFinished: viewModel.otpFinished, onBack: viewModel.otpBack)
                    .transition(.opacity)
                    // Above the sign-in screen (which has zIndex 1 for its slide-in).
                    .zIndex(2)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: viewModel.adsReady == nil)
        .animation(.easeInOut(duration: 0.25), value: viewModel.firstLaunchFlowActive)
        .animation(.easeInOut(duration: 0.25), value: viewModel.showOnboarding)
        .animation(.easeInOut(duration: 0.25), value: viewModel.showAuth)
        .animation(.easeInOut(duration: 0.25), value: viewModel.showOtp)
        .animation(.easeInOut(duration: 0.25), value: viewModel.showStoreAlerts)
        .onReceive(NotificationCenter.default.publisher(for: .retallyShowAuth)) { _ in
            viewModel.showAuthFromSettings()
        }
    }
}
