import XCTest
@testable import KsuserAuth

final class QRScanStateTests: XCTestCase {
    @MainActor func testLeavingScannerCancelsPendingPhotoLoadWithoutDeliveringOrDecoding() async throws {
        let state = QRScanState()
        let load = ScanGate<Data?>(started: expectation(description: "Photo load started"))
        let recorder = ScanRecorder()
        state.activate()
        let task = try XCTUnwrap(state.readPhoto(load: { try await load.wait() }, decode: { data in await recorder.decode(data) }, onDetected: { recorder.detected.append($0) }))
        await fulfillment(of: [load.started], timeout: 2)
        XCTAssertTrue(state.processing)
        state.deactivate()
        XCTAssertTrue(task.isCancelled)
        XCTAssertFalse(state.processing)
        load.succeed(Data([1]))
        await task.value
        XCTAssertEqual(recorder.decodeCount, 0)
        XCTAssertTrue(recorder.detected.isEmpty)
        XCTAssertNil(state.error)
    }

    @MainActor func testOldDecodeCannotDeliverOrClearProcessingAfterScannerReopens() async throws {
        let state = QRScanState()
        let oldDecode = ScanGate<[String]>(started: expectation(description: "Old decode started"))
        let newDecode = ScanGate<[String]>(started: expectation(description: "New decode started"))
        var detected: [String] = []
        state.activate()
        let oldTask = try XCTUnwrap(state.readPhoto(load: { Data([1]) }, decode: { _ in try await oldDecode.wait() }, onDetected: { detected.append($0) }))
        await fulfillment(of: [oldDecode.started], timeout: 2)
        state.deactivate()
        state.activate()
        let newTask = try XCTUnwrap(state.readPhoto(load: { Data([2]) }, decode: { _ in try await newDecode.wait() }, onDetected: { detected.append($0) }))
        await fulfillment(of: [newDecode.started], timeout: 2)
        // Vision and some item providers may finish despite cancellation.
        oldDecode.succeed(["old-code"])
        await oldTask.value
        XCTAssertTrue(state.processing)
        XCTAssertTrue(detected.isEmpty)
        XCTAssertNil(state.error)
        newDecode.succeed(["new-code"])
        await newTask.value
        XCTAssertEqual(detected, ["new-code"])
        XCTAssertFalse(state.processing)
    }

    @MainActor func testCameraDeliveryCancelsPhotoAndOnlyDeliversOnce() async throws {
        let state = QRScanState()
        let decode = ScanGate<[String]>(started: expectation(description: "Photo decode started"))
        var detected: [String] = []
        state.activate()
        let task = try XCTUnwrap(state.readPhoto(load: { Data([1]) }, decode: { _ in try await decode.wait() }, onDetected: { detected.append($0) }))
        await fulfillment(of: [decode.started], timeout: 2)
        state.deliver("camera-code", onDetected: { detected.append($0) })
        state.deliver("duplicate-code", onDetected: { detected.append($0) })
        decode.succeed(["photo-code"])
        await task.value
        XCTAssertEqual(detected, ["camera-code"])
        XCTAssertTrue(task.isCancelled)
        XCTAssertNil(state.error)
    }

    @MainActor func testEmptyPhotoResultCanBeRetried() async throws {
        let state = QRScanState()
        var detected: [String] = []
        state.activate()
        let empty = try XCTUnwrap(state.readPhoto(load: { Data([1]) }, decode: { _ in [] }, onDetected: { detected.append($0) }))
        await empty.value
        XCTAssertNotNil(state.error)
        XCTAssertFalse(state.processing)
        let retry = try XCTUnwrap(state.readPhoto(load: { Data([2]) }, decode: { _ in ["valid-code"] }, onDetected: { detected.append($0) }))
        await retry.value
        XCTAssertNil(state.error)
        XCTAssertEqual(detected, ["valid-code"])
    }

    @MainActor func testLateFailureFromClosedScannerDoesNotAppearAfterReopening() async throws {
        let state = QRScanState()
        let decode = ScanGate<[String]>(started: expectation(description: "Decode started"))
        state.activate()
        let task = try XCTUnwrap(state.readPhoto(load: { Data([1]) }, decode: { _ in try await decode.wait() }, onDetected: { _ in XCTFail("Closed scanner delivered a result") }))
        await fulfillment(of: [decode.started], timeout: 2)
        state.deactivate()
        state.activate()
        decode.fail(URLError(.cannotDecodeContentData))
        await task.value
        XCTAssertNil(state.error)
        XCTAssertFalse(state.processing)
    }
}

@MainActor private final class ScanRecorder {
    var decodeCount = 0
    var detected: [String] = []
    func decode(_ data: Data) -> [String] { decodeCount += 1; return ["decoded"] }
}

@MainActor private final class ScanGate<Value> {
    let started: XCTestExpectation
    private var continuation: CheckedContinuation<Value, Error>?
    init(started: XCTestExpectation) { self.started = started }
    func wait() async throws -> Value {
        try await withCheckedThrowingContinuation { continuation in
            self.continuation = continuation
            started.fulfill()
        }
    }
    func succeed(_ value: Value) { continuation?.resume(returning: value); continuation = nil }
    func fail(_ error: Error) { continuation?.resume(throwing: error); continuation = nil }
}
