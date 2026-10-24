import XCTest
@testable import KsuserAuth

final class AppModelRefreshTests: XCTestCase {
    private let environment = AppEnvironment(apiBaseURL: URL(string: "https://api.example.invalid")!, passkeyRPID: "auth.example.invalid", qqAppID: "fixture", webURL: URL(string: "https://auth.example.invalid")!)

    @MainActor private func model(_ transport: RefreshFixtureTransport, showCachedUser: Bool = true) -> AppModel {
        let profile = UserProfile(uuid: "a", username: "account_a", email: "a@example.invalid")
        let csrf = HTTPCookie(properties: [.name: "XSRF-TOKEN", .value: "csrf-fixture", .domain: "api.example.invalid", .path: "/", .secure: "TRUE"])!
        let snapshot = SessionSnapshot(accessToken: "a", cookies: [StoredCookie(csrf)], profile: profile)
        let client = APIClient(environment: environment, storage: MemorySessionStore(snapshot), transport: transport)
        let model = AppModel(native: RefreshFixtureNative(), repository: KsuserRepository(client: client), environment: environment)
        if showCachedUser { model.user = profile }
        return model
    }

    @MainActor func testAccountSwitchDoesNotReuseTOTPWhenNewAccountStatusFails() async {
        let transport = RefreshFixtureTransport(failNewTOTP: true)
        let model = model(transport)
        await model.refreshSecurity()
        XCTAssertTrue(model.totpStatusLoaded)
        XCTAssertTrue(model.totpStatus.enabled)
        XCTAssertEqual(model.totpStatus.recoveryCodesCount, 8)

        await model.login(email: "b@example.invalid", password: "fixture")

        XCTAssertEqual(model.user?.uuid, "b")
        XCTAssertFalse(model.totpStatusLoaded)
        XCTAssertFalse(model.totpStatus.enabled)
        XCTAssertEqual(model.totpStatus.recoveryCodesCount, 0)
        XCTAssertNotNil(model.errorMessage)
    }

    @MainActor func testAccountSwitchDiscardsPartialOldSecurityRefresh() async {
        let transport = RefreshFixtureTransport(failNewTOTP: true, gates: ["a|/oauth/accounts/status"])
        let model = model(transport)
        let oldRefresh = Task { await model.refreshSecurity() }
        await transport.waitForRequest("a|/oauth/accounts/status")
        await transport.waitForResponses(["a|/auth/passkey/list", "a|/auth/totp/status", "a|/auth/adaptive-auth/status", "a|/oauth/apple/status"])
        await model.login(email: "b@example.invalid", password: "fixture")

        await transport.release("a|/oauth/accounts/status")
        await oldRefresh.value

        XCTAssertEqual(model.user?.uuid, "b")
        XCTAssertEqual(model.passkeys.map(\.name), ["b-key"])
        XCTAssertFalse(model.totpStatusLoaded)
        XCTAssertFalse(model.totpStatus.enabled)
        XCTAssertEqual(model.totpStatus.recoveryCodesCount, 0)
        XCTAssertFalse(model.appleBound)
        XCTAssertFalse(model.qqBound)
    }

    @MainActor func testConcurrentDashboardLoadsShareRequestsAndManualRefreshReloads() async {
        let transport = RefreshFixtureTransport(gates: ["a|/auth/info", "a|/auth/totp/status"])
        let model = model(transport)
        var started = 0
        let tasks = (0..<8).map { _ in
            Task {
                started += 1
                await model.loadDashboard(forceRefresh: false)
            }
        }
        while started < tasks.count { await Task.yield() }
        await transport.waitForRequest("a|/auth/info")
        await transport.release("a|/auth/info")
        await transport.waitForRequest("a|/auth/totp/status")
        await transport.release("a|/auth/totp/status")
        for task in tasks { await task.value }
        let initialCounts = await transport.counts
        XCTAssertEqual(initialCounts["a|/auth/info"], 1)
        XCTAssertEqual(initialCounts["a|/auth/totp/status"], 1)
        XCTAssertEqual(initialCounts["a|/auth/passkey/list"], 1)

        await model.loadDashboard(forceRefresh: false)
        let reusedCounts = await transport.counts
        XCTAssertEqual(reusedCounts, initialCounts)

        await model.loadDashboard()
        let manualCounts = await transport.counts
        XCTAssertEqual(manualCounts["a|/auth/info"], 2)
        XCTAssertEqual(manualCounts["a|/auth/totp/status"], 2)
        XCTAssertEqual(manualCounts["a|/auth/passkey/list"], 2)
    }

