import AVFoundation
import CoreImage.CIFilterBuiltins
import SwiftUI
import UIKit
import Vision
import ImageIO

enum LocalQRCode {
    static func image(from text: String) -> UIImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(text.utf8)
        filter.correctionLevel = "M"
        guard let code = filter.outputImage else { return nil }
        let background = CIImage(color: .white).cropped(to: code.extent.insetBy(dx: -4, dy: -4))
        let output = code.composited(over: background).transformed(by: CGAffineTransform(scaleX: 8, y: 8))
        guard let cg = CIContext().createCGImage(output, from: output.extent) else { return nil }
        return UIImage(cgImage: cg)
    }
}

enum QRImageDecoder {
    static func decode(_ data: Data) throws -> [String] {
        let request = VNDetectBarcodesRequest()
        #if targetEnvironment(simulator)
        request.revision = VNDetectBarcodesRequestRevision3
        request.usesCPUOnly = true
        #endif
        request.symbologies = [.qr]
        var visionError: Error?
        do {
            try VNImageRequestHandler(data: data, options: [:]).perform([request])
            let values = request.results?.compactMap(\.payloadStringValue) ?? []
            if !values.isEmpty { return values }
        } catch { visionError = error }
        // Preserve local reading if a runtime's Vision engine fails or misses a QR.
        guard let image = CIImage(data: data),
              let detector = CIDetector(ofType: CIDetectorTypeQRCode, context: CIContext(options: [.useSoftwareRenderer: true]), options: [CIDetectorAccuracy: CIDetectorAccuracyHigh]) else {
            if let visionError { throw visionError }; return []
        }
        let orientation = image.properties[kCGImagePropertyOrientation as String] as? Int ?? 1
        let values = detector.features(in: image, options: [CIDetectorImageOrientation: orientation]).compactMap { ($0 as? CIQRCodeFeature)?.messageString }
        if values.isEmpty, let visionError { throw visionError }
        return values
    }
}

struct QRScannerView: UIViewControllerRepresentable {
    let onDetected: (String) -> Void
    let onError: (String) -> Void
    func makeUIViewController(context: Context) -> ScannerController { ScannerController(onDetected: onDetected, onError: onError) }
    func updateUIViewController(_ uiViewController: ScannerController, context: Context) {}
    static func dismantleUIViewController(_ controller: ScannerController, coordinator: ()) { controller.stop() }
}

final class CameraSession: @unchecked Sendable {
    let session = AVCaptureSession()
    let queue = DispatchQueue(label: "cn.ksuser.auth.camera")
    func stop() { queue.async { [self] in if session.isRunning { session.stopRunning() } } }
}

@MainActor final class ScannerController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    private let camera = CameraSession()
    private let onDetected: (String) -> Void
    private let onError: (String) -> Void
    private var preview: AVCaptureVideoPreviewLayer?
    private var delivered = false
    private var active = true
    init(onDetected: @escaping (String) -> Void, onError: @escaping (String) -> Void) {
        self.onDetected = onDetected; self.onError = onError; super.init(nibName: nil, bundle: nil)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) is unavailable") }
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        Task {
            let permission = AVCaptureDevice.authorizationStatus(for: .video)
            var granted = permission == .authorized
            if permission == .notDetermined { granted = await AVCaptureDevice.requestAccess(for: .video) }
            guard active else { return }
            guard granted else { onError("请在系统设置中允许相机权限，也可以从相册选择二维码"); return }
            configure()
        }
    }
    private func configure() {
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) ?? AVCaptureDevice.default(for: .video) else { onError("当前设备没有可用相机，请从相册选择二维码"); return }
        do {
            let input = try AVCaptureDeviceInput(device: device)
            let output = AVCaptureMetadataOutput()
            camera.session.beginConfiguration()
            guard camera.session.canAddInput(input), camera.session.canAddOutput(output) else { camera.session.commitConfiguration(); onError("无法启动相机"); return }
            camera.session.addInput(input); camera.session.addOutput(output)
            output.setMetadataObjectsDelegate(self, queue: .main)
            output.metadataObjectTypes = [.qr]
            camera.session.commitConfiguration()
            let preview = AVCaptureVideoPreviewLayer(session: camera.session)
            preview.videoGravity = .resizeAspectFill; view.layer.addSublayer(preview); self.preview = preview
            view.setNeedsLayout()
            camera.queue.async { [camera] in camera.session.startRunning() }
        } catch { onError("相机启动失败：\(error.localizedDescription)") }
    }
    override func viewDidLayoutSubviews() { super.viewDidLayoutSubviews(); preview?.frame = view.bounds }
    func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
        guard active, !delivered, let code = metadataObjects.compactMap({ ($0 as? AVMetadataMachineReadableCodeObject)?.stringValue }).first else { return }
        delivered = true; camera.stop(); onDetected(code)
    }
    func stop() { active = false; camera.stop() }
}
