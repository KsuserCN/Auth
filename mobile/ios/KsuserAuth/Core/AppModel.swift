import Foundation
import Observation

@MainActor @Observable final class AppModel {
    let environment: AppEnvironment
    var user: UserProfile?
    var isBusy: Bool { busyCount > 0 }
    var isAuthenticated: Bool { user != nil }
    var errorMessage: String?
    var noticeMessage: String?
    var passwordRequirement: PasswordRequirement?
    var mfaChallenge: MFAChallenge?
    var pendingOAuth: PendingOAuth?
    var sensitiveRequest: SensitiveRequest?
    var passkeys: [PasskeyListItem] = []
    var totpStatus = TotpStatus(enabled: false, recoveryCodesCount: 0)
    var totpSetup: TotpRegistrationOptions?
    var recoveryCodes: [String] = []
    var sessions: [SessionItem] = []
    var logs: [SensitiveLogItem] = []
    var logsPage = 1
    var totalPages = 1
    var logsTotal = 0
    var adaptiveStatus: AdaptiveAuthStatus?
    var appleBound = false
    var qrConfirmation: QRConfirmation?
    var bridgeConfirmation: BridgeConfirmation?
    var recoveryTicket: AccountRecoveryTicket?
    var recoveryTicketExpiresAt: Date?
    var updateInfo: AppUpdateInfo?
    var settings: UserSettings { user?.settings ?? UserSettings() }

    @ObservationIgnored private let repository: any KsuserRepositoryProviding
    @ObservationIgnored private let native: any NativeAuthenticationProviding
    @ObservationIgnored private var authenticationRepository: KsuserRepository?
    @ObservationIgnored private var sensitiveContinuation: (@MainActor () async throws -> Void)?
    private var busyCount = 0
    @ObservationIgnored private var didRestore = false
    @ObservationIgnored private var logOperationType: String?
    @ObservationIgnored private var logResult: String?
    @ObservationIgnored private var authGeneration = 0
    @ObservationIgnored private var bindingAfterAuthentication = false
    @ObservationIgnored private var pendingRawQRCode: String?
    @ObservationIgnored private let isUITesting: Bool

    init(native: any NativeAuthenticationProviding, repository: (any KsuserRepositoryProviding)? = nil, environment: AppEnvironment = .current) {
        self.native = native; self.environment = environment
        self.repository = repository ?? KsuserRepository(client: APIClient(environment: environment))
        #if DEBUG
        // The default app host is inert during XCTest; injected repositories still execute isolated unit-test fixtures.
        isUITesting = repository == nil && (ProcessInfo.processInfo.arguments.contains("--ui-testing") || ProcessInfo.processInfo.environment["KSUSER_UI_TESTING"] == "1")
        if isUITesting {
            passwordRequirement = PasswordRequirement(minLength: 8, maxLength: 64, requireUppercase: true, requireLowercase: true, requireDigits: true, requireSpecialChars: false, rejectCommonWeakPasswords: true, requirementMessage: "8–64 个字符，包含大小写字母和数字")
        }
        if isUITesting, ProcessInfo.processInfo.arguments.contains("--ui-test-authenticated") { configureUITestFixture() }
        if isUITesting, ProcessInfo.processInfo.arguments.contains("--ui-test-notice") { noticeMessage = "验证码已发送，请检查邮箱" }
        if isUITesting, ProcessInfo.processInfo.arguments.contains("--ui-test-error") { errorMessage = "验证码无效或已过期，请重新获取验证码后再试。" }
        #else
        isUITesting = false
        #endif
    }

