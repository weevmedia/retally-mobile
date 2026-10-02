import SwiftUI

struct HomeView: View {
    @StateObject private var viewModel = HomeViewModel()

    var body: some View {
        NavigationStack {
            ComposeHostView(controller: viewModel.screen.viewController)
                // Top only: Compose draws its own title bar under the status bar, while the
                // native TabView reserves the bottom for its bar.
                .ignoresSafeArea(edges: .top)
                .toolbar(.hidden, for: .navigationBar) // Compose draws the top bar.
                .navigationDestination(isPresented: $viewModel.isReviewPresented) {
                    ScanReviewView(source: viewModel.reviewSource, onFinished: viewModel.reviewFinished(fromScan:saved:))
                }
                .navigationDestination(isPresented: $viewModel.isDuplicatesPresented) {
                    DuplicatesView(onFinished: viewModel.duplicatesFinished)
                }
                .fullScreenCover(isPresented: $viewModel.isScannerPresented) {
                    ZStack(alignment: .bottomLeading) {
                        DocumentScannerView(onResult: viewModel.scannerFinished)
                            .ignoresSafeArea()
                        // VisionKit's camera UI is closed, so the gallery shortcut
                        // floats above it (bottom-left, clear of the shutter).
                        Button {
                            viewModel.isGalleryPresented = true
                        } label: {
                            Image(systemName: "photo.on.rectangle")
                                .font(.title2)
                                .foregroundColor(.white)
                                .padding(14)
                                .background(Color.black.opacity(0.55))
                                .clipShape(Circle())
                        }
                        .padding(.leading, 24)
                        .padding(.bottom, 48)
                        .accessibilityLabel(Text(NSLocalizedString("scan_source_gallery", comment: "")))
                    }
                    .ignoresSafeArea()
                    // The review push waits for this: pushing while the cover is
                    // still dismissing is dropped by SwiftUI.
                    .onDisappear { viewModel.scannerCoverDismissed() }
                    .sheet(isPresented: $viewModel.isGalleryPresented) {
                        GalleryImagePicker { result in
                            // PHPicker's completion fires off the main thread.
                            Task { @MainActor in viewModel.galleryFinished(result) }
                        }
                    }
                }
        }
    }
}
