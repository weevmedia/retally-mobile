//
//  Copyright (c) 2026-2027 Weev Media. All rights reserved.
//

import SwiftUI

/// Home bell: notifications ReTally raised in the last 48 hours, until dismissed.
struct NotificationsView: View {
    @StateObject private var viewModel: NotificationsViewModel

    init(onFinished: @escaping () -> Void, onOpenBudget: @escaping () -> Void = {}) {
        _viewModel = StateObject(wrappedValue: NotificationsViewModel(onFinished: onFinished, onOpenBudget: onOpenBudget))
    }

    var body: some View {
        ComposeHostView(controller: viewModel.screen.viewController)
            .ignoresSafeArea()
            .toolbar(.hidden, for: .navigationBar) // Compose draws the top bar with its own Back.
            // Scan button shows only on the Home and Receipts screens themselves.
            .onAppear { NotificationCenter.default.post(name: .retallyHideScanButton, object: nil) }
            .onDisappear { NotificationCenter.default.post(name: .retallyShowScanButton, object: nil) }
    }
}
