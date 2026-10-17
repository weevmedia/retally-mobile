import UIKit
import AVFoundation
import Vision
import CoreImage
import CoreImage.CIFilterBuiltins

/// Four corners in Vision's normalized space (0...1, origin bottom-left, raw sensor orientation).
private struct Quad {
    var topLeft, topRight, bottomRight, bottomLeft: CGPoint

    init(_ o: VNRectangleObservation) {
        topLeft = o.topLeft; topRight = o.topRight
        bottomRight = o.bottomRight; bottomLeft = o.bottomLeft
    }

    var points: [CGPoint] { [topLeft, topRight, bottomRight, bottomLeft] }

    /// Average corner movement between two frames.
    func distance(to other: Quad) -> CGFloat {
        zip(points, other.points)
            .map { hypot($0.x - $1.x, $0.y - $1.y) }
            .reduce(0, +) / 4
    }

    /// Moves each corner part of the way toward another quad, for smooth tracking.
    func lerp(to o: Quad, _ t: CGFloat) -> Quad {
        func l(_ a: CGPoint, _ b: CGPoint) -> CGPoint {
            CGPoint(x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t)
        }
        var q = self
        q.topLeft = l(topLeft, o.topLeft)
        q.topRight = l(topRight, o.topRight)
        q.bottomRight = l(bottomRight, o.bottomRight)
        q.bottomLeft = l(bottomLeft, o.bottomLeft)
        return q
    }
}

final class ReceiptScannerViewController: UIViewController {

    /// Torch behaviour, cycled by the bolt button and remembered between scans.
    private enum TorchMode: String {
        case auto, on, off

        var next: TorchMode { self == .auto ? .on : self == .on ? .off : .auto }
        var symbol: String {
            switch self {
            case .auto: return "bolt.badge.automatic"
            case .on: return "bolt.fill"
            case .off: return "bolt.slash"
            }
        }
        var label: String {
            switch self {
            case .auto: return "Flash auto"
            case .on: return "Flash on"
            case .off: return "Flash off"
            }
        }
    }

    // MARK: Public API
    /// Called as soon as the cleaned-up image exists, while the capture animation is still
    /// playing — lets the host start OCR early so review opens sooner.
    var onCaptured: ((UIImage) -> Void)?
    var onScan: ((UIImage) -> Void)?
    var onCancel: (() -> Void)?

    // MARK: Tuning
    /// Auto-capture once the receipt has held still this long (time-based, so dropped
    /// frames don't stretch the wait the way a frame count did).
    private let stableDuration: CFTimeInterval = 0.45
    private let stabilityThreshold: CGFloat = 0.02
    /// Rectangle detection rate. Running Vision on every 30fps frame starved the preview
    /// and made the overlay stutter; ~12 detections a second tracks just as well.
    private let detectInterval: CFTimeInterval = 1.0 / 12.0
    /// Long edge for post-capture processing. A 12MP photo through perspective correction +
    /// document enhancement took ~1s; OCR and storage only need ~2000px anyway.
    private let processingMaxEdge: CGFloat = 2600

    // MARK: Torch
    /// EXIF brightness (APEX) below this counts as "too dark to read a receipt".
    private let darkBrightness: Double = -0.5
    /// Auto mode switches the torch on after it has been dark this long (ignores a passing shadow).
    private let darkDuration: CFTimeInterval = 0.8
    /// Softer than full power: less glare on glossy thermal paper.
    private let torchLevel: Float = 0.6
    private static let torchModeKey = "scanner.torchMode"
    private var torchMode: TorchMode =
        TorchMode(rawValue: UserDefaults.standard.string(forKey: torchModeKey) ?? "") ?? .auto
    private var device: AVCaptureDevice?
    /// Touched only on videoQueue.
    private var darkSince: CFTimeInterval?
    private var torchIsOn = false

    // MARK: Capture
    private let session = AVCaptureSession()
    private let sessionQueue = DispatchQueue(label: "receipt.scanner.session")
    private let videoQueue = DispatchQueue(label: "receipt.scanner.video")
    private let videoOutput = AVCaptureVideoDataOutput()
    private let photoOutput = AVCapturePhotoOutput()
    private lazy var previewLayer = AVCaptureVideoPreviewLayer(session: session)
    private let ciContext = CIContext()