    func restoreSession() async {
        guard !didRestore, !isUITesting else { return }; didRestore = true
        let snapshot = await repository.client.snapshot()
        user = snapshot.profile
        guard await repository.client.hasSession() else { user = nil; return }
        await run {
            if snapshot.accessToken == nil { _ = try await self.repository.client.restoreAccessToken() }
            let profile = try await self.repository.currentUser()
            try await self.repository.client.cacheUser(profile); self.user = profile
            await self.refreshSecurity()
        }
    }
    func loadPasswordRequirement() async { await run { self.passwordRequirement = try await self.repository.passwordRequirement() } }
    func checkUsername(_ username: String) async -> Bool {
        if isUITesting { return username.count >= 3 }
        var available = false
        await run {
            available = try await self.repository.usernameAvailable(username)
            if !available { throw APIError.server(409, "该用户名已被使用，请换一个") }
        }
        return available
    }
    func sendCode(email: String? = nil, type: String) async {
        await run { try await self.repository.sendCode(email: email, type: type); self.noticeMessage = "验证码已发送，请检查邮箱" }
    }
    func login(email: String, password: String) async {
        await authenticate { try await $0.login(email: email, password: password) }
    }
    func loginWithCode(email: String, code: String) async {
        await authenticate(source: "email-code") { try await $0.loginWithCode(email: email, code: code) }
    }
    func loginWithPasskey() async {
        await authenticate(source: "passkey") { staged in
            let options = try await staged.passkeyAuthenticationOptions()
            let payload = try await self.native.authenticatePasskey(options: options)
            return try await staged.loginWithPasskey(options: options, payload: payload)
        }
    }
    func loginWithQQ() async {
        await authenticate(source: "qq") { staged in try await staged.loginWithQQ(self.native.signInWithQQ()) }
    }
    func loginWithApple() async {
        await authenticate(source: "apple") { staged in
            let challenge = try await staged.appleChallenge(purpose: "login")
            let credential = try await self.native.signInWithApple(challenge: challenge)
            return try await staged.loginWithApple(challenge: challenge, credential: credential)
        }
    }
    func register(username: String, email: String, password: String, code: String) async {
        await authenticate { staged in
            if let error = self.passwordRequirement?.validationError(for: password) { throw APIError.server(400, error) }
            return .success(try await staged.register(username: username, email: email, password: password, code: code))
        }
    }
    func verifyMFA(code: String? = nil, recoveryCode: String? = nil) async {
        guard let challenge = mfaChallenge, let staged = authenticationRepository else { return }
        let generation = authGeneration
        await run {
            let token = try await staged.verifyMFA(challenge: challenge, code: code, recoveryCode: recoveryCode)
            guard generation == self.authGeneration else { throw APIError.cancelled }
            try await self.finishAuthentication(token: token, staged: staged, expectedGeneration: generation, source: challenge.source)
        }
    }
    func verifyMFAPasskey() async {
        guard let challenge = mfaChallenge, let staged = authenticationRepository, challenge.methods.contains("passkey") else { return }
        let generation = authGeneration
        await run {
            let options = try await staged.passkeyAuthenticationOptions()
            let payload = try await self.native.authenticatePasskey(options: options)
            let token = try await staged.verifyMFAPasskey(challenge: challenge, options: options, payload: payload)
            guard generation == self.authGeneration else { throw APIError.cancelled }
            try await self.finishAuthentication(token: token, staged: staged, expectedGeneration: generation, source: challenge.source)
        }
    }
    func cancelMFA() { authGeneration += 1; mfaChallenge = nil; authenticationRepository = nil; bindingAfterAuthentication = false; pendingRawQRCode = nil }
    func cancelPendingOAuth() { pendingOAuth = nil; bindingAfterAuthentication = false; pendingRawQRCode = nil }
    func createPendingAccount() async {
        guard let pending = pendingOAuth, pending.provider == "apple", pending.canRegister else { errorMessage = "请登录已有账号后完成关联"; return }
        await authenticate(source: "apple", preservePending: false) { staged in try await staged.createAppleAccount(bindToken: pending.bindToken) }
    }
    func bindPendingOAuth() async {
        guard let pending = pendingOAuth else { return }
        guard isAuthenticated else { bindingAfterAuthentication = true; return }
        if pending.provider == "apple" { await bindApple(); return }
        await run {
            try await self.repository.bindOAuth(pending)
            self.pendingOAuth = nil; self.bindingAfterAuthentication = false; self.noticeMessage = "QQ 账号已关联"
        }
    }
    func bindApple() async {
        await requireSensitive(title: "关联 Apple 账号") {
            let challenge = try await self.repository.appleChallenge(purpose: "bind")
            let credential = try await self.native.signInWithApple(challenge: challenge)
            let result = try await self.repository.loginWithApple(challenge: challenge, credential: credential, binding: true)
            guard case .needsOAuth(let pending) = result else { throw APIError.server(409, "此 Apple 账号已关联其他账号") }
            try await self.repository.bindOAuth(pending)
            try self.native.acceptAppleCredential()
            self.appleBound = true; self.pendingOAuth = nil; self.bindingAfterAuthentication = false; self.noticeMessage = "Apple 账号已关联"
        }
    }
    func unbindApple() async {
        await requireSensitive(title: "解除 Apple 关联") {
            try await self.repository.unbindApple(); try self.native.clearAppleCredential(); self.appleBound = false; self.noticeMessage = "Apple 账号已解除关联"
        }
    }
    func validateAppleCredential() async {
        guard !isUITesting, isAuthenticated else { return }
        if await native.appleCredentialRevoked() { await handleAppleCredentialRevocation() }
    }
    func handleAppleCredentialRevocation() async {
        guard !isUITesting else { return }
        let snapshot = await repository.client.snapshot()
        if snapshot.authSource == "apple" {
            await run {
                try await self.clearSessionAndUser(); self.noticeMessage = "Apple 授权已撤销，请重新登录"
            }
        } else if isAuthenticated {
            await run { self.appleBound = try await self.repository.appleStatus() }
        }
    }

