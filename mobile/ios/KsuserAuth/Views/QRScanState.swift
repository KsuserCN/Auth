import Foundation
import Observation

@MainActor @Observable final class QRScanState {
    private(set) var processing = false
    private(set) var error: LocalizedMessage?
    @ObservationIgnored private var photoTask: Task<Void, Never>?
    @ObservationIgnored private var generation = UUID()
    @ObservationIgnored private var active = false
    @ObservationIgnored private var delivered = false

    func activate() {
        deactivate()
        active = true
        delivered = false
        error = nil
    }

    func deactivate() {
        active = false
        generation = UUID()
        photoTask?.cancel()
        photoTask = nil
        processing = false
    }

    func report(_ error: LocalizedMessage) {
        guard active, !delivered else { return }
        self.error = error
    }

    func deliver(_ value: String, onDetected: (String) -> Void) {
        guard active, !delivered else { return }
        delivered = true
        deactivate()
        onDetected(value)
    }

    @discardableResult
    func readPhoto(load: @MainActor @escaping () async throws -> Data?,
                   decode: @escaping @Sendable (Data) async throws -> [String] = { try await QRScanState.decodeImage($0) },
                   onDetected: @MainActor @escaping (String) -> Void) -> Task<Void, Never>? {
        guard active, !delivered else { return nil }
        photoTask?.cancel()
        generation = UUID()
        let requestID = generation
        processing = true
        error = nil
        let task = Task { [weak self] in
            defer {
                if let self, self.generation == requestID {
                    self.processing = false
                    self.photoTask = nil
                }
            }
            do {
                guard let data = try await load() else { throw ScanError.noCode }
                try Task.checkCancellation()
                let codes = try await decode(data)
                try Task.checkCancellation()
                guard let self, self.active, self.generation == requestID else { return }
                guard let result = codes.first else { throw ScanError.noCode }
                self.deliver(result, onDetected: onDetected)
            } catch {
                guard let self, self.active, self.generation == requestID,
                      !Task.isCancelled, !(error is CancellationError) else { return }
                self.error = LocalizedMessage(error: error)
            }
        }
        photoTask = task
        return task
    }

    nonisolated static func decodeImage(_ data: Data) async throws -> [String] {
        let worker = Task.detached(priority: .userInitiated) {
            try Task.checkCancellation()
            let codes = try QRImageDecoder.decode(data)
            try Task.checkCancellation()
            return codes
        }
        return try await withTaskCancellationHandler(operation: { try await worker.value }, onCancel: { worker.cancel() })
    }

    private enum ScanError: LocalizedError {
        case noCode
        var errorDescription: String? { L10n.text("没有找到可识别的二维码，请选择另一张图片。") }
    }
}
