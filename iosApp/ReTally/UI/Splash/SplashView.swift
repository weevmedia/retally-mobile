//
//  Copyright (c) 2026-2027 Weev Media. All rights reserved.
//

import SwiftUI

struct SplashView: View {
    @StateObject private var viewModel: SplashViewModel

    init(onFinished: @escaping (Bool, Bool) -> Void) {
        _viewModel = StateObject(wrappedValue: SplashViewModel(onFinished: onFinished))
    }

    var body: some View {
        ComposeHostView(controller: viewModel.screen.viewController)
            .ignoresSafeArea() // Compose pads for the system bars itself.
    }
}
