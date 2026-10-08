import SwiftUI

struct SettingsView: View {
    @StateObject private var viewModel = SettingsViewModel()

    var body: some View {
        ComposeHostView(controller: viewModel.screen.viewController)
            .ignoresSafeArea()
    }
}
