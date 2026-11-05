//
//  Copyright (c) 2026-2027 Weev Media. All rights reserved.
//

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
    /// Screens pushed over Home / Receipts that hide the scan button (receipt page, budget,
    /// duplicates, notifications). A count, not a flag, so a screen pushed on top of another
    /// (e.g. a receipt opened from Duplicates) doesn't bring the button back too early.
    @SwiftUI.State private var scanHiders = 0
    /// Live distance from the bottom of the screen to the top edge of the real tab bar.
    @SwiftUI.State private var tabBarTopFromBottom: CGFloat?

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
        // Shown only on the Home and Receipts tabs themselves; hidden on the Smart List and More
        // tabs and while any screen is pushed over Home / Receipts.
        // Measures the real UITabBar (its height differs per device, and the iOS 26 glass bar
        // floats above the home indicator), so the button keeps exactly its gap above it.
        .background(TabBarTopProbe { tabBarTopFromBottom = $0 }.allowsHitTesting(false))
        .overlay(alignment: .bottomTrailing) {
            if scanHiders == 0 && (viewModel.selectedTab == .home || viewModel.selectedTab == .receipts) {
                ScanFloatingButton(action: viewModel.scan)
                    .padding(.trailing, ScanFloatingButton.trailingInset)
                    .padding(.bottom, scanButtonBottomPadding)
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .retallyHideScanButton)) { _ in
            scanHiders += 1
        }
        .onReceive(NotificationCenter.default.publisher(for: .retallyShowScanButton)) { _ in
            scanHiders = max(0, scanHiders - 1)
        }
    }
}

extension TabBarView {
    /// Bottom padding for the scan button inside the safe area: the live bar top when known,
    /// otherwise the measured system bar height.
    private var scanButtonBottomPadding: CGFloat {
        let gap = ScanFloatingButton.gapAboveTabBar
        guard let top = tabBarTopFromBottom else { return ScanFloatingButton.tabBarHeight + gap }
        // The overlay already sits above the home-indicator inset, so subtract it.
        return max(0, top - TabBarTopProbe.bottomSafeInset) + gap
    }
}

/// Invisible view that finds the window's `UITabBar` and reports how far its top edge is
/// from the bottom of the window, re-measuring on every layout pass (rotation, iPad size
/// changes, the bar hiding on pushed screens).
private struct TabBarTopProbe: UIViewRepresentable {
    let onChange: (CGFloat) -> Void

    static var bottomSafeInset: CGFloat {
        UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.keyWindow }
            .first?.safeAreaInsets.bottom ?? 0
    }

    func makeUIView(context: Context) -> ProbeView {
        let view = ProbeView()
        view.onChange = onChange
        view.isUserInteractionEnabled = false
        return view
    }

    func updateUIView(_ uiView: ProbeView, context: Context) {
        uiView.onChange = onChange
        uiView.setNeedsLayout()
    }

    final class ProbeView: UIView {
        var onChange: ((CGFloat) -> Void)?
        private var last: CGFloat?

        override func layoutSubviews() {
            super.layoutSubviews()
            measure()
        }

        override func didMoveToWindow() {
            super.didMoveToWindow()
            // The TabView's controller may not be in place on the first pass.
            DispatchQueue.main.async { [weak self] in self?.measure() }
        }

        private func measure() {
            guard let window, let bar = Self.findTabBar(in: window.rootViewController),
                  !bar.isHidden, bar.window != nil else { return }
            let top = bar.convert(bar.bounds, to: window).minY
            let fromBottom = window.bounds.height - top
            guard fromBottom > 0, fromBottom != last else { return }
            last = fromBottom
            DispatchQueue.main.async { [weak self] in self?.onChange?(fromBottom) }
        }

        private static func findTabBar(in controller: UIViewController?) -> UITabBar? {
            guard let controller else { return nil }
            if let tabs = controller as? UITabBarController { return tabs.tabBar }
            for child in controller.children {
                if let bar = findTabBar(in: child) { return bar }
            }
            return findTabBar(in: controller.presentedViewController)
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

    /// Fallback until `TabBarTopProbe` has measured the live bar: the system tab bar height,
    /// excluding the home-indicator inset the overlay already clears.
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
