import AVFoundation
import SwiftUI

/// Barcode and QR scanning for item labels.
///
/// Built on AVFoundation's metadata output rather than VisionKit's
/// `DataScannerViewController`, which needs an A12 or later. A tradesperson's
/// spare site phone is often older than that, and a scanner that silently does
/// not exist on their device is worse than a plain one that always works.
struct BarcodeScannerView: View {

    @Environment(Localization.self) private var loc
    @Environment(\.dismiss) private var dismiss

    let onScan: (String) -> Void

    @State private var authorization = AVCaptureDevice.authorizationStatus(for: .video)

    var body: some View {
        NavigationStack {
            Group {
                switch authorization {
                case .authorized:
                    ScannerRepresentable { code in
                        onScan(code)
                    }
                    .ignoresSafeArea(edges: .bottom)
                case .notDetermined:
                    // The reason comes before the system prompt, in the user's
                    // language, which is what both stores expect.
                    rationale
                default:
                    ContentUnavailableView(
                        loc["perm_camera_title"],
                        systemImage: "camera.fill",
                        description: Text(loc["perm_camera_body"])
                    )
                }
            }
            .navigationTitle(loc["inv_scan"])
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(loc["action_cancel"]) { dismiss() }
                }
            }
        }
    }

    private var rationale: some View {
        VStack(spacing: 16) {
            Image(systemName: "barcode.viewfinder").font(.largeTitle)
            Text(loc["perm_camera_title"]).font(.headline)
            Text(loc["perm_camera_body"])
                .font(.callout)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            Button(loc["perm_allow"]) {
                AVCaptureDevice.requestAccess(for: .video) { _ in
                    Task { @MainActor in
                        authorization = AVCaptureDevice.authorizationStatus(for: .video)
                    }
                }
            }
            .buttonStyle(.borderedProminent)
            Button(loc["perm_not_now"]) { dismiss() }
        }
        .padding(32)
    }
}

private struct ScannerRepresentable: UIViewControllerRepresentable {

    let onScan: (String) -> Void

    func makeUIViewController(context: Context) -> ScannerViewController {
        let controller = ScannerViewController()
        controller.onScan = onScan
        return controller
    }

    func updateUIViewController(_ controller: ScannerViewController, context: Context) {}
}

final class ScannerViewController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {

    var onScan: ((String) -> Void)?

    private let session = AVCaptureSession()
    private var preview: AVCaptureVideoPreviewLayer?
    /// One scan per presentation. Without this the delegate fires on every
    /// frame and the caller is handed the same code dozens of times.
    private var hasReported = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        configureSession()
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        guard !session.isRunning else { return }
        // Starting the session blocks; keeping it off the main thread is what
        // stops the sheet freezing as it appears.
        DispatchQueue.global(qos: .userInitiated).async { [session] in
            session.startRunning()
        }
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        if session.isRunning { session.stopRunning() }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        preview?.frame = view.bounds
    }

    private func configureSession() {
        guard let device = AVCaptureDevice.default(for: .video),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input)
        else { return }
        session.addInput(input)

        let output = AVCaptureMetadataOutput()
        guard session.canAddOutput(output) else { return }
        session.addOutput(output)
        output.setMetadataObjectsDelegate(self, queue: .main)
        // The symbologies actually found on trade packaging and asset labels.
        output.metadataObjectTypes = [.ean13, .ean8, .code128, .code39, .itf14, .qr, .dataMatrix]

        let layer = AVCaptureVideoPreviewLayer(session: session)
        layer.videoGravity = .resizeAspectFill
        layer.frame = view.bounds
        view.layer.addSublayer(layer)
        preview = layer
    }

    func metadataOutput(
        _ output: AVCaptureMetadataOutput,
        didOutput metadataObjects: [AVMetadataObject],
        from connection: AVCaptureConnection
    ) {
        guard !hasReported,
              let object = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              let value = object.stringValue,
              !value.isEmpty
        else { return }

        hasReported = true
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        onScan?(value)
    }
}