    func loadDashboard() async {
        guard isAuthenticated else { return }
        await refreshProfile()
        await refreshSecurity()
    }
    func refreshProfile() async {
        await run { try await self.setProfile(self.repository.currentUser()) }
    }
    func updateProfile(key: String, value: String) async {
        await run { try await self.setProfile(self.repository.updateProfile(key: key, value: value)); self.noticeMessage = "资料已保存" }
    }
    func uploadAvatar(data: Data, mimeType: String = "image/jpeg") async {
        await run { try await self.setProfile(self.repository.uploadAvatar(data: data, mimeType: mimeType)); self.noticeMessage = "头像已更新" }
    }
    func refreshSecurity() async {
        guard isAuthenticated else { return }
        await run {
            async let keys = capture { try await self.repository.passkeys() }
            async let totp = capture { try await self.repository.totpStatus() }
            async let adaptive = capture { try await self.repository.adaptiveStatus() }
            async let apple = capture { try await self.repository.appleStatus() }
            let results = await (keys, totp, adaptive, apple)
            if case .success(let value) = results.0 { self.passkeys = value }
            if case .success(let value) = results.1 { self.totpStatus = value }
            if case .success(let value) = results.2 { self.adaptiveStatus = value }
            if case .success(let value) = results.3 { self.appleBound = value }
            for result in [results.0.map { _ in () }, results.1.map { _ in () }, results.2.map { _ in () }] {
                if case .failure(let error) = result { throw error }
            }
            // Existing deployments do not expose Apple status until the matching API release.
            if case .failure(let error) = results.3, let apiError = error as? APIError, apiError.isUnauthorized { throw error }
        }
    }
    func updateSetting(field: String, value: Bool) async {
        await requireSensitive(title: "更改安全设置") {
            let settings = try await self.repository.updateSetting(field: field, value: value)
            self.user?.settings = settings; if let user = self.user { try await self.repository.client.cacheUser(user) }
            self.noticeMessage = "设置已保存"
        }
    }
    func updateSetting(field: String, stringValue: String) async {
        await requireSensitive(title: "更改验证方式") {
            let settings = try await self.repository.updateSetting(field: field, stringValue: stringValue)
            self.user?.settings = settings; if let user = self.user { try await self.repository.client.cacheUser(user) }
            self.noticeMessage = "设置已保存"
        }
    }
    func requireSensitive(title: String = "验证身份", action: @MainActor @escaping () async throws -> Void) async {
        await run {
            let status = try await self.repository.sensitiveStatus()
            if status.verified { try await action() }
            else { self.sensitiveContinuation = action; self.sensitiveRequest = SensitiveRequest(title: title, status: status) }
        }
    }
    func verifySensitive(method: String, password: String? = nil, code: String? = nil, recoveryCode: String? = nil) async {
        guard sensitiveRequest != nil else { return }
        await run {
            if method == "passkey" {
                let options = try await self.repository.sensitivePasskeyOptions()
                let payload = try await self.native.authenticatePasskey(options: options)
                try await self.repository.verifySensitivePasskey(options: options, payload: payload)
            } else if method == "apple" {
                let challenge = try await self.repository.appleChallenge(purpose: "sensitive")
                let credential = try await self.native.signInWithApple(challenge: challenge)
                try await self.repository.verifySensitiveApple(challenge: challenge, credential: credential)
            } else {
                try await self.repository.verifySensitive(method: method, password: password, code: code, recoveryCode: recoveryCode)
            }
            let continuation = self.sensitiveContinuation
            self.sensitiveContinuation = nil; self.sensitiveRequest = nil
            try await continuation?()
        }
    }
    func cancelSensitive() { sensitiveContinuation = nil; sensitiveRequest = nil }
    func addPasskey(name: String) async {
        await requireSensitive(title: "添加 Passkey") {
            let options = try await self.repository.passkeyRegistrationOptions(name: name)
            let payload = try await self.native.registerPasskey(options: options)
            try await self.repository.createPasskey(name: name, payload: payload)
            self.passkeys = try await self.repository.passkeys(); self.noticeMessage = "Passkey 已添加"
        }
    }
    func renamePasskey(id: Int64, name: String) async {
        await requireSensitive(title: "重命名 Passkey") {
            try await self.repository.renamePasskey(id, name: name); self.passkeys = try await self.repository.passkeys(); self.noticeMessage = "名称已更新"
        }
    }
    func deletePasskey(id: Int64) async {
        await requireSensitive(title: "删除 Passkey") {
            try await self.repository.deletePasskey(id); self.passkeys = try await self.repository.passkeys(); self.noticeMessage = "Passkey 已删除"
        }
    }
    func startTOTP() async {
        await requireSensitive(title: "启用验证器") { self.totpSetup = try await self.repository.startTOTP() }
    }
    func confirmTOTP(code: String) async {
        guard let setup = totpSetup else { return }
        await run {
            try await self.repository.confirmTOTP(code: code, recoveryCodes: setup.recoveryCodes)
            self.recoveryCodes = setup.recoveryCodes; self.totpSetup = nil; self.totpStatus = try await self.repository.totpStatus(); self.noticeMessage = "验证器已启用，请妥善保存恢复码"
        }
    }
    func disableTOTP() async {
        await requireSensitive(title: "关闭验证器") {
            try await self.repository.disableTOTP(); self.totpStatus = try await self.repository.totpStatus(); self.recoveryCodes = []; self.totpSetup = nil; self.noticeMessage = "验证器已关闭"
        }
    }
    func showRecoveryCodes() async {
        await requireSensitive(title: "查看恢复码") { self.recoveryCodes = try await self.repository.recoveryCodes() }
    }
    func regenerateRecoveryCodes() async {
        await requireSensitive(title: "重新生成恢复码") {
            self.recoveryCodes = try await self.repository.recoveryCodes(regenerate: true); self.totpStatus = try await self.repository.totpStatus(); self.noticeMessage = "恢复码已更新，之前的恢复码立即失效"
        }
    }
    func changeEmail(newEmail: String, code: String) async {
        await requireSensitive(title: "修改邮箱") { try await self.setProfile(self.repository.changeEmail(newEmail, code: code)); self.noticeMessage = "邮箱已更新" }
    }
    func changePassword(newPassword: String) async {
        await requireSensitive(title: "修改密码") {
            if let error = self.passwordRequirement?.validationError(for: newPassword) { throw APIError.server(400, error) }
            try await self.repository.changePassword(newPassword); self.user?.hasPassword = true
            if let user = self.user { try await self.repository.client.cacheUser(user) }; self.noticeMessage = "密码已更新"
        }
    }
    func deleteAccount() async {
        await requireSensitive(title: "注销账号") {
            try await self.repository.deleteAccount(confirmText: "我真的不想要我的号辣")
            try await self.clearSessionAndUser(); self.noticeMessage = "账号已注销"
        }
    }
    func logout(allDevices: Bool = false) async {
        if allDevices {
            await requireSensitive(title: "退出全部设备") {
                try await self.repository.logout(all: true); try await self.clearSessionAndUser()
            }
        } else {
            await run {
                do { try await self.repository.logout() } catch {
                    try await self.clearSessionAndUser(); throw error
                }
                try await self.clearSessionAndUser()
            }
        }
    }
    func refreshSessions() async { await run { self.sessions = try await self.repository.sessions() } }
    func revokeSession(id: Int64) async {
        await requireSensitive(title: "撤销设备登录") { try await self.repository.revokeSession(id); self.sessions = try await self.repository.sessions(); self.noticeMessage = "设备会话已撤销" }
    }
    func loadLogs(page: Int = 1, operationType: String? = nil, result: String? = nil) async {
        await run {
            let logs = try await self.repository.logs(page: page, operationType: operationType, result: result)
            self.logs = logs.data; self.logsPage = logs.page; self.totalPages = logs.totalPages; self.logsTotal = logs.total
            self.logOperationType = operationType; self.logResult = result
        }
    }
    func generateRecoveryTicket() async {
        await requireSensitive(title: "生成恢复授权") {
            let ticket = try await self.repository.generateRecoveryTicket()
            self.recoveryTicket = ticket; self.recoveryTicketExpiresAt = Date().addingTimeInterval(Double(ticket.expiresInSeconds))
        }
    }
    func previewQRCode(_ raw: String) async {
        await run {
            let parsed = try QRCodeParser.parse(raw)
            if !parsed.transfer && !self.isAuthenticated {
                self.pendingRawQRCode = raw
                self.noticeMessage = "请先登录，登录后将继续确认扫码请求"
                return
            }
            let preview = try await self.repository.previewQRCode(code: parsed.code, transfer: parsed.transfer)
            guard preview.expiresInSeconds > 0 else { throw APIError.server(410, "二维码已过期") }
            self.qrConfirmation = QRConfirmation(code: parsed.code, isTransfer: parsed.transfer, preview: preview, expiresAt: Date().addingTimeInterval(Double(preview.expiresInSeconds)))
        }
    }
    func approveQRCode() async {
        guard let pending = qrConfirmation else { return }
        guard pending.expiresAt > Date() else { qrConfirmation = nil; errorMessage = "二维码已过期，请重新扫码"; return }
        await run {
            if pending.isTransfer {
                let staged = KsuserRepository(client: await self.repository.client.stagedClient())
                let token = try await staged.exchangeTransfer(pending.code)
                try await self.finishAuthentication(token: token, staged: staged, source: "session-transfer")
                self.noticeMessage = "此设备已登录新账号"
            } else {
                try await self.repository.approveQRCode(pending.code); self.noticeMessage = "请求已批准"
            }
            self.qrConfirmation = nil
        }
    }
    func dismissQR() { qrConfirmation = nil; pendingRawQRCode = nil }
    func handleURL(_ url: URL) async {
        guard url.scheme == "https", url.host?.lowercased() == environment.webURL.host?.lowercased(), url.path == "/app/bridge-login" else { return }
        guard let challenge = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { $0.name == "challengeId" })?.value, !challenge.isEmpty else { errorMessage = "网页登录请求参数缺失"; return }
        await run {
            let status = try await self.repository.bridgeStatus(challenge)
            guard status.status.lowercased() == "pending", status.expiresInSeconds > 0 else { throw APIError.server(410, "网页登录请求已完成或过期，请返回浏览器重新发起") }
            self.bridgeConfirmation = BridgeConfirmation(challengeId: challenge, status: status, expiresAt: Date().addingTimeInterval(Double(status.expiresInSeconds)))
            if !self.isAuthenticated { self.noticeMessage = "请登录后确认浏览器授权" }
        }
    }
    func approveBridge() async -> URL? {
        guard let pending = bridgeConfirmation, isAuthenticated else { return nil }
        var returnURL: URL?
        await run {
            guard pending.expiresAt > Date() else { self.bridgeConfirmation = nil; throw APIError.server(410, "网页登录请求已过期") }
            let result = try await self.repository.approveBridge(pending.challengeId)
            guard let url = URL(string: result.returnUrl), ["https", "http"].contains(url.scheme?.lowercased() ?? ""), url.host != nil else { throw APIError.invalidResponse }
            returnURL = url; self.bridgeConfirmation = nil; self.noticeMessage = "网页登录已确认"
        }
        return returnURL
    }
    @discardableResult func cancelBridge() async -> URL? {
        guard let pending = bridgeConfirmation else { return nil }
        var returnURL: URL?
        await run {
            try await self.repository.cancelBridge(pending.challengeId)
            if let value = pending.status.returnUrl, let url = URL(string: value),
               ["https", "http"].contains(url.scheme?.lowercased() ?? ""), url.host != nil { returnURL = url }
            self.bridgeConfirmation = nil; self.noticeMessage = "网页登录已取消"
        }
        return returnURL
    }
    func checkUpdate() async {
        await run(reportError: false) {
            var request = URLRequest(url: self.environment.updateManifestURL); request.setValue("application/json", forHTTPHeaderField: "Accept")
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else { throw APIError.server(503, "检查更新失败，请稍后重试") }
            self.updateInfo = try JSONDecoder().decode(AppUpdateInfo.self, from: data)
        }
    }
    func manualCheckUpdate() async {
        await checkUpdate()
        if updateInfo == nil { errorMessage = "检查更新失败，请稍后重试" }
        else if updateInfo?.available == false { noticeMessage = "当前已是最新版本" }
    }

    private func authenticate(source: String = "password", preservePending: Bool = true, operation: @MainActor (KsuserRepository) async throws -> AuthResult) async {
        authGeneration += 1
        let generation = authGeneration
        let staged = KsuserRepository(client: await repository.client.stagedClient())
        bindingAfterAuthentication = preservePending && pendingOAuth != nil
        await run {
            let result = try await operation(staged)
            guard generation == self.authGeneration else { throw APIError.cancelled }
            switch result {
            case .success(let token): try await self.finishAuthentication(token: token, staged: staged, expectedGeneration: generation, source: source)
            case .needsMFA(let challenge): self.authenticationRepository = staged; self.mfaChallenge = challenge
            case .needsOAuth(let pending): self.pendingOAuth = pending; self.bindingAfterAuthentication = false
            }
        }
    }
    private func finishAuthentication(token: String, staged: KsuserRepository, expectedGeneration: Int? = nil, source: String? = nil) async throws {
        try await staged.client.setAccessToken(token, source: source)
        let profile = try await staged.currentUser()
        try await staged.client.cacheUser(profile)
        let previousSnapshot = await repository.client.snapshot()
        if let expectedGeneration, expectedGeneration != authGeneration { throw APIError.cancelled }
        let previousUUID = user?.uuid ?? previousSnapshot.profile?.uuid
        try await repository.client.replaceSession(staged.client.snapshot())
        user = profile; mfaChallenge = nil; authenticationRepository = nil
        if source != "apple", previousUUID != profile.uuid { try native.clearAppleCredential() }
        if source == "apple" { try native.acceptAppleCredential() }
        // Erase previous-account data only after the replacement profile and credentials are complete.
        passkeys = []; sessions = []; logs = []; adaptiveStatus = nil; recoveryCodes = []; totpSetup = nil; recoveryTicket = nil; recoveryTicketExpiresAt = nil
        if let pending = pendingOAuth, bindingAfterAuthentication {
            bindingAfterAuthentication = false
            if pending.provider == "qq" { try await repository.bindOAuth(pending); pendingOAuth = nil; noticeMessage = "登录成功，QQ 账号已关联" }
            else if pending.provider == "apple" {
                // A login-origin Apple ticket cannot bind to an existing account; obtain a new bound-session challenge.
                await bindApple()
            }
        } else { pendingOAuth = nil }
        await refreshSecurity()
        if let raw = pendingRawQRCode {
            pendingRawQRCode = nil
            await previewQRCode(raw)
        }
    }
    private func setProfile(_ profile: UserProfile) async throws { try await repository.client.cacheUser(profile); user = profile }
    private func clearSessionAndUser() async throws {
        var cleanupError: Error?
        do { try await repository.client.clearSession() } catch { cleanupError = error }
        do { try native.clearAppleCredential() } catch { if cleanupError == nil { cleanupError = error } }
        bridgeConfirmation = nil
        clearUserState()
        if let cleanupError { throw cleanupError }
    }
    private func clearUserState() {
        authGeneration += 1; user = nil; mfaChallenge = nil; authenticationRepository = nil; pendingOAuth = nil
        sensitiveRequest = nil; sensitiveContinuation = nil; bindingAfterAuthentication = false
        passkeys = []; sessions = []; logs = []; adaptiveStatus = nil; appleBound = false; totpSetup = nil
        totpStatus = TotpStatus(enabled: false, recoveryCodesCount: 0); recoveryCodes = []; qrConfirmation = nil; pendingRawQRCode = nil; recoveryTicket = nil; recoveryTicketExpiresAt = nil
    }
    private func run(reportError: Bool = true, operation: @MainActor () async throws -> Void) async {
        guard !isUITesting else { return }
        busyCount += 1
        if reportError { errorMessage = nil; noticeMessage = nil }
        defer { busyCount -= 1 }
        do { try await operation() }
        catch is CancellationError { }
        catch APIError.cancelled { }
        catch {
            if let apiError = error as? APIError, apiError.isUnauthorized, !(await repository.client.hasSession()) {
                try? native.clearAppleCredential(); clearUserState()
            }
            if reportError { errorMessage = error.localizedDescription }
        }
    }
    #if DEBUG
    private func configureUITestFixture() {
        user = UserProfile(uuid: "ui-fixture-only", username: "ios_test_user", email: "ios.fixture@example.invalid", realName: "体验账号", region: "上海", bio: "这个账号只用于本地界面测试", settings: UserSettings(mfaEnabled: true), hasPassword: true, appleBound: true)
        appleBound = true
        passkeys = [PasskeyListItem(id: 1, name: "iPhone", transports: "internal", lastUsedAt: "2026-10-04T10:00:00", createdAt: "2026-10-01T10:00:00")]
        totpStatus = TotpStatus(enabled: true, recoveryCodesCount: 8)
        sessions = [SessionItem(id: 1, ipAddress: "192.0.2.1", ipLocation: "中国 · 上海", userAgent: "KsuserAuthMobile/1.0 (iOS 17.0)", browser: "Ksuser iOS", deviceType: "iPhone", createdAt: "2026-10-01T10:00:00", lastSeenAt: "2026-10-04T10:00:00", expiresAt: "2026-11-01T10:00:00", revokedAt: nil, online: true, current: true)]
        logs = [SensitiveLogItem(id: 1, operationType: "LOGIN", loginMethod: "passkey", loginMethods: ["passkey"], ipAddress: "192.0.2.1", ipLocation: "中国 · 上海", browser: "Ksuser iOS", deviceType: "iPhone", result: "success", failureReason: nil, riskScore: 10, actionTaken: "ALLOW", triggeredMultiErrorLock: false, triggeredRateLimitLock: false, durationMs: 80, createdAt: "2026-10-04T10:00:00")]
        adaptiveStatus = AdaptiveAuthStatus(sessionId: 1, riskScore: 10, riskLevel: "low", policyDecision: "ALLOW", policyVersion: "1.0.0", trusted: true, requiresStepUp: false, sessionFrozen: false, sensitiveVerified: false, sensitiveVerificationRemainingSeconds: 0, authAgeSeconds: 60, idleSeconds: 0, currentIp: "192.0.2.1", currentLocation: "中国 · 上海", sessionIp: "192.0.2.1", sessionLocation: "中国 · 上海", browser: "Ksuser iOS", deviceType: "iPhone", multiEndpointAlert: false, alertLevel: nil, alertTitle: nil, alertMessage: nil, alertRemainingSeconds: 0, recommendedAction: "当前登录状态安全", reasons: [])
        passwordRequirement = PasswordRequirement(minLength: 8, maxLength: 64, requireUppercase: true, requireLowercase: true, requireDigits: true, requireSpecialChars: false, rejectCommonWeakPasswords: true, requirementMessage: "8–64 个字符，包含大小写字母和数字")
    }
    #endif
}

