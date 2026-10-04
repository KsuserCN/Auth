import XCTest
@testable import KsuserAuth

final class CoreTests: XCTestCase {
    @MainActor func testLoginLoadingCoversNestedRequestsAndIgnoresDuplicateLogin() async {
        let transport = LoadingGateTransport(mode: .scanAfterLogin, paths: ["/auth/login", "/auth/totp/status"])
        let client = APIClient(environment: environment, storage: MemorySessionStore(), transport: transport)
        let model = AppModel(native: FixtureNative(), repository: KsuserRepository(client: client), environment: environment)
        let login = Task { await model.login(email: "fixture@example.invalid", password: "fixture") }
        await transport.waitForRequest("/auth/login")
        XCTAssertTrue(model.isBusy)
        XCTAssertEqual(model.loadingActivity?.message, "正在登录…")
        let activityID = model.loadingActivity?.id
        await model.login(email: "fixture@example.invalid", password: "fixture")
        let loginRequests = await transport.requestCount("/auth/login")
        XCTAssertEqual(loginRequests, 1)
        await transport.release("/auth/login")
        await transport.waitForRequest("/auth/totp/status")
        XCTAssertTrue(model.isAuthenticated)
        XCTAssertTrue(model.isBusy)
        XCTAssertEqual(model.loadingActivity?.id, activityID)
        await transport.release("/auth/totp/status")
        await login.value
        XCTAssertFalse(model.isBusy)
        XCTAssertNil(model.loadingActivity)
        XCTAssertNil(model.errorMessage)
    }

    @MainActor func testLoadingEndsAfterFailureAndCancellation() async {
        for cancellation in [false, true] {
            let transport = LoadingGateTransport(mode: .scanAfterLogin, paths: ["/auth/login"])
            let model = AppModel(native: FixtureNative(), repository: KsuserRepository(client: APIClient(environment: environment, storage: MemorySessionStore(), transport: transport)), environment: environment)
            let login = Task { await model.login(email: "fixture@example.invalid", password: "fixture") }
            await transport.waitForRequest("/auth/login")
            XCTAssertTrue(model.isBusy)
            await transport.release("/auth/login", error: cancellation ? CancellationError() : URLError(.timedOut))
            await login.value
            XCTAssertFalse(model.isBusy)
            XCTAssertNil(model.loadingActivity)
            XCTAssertFalse(model.isAuthenticated)
            XCTAssertEqual(model.errorMessage == nil, cancellation)
        }
    }

    @MainActor func testConcurrentLoadingKeepsRemainingOperationVisible() async {
        let transport = LoadingGateTransport(mode: .normalizedInput, paths: ["/auth/send-code", "/auth/logout"])
        let model = AppModel(native: FixtureNative(), repository: KsuserRepository(client: APIClient(environment: environment, storage: MemorySessionStore(snapshot(token: "fixture-access")), transport: transport)), environment: environment)
        model.user = UserProfile(uuid: "fixture", username: "fixture", email: "fixture@example.invalid")
        let send = Task { await model.sendCode(email: "fixture@example.invalid", type: "login") }
        await transport.waitForRequest("/auth/send-code")
        let logout = Task { await model.logout() }
        await transport.waitForRequest("/auth/logout")
        await model.logout()
        let logoutRequests = await transport.requestCount("/auth/logout")
        XCTAssertEqual(logoutRequests, 1)
        await transport.release("/auth/send-code")
        await send.value
        XCTAssertTrue(model.isBusy)
        XCTAssertEqual(model.loadingActivity?.message, "正在退出登录…")
        await transport.release("/auth/logout")
        await logout.value
        XCTAssertFalse(model.isBusy)
        XCTAssertNil(model.loadingActivity)
        XCTAssertFalse(model.isAuthenticated)
    }

