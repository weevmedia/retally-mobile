import SwiftUI

/// Food recall alerts — pushed from Home's bell.
struct AlertsView: View {
    @StateObject private var viewModel: AlertsViewModel

    init(onFinished: @escaping () -> Void) {
        _viewModel = StateObject(wrappedValue: AlertsViewModel(onFinished: onFinished))
    }

    var body: some View {
        ComposeHostView(controller: viewModel.screen.viewController)
            .ignoresSafeArea()
            .toolbar(.hidden, for: .navigationBar) // Compose draws the top bar with its own Back.
    }
}
