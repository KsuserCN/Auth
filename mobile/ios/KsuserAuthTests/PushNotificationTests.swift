import XCTest
import UserNotifications
@testable import KsuserAuth

final class PushNotificationTests: XCTestCase {
    @MainActor func testNotificationResponseFromBackgroundCompletesOnMainThreadWithLogTarget() async {
        AppDelegate.pendingPushAccountID = nil
        AppDelegate.pendingPushEventID = nil
        defer { AppDelegate.pendingPushAccountID = nil; AppDelegate.pendingPushEventID = nil }
        let completed = expectation(description: "Notification response completed on main thread")
        completed.assertForOverFulfill = true
        DispatchQueue.global(qos: .userInitiated).async {
            AppDelegate.handlePushResponse(userInfo: ["kind": "security", "accountId": "account", "eventId": "123"],
                                           actionIdentifier: UNNotificationDefaultActionIdentifier) {
                XCTAssertTrue(Thread.isMainThread)
                completed.fulfill()
            }
        }
        await fulfillment(of: [completed], timeout: 3)
        XCTAssertEqual(AppDelegate.pendingPushAccountID, "account")
        XCTAssertEqual(AppDelegate.pendingPushEventID, 123)
    }

    @MainActor func testNumericNotificationEventIDIsKept() async {
        defer { AppDelegate.pendingPushAccountID = nil; AppDelegate.pendingPushEventID = nil }
        let completed = expectation(description: "Numeric event response completed")
        AppDelegate.handlePushResponse(userInfo: ["kind": "security", "accountId": "account", "eventId": NSNumber(value: 456)],
                                       actionIdentifier: UNNotificationDefaultActionIdentifier) { completed.fulfill() }
        await fulfillment(of: [completed], timeout: 3)
        XCTAssertEqual(AppDelegate.pendingPushEventID, 456)
    }

    @MainActor func testDismissedNotificationCompletesOnMainThreadWithoutOpeningLogs() async {
        AppDelegate.pendingPushAccountID = nil
        AppDelegate.pendingPushEventID = nil
        let completed = expectation(description: "Dismissal completed without navigation")
        completed.assertForOverFulfill = true
        DispatchQueue.global(qos: .userInitiated).async {
            AppDelegate.handlePushResponse(userInfo: ["kind": "security", "accountId": "account", "eventId": "123"],
                                           actionIdentifier: UNNotificationDismissActionIdentifier) {
                XCTAssertTrue(Thread.isMainThread)
                completed.fulfill()
            }
        }
        await fulfillment(of: [completed], timeout: 3)
        XCTAssertNil(AppDelegate.pendingPushAccountID)
        XCTAssertNil(AppDelegate.pendingPushEventID)
    }

    @MainActor private func manager(system: FixturePushSystem, transport: PushTransport, defaults: UserDefaults) -> PushNotificationManager {
        let environment = AppEnvironment(apiBaseURL: URL(string: "https://api.example.invalid")!, passkeyRPID: "auth.example.invalid", qqAppID: "fixture", webURL: URL(string: "https://auth.example.invalid")!)
        let csrf = HTTPCookie(properties: [.name: "XSRF-TOKEN", .value: "csrf-fixture", .domain: "api.example.invalid", .path: "/", .secure: "TRUE"])!
        let client = APIClient(environment: environment, storage: MemorySessionStore(SessionSnapshot(accessToken: "fixture-access", cookies: [StoredCookie(csrf)])), transport: transport)
        return PushNotificationManager(client: client, defaults: defaults, system: system)
    }
    private func preferences() -> UserDefaults { UserDefaults(suiteName: "push-tests.\(UUID().uuidString)")! }

    @MainActor func testModesPersistPerAccountAcrossLogoutAndRestart() async {
        let defaults = preferences(); let system = FixturePushSystem(); let transport = PushTransport()
        let push = manager(system: system, transport: transport, defaults: defaults)
        await push.synchronize(accountID: "first")
        await push.setMode(.loginAndAbnormal)
        push.signOut()
        await push.synchronize(accountID: "second")
        XCTAssertEqual(push.mode, .all)
        await push.setMode(.off)
        let restarted = manager(system: FixturePushSystem(), transport: PushTransport(), defaults: defaults)
        await restarted.synchronize(accountID: "first")
        XCTAssertEqual(restarted.mode, .loginAndAbnormal)
        await restarted.synchronize(accountID: "second")
        XCTAssertEqual(restarted.mode, .off)
    }

