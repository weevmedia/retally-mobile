import Foundation
import Shared
import UIKit

/// Owns `ShoppingListContract.Store` for the AI Shopping List tab. The Store does the work;
/// this class handles platform glue: sign-in overlay and the share sheet.
@MainActor
final class ShoppingListViewModel: ObservableObject {
    let store: ShoppingListContractStore
    let screen: ComposeScreen

    private var sideEffects: Closeable?

    init() {
        let store = IosDependencies.shared.getShoppingListStore()
        self.store = store
        screen = IosScreens.shared.shoppingList(
            store: store,
            mainStore: IosDependencies.shared.getMainStore()
        )

        sideEffects = store.collectSideEffect { [weak self] effect in
            guard let self else { return }
            switch effect {
            case let message as ShoppingListContractSideEffect.ShowMessage:
                self.screen.showMessage(message: message.message)
            case is ShoppingListContractSideEffect.OpenSignIn:
                NotificationCenter.default.post(name: .retallyShowAuth, object: nil)
            case let share as ShoppingListContractSideEffect.ShareText:
                self.presentShareSheet(text: share.text)
            default:
                break
            }
        }
    }

    func appeared() {
        store.dispatch(action: ShoppingListContractAction.Appeared.shared)
    }

    private func presentShareSheet(text: String) {
        let activityVC = UIActivityViewController(activityItems: [text], applicationActivities: nil)
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let rootVC = windowScene.windows.first?.rootViewController else { return }
        var topVC = rootVC
        while let presented = topVC.presentedViewController {
            topVC = presented
        }
        if let popover = activityVC.popoverPresentationController {
            popover.sourceView = topVC.view
            popover.sourceRect = CGRect(x: topVC.view.bounds.midX, y: topVC.view.bounds.midY, width: 0, height: 0)
        }
        topVC.present(activityVC, animated: true)
    }

    deinit {
        sideEffects?.close()
        screen.dispose()
        store.clear()
    }
}
