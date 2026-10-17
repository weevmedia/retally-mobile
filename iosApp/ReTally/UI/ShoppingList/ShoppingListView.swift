import Shared
import SwiftUI

/// AI Shopping List tab — hosts the shared Compose screen.
struct ShoppingListView: View {
    @StateObject private var viewModel = ShoppingListViewModel()

    var body: some View {
        ComposeHostView(controller: viewModel.screen.viewController)
            .ignoresSafeArea()
            // Picks up a store handed over by a "you're near {store}" notification tap.
            .onAppear { viewModel.appeared() }
    }
}
