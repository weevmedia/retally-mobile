//
//  Copyright (c) 2026-2027 Weev Media. All rights reserved.
//

import SwiftUI

/// First launch only: the store-alerts opt-in between sign-in and the tab bar.
struct StoreAlertsView: View {
    @StateObject private var viewModel: StoreAlertsViewModel

    init(onFinished: @escaping () -> Void, onBack: @escaping () -> Void) {
        _viewModel = StateObject(wrappedValue: StoreAlertsViewModel(onFinished: onFinished, onBack: onBack))
    }

    var body: some View {
        ComposeHostView(controller: viewModel.screen.viewController)
            .ignoresSafeArea() // Compose pads for the system bars itself.
    }
}