    // MARK: Detection state (touched only on videoQueue)
    private var lastQuad: Quad?
    private var stableSince: CFTimeInterval?
    private var lastDetectAt: CFTimeInterval = 0
    private lazy var liveRequest = Self.makeRectangleRequest()
    private var isCapturing = false
    private var autoCapture = true
    private var captureQuad: Quad?
    private var smoothedQuad: Quad?      // what's drawn on screen
    private var missedFrames = 0
    private let maxMissedFrames = 4      // keep the box ~0.3s (at ~12 detections/s) when Vision misses

    // MARK: UI
    private let overlay = CAShapeLayer()
    private let hintLabel = UILabel()
    private let shutterButton = UIButton(type: .custom)
    private let galleryButton = UIButton(type: .custom)
    private let modeButton = UIButton(type: .system)
    private let torchButton = UIButton(type: .system)
    private let flashView = UIView()

    /// ReTally emerald.
    private static let emerald = UIColor(red: 16/255, green: 185/255, blue: 129/255, alpha: 1)

    override var prefersStatusBarHidden: Bool { true }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        setUpPreview()
        setUpControls()
        checkPermissionAndStart()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer.frame = view.bounds
        overlay.frame = view.bounds
        flashView.frame = view.bounds
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        setTorch(false)
        sessionQueue.async { [session] in session.stopRunning() }
    }

    // MARK: Setup

    private func setUpPreview() {
        previewLayer.videoGravity = .resizeAspectFill
        view.layer.addSublayer(previewLayer)

        // ReTally emerald translucent highlight
        overlay.fillColor = Self.emerald.withAlphaComponent(0.25).cgColor
        overlay.strokeColor = Self.emerald.cgColor
        overlay.lineWidth = 2
        overlay.lineJoin = .round
        view.layer.addSublayer(overlay)

        flashView.backgroundColor = .white
        flashView.alpha = 0
        view.addSubview(flashView)
    }

    private func setUpControls() {
        let cancel = UIButton(type: .system)
        cancel.setTitle("Cancel", for: .normal)
        cancel.tintColor = .white
        cancel.titleLabel?.font = .systemFont(ofSize: 17)
        cancel.addTarget(self, action: #selector(cancelTapped), for: .touchUpInside)

        modeButton.setTitle("Auto", for: .normal)
        modeButton.tintColor = .white
        modeButton.titleLabel?.font = .systemFont(ofSize: 17, weight: .semibold)
        modeButton.addTarget(self, action: #selector(modeTapped), for: .touchUpInside)

        torchButton.tintColor = .white
        torchButton.addTarget(self, action: #selector(torchTapped), for: .touchUpInside)
        updateTorchButton()

        hintLabel.text = "Position the receipt in view"
        hintLabel.textColor = .white
        hintLabel.font = .systemFont(ofSize: 15, weight: .medium)
        hintLabel.textAlignment = .center
        hintLabel.backgroundColor = UIColor.black.withAlphaComponent(0.5)
        hintLabel.layer.cornerRadius = 8
        hintLabel.clipsToBounds = true

        // White ring shutter like the system camera
        shutterButton.backgroundColor = .white
        shutterButton.layer.cornerRadius = 32
        shutterButton.layer.borderWidth = 4
        shutterButton.layer.borderColor = UIColor.black.withAlphaComponent(0.3).cgColor
        shutterButton.addTarget(self, action: #selector(shutterTapped), for: .touchUpInside)

        // Gallery button (bottom-left)
        galleryButton.setImage(UIImage(systemName: "photo.on.rectangle"), for: .normal)
        galleryButton.tintColor = .white
        galleryButton.backgroundColor = UIColor.white.withAlphaComponent(0.16)
        galleryButton.layer.cornerRadius = 14
        galleryButton.layer.borderWidth = 1
        galleryButton.layer.borderColor = UIColor.white.withAlphaComponent(0.35).cgColor
        galleryButton.addTarget(self, action: #selector(galleryTapped), for: .touchUpInside)

        [cancel, modeButton, torchButton, hintLabel, shutterButton, galleryButton].forEach {
            $0.translatesAutoresizingMaskIntoConstraints = false
            view.addSubview($0)
        }
        let g = view.safeAreaLayoutGuide
        NSLayoutConstraint.activate([
            cancel.leadingAnchor.constraint(equalTo: g.leadingAnchor, constant: 20),
            cancel.topAnchor.constraint(equalTo: g.topAnchor, constant: 12),

            modeButton.trailingAnchor.constraint(equalTo: g.trailingAnchor, constant: -20),
            modeButton.centerYAnchor.constraint(equalTo: cancel.centerYAnchor),

            torchButton.trailingAnchor.constraint(equalTo: modeButton.leadingAnchor, constant: -20),
            torchButton.centerYAnchor.constraint(equalTo: cancel.centerYAnchor),
            torchButton.widthAnchor.constraint(equalToConstant: 36),
            torchButton.heightAnchor.constraint(equalToConstant: 36),

            hintLabel.centerXAnchor.constraint(equalTo: g.centerXAnchor),
            hintLabel.topAnchor.constraint(equalTo: cancel.bottomAnchor, constant: 16),
            hintLabel.heightAnchor.constraint(equalToConstant: 32),
            hintLabel.widthAnchor.constraint(greaterThanOrEqualToConstant: 240),

            shutterButton.centerXAnchor.constraint(equalTo: g.centerXAnchor),
            shutterButton.bottomAnchor.constraint(equalTo: g.bottomAnchor, constant: -24),
            shutterButton.widthAnchor.constraint(equalToConstant: 64),
            shutterButton.heightAnchor.constraint(equalToConstant: 64),

            galleryButton.leadingAnchor.constraint(equalTo: g.leadingAnchor, constant: 28),
            galleryButton.centerYAnchor.constraint(equalTo: shutterButton.centerYAnchor),
            galleryButton.widthAnchor.constraint(equalToConstant: 52),
            galleryButton.heightAnchor.constraint(equalToConstant: 52),
        ])
    }

    private func checkPermissionAndStart() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            configureSession()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                DispatchQueue.main.async {
                    granted ? self?.configureSession() : self?.cancelTapped()
                }
            }
        default:
            hintLabel.text = "Camera access is off in Settings"
        }
    }

    private func configureSession() {
        sessionQueue.async { [weak self] in
            guard let self else { return }
            session.beginConfiguration()
            session.sessionPreset = .photo   // same 4:3 frame for preview and photo

            guard
                let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
                let input = try? AVCaptureDeviceInput(device: device),
                session.canAddInput(input)
            else { session.commitConfiguration(); return }
            session.addInput(input)
            self.device = device

            videoOutput.alwaysDiscardsLateVideoFrames = true
            videoOutput.videoSettings = [
                kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_420YpCbCr8BiPlanarFullRange
            ]
            videoOutput.setSampleBufferDelegate(self, queue: videoQueue)
            if session.canAddOutput(videoOutput) { session.addOutput(videoOutput) }
            if session.canAddOutput(photoOutput) { session.addOutput(photoOutput) }

            session.commitConfiguration()
            session.startRunning()
            // "On" applies immediately; "Auto" waits for the brightness check.
            if torchMode == .on { setTorch(true) }
        }
    }

    // MARK: Actions

    @objc private func cancelTapped() {
        setTorch(false)
        onCancel?()
    }

    @objc private func torchTapped() {
        torchMode = torchMode.next
        UserDefaults.standard.set(torchMode.rawValue, forKey: Self.torchModeKey)
        updateTorchButton()
        UISelectionFeedbackGenerator().selectionChanged()
        switch torchMode {
        case .on: setTorch(true)
        case .off: setTorch(false)
        case .auto: videoQueue.async { self.darkSince = nil }   // re-evaluate from the next frames
        }
    }

    private func updateTorchButton() {
        let config = UIImage.SymbolConfiguration(pointSize: 20, weight: .semibold)
        torchButton.setImage(UIImage(systemName: torchMode.symbol, withConfiguration: config), for: .normal)
        torchButton.tintColor = torchMode == .on ? .systemYellow : .white
        torchButton.accessibilityLabel = torchMode.label
    }

    /// Turns the torch on (at [torchLevel]) or off. Safe to call from any thread.
    private func setTorch(_ on: Bool) {
        sessionQueue.async { [weak self] in
            guard let self, let device, device.hasTorch, device.isTorchAvailable else { return }
            guard on != torchIsOn || (!on && device.torchMode != .off) else { return }
            do {
                try device.lockForConfiguration()
                if on {
                    try device.setTorchModeOn(level: torchLevel)
                } else {
                    device.torchMode = .off
                }
                device.unlockForConfiguration()
                torchIsOn = on
            } catch {
                // Torch busy (e.g. thermal limit): carry on without it.
            }
        }
    }

    /// Auto mode: switches the torch on once the scene has been dark for [darkDuration].
    /// It never switches itself off mid-scan — the torch brightens the receipt, which would
    /// read as "bright now" and make it flicker. Must run on videoQueue.
    private func evaluateAutoTorch(_ sampleBuffer: CMSampleBuffer, now: CFTimeInterval) {
        guard torchMode == .auto, !torchIsOn else { return }
        guard let brightness = Self.exifBrightness(of: sampleBuffer) else { return }
        if brightness < darkBrightness {
            if darkSince == nil { darkSince = now }
            if let since = darkSince, now - since >= darkDuration {
                darkSince = nil
                setTorch(true)
                DispatchQueue.main.async {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                }
            }
        } else {
            darkSince = nil
        }
    }

    /// Scene brightness the camera reports with each frame (EXIF BrightnessValue, APEX units).
    private static func exifBrightness(of sampleBuffer: CMSampleBuffer) -> Double? {
        guard
            let attachments = CMCopyDictionaryOfAttachments(
                allocator: kCFAllocatorDefault,
                target: sampleBuffer,
                attachmentMode: kCMAttachmentMode_ShouldPropagate
            ) as? [String: Any],
            let exif = attachments[kCGImagePropertyExifDictionary as String] as? [String: Any]
        else { return nil }
        return (exif[kCGImagePropertyExifBrightnessValue as String] as? NSNumber)?.doubleValue
    }

    @objc private func modeTapped() {
        videoQueue.async { [weak self] in
            guard let self else { return }
            autoCapture.toggle()
            stableSince = nil
            let title = autoCapture ? "Auto" : "Manual"
            DispatchQueue.main.async { self.modeButton.setTitle(title, for: .normal) }
        }
    }

    @objc private func shutterTapped() {
        videoQueue.async { [weak self] in
            guard let self else { return }
            // Manual capture uses the smoothed quad if available.
            if captureQuad == nil {
                captureQuad = smoothedQuad ?? lastQuad
            }
            self.capture()
        }
    }

    @objc private func galleryTapped() {
        let picker = UIImagePickerController()
        picker.sourceType = .photoLibrary
        picker.delegate = self
        present(picker, animated: true)
    }

    /// Must run on videoQueue.
    /// Note: captureQuad should be set by the caller (auto or manual path).
    private func capture() {
        guard !isCapturing else { return }
        isCapturing = true
        // captureQuad is set by the caller; fall back to last known if missing.
        if captureQuad == nil {
            captureQuad = smoothedQuad ?? lastQuad
        }

        DispatchQueue.main.async {
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            self.hintLabel.text = "Scanning…"
            self.flashView.alpha = 0.8
            UIView.animate(withDuration: 0.35) { self.flashView.alpha = 0 }
        }
        // Speed over computational-photography polish: a flat receipt doesn't need Deep
        // Fusion, and .speed shortens the shutter-to-photo delay noticeably.
        let settings = AVCapturePhotoSettings()
        settings.photoQualityPrioritization = .speed
        photoOutput.capturePhoto(with: settings, delegate: self)
    }

    // MARK: Overlay

    private func drawOverlay(_ quad: Quad?, progress: CGFloat) {
        CATransaction.begin()
        CATransaction.setDisableActions(true)   // smoothing already does the motion
        defer { CATransaction.commit() }

        guard let quad else { overlay.path = nil; return }

        let pts = quad.points.map {
            previewLayer.layerPointConverted(fromCaptureDevicePoint: CGPoint(x: $0.x, y: 1 - $0.y))
        }
        let path = UIBezierPath()
        path.move(to: pts[0])
        pts.dropFirst().forEach { path.addLine(to: $0) }
        path.close()
        overlay.path = path.cgPath

        // ReTally emerald: fill gets stronger as auto-capture approaches
        overlay.fillColor = Self.emerald.withAlphaComponent(0.2 + 0.25 * progress).cgColor
        overlay.lineWidth = 2 + progress
    }

    // MARK: Image processing

    private static func detectQuad(in image: CIImage) -> Quad? {
        let request = makeRectangleRequest()
        try? VNImageRequestHandler(ciImage: image, orientation: .up).perform([request])
        return request.results?.first.map(Quad.init)
    }

    /// Scales a full-resolution photo down to [processingMaxEdge] before any filtering.
    private func downscaled(_ cgImage: CGImage) -> CIImage {
        let image = CIImage(cgImage: cgImage)
        let longEdge = max(image.extent.width, image.extent.height)
        guard longEdge > processingMaxEdge else { return image }
        let s = processingMaxEdge / longEdge
        return image.transformed(by: CGAffineTransform(scaleX: s, y: s))
    }

    private static func makeRectangleRequest() -> VNDetectRectanglesRequest {
        let request = VNDetectRectanglesRequest()
        request.maximumObservations = 1
        request.minimumConfidence = 0.6      // less strict for receipts
        request.minimumAspectRatio = 0.15    // long receipts
        request.maximumAspectRatio = 1.0
        request.minimumSize = 0.15           // was 0.25
        request.quadratureTolerance = 30
        return request
    }

    private func process(image input: CIImage, orientation: CGImagePropertyOrientation, quad: Quad?) -> UIImage? {
        var image = input
        let w = image.extent.width, h = image.extent.height

        if let quad {
            // CIImage and Vision both use a bottom-left origin, so just scale.
            func scale(_ p: CGPoint) -> CGPoint { CGPoint(x: p.x * w, y: p.y * h) }
            let filter = CIFilter.perspectiveCorrection()
            filter.inputImage = image
            filter.topLeft = scale(quad.topLeft)
            filter.topRight = scale(quad.topRight)
            filter.bottomRight = scale(quad.bottomRight)
            filter.bottomLeft = scale(quad.bottomLeft)
            if let output = filter.outputImage { image = output }
        }

        image = image.oriented(orientation)   // rotate upright

        // Scanner-style cleanup (iOS 16+)
        if let enhancer = CIFilter(name: "CIDocumentEnhancer") {
            enhancer.setValue(image, forKey: kCIInputImageKey)
            enhancer.setValue(1.0, forKey: "inputAmount")
            if let output = enhancer.outputImage { image = output }
        }

        guard let out = ciContext.createCGImage(image, from: image.extent) else { return nil }
        return UIImage(cgImage: out)
    }

    /// Shows the cleaned-up scan where the receipt was, then shrinks it into a
    /// bottom-left thumbnail (like VNDocumentCameraViewController).
    private func playCapturePreview(_ image: UIImage, completion: @escaping () -> Void) {
        let source = overlay.path?.boundingBox ?? view.bounds.insetBy(dx: 40, dy: 140)
        let start = AVMakeRect(aspectRatio: image.size, insideRect: source)

        let preview = UIImageView(image: image)
        preview.contentMode = .scaleAspectFill
        preview.clipsToBounds = true
        preview.frame = start
        preview.layer.borderColor = UIColor.white.cgColor
        preview.layer.borderWidth = 2
        preview.layer.shadowColor = UIColor.black.cgColor
        preview.layer.shadowOpacity = 0.4
        preview.layer.shadowRadius = 8
        preview.layer.masksToBounds = false
        view.addSubview(preview)

        overlay.path = nil
        hintLabel.isHidden = true

        let safe = view.safeAreaLayoutGuide.layoutFrame
        let thumbHeight: CGFloat = 76
        let thumbWidth = thumbHeight * image.size.width / image.size.height
        let end = CGRect(x: safe.minX + 24,
                         y: shutterButton.frame.midY - thumbHeight / 2,
                         width: thumbWidth, height: thumbHeight)

        // 1) pop slightly, 2) hold so the user sees the straightened scan, 3) fly to the thumbnail
        UIView.animate(withDuration: 0.2, animations: {
            preview.transform = CGAffineTransform(scaleX: 1.04, y: 1.04)
        }) { _ in
            UIView.animate(withDuration: 0.45, delay: 0.2,
                           usingSpringWithDamping: 0.85, initialSpringVelocity: 0,
                           options: [.curveEaseInOut]) {
                preview.transform = .identity
                preview.frame = end
                preview.layer.cornerRadius = 4
            } completion: { _ in
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.1, execute: completion)
            }
        }
    }

    private func finish(with image: UIImage?) {
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            if let image {
                setTorch(false)
                sessionQueue.async { [session] in session.stopRunning() }   // camera freezes on the last frame, as Apple's does
                onCaptured?(image)            // start OCR while the animation plays
                playCapturePreview(image) { [weak self] in
                    self?.onScan?(image)      // presenter closes the scanner here
                }
            } else {
                videoQueue.async { self.isCapturing = false; self.stableSince = nil }
                hintLabel.text = "Try again"
            }
        }
    }
}