    func testDateDisplayKeepsLocalTimeAndRemovesISOSeparator() {
        XCTAssertEqual(displayDate("2026-10-04T10:02:03"), "2026-10-04 10:02:03")
        XCTAssertEqual(displayDate("2026-10-04T10:02:03.123456789"), "2026-10-04 10:02:03.123456789")
        XCTAssertEqual(displayDate("2026-10-04 10:02:03"), "2026-10-04 10:02:03")
        XCTAssertEqual(displayDate(nil), "暂无记录")
        XCTAssertEqual(displayDate("  "), "暂无记录")
        XCTAssertNotNil(displayDate("2026-10-04T10:02:03.123Z").range(of: #"^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$"#, options: .regularExpression))
    }

    private var environment: AppEnvironment {
        AppEnvironment(apiBaseURL: URL(string: "https://api.ksuser.cn")!, passkeyRPID: "auth.ksuser.cn", qqAppID: "test-only", webURL: URL(string: "https://auth.ksuser.cn")!)
    }
    private func snapshot(token: String = "expired") -> SessionSnapshot {
        let csrf = HTTPCookie(properties: [.name: "XSRF-TOKEN", .value: "csrf-fixture", .domain: "api.ksuser.cn", .path: "/", .secure: "TRUE"])!
        let refresh = HTTPCookie(properties: [.name: "refreshToken", .value: "refresh-fixture", .domain: "api.ksuser.cn", .path: "/auth", .secure: "TRUE"])!
        return SessionSnapshot(accessToken: token, cookies: [StoredCookie(csrf), StoredCookie(refresh)])
    }
    func testConcurrentUnauthorizedRequestsRefreshOnce() async throws {
        let transport = FixtureTransport(mode: .refreshSucceeds)
        let client = APIClient(environment: environment, storage: MemorySessionStore(snapshot()), transport: transport)
        try await withThrowingTaskGroup(of: JSONValue.self) { group in
            for _ in 0..<20 { group.addTask { try await client.request("/auth/fixture") } }
            for try await value in group { XCTAssertEqual(value.objectValue?["ok"], .bool(true)) }
        }
        let counters = await transport.counters()
        XCTAssertEqual(counters.refreshes, 1)
        let stored = await client.snapshot(); XCTAssertEqual(stored.accessToken, "fresh")
    }
    func testReplayedRequestStopsAfterOneReplayAndClearsExpiredSession() async throws {
        let transport = FixtureTransport(mode: .replayUnauthorized)
        let client = APIClient(environment: environment, storage: MemorySessionStore(snapshot()), transport: transport)
        do { let _: JSONValue = try await client.request("/auth/fixture"); XCTFail("Expected expiration") }
        catch let error as APIError { XCTAssertTrue(error.isUnauthorized) }
        let counters = await transport.counters()
        XCTAssertEqual(counters.refreshes, 1); XCTAssertEqual(counters.protectedCalls, 2)
        let hasSession = await client.hasSession(); XCTAssertFalse(hasSession)
    }
    func testOfflineRefreshPreservesCredentials() async throws {
        let original = snapshot()
        let client = APIClient(environment: environment, storage: MemorySessionStore(original), transport: FixtureTransport(mode: .offlineRefresh))
        do { let _: JSONValue = try await client.request("/auth/fixture"); XCTFail("Expected temporary network failure") }
        catch let error as URLError { XCTAssertEqual(error.code, .notConnectedToInternet) }
        let stored = await client.snapshot()
        XCTAssertEqual(stored.accessToken, original.accessToken)
        XCTAssertEqual(stored.cookies.map(\.value), original.cookies.map(\.value))
    }
    func testDefinitiveRefresh401ClearsSessionButCSRF403KeepsIt() async throws {
        for forbidden in [false, true] {
            let client = APIClient(environment: environment, storage: MemorySessionStore(snapshot()), transport: FixtureTransport(mode: forbidden ? .forbiddenRefresh : .invalidRefresh))
            do { let _: JSONValue = try await client.request("/auth/fixture"); XCTFail("Refresh must fail") }
            catch let error as APIError {
                guard case .server(let code, _) = error else { return XCTFail("Expected server error") }
                XCTAssertEqual(code, forbidden ? 403 : 401)
            }
            let hasSession = await client.hasSession(); XCTAssertEqual(hasSession, forbidden)
        }
    }
    func testLogoutWhileRefreshIsInFlightCannotResurrectSession() async throws {
        let transport = FixtureTransport(mode: .refreshSuspends)
        let client = APIClient(environment: environment, storage: MemorySessionStore(snapshot()), transport: transport)
        let request = Task { let _: JSONValue = try await client.request("/auth/fixture") }
        await transport.waitForRefresh()
        try await client.clearSession()
        await transport.releaseRefresh()
        do { try await request.value; XCTFail("Cancelled session must not complete") }
        catch APIError.cancelled {} catch is CancellationError {}
        let stored = await client.snapshot(); XCTAssertNil(stored.accessToken); XCTAssertTrue(stored.cookies.isEmpty)
    }
    func testMFAAndPendingAppleBindingAreNotLoggedInSessions() async throws {
        let mfaClient = APIClient(environment: environment, storage: MemorySessionStore(), transport: FixtureTransport(mode: .mfaLogin))
        let mfa = try await KsuserRepository(client: mfaClient).login(email: "fixture@example.invalid", password: "fixture-only")
        guard case .needsMFA(let challenge) = mfa else { return XCTFail("Expected MFA") }
        XCTAssertEqual(challenge.methods, ["totp", "passkey"])
        let mfaSnapshot = await mfaClient.snapshot(); XCTAssertNil(mfaSnapshot.accessToken)
        let appleClient = APIClient(environment: environment, storage: MemorySessionStore(), transport: FixtureTransport(mode: .pendingApple))
        let pending = try await KsuserRepository(client: appleClient).loginWithApple(challenge: AppleChallenge(challengeId: "fixture", nonce: "nonce", state: "state", expiresInSeconds: 300), credential: AppleCredential(identityToken: "fixture", authorizationCode: "fixture"))
        guard case .needsOAuth(let ticket) = pending else { return XCTFail("Expected pending Apple identity") }
        XCTAssertTrue(ticket.emailConflict); XCTAssertFalse(ticket.canRegister)
        let appleSnapshot = await appleClient.snapshot(); XCTAssertNil(appleSnapshot.accessToken)
    }
    func testCSRFBootstrapIsSentWithMutation() async throws {
        let transport = FixtureTransport(mode: .csrf)
        let client = APIClient(environment: environment, storage: MemorySessionStore(), transport: transport)
        let _: EmptyPayload = try await client.request("/auth/send-code", method: "POST", body: ["type": .string("login")], authenticated: false)
        let request = await transport.lastMutation
        XCTAssertEqual(request?.value(forHTTPHeaderField: "X-XSRF-TOKEN"), "csrf-fixture")
        XCTAssertTrue(request?.value(forHTTPHeaderField: "Cookie")?.contains("XSRF-TOKEN=csrf-fixture") == true)
        XCTAssertEqual(request?.value(forHTTPHeaderField: "Authorization"), nil)
    }
    func testStringMutationPayloadDecodes() throws {
        let response = try JSONDecoder().decode(APIEnvelope<EmptyPayload>.self, from: Data(#"{"code":200,"msg":"ok","data":"operation completed"}"#.utf8))
        XCTAssertEqual(response.code, 200); XCTAssertNotNil(response.data)
    }
    func testAdaptivePolicyLogWithNullDurationDecodes() throws {
        let log = try JSONDecoder().decode(SensitiveLogItem.self, from: Data(#"{"id":1,"operationType":"ADAPTIVE_POLICY","ipAddress":"192.0.2.1","result":"success","riskScore":20,"triggeredMultiErrorLock":false,"triggeredRateLimitLock":false,"durationMs":null,"createdAt":"2026-10-04T10:00:00"}"#.utf8))
        XCTAssertNil(log.durationMs); XCTAssertEqual(log.operationType, "ADAPTIVE_POLICY")
    }
    func testWebAuthnOptionsAcceptStringEncodedAndNativeJSON() throws {
        let encoded = Data(#"{"challenge":"abc","challengeId":"challenge","timeout":"60000","rpId":"auth.ksuser.cn","allowCredentials":"[{\"id\":\"id1\",\"type\":\"public-key\"}]"}"#.utf8)
        let native = Data(#"{"challenge":"abc","challengeId":"challenge","timeout":60000,"rpId":"auth.ksuser.cn","allowCredentials":[{"id":"id1","type":"public-key"}]}"#.utf8)
        let first = try JSONDecoder().decode(PasskeyAuthenticationOptions.self, from: encoded)
        let second = try JSONDecoder().decode(PasskeyAuthenticationOptions.self, from: native)
        XCTAssertEqual(first.timeout?.intValue, second.timeout?.intValue)
        XCTAssertEqual(first.allowCredentials?.arrayValue, second.allowCredentials?.arrayValue)
    }
    func testPasswordPolicyMatchesServerASCIIAndUTF16Rules() {
        let policy = PasswordRequirement(minLength: 8, maxLength: 64, requireUppercase: true, requireLowercase: true, requireDigits: true, requireSpecialChars: true, rejectCommonWeakPasswords: true, requirementMessage: nil)
        XCTAssertNil(policy.validationError(for: "Abc1234@"))
        XCTAssertNotNil(policy.validationError(for: "Abc1234#"))
        XCTAssertNotNil(policy.validationError(for: "Äbc1234@"))
        XCTAssertNotNil(policy.validationError(for: "Abc١٢٣٤@"))
        let lengthPolicy = PasswordRequirement(minLength: 8, maxLength: 8, requireUppercase: false, requireLowercase: false, requireDigits: false, requireSpecialChars: false, rejectCommonWeakPasswords: true, requirementMessage: nil)
        XCTAssertNil(lengthPolicy.validationError(for: "Ab1234😀"))
    }
    func testCookieDomainPathAndSecureMatching() {
        let cookie = StoredCookie(HTTPCookie(properties: [.name: "refreshToken", .value: "fixture", .domain: ".ksuser.cn", .path: "/auth", .secure: "TRUE"])!)
        XCTAssertTrue(cookie.matches(URL(string: "https://api.ksuser.cn/auth/refresh")!))
        XCTAssertFalse(cookie.matches(URL(string: "http://api.ksuser.cn/auth/refresh")!))
        XCTAssertFalse(cookie.matches(URL(string: "https://api.ksuser.cn/authentication")!))
        XCTAssertFalse(cookie.matches(URL(string: "https://ksuser.cn.evil.invalid/auth/refresh")!))
    }
    func testQRParserSupportsAndroidWireFormatsAndRejectsOtherHosts() throws {
        XCTAssertEqual(try QRCodeParser.parse("KSUSER-AUTH-QR:approval"), .init(code: "approval", transfer: false))
        XCTAssertEqual(try QRCodeParser.parse("KSUSER-AUTH-XFER:v1:transfer"), .init(code: "transfer", transfer: true))
        XCTAssertEqual(try QRCodeParser.parse("https://auth.ksuser.cn/qr?transferCode=a%2Bb"), .init(code: "a+b", transfer: true))
        XCTAssertThrowsError(try QRCodeParser.parse("https://evil.invalid/?approveCode=approval"))
        XCTAssertThrowsError(try QRCodeParser.parse("KSUSER-AUTH-QR:"))
    }
    func testRecoveryQRUsesExistingWebFlow() {
        let ticket = AccountRecoveryTicket(recoveryCode: "a+b /c", expiresInSeconds: 300, username: nil, maskedEmail: nil, sponsorClientName: nil, sponsorBrowser: nil, sponsorSystem: nil, sponsorIpLocation: nil)
        let url = URLComponents(string: ticket.qrContent!)!
        XCTAssertEqual(url.host, "auth.ksuser.cn"); XCTAssertEqual(url.path, "/forgot-password")
        XCTAssertEqual(url.queryItems?.first(where: { $0.name == "recoveryCode" })?.value, "a+b /c")
    }
    func testPartialProfileMutationResponsesReloadCompleteUserInformation() async throws {
        let client = APIClient(environment: environment, storage: MemorySessionStore(snapshot()), transport: FixtureTransport(mode: .partialProfile))
        let repository = KsuserRepository(client: client)
        let profile = try await repository.updateProfile(key: "username", value: "updated")
        let avatar = try await repository.uploadAvatar(data: Data([0, 1, 2]), mimeType: "image/jpeg")
        let email = try await repository.changeEmail("updated@example.invalid", code: "fixture")
        for complete in [profile, avatar, email] {
            XCTAssertEqual(complete.uuid, "complete-fixture")
            XCTAssertEqual(complete.bio, "preserved profile")
            XCTAssertEqual(complete.settings?.mfaEnabled, true)
            XCTAssertEqual(complete.hasPassword, false)
            XCTAssertEqual(complete.email, "")
        }
    }
    func testRepositoryNormalizesIdentifiersAndCodesWhilePreservingPasswordsAndFreeFormProfile() async throws {
        let transport = FixtureTransport(mode: .normalizedInput)
        let repository = KsuserRepository(client: APIClient(environment: environment, storage: MemorySessionStore(snapshot(token: "fixture-access")), transport: transport))
        let email = " \tfixture@example.invalid\n "
        let username = " \tfixture_user\n "
        let code = " \t123456\n "
        let password = " \tAbcd1234@  "
        let freeForm = "  Preserve spaces\n and formatting  "
        _ = try await repository.usernameAvailable(username)
        try await repository.sendCode(email: email, type: "login")
        _ = try await repository.login(email: email, password: password)
        _ = try await repository.loginWithCode(email: email, code: code)
        _ = try await repository.register(username: username, email: email, password: password, code: code)
        _ = try await repository.changeEmail(email, code: code)
        _ = try await repository.updateProfile(key: "username", value: username)
        for key in ["realName", "region", "bio"] { _ = try await repository.updateProfile(key: key, value: freeForm) }
        try await repository.verifySensitive(method: "password", password: password, code: nil, recoveryCode: nil)
        try await repository.verifySensitive(method: "email-code", password: nil, code: code, recoveryCode: nil)
        try await repository.verifySensitive(method: "totp", password: nil, code: code, recoveryCode: "  abcd-efgh \n")
        _ = try await repository.verifyMFA(challenge: MFAChallenge(challengeId: "fixture-challenge", method: "totp", methods: ["totp"], source: "password"), code: code, recoveryCode: "  abcd-efgh \n")
        try await repository.confirmTOTP(code: code, recoveryCodes: ["server-issued-recovery-code"])
        _ = try await repository.verifyTOTP(code: code, recoveryCode: "  abcd-efgh \n")
        try await repository.changePassword(password)
        try await repository.renamePasskey(1, name: "  iPhone \n")

        let requests = await transport.inputRequests
        func bodies(_ path: String) throws -> [[String: JSONValue]] {
            try requests.filter { $0.url?.path == path }.map { try JSONDecoder().decode([String: JSONValue].self, from: XCTUnwrap($0.httpBody)) }
        }
        let usernameRequest = try XCTUnwrap(requests.first { $0.url?.path == "/auth/check-username" })
        XCTAssertEqual(URLComponents(url: usernameRequest.url!, resolvingAgainstBaseURL: false)?.queryItems?.first { $0.name == "username" }?.value, "fixture_user")
        XCTAssertEqual(try bodies("/auth/send-code").first?["email"], .string("fixture@example.invalid"))
        XCTAssertEqual(try bodies("/auth/login").first?["email"], .string("fixture@example.invalid"))
        XCTAssertEqual(try bodies("/auth/login").first?["password"], .string(password))
        let codeLogin = try XCTUnwrap(bodies("/auth/login-with-code").first)
        XCTAssertEqual(codeLogin["email"], .string("fixture@example.invalid")); XCTAssertEqual(codeLogin["code"], .string("123456"))
        let registration = try XCTUnwrap(bodies("/auth/register").first)
        XCTAssertEqual(registration["username"], .string("fixture_user")); XCTAssertEqual(registration["email"], .string("fixture@example.invalid"))
        XCTAssertEqual(registration["code"], .string("123456")); XCTAssertEqual(registration["password"], .string(password))
        let changedEmail = try XCTUnwrap(bodies("/auth/update/email").first)
        XCTAssertEqual(changedEmail["newEmail"], .string("fixture@example.invalid")); XCTAssertEqual(changedEmail["code"], .string("123456"))
        let profileMutations = try bodies("/auth/update/profile")
        XCTAssertEqual(profileMutations.first?["value"], .string("fixture_user"))
        for body in profileMutations.dropFirst() { XCTAssertEqual(body["value"], .string(freeForm)) }
        let sensitive = try bodies("/auth/verify-sensitive")
        XCTAssertEqual(sensitive[0]["password"], .string(password)); XCTAssertEqual(sensitive[1]["code"], .string("123456"))
        XCTAssertEqual(sensitive[2]["code"], .string("123456")); XCTAssertEqual(sensitive[2]["recoveryCode"], .string("ABCD-EFGH"))
        for path in ["/auth/totp/mfa-verify", "/auth/totp/verify"] {
            let body = try XCTUnwrap(bodies(path).first)
            XCTAssertEqual(body["code"], .string("123456")); XCTAssertEqual(body["recoveryCode"], .string("ABCD-EFGH"))
        }
        XCTAssertEqual(try bodies("/auth/totp/registration-verify").first?["code"], .string("123456"))
        XCTAssertEqual(try bodies("/auth/update/password").first?["newPassword"], .string(password))
        XCTAssertEqual(try bodies("/auth/passkey/1/rename").first?["newName"], .string("iPhone"))
    }
    @MainActor func testProfileReloadFailureKeepsCompleteExistingModel() async throws {
        var original = snapshot(token: "existing")
        original.profile = UserProfile(uuid: "old-account", username: "original", email: "", bio: "preserved", settings: UserSettings(mfaEnabled: true), hasPassword: false)
        let client = APIClient(environment: environment, storage: MemorySessionStore(original), transport: FixtureTransport(mode: .partialProfileFetchFails))
        let model = AppModel(native: FixtureNative(), repository: KsuserRepository(client: client), environment: environment)
        model.user = original.profile
        await model.updateProfile(key: "username", value: "updated")
        XCTAssertEqual(model.user?.username, "original")
        XCTAssertEqual(model.user?.bio, "preserved")
        XCTAssertEqual(model.user?.settings?.mfaEnabled, true)
        XCTAssertEqual(model.user?.hasPassword, false)
        XCTAssertNotNil(model.errorMessage)
        let stored = await client.snapshot(); XCTAssertEqual(stored.profile?.username, "original")
    }
    @MainActor func testFailedTransferDoesNotReplaceCurrentProfileOrCredentials() async throws {
        var original = snapshot(token: "existing-access")
        original.profile = UserProfile(uuid: "old-account", username: "old", email: "old@example.invalid")
        let store = MemorySessionStore(original)
        let client = APIClient(environment: environment, storage: store, transport: FixtureTransport(mode: .transferProfileFails))
        let model = AppModel(native: FixtureNative(), repository: KsuserRepository(client: client), environment: environment)
        model.user = original.profile
        model.qrConfirmation = QRConfirmation(code: "transfer-fixture", isTransfer: true, preview: QrScanPreview(codeType: "transfer", clientName: nil, browser: nil, system: nil, ipAddress: nil, ipLocation: nil, expiresInSeconds: 300), expiresAt: Date().addingTimeInterval(300))
        await model.approveQRCode()
        XCTAssertEqual(model.user?.uuid, "old-account")
        let stored = await client.snapshot(); XCTAssertEqual(stored.accessToken, "existing-access")
        XCTAssertEqual(stored.cookies.map(\.value), original.cookies.map(\.value))
        XCTAssertNotNil(model.errorMessage)
    }
    @MainActor func testUnauthenticatedScanContinuesOnlyAfterFullLoginIncludingMFA() async throws {
        for needsMFA in [false, true] {
            let transport = FixtureTransport(mode: needsMFA ? .scanAfterMFALogin : .scanAfterLogin)
            let client = APIClient(environment: environment, storage: MemorySessionStore(), transport: transport)
            let model = AppModel(native: FixtureNative(), repository: KsuserRepository(client: client), environment: environment)
            await model.previewQRCode("KSUSER-AUTH-QR:pending-approval")
            XCTAssertNil(model.qrConfirmation); XCTAssertNotNil(model.noticeMessage)
            let beforeLogin = await transport.lastQRPreview; XCTAssertNil(beforeLogin)
            await model.login(email: "fixture@example.invalid", password: "fixture")
            if needsMFA {
                XCTAssertNotNil(model.mfaChallenge); XCTAssertNil(model.qrConfirmation)
                let beforeMFA = await transport.lastQRPreview; XCTAssertNil(beforeMFA)
                await model.verifyMFA(code: "123456")
            }
            XCTAssertEqual(model.qrConfirmation?.code, "pending-approval")
            XCTAssertFalse(model.qrConfirmation?.isTransfer ?? true)
            let preview = await transport.lastQRPreview
            XCTAssertEqual(preview?.value(forHTTPHeaderField: "Authorization"), "Bearer fresh")
            XCTAssertTrue(model.isAuthenticated)
        }
    }
    @MainActor func testCancellingMFAClearsPendingScan() async throws {
        let transport = FixtureTransport(mode: .scanAfterMFALogin)
        let client = APIClient(environment: environment, storage: MemorySessionStore(), transport: transport)
        let model = AppModel(native: FixtureNative(), repository: KsuserRepository(client: client), environment: environment)
        await model.previewQRCode("KSUSER-AUTH-QR:cancelled-approval")
        await model.login(email: "fixture@example.invalid", password: "fixture")
        model.cancelMFA()
        await model.login(email: "fixture@example.invalid", password: "fixture")
        await model.verifyMFA(code: "123456")
        XCTAssertNil(model.qrConfirmation)
        let preview = await transport.lastQRPreview; XCTAssertNil(preview)
    }
    @MainActor func testAccountSwitchClearsOldAppleMarkerWithoutClearingFreshAppleCredential() async throws {
        for useApple in [false, true] {
            var original = snapshot(token: "old-account-access")
            original.profile = UserProfile(uuid: "old-account", username: "old", email: "old@example.invalid")
            let client = APIClient(environment: environment, storage: MemorySessionStore(original), transport: FixtureTransport(mode: .scanAfterLogin))
            let native = FixtureNative()
            let model = AppModel(native: native, repository: KsuserRepository(client: client), environment: environment)
            model.user = original.profile
            if useApple {
                model.pendingOAuth = PendingOAuth(provider: "apple", bindToken: "fixture", message: nil)
                await model.createPendingAccount()
            } else { await model.login(email: "fixture@example.invalid", password: "fixture") }
            XCTAssertEqual(model.user?.uuid, "login-fixture")
            XCTAssertEqual(native.clearCalls, useApple ? 0 : 1)
            XCTAssertEqual(native.acceptCalls, useApple ? 1 : 0)
        }
    }
    @MainActor func testBridgeCancellationReturnsOnlyServerBrowserURL() async throws {
        for destination in ["https://fixture.example.invalid/login", "javascript:alert(1)"] {
            let client = APIClient(environment: environment, storage: MemorySessionStore(), transport: FixtureTransport(mode: .bridgeCancel))
            let model = AppModel(native: FixtureNative(), repository: KsuserRepository(client: client), environment: environment)
            model.bridgeConfirmation = BridgeConfirmation(challengeId: "fixture", status: MobileBridgeStatusPayload(status: "pending", transferCode: nil, returnUrl: destination, returnOrigin: "https://fixture.example.invalid", expiresInSeconds: 300), expiresAt: Date().addingTimeInterval(300))
            let url = await model.cancelBridge()
            XCTAssertEqual(url?.absoluteString, destination.hasPrefix("https:") ? destination : nil)
            XCTAssertNil(model.bridgeConfirmation)
        }
    }
}

private actor LoadingGateTransport: HTTPTransport {
    private let base: FixtureTransport
    private let paths: Set<String>
    private var gates: [String: CheckedContinuation<Void, Error>] = [:]
    private var counts: [String: Int] = [:]

    init(mode: FixtureTransport.Mode, paths: Set<String>) { base = FixtureTransport(mode: mode); self.paths = paths }
    func requestCount(_ path: String) -> Int { counts[path, default: 0] }
    func waitForRequest(_ path: String) async { while gates[path] == nil { await Task.yield() } }
    func release(_ path: String, error: Error? = nil) {
        let gate = gates.removeValue(forKey: path)
        if let error { gate?.resume(throwing: error) } else { gate?.resume() }
    }
    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let path = request.url!.path
        counts[path, default: 0] += 1
        if paths.contains(path) { try await withCheckedThrowingContinuation { gates[path] = $0 } }
        return try await base.data(for: request)
    }
}

private actor FixtureTransport: HTTPTransport {
    enum Mode { case refreshSucceeds, replayUnauthorized, offlineRefresh, refreshSuspends, invalidRefresh, forbiddenRefresh, csrf, transferProfileFails, mfaLogin, pendingApple, partialProfile, partialProfileFetchFails, scanAfterLogin, scanAfterMFALogin, bridgeCancel, normalizedInput }
    let mode: Mode
    var refreshes = 0
    var protectedCalls = 0
    var lastMutation: URLRequest?
    var lastQRPreview: URLRequest?
    var inputRequests: [URLRequest] = []
    private var refreshGate: CheckedContinuation<Void, Never>?
    init(mode: Mode) { self.mode = mode }
    func counters() -> (refreshes: Int, protectedCalls: Int) { (refreshes, protectedCalls) }
    func waitForRefresh() async { while refreshGate == nil { await Task.yield() } }
    func releaseRefresh() { refreshGate?.resume(); refreshGate = nil }
    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let path = request.url!.path
        if path == "/auth/csrf-token" { return response(request, 200, #"{"code":200,"data":{"csrfToken":"csrf-fixture"}}"#, cookie: "XSRF-TOKEN=csrf-fixture; Path=/; Secure; SameSite=Lax") }
        if mode == .normalizedInput {
            inputRequests.append(request)
            if path == "/auth/check-username" { return response(request, 200, #"{"code":200,"data":{"exists":false}}"#) }
            if ["/auth/login", "/auth/login-with-code", "/auth/register", "/auth/totp/mfa-verify"].contains(path) { return response(request, 200, #"{"code":200,"data":{"accessToken":"fixture-access"}}"#) }
            if path == "/auth/info" { return response(request, 200, #"{"code":200,"data":{"uuid":"fixture","username":"fixture_user","email":"fixture@example.invalid"}}"#) }
            if path == "/auth/totp/verify" { return response(request, 200, #"{"code":200,"data":{"success":true}}"#) }
            return response(request, 200, #"{"code":200,"data":null}"#)
        }
        if mode == .bridgeCancel { return response(request, 200, #"{"code":200,"data":null}"#) }
        if path == "/auth/refresh" {
            refreshes += 1
            if mode == .offlineRefresh { throw URLError(.notConnectedToInternet) }
            if mode == .invalidRefresh { return response(request, 401, #"{"code":401,"msg":"RefreshToken已失效"}"#) }
            if mode == .forbiddenRefresh { return response(request, 403, #"{"code":403,"msg":"无权限"}"#) }
            if mode == .refreshSuspends { await withCheckedContinuation { refreshGate = $0 } }
            else { try await Task.sleep(nanoseconds: 20_000_000) }
            return response(request, 200, #"{"code":200,"data":{"accessToken":"fresh"}}"#)
        }
        if mode == .csrf { lastMutation = request; return response(request, 200, #"{"code":200,"data":"验证码已发送"}"#) }
        if mode == .mfaLogin { return response(request, 200, #"{"code":201,"data":{"challengeId":"mfa-fixture","method":"totp","methods":["totp","passkey"]}}"#) }
        if mode == .pendingApple { return response(request, 200, #"{"code":202,"data":{"needBind":true,"provider":"apple","oauthBindToken":"fixture-only","canRegister":false,"emailConflict":true}}"#) }
        if mode == .scanAfterLogin || mode == .scanAfterMFALogin {
            if path == "/auth/login" || path == "/oauth/apple/register-pending" {
                if mode == .scanAfterMFALogin { return response(request, 200, #"{"code":201,"data":{"challengeId":"mfa-fixture","method":"totp","methods":["totp"]}}"#) }
                return response(request, 200, #"{"code":200,"data":{"accessToken":"fresh"}}"#)
            }
            if path == "/auth/totp/mfa-verify" { return response(request, 200, #"{"code":200,"data":{"accessToken":"fresh"}}"#) }
            if path == "/auth/info" { return response(request, 200, #"{"code":200,"data":{"uuid":"login-fixture","username":"fixture","email":"fixture@example.invalid"}}"#) }
            if path == "/auth/passkey/list" { return response(request, 200, #"{"code":200,"data":{"passkeys":[]}}"#) }
            if path == "/auth/totp/status" { return response(request, 200, #"{"code":200,"data":{"enabled":false,"recoveryCodesCount":0}}"#) }
            if path == "/oauth/apple/status" { return response(request, 200, #"{"code":200,"data":{"bound":false}}"#) }
            if path == "/auth/adaptive-auth/status" { return response(request, 200, #"{"code":200,"data":{"riskScore":10,"riskLevel":"low","trusted":true,"requiresStepUp":false,"sessionFrozen":false,"sensitiveVerified":false,"sensitiveVerificationRemainingSeconds":0,"authAgeSeconds":0,"idleSeconds":0,"multiEndpointAlert":false,"alertRemainingSeconds":0,"recommendedAction":"safe","reasons":[]}}"#) }
            if path == "/auth/qr/preview" { lastQRPreview = request; return response(request, 200, #"{"code":200,"data":{"codeType":"approve_login","clientName":"fixture-browser","expiresInSeconds":300}}"#) }
        }
        if mode == .partialProfile || mode == .partialProfileFetchFails {
            if path == "/auth/info" {
                if mode == .partialProfileFetchFails { throw URLError(.notConnectedToInternet) }
                return response(request, 200, #"{"code":200,"data":{"uuid":"complete-fixture","username":"updated","email":"","bio":"preserved profile","settings":{"mfaEnabled":true,"detectUnusualLogin":true,"notifySensitiveActionEmail":true,"subscribeNewsEmail":false},"hasPassword":false}}"#)
            }
            return response(request, 200, #"{"code":200,"data":{"username":"updated","email":null}}"#)
        }
        if mode == .transferProfileFails {
            if path == "/auth/session-transfer/exchange" { return response(request, 200, #"{"code":200,"data":{"accessToken":"new-account-access"}}"#, cookie: "refreshToken=new-account-refresh; Path=/auth; Secure") }
            if path == "/auth/info" { throw URLError(.notConnectedToInternet) }
        }
        protectedCalls += 1
        if mode == .replayUnauthorized || request.value(forHTTPHeaderField: "Authorization") != "Bearer fresh" {
            return response(request, 401, #"{"code":401,"msg":"登录已失效"}"#)
        }
        return response(request, 200, #"{"code":200,"data":{"ok":true}}"#)
    }
    private func response(_ request: URLRequest, _ status: Int, _ body: String, cookie: String? = nil) -> (Data, HTTPURLResponse) {
        let headers = cookie.map { ["Set-Cookie": $0] } ?? [:]
        return (Data(body.utf8), HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: "HTTP/1.1", headerFields: headers)!)
    }
}
@MainActor private final class FixtureNative: NativeAuthenticationProviding {
    var clearCalls = 0
    var acceptCalls = 0
    func authenticatePasskey(options: PasskeyAuthenticationOptions) async throws -> PasskeyAuthenticationPayload { throw APIError.cancelled }
    func registerPasskey(options: PasskeyRegistrationOptions) async throws -> PasskeyRegistrationPayload { throw APIError.cancelled }
    func signInWithApple(challenge: AppleChallenge) async throws -> AppleCredential { throw APIError.cancelled }
    func signInWithQQ() async throws -> QQCredential { throw APIError.cancelled }
    func appleCredentialRevoked() async -> Bool { false }
    func acceptAppleCredential() throws { acceptCalls += 1 }
    func clearAppleCredential() throws { clearCalls += 1 }
}