    @MainActor func testRestoreAndHomepageShareSingleProfileAndSecurityRefresh() async {
        let transport = RefreshFixtureTransport(gates: ["a|/auth/info"])
        let model = model(transport, showCachedUser: false)
        let restoration = Task { await model.restoreSession() }
        await transport.waitForRequest("a|/auth/info")
        let homepage = Task { await model.loadDashboard(forceRefresh: false) }
        await transport.release("a|/auth/info")
        await restoration.value
        await homepage.value
        let counts = await transport.counts
        XCTAssertEqual(counts["a|/auth/info"], 1)
        XCTAssertEqual(counts["a|/auth/totp/status"], 1)
        XCTAssertTrue(model.totpStatusLoaded)
    }

    @MainActor func testLoginAndHomepageReuseStagedProfileAndSecurityRefresh() async {
        let transport = RefreshFixtureTransport(gates: ["b|/auth/totp/status"])
        let model = model(transport)
        let login = Task { await model.login(email: "b@example.invalid", password: "fixture") }
        await transport.waitForRequest("b|/auth/totp/status")
        XCTAssertEqual(model.user?.uuid, "b")
        let homepage = Task { await model.loadDashboard(forceRefresh: false) }
        await transport.release("b|/auth/totp/status")
        await login.value
        await homepage.value
        let counts = await transport.counts
        XCTAssertEqual(counts["b|/auth/info"], 1)
        XCTAssertEqual(counts["b|/auth/totp/status"], 1)
    }

    @MainActor func testOldProfileFailureCannotShowDuringNewAuthentication() async {
        let transport = RefreshFixtureTransport(gates: ["a|/auth/info", "a|/auth/login"])
        let model = model(transport)
        let oldRefresh = Task { await model.refreshProfile() }
        await transport.waitForRequest("a|/auth/info")
        let login = Task { await model.login(email: "b@example.invalid", password: "fixture") }
        await transport.waitForRequest("a|/auth/login")
        // The live APIClient still has account A here; only AppModel's authentication
        // generation has changed, so it must suppress this stale network failure.
        await transport.release("a|/auth/info", error: URLError(.timedOut))
        await oldRefresh.value
        XCTAssertNil(model.errorMessage)
        await transport.release("a|/auth/login")
        await login.value
        XCTAssertEqual(model.user?.uuid, "b")
    }

    @MainActor func testFetchLogUsesIDWithoutChangingListAndDiscardsOldAccountResult() async {
        let transport = RefreshFixtureTransport(gates: ["a|/auth/sensitive-logs/1000"])
        let model = model(transport)
        model.logsPage = 7
        let log = await model.fetchLog(id: 999)
        XCTAssertEqual(log?.id, 999)
        XCTAssertTrue(model.logs.isEmpty)
        XCTAssertEqual(model.logsPage, 7)
        let counts = await transport.counts
        XCTAssertNil(counts["a|/auth/sensitive-logs"])

        let pendingLog = Task { await model.fetchLog(id: 1000) }
        await transport.waitForRequest("a|/auth/sensitive-logs/1000")
        await model.login(email: "b@example.invalid", password: "fixture")
        await transport.release("a|/auth/sensitive-logs/1000")
        let staleLog = await pendingLog.value
        XCTAssertNil(staleLog)
        XCTAssertEqual(model.user?.uuid, "b")
        XCTAssertNil(model.errorMessage)
    }
}