// MARK: - Live detection

extension ReceiptScannerViewController: AVCaptureVideoDataOutputSampleBufferDelegate {
    func captureOutput(_ output: AVCaptureOutput,
                       didOutput sampleBuffer: CMSampleBuffer,
                       from connection: AVCaptureConnection) {
        guard !isCapturing, let buffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        let now = CACurrentMediaTime()
        guard now - lastDetectAt >= detectInterval else { return }
        lastDetectAt = now
        evaluateAutoTorch(sampleBuffer, now: now)

        let request = liveRequest
        try? VNImageRequestHandler(cvPixelBuffer: buffer, orientation: .up).perform([request])

        guard let quad = request.results?.first.map(Quad.init) else {
            missedFrames += 1
            if missedFrames > maxMissedFrames {          // really lost it, so hide
                smoothedQuad = nil; lastQuad = nil; stableSince = nil
                DispatchQueue.main.async {
                    self.drawOverlay(nil, progress: 0)
                    self.hintLabel.text = "Position the receipt in view"
                }
            }
            return
        }
        missedFrames = 0

        // Stability is measured on raw detections, by time held still.
        if let last = lastQuad, quad.distance(to: last) < stabilityThreshold {
            if stableSince == nil { stableSince = now }
        } else {
            stableSince = nil
        }
        lastQuad = quad
        let heldFor = stableSince.map { now - $0 } ?? 0

        // What's drawn glides toward the new detection instead of jumping. Detections are
        // sparser than frames now, so step further each time.
        let display = smoothedQuad.map { $0.lerp(to: quad, 0.55) } ?? quad
        smoothedQuad = display

        let progress = autoCapture ? min(1, CGFloat(heldFor / stableDuration)) : 0
        let auto = autoCapture
        DispatchQueue.main.async {
            self.drawOverlay(display, progress: progress)
            self.hintLabel.text = auto ? "Hold steady…" : "Tap the shutter"
        }

        if autoCapture && heldFor >= stableDuration {
            captureQuad = display
            capture()
        }
    }
}

