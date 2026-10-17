import Shared
import SwiftUI
import UIKit

extension Notification.Name {
    static let retallyHideScanButton = Notification.Name("retallyHideScanButton")
    static let retallyShowScanButton = Notification.Name("retallyShowScanButton")
}

/// Shell: Home · Receipts · AI List · More in the **native** `TabView` bar, with Scan as a
/// floating button in the bottom-right corner above it.
///
/// The bar is deliberately the system one, not the shared Compose `ReTallyTabBar`: only a
/// native bar gets Liquid Glass on iOS 26, the system selection capsule and the platform's
/// own layout and accessibility behaviour. Android keeps the Compose bar with its centre
/// scan FAB, so the two shells differ here on purpose.
struct TabBarView: View {
    @StateObject private var viewModel: TabBarViewModel
    @SwiftUI.State private var isScanButtonHidden = false

    init(adsReady: Bool) {
        _viewModel = StateObject(wrappedValue: TabBarViewModel(adsReady: adsReady))
    }

    var body: some View {
        TabView(selection: Binding(get: { viewModel.selectedTab }, set: { viewModel.select($0) })) {
            HomeView()
                .tabItem { Label(NSLocalizedString("tab_home", comment: ""), systemImage: "house.fill") }
                .tag(MainTab.home)
            ReceiptsView()
                // Not an SF Symbol: the Material `ReceiptLong` vector Android draws, so both
                // shells show the same receipt glyph. Template-rendered, so the bar tints it.
                .tabItem {
                    Label {
                        Text(NSLocalizedString("tab_receipts", comment: ""))
                    } icon: {
                        Image("ic_receipt_long").renderingMode(.template)
                    }
                }
                .tag(MainTab.receipts)
            ShoppingListView()
                // The same basket-with-sparkle vector Android's Compose bar draws (ShoppingBasketIcon).
                .tabItem {
                    Label {
                        Text(NSLocalizedString("tab_ai_list", comment: ""))
                    } icon: {
                        Image("ic_shopping_basket").renderingMode(.template)
                    }
                }
                .tag(MainTab.aiList)
            SettingsView()
                .tabItem { Label(NSLocalizedString("tab_more", comment: ""), systemImage: "ellipsis") }
                .tag(MainTab.more)
        }
        // Sits above the native bar in the trailing corner, clear of every tab item.
        // Hidden while the receipt page (or its image viewer) is open, and on the Shopping List tab.
        .overlay(alignment: .bottomTrailing) {
            if !isScanButtonHidden && viewModel.selectedTab != .aiList {
                ScanFloatingButton(action: viewModel.scan)
                    .padding(.trailing, ScanFloatingButton.trailingInset)
                    .padding(.bottom, ScanFloatingButton.tabBarHeight + ScanFloatingButton.gapAboveTabBar)
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .retallyHideScanButton)) { _ in
            isScanButtonHidden = true
        }
        .onReceive(NotificationCenter.default.publisher(for: .retallyShowScanButton)) { _ in
            isScanButtonHidden = false
        }
    }
}

/// Round brand-green scan button. Glass-backed on iOS 26 so it sits with the native bar;
/// a plain shadowed disc below that.
private struct ScanFloatingButton: View {
    static let size: CGFloat = 56
    static let iconSize: CGFloat = 26
    static let trailingInset: CGFloat = 16
    /// Clearance between the button and the top edge of the native tab bar.
    static let gapAboveTabBar: CGFloat = 12

    /// Height of the system tab bar, excluding the home-indicator inset the overlay already
    /// clears. Measured rather than hardcoded so the button keeps its gap on every bar metric
    /// (compact phone, regular width, iOS 26 glass bar).
    static var tabBarHeight: CGFloat {
        let measured = UITabBar().sizeThatFits(.zero).height
        return measured > 0 ? measured : 49
    }

    let action: () -> Void

    var body: some View {
        Button(action: action) {
            // Material `DocumentScanner`, the same vector the Compose bar draws on Android.
            Image("ic_document_scanner")
                .renderingMode(.template)
                .resizable()
                .scaledToFit()
                .frame(width: Self.iconSize, height: Self.iconSize)
                .foregroundStyle(.white)
                .frame(width: Self.size, height: Self.size)
                .background(Circle().fill(Color(red: 0x10 / 255, green: 0xB9 / 255, blue: 0x81 / 255)))
        }
        .accessibilityLabel(NSLocalizedString("tab_scan_receipt_a11y", comment: ""))
        .modifier(ScanButtonSurface())
    }
}

/// iOS 26 gives the button the same Liquid Glass lift as the bar; older systems get a shadow.
private struct ScanButtonSurface: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content.glassEffect(.regular.interactive(), in: Circle())
        } else {
            content.shadow(color: .black.opacity(0.18), radius: 8, x: 0, y: 4)
        }
    }
}
