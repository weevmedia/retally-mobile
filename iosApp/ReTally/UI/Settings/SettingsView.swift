//
//  Copyright (c) 2026-2027 Weev Media. All rights reserved.
//

import Shared
import SwiftUI

struct SettingsView: View {
    @StateObject private var viewModel = SettingsViewModel()

    var body: some View {
        NavigationStack {
            ComposeHostView(controller: viewModel.screen.viewController)
                .ignoresSafeArea()
                .toolbar(.hidden, for: .navigationBar) // Compose draws the top bar.
                .navigationDestination(isPresented: $viewModel.isRecallsPresented) {
                    AlertsView(onFinished: viewModel.recallsFinished)
                }
                .navigationDestination(isPresented: $viewModel.isWebPagePresented) {
                    WebPageView(title: viewModel.webPage.title, url: viewModel.webPage.url, onFinished: viewModel.webPageFinished)
                }
        }
    }
}

/// Privacy / Terms / Help inside the app: Compose top bar + WKWebView, no address bar.
/// Lives in this file so no Xcode project change is needed.
struct WebPageView: View {
    @StateObject private var holder: WebPageScreenHolder

    init(title: String, url: String, onFinished: @escaping () -> Void) {
        _holder = StateObject(wrappedValue: WebPageScreenHolder(title: title, url: url, onFinished: onFinished))
    }

    var body: some View {
        ComposeHostView(controller: holder.screen.viewController)
            .ignoresSafeArea()
            .toolbar(.hidden, for: .navigationBar) // Compose draws the top bar with its own Back.
            .toolbar(.hidden, for: .tabBar)
            .onAppear { NotificationCenter.default.post(name: .retallyHideScanButton, object: nil) }
            .onDisappear { NotificationCenter.default.post(name: .retallyShowScanButton, object: nil) }
    }
}

/// Keeps the Compose screen alive for as long as the page is shown.
@MainActor
final class WebPageScreenHolder: ObservableObject {
    let screen: ComposeScreen

    init(title: String, url: String, onFinished: @escaping () -> Void) {
        screen = IosScreens.shared.webPage(title: title, url: url, onBack: onFinished)
    }

    deinit {
        screen.dispose()
    }
}