private actor RefreshFixtureTransport: HTTPTransport {
    private let failNewTOTP: Bool
    private var gatedKeys: Set<String>
    private var gates: [String: CheckedContinuation<Void, Error>] = [:]
    private var responses: Set<String> = []
    private(set) var counts: [String: Int] = [:]

    init(failNewTOTP: Bool = false, gates: Set<String> = []) {
        self.failNewTOTP = failNewTOTP
        gatedKeys = gates
    }
    func waitForRequest(_ key: String) async { while gates[key] == nil { await Task.yield() } }
    func waitForResponses(_ keys: Set<String>) async { while !keys.isSubset(of: responses) { await Task.yield() } }
    func release(_ key: String, error: Error? = nil) {
        gatedKeys.remove(key)
        let continuation = gates.removeValue(forKey: key)
        if let error { continuation?.resume(throwing: error) }
        else { continuation?.resume() }
    }
    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let path = request.url!.path
        let account = request.value(forHTTPHeaderField: "Authorization") == "Bearer b" ? "b" : "a"
        let key = account + "|" + path
        counts[key, default: 0] += 1
        if gatedKeys.contains(key) { try await withCheckedThrowingContinuation { gates[key] = $0 } }
        let body: String
        var status = 200
        var headers: [String: String]?
        switch path {
        case "/auth/csrf-token":
            body = #"{"code":200,"data":{"csrfToken":"csrf-fixture"}}"#
            headers = ["Set-Cookie": "XSRF-TOKEN=csrf-fixture; Path=/; Secure"]
        case "/auth/login": body = #"{"code":200,"data":{"accessToken":"b"}}"#
        case "/auth/info":
            body = "{\"code\":200,\"data\":{\"uuid\":\"\(account)\",\"username\":\"account_\(account)\",\"email\":\"\(account)@example.invalid\"}}"
        case "/auth/passkey/list":
            body = "{\"code\":200,\"data\":{\"passkeys\":[{\"id\":1,\"name\":\"\(account)-key\",\"createdAt\":\"2026-10-24T12:00:00\"}]}}"
        case "/auth/totp/status":
            if account == "b", failNewTOTP { status = 403; body = #"{"code":403,"errorCode":"FORBIDDEN"}"# }
            else { body = "{\"code\":200,\"data\":{\"enabled\":\(account == "a"),\"recoveryCodesCount\":\(account == "a" ? 8 : 0)}}" }
        case "/auth/adaptive-auth/status":
            body = #"{"code":200,"data":{"riskScore":10,"riskLevel":"low","trusted":true,"requiresStepUp":false,"sessionFrozen":false,"sensitiveVerified":false,"sensitiveVerificationRemainingSeconds":0,"authAgeSeconds":0,"idleSeconds":0,"multiEndpointAlert":false,"alertRemainingSeconds":0}}"#
        case "/oauth/apple/status": body = "{\"code\":200,\"data\":{\"bound\":\(account == "a")}}"
        case "/oauth/accounts/status": body = "{\"code\":200,\"data\":[{\"provider\":\"qq\",\"bound\":\(account == "a")}]}"
        default:
            guard path.hasPrefix("/auth/sensitive-logs/"), let id = Int64(path.split(separator: "/").last ?? "") else { throw APIError.invalidResponse }
            body = "{\"code\":200,\"data\":{\"id\":\(id),\"operationType\":\"LOGIN\",\"ipAddress\":\"192.0.2.1\",\"result\":\"success\",\"riskScore\":0,\"triggeredMultiErrorLock\":false,\"triggeredRateLimitLock\":false,\"createdAt\":\"2026-10-24T12:00:00\"}}"
        }
        responses.insert(key)
        return (Data(body.utf8), HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil, headerFields: headers)!)
    }
}

@MainActor private final class RefreshFixtureNative: NativeAuthenticationProviding {
    func authenticatePasskey(options: PasskeyAuthenticationOptions) async throws -> PasskeyAuthenticationPayload { throw APIError.cancelled }
    func registerPasskey(options: PasskeyRegistrationOptions) async throws -> PasskeyRegistrationPayload { throw APIError.cancelled }
    func signInWithApple(challenge: AppleChallenge) async throws -> AppleCredential { throw APIError.cancelled }
    func signInWithQQ() async throws -> QQCredential { throw APIError.cancelled }
    func appleCredentialRevoked() async -> Bool { false }
    func acceptAppleCredential() throws { }
    func clearAppleCredential() throws { }
}
