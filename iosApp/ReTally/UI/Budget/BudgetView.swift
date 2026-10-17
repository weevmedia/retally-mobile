import SwiftUI

/// Monthly grocery budget — pushed from Home's Spent tile.
struct BudgetView: View {
    @StateObject private var viewModel: BudgetViewModel

    init(onFinished: @escaping () -> Void) {
        _viewModel = StateObject(wrappedValue: BudgetViewModel(onFinished: onFinished))
    }

    var body: some View {
        ComposeHostView(controller: viewModel.screen.viewController)
            .ignoresSafeArea()
            .toolbar(.hidden, for: .navigationBar) // Compose draws the top bar with its own Back.
    }
}
