//
//  Copyright (c) 2026-2027 Weev Media. All rights reserved.
//

import SwiftUI

/// "Duplicates found" — pushed from Home's stat card. Can push a receipt on top.
struct DuplicatesView: View {
    @StateObject private var viewModel: DuplicatesViewModel

    init(onFinished: @escaping () -> Void) {
        _viewModel = StateObject(wrappedValue: DuplicatesViewModel(onFinished: onFinished))
    }

    var body: some View {
        ComposeHostView(controller: viewModel.screen.viewController)
            .ignoresSafeArea()
            .toolbar(.hidden, for: .navigationBar) // Compose draws the top bar with its own Back.
            .toolbar(.hidden, for: .tabBar)
            // Scan button shows only on the Home and Receipts screens themselves.
            .onAppear { NotificationCenter.default.post(name: .retallyHideScanButton, object: nil) }
            .onDisappear { NotificationCenter.default.post(name: .retallyShowScanButton, object: nil) }
            .navigationDestination(isPresented: $viewModel.isReceiptPresented) {
                ScanReviewView(source: .existing(receiptId: viewModel.receiptId), onFinished: { _, _ in viewModel.receiptFinished() })
            }
    }
}