private func capture<T: Sendable>(_ operation: @Sendable () async throws -> T) async -> Result<T, Error> {
    do { return .success(try await operation()) } catch { return .failure(error) }
}

enum QRCodeParser {
    struct Parsed: Equatable, Sendable { let code: String; let transfer: Bool }
    static func parse(_ content: String) throws -> Parsed {
        let raw = content.trimmingCharacters(in: .whitespacesAndNewlines)
        let prefixes = [("KSUSER-AUTH-XFER:v1:", true), ("KSUSER-AUTH-QR:", false)]
        for (prefix, transfer) in prefixes where raw.uppercased().hasPrefix(prefix.uppercased()) {
            let code = String(raw.dropFirst(prefix.count)).trimmingCharacters(in: .whitespacesAndNewlines)
            guard !code.isEmpty, code.count <= 4096 else { throw APIError.invalidQRCode }
            return Parsed(code: code, transfer: transfer)
        }
        guard let url = URLComponents(string: raw), url.scheme?.lowercased() == "https", url.host?.lowercased() == "auth.ksuser.cn" else { throw APIError.invalidQRCode }
        for (key, transfer) in [("transferCode", true), ("approveCode", false), ("code", false)] {
            if let code = url.queryItems?.first(where: { $0.name == key })?.value, !code.isEmpty, code.count <= 4096 { return Parsed(code: code, transfer: transfer) }
        }
        throw APIError.invalidQRCode
    }
}
