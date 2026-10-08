import Shared
import SwiftUI

/// Hosts the shared placeholder screen for a tab whose feature is not built yet.
struct PlaceholderView: View {
    @StateObject private var viewModel: PlaceholderViewModel

    init(tab: PlaceholderTab) {
        _viewModel = StateObject(wrappedValue: PlaceholderViewModel(tab: tab))
    }

    var body: some View {
        ComposeHostView(controller: viewModel.screen.viewController)
            .ignoresSafeArea()
    }
}

@MainActor
final class PlaceholderViewModel: ObservableObject {
    let screen: ComposeScreen

    init(tab: PlaceholderTab) {
        screen = IosScreens.shared.placeholder(tab: tab)
    }

    deinit { screen.dispose() }
}