    @MainActor func testOffDeletesAfterPendingRegistrationAndImmediatelyDisablesSystemDelivery() async {
        let system = FixturePushSystem(); let transport = PushTransport(gatePost: true)
        let push = manager(system: system, transport: transport, defaults: preferences())
        let registration = Task { await push.synchronize(accountID: "account") }
        await transport.waitForPost()
        let disable = Task { await push.setMode(.off) }
        while push.mode != .off { await Task.yield() }
        XCTAssertNil(system.registeredAccountID)
        await transport.releasePost(); await registration.value; await disable.value
        let requests = await transport.requests
        XCTAssertEqual(requests.map(\.httpMethod), ["POST", "DELETE"])
        XCTAssertEqual(push.statusMessage, "此设备已关闭推送")
        XCTAssertFalse(push.isSyncing)
    }

    @MainActor func testDeniedSystemPermissionDeletesRegistrationWithoutRequestingAgain() async {
        let system = FixturePushSystem(); system.status = .denied; let transport = PushTransport()
        let push = manager(system: system, transport: transport, defaults: preferences())
        await push.synchronize(accountID: "account")
        XCTAssertFalse(system.permissionRequested)
        XCTAssertNil(system.registeredAccountID)
        XCTAssertEqual(push.authorizationStatus, .denied)
        let requests = await transport.requests
        XCTAssertEqual(requests.map(\.httpMethod), ["DELETE"])
    }

    @MainActor func testLogoutDuringPermissionPromptCannotRegisterOrRestoreAccount() async {
        let system = FixturePushSystem(); system.status = .notDetermined; system.gatePermission = true
        let transport = PushTransport(); let push = manager(system: system, transport: transport, defaults: preferences())
        let registration = Task { await push.synchronize(accountID: "account") }
        while !system.permissionRequested { await Task.yield() }
        push.signOut(); system.releasePermission(); await registration.value
        XCTAssertNil(system.registeredAccountID)
        let requests = await transport.requests; XCTAssertTrue(requests.isEmpty)
        XCTAssertEqual(push.statusMessage, "登录后接收账号安全提醒")
        XCTAssertFalse(push.isSyncing)
    }

    @MainActor func testNetworkFailureKeepsPreferenceAndRetriesOnNextSync() async throws {
        let system = FixturePushSystem(); let transport = PushTransport(); let defaults = preferences()
        let push = manager(system: system, transport: transport, defaults: defaults)
        await push.synchronize(accountID: "account")
        await transport.failNextPost()
        await push.setMode(.loginAndAbnormal)
        XCTAssertEqual(push.mode, .loginAndAbnormal)
        XCTAssertTrue(push.statusMessage.contains("待同步"))
        await push.synchronize(accountID: "account", requestPermission: false)
        XCTAssertEqual(push.statusMessage, "已开启：仅登录或异常请求")
        let requests = await transport.requests
        let data = try XCTUnwrap(requests.last?.httpBody)
        let body = try JSONDecoder().decode([String: JSONValue].self, from: data)
        XCTAssertEqual(body["mode"], .string("LOGIN_AND_ABNORMAL"))
        XCTAssertEqual(body["deviceToken"], .string(String(repeating: "a", count: 64)))
        XCTAssertEqual(requests.last?.value(forHTTPHeaderField: "Authorization"), "Bearer fixture-access")
    }
}

@MainActor private final class FixturePushSystem: PushNotificationSystem {
    var deviceToken: String? = String(repeating: "a", count: 64)
    var status: UNAuthorizationStatus = .authorized
    var permissionRequested = false
    var gatePermission = false
    var registeredAccountID: String?
    private var permissionContinuation: CheckedContinuation<Void, Never>?
    func authorizationStatus() async -> UNAuthorizationStatus { status }
    func requestPermission() async throws {
        permissionRequested = true
        if gatePermission { await withCheckedContinuation { permissionContinuation = $0 } }
        status = .authorized
    }
    func releasePermission() { permissionContinuation?.resume(); permissionContinuation = nil }
    func register(accountID: String) { registeredAccountID = accountID }
    func disable() { registeredAccountID = nil }
}

private actor PushTransport: HTTPTransport {
    private(set) var requests: [URLRequest] = []
    private var gatePost: Bool
    private var started = false
    private var failure = false
    private var postContinuation: CheckedContinuation<Void, Never>?
    private var startedContinuation: CheckedContinuation<Void, Never>?
    init(gatePost: Bool = false) { self.gatePost = gatePost }
    func waitForPost() async {
        if !started { await withCheckedContinuation { startedContinuation = $0 } }
    }
    func releasePost() { gatePost = false; postContinuation?.resume(); postContinuation = nil }
    func failNextPost() { failure = true }
    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        requests.append(request)
        if request.httpMethod == "POST" {
            started = true; startedContinuation?.resume(); startedContinuation = nil
            if gatePost { await withCheckedContinuation { postContinuation = $0 } }
            if failure { failure = false; throw URLError(.notConnectedToInternet) }
        }
        let json = request.httpMethod == "POST" ? "{\"code\":200,\"data\":{\"enabled\":true}}" : "{\"code\":200}"
        return (Data(json.utf8), HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: "HTTP/2", headerFields: nil)!)
    }
}