// MARK: - Photo

extension ReceiptScannerViewController: AVCapturePhotoCaptureDelegate {
    func photoOutput(_ output: AVCapturePhotoOutput,
                     didFinishProcessingPhoto photo: AVCapturePhoto,
                     error: Error?) {
        guard error == nil, let cgImage = photo.cgImageRepresentation() else {
            finish(with: nil)
            return
        }
        let rawOrientation = photo.metadata[kCGImagePropertyOrientation as String] as? UInt32 ?? 6
        let orientation = CGImagePropertyOrientation(rawValue: rawOrientation) ?? .right

        // Re-detect on the (downscaled) photo; fall back to the live-preview corners.
        videoQueue.async { [weak self] in
            guard let self else { return }
            let image = downscaled(cgImage)
            let quad = Self.detectQuad(in: image) ?? captureQuad
            finish(with: process(image: image, orientation: orientation, quad: quad))
        }
    }
}

// MARK: - Gallery

extension ReceiptScannerViewController: UIImagePickerControllerDelegate, UINavigationControllerDelegate {
    func imagePickerController(_ picker: UIImagePickerController,
                               didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
        picker.dismiss(animated: true)
        guard let image = info[.originalImage] as? UIImage,
              let cgImage = image.cgImage else { return }
        // Run through the same pipeline: detect, perspective-correct, enhance.
        videoQueue.async { [weak self] in
            guard let self else { return }
            let input = downscaled(cgImage)
            let quad = Self.detectQuad(in: input)
            let orientation: CGImagePropertyOrientation = {
                switch image.imageOrientation {
                case .up: return .up
                case .down: return .down
                case .left: return .left
                case .right: return .right
                case .upMirrored: return .upMirrored
                case .downMirrored: return .downMirrored
                case .leftMirrored: return .leftMirrored
                case .rightMirrored: return .rightMirrored
                @unknown default: return .up
                }
            }()
            finish(with: process(image: input, orientation: orientation, quad: quad))
        }
    }

    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        picker.dismiss(animated: true)
    }
}
