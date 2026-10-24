import XCTest
@testable import KsuserAuth

@MainActor final class ServiceAvailabilityTests: XCTestCase {
    func testConnectivityFailuresRemainLocalToTheRequest() async {
        for code in [URLError.notConnectedToInternet, .timedOut, .networkConnectionLost, .cancelled] {
            let monitor = ServiceAvailabilityMonitor()
            let client = APIClient(storage: MemorySessionStore(), transport: AvailabilityTransport(error: URLError(code)), availabilityMonitor: monitor)
            do {
                let _: JSONValue = try await client.request("/info/password-requirement", authenticated: false)
                XCTFail("Expected network failure")
            } catch {
                XCTAssertEqual((error as? URLError)?.code, code)
            }
            XCTAssertFalse(monitor.isUnavailable)
        }
    }

    func testServerFailureCanRecoverWithoutReplacingTheSession() async throws {
        let monitor = ServiceAvailabilityMonitor()
        let transport = AvailabilityTransport(statuses: [503, 200])
        let snapshot = SessionSnapshot(accessToken: "fixture-access")
        let client = APIClient(storage: MemorySessionStore(snapshot), transport: transport, availabilityMonitor: monitor)
        do {
            let _: JSONValue = try await client.request("/info/password-requirement", authenticated: false)
            XCTFail("Expected server failure")
        } catch APIError.server(503, _) { }
        XCTAssertTrue(monitor.isUnavailable)
        let token = await client.snapshot().accessToken
        XCTAssertEqual(token, "fixture-access")
        let _: JSONValue = try await client.request("/info/password-requirement", authenticated: false)
        XCTAssertFalse(monitor.isUnavailable)
    }

    func testLateNetworkFailureAfterAccountReplacementIsCancelled() async throws {
        let transport = LateFailureTransport()
        let client = APIClient(storage: MemorySessionStore(SessionSnapshot(accessToken: "old-account")), transport: transport)
        let request = Task { let _: JSONValue = try await client.request("/auth/info") }
        await transport.waitForRequest()
        try await client.replaceSession(SessionSnapshot(accessToken: "new-account"))
        await transport.fail()
        do {
            try await request.value
            XCTFail("Expected stale request cancellation")
        } catch APIError.cancelled { }
        let token = await client.snapshot().accessToken
        XCTAssertEqual(token, "new-account")
    }
}

private actor LateFailureTransport: HTTPTransport {
    private var started = false
    private var waiter: CheckedContinuation<Void, Never>?
    private var response: CheckedContinuation<(Data, HTTPURLResponse), Error>?
    func waitForRequest() async {
        if started { return }
        await withCheckedContinuation { waiter = $0 }
    }
    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        started = true
        waiter?.resume(); waiter = nil
        return try await withCheckedThrowingContinuation { response = $0 }
    }
    func fail() { response?.resume(throwing: URLError(.timedOut)); response = nil }
}

private actor AvailabilityTransport: HTTPTransport {
    private let error: URLError?
    private var statuses: [Int]
    init(error: URLError) { self.error = error; statuses = [] }
    init(statuses: [Int]) { error = nil; self.statuses = statuses }
    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        if let error { throw error }
        let status = statuses.removeFirst()
        let body = status == 200 ? #"{"code":200,"data":{}}"# : "{\"code\":\(status)}"
        return (Data(body.utf8), HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil, headerFields: nil)!)
    }
}
