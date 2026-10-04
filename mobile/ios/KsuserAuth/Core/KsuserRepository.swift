import Foundation

protocol KsuserRepositoryProviding: Sendable { var client: APIClient { get } }
struct KsuserRepository: KsuserRepositoryProviding { let client: APIClient }

extension KsuserRepositoryProviding {
    func passwordRequirement() async throws -> PasswordRequirement { try await client.request("/info/password-requirement", authenticated: false) }
    func usernameAvailable(_ username: String) async throws -> Bool {
        let response: JSONValue = try await client.request("/auth/check-username", query: ["username": trimmedUserInput(username)], authenticated: false)
        guard let exists = response.objectValue?["exists"]?.boolValue else { throw APIError.invalidResponse }
        return !exists
    }
    func sendCode(email: String?, type: String) async throws {
        var body: [String: JSONValue] = ["type": .string(type)]
        if let email {
            let email = trimmedUserInput(email)
            if !email.isEmpty { body["email"] = .string(email) }
        }
        let _: EmptyPayload = try await client.request("/auth/send-code", method: "POST", body: body, authenticated: type != "login" && type != "register")
    }
    func login(email: String, password: String) async throws -> AuthResult { try await auth("/auth/login", body: ["email": .string(trimmedUserInput(email)), "password": .string(password)], source: "password") }
    func loginWithCode(email: String, code: String) async throws -> AuthResult { try await auth("/auth/login-with-code", body: ["email": .string(trimmedUserInput(email)), "code": .string(trimmedUserInput(code))], source: "email-code") }
    func loginWithQQ(_ credential: QQCredential) async throws -> AuthResult {
        try await auth("/oauth/qq/mobile-login", body: qqBody(credential), source: "qq")
    }
    func bindQQ(_ credential: QQCredential) async throws {
        let _: EmptyPayload = try await client.request("/oauth/qq/mobile-bind", method: "POST", body: qqBody(credential))
    }
    func unbindQQ() async throws {
        let _: EmptyPayload = try await client.request("/oauth/qq/unbind", method: "POST", body: [:])
    }
    func qqStatus() async throws -> Bool {
        let statuses: [OAuthAccountStatusItem] = try await client.request("/oauth/accounts/status")
        guard let qq = statuses.first(where: { $0.provider == "qq" }) else { throw APIError.invalidResponse }
        return qq.bound
    }
    func register(username: String, email: String, password: String, code: String) async throws -> String {
        let payload: TokenPayload = try await client.request("/auth/register", method: "POST", body: ["username": .string(trimmedUserInput(username)), "email": .string(trimmedUserInput(email)), "password": .string(password), "code": .string(trimmedUserInput(code))], authenticated: false)
        return payload.accessToken
    }
    func currentUser() async throws -> UserProfile { try await client.request("/auth/info", query: ["type": "details"]) }
    func logout(all: Bool = false) async throws { let _: EmptyPayload = try await client.request(all ? "/auth/logout/all" : "/auth/logout", method: "POST", body: [:]) }
    func passkeyAuthenticationOptions() async throws -> PasskeyAuthenticationOptions { try await client.request("/auth/passkey/authentication-options", method: "POST", body: [:], authenticated: false) }
    func loginWithPasskey(options: PasskeyAuthenticationOptions, payload: PasskeyAuthenticationPayload) async throws -> AuthResult {
        try await auth("/auth/passkey/authentication-verify", query: ["challengeId": options.challengeId], body: assertionBody(payload), source: "passkey")
    }
    func verifyMFA(challenge: MFAChallenge, code: String?, recoveryCode: String?) async throws -> String {
        var body: [String: JSONValue] = ["challengeId": .string(challenge.challengeId)]
        if let code, !trimmedUserInput(code).isEmpty { body["code"] = .string(trimmedUserInput(code)) }
        if let recoveryCode, !trimmedUserInput(recoveryCode).isEmpty { body["recoveryCode"] = .string(trimmedUserInput(recoveryCode).uppercased()) }
        let token: TokenPayload = try await client.request("/auth/totp/mfa-verify", method: "POST", body: body, authenticated: false)
        return token.accessToken
    }
    func verifyMFAPasskey(challenge: MFAChallenge, options: PasskeyAuthenticationOptions, payload: PasskeyAuthenticationPayload) async throws -> String {
        var body = assertionBody(payload); body["mfaChallengeId"] = .string(challenge.challengeId); body["passkeyChallengeId"] = .string(options.challengeId)
        let token: TokenPayload = try await client.request("/auth/passkey/mfa-verify", method: "POST", body: body, authenticated: false)
        return token.accessToken
    }
    func appleChallenge(purpose: String) async throws -> AppleChallenge {
        try await client.request("/oauth/apple/challenge", method: "POST", body: ["purpose": .string(purpose), "clientId": .string(Bundle.main.bundleIdentifier ?? "cn.ksuser.auth")], authenticated: purpose != "login")
    }
    func loginWithApple(challenge: AppleChallenge, credential: AppleCredential, binding: Bool = false) async throws -> AuthResult {
        try await auth("/oauth/apple/mobile-login", body: appleBody(challenge, credential), source: "apple", authenticated: binding)
    }
    func createAppleAccount(bindToken: String) async throws -> AuthResult {
        try await auth("/oauth/apple/register-pending", body: ["oauthBindToken": .string(bindToken), "acceptTerms": .bool(true)], source: "apple")
    }
    func bindOAuth(_ pending: PendingOAuth) async throws {
        let _: EmptyPayload = try await client.request("/oauth/\(pending.provider)/bind-pending", method: "POST", body: ["provider": .string(pending.provider), "oauthBindToken": .string(pending.bindToken), "bindToken": .string(pending.bindToken)])
    }
    func appleStatus() async throws -> Bool {
        let status: JSONValue = try await client.request("/oauth/apple/status")
        return status.objectValue?["bound"]?.boolValue ?? false
    }
    func verifySensitiveApple(challenge: AppleChallenge, credential: AppleCredential) async throws {
        let _: EmptyPayload = try await client.request("/oauth/apple/sensitive-verify", method: "POST", body: appleBody(challenge, credential))
    }
    func unbindApple() async throws { let _: EmptyPayload = try await client.request("/oauth/apple/unbind", method: "POST", body: [:]) }
    func updateProfile(key: String, value: String) async throws -> UserProfile {
        // Android preserves free-form profile input. Normalize only the validated username.
        let value = key == "username" ? trimmedUserInput(value) : value
        let _: EmptyPayload = try await client.request("/auth/update/profile", method: "POST", body: ["key": .string(key), "value": .string(value)])
        return try await currentUser()
    }
    func uploadAvatar(data: Data, mimeType: String) async throws -> UserProfile {
        let _: EmptyPayload = try await client.upload("/auth/upload/avatar", data: data, mimeType: mimeType)
        return try await currentUser()
    }
    func updateSetting(field: String, value: Bool? = nil, stringValue: String? = nil) async throws -> UserSettings {
        var body: [String: JSONValue] = ["field": .string(field)]
        if let value { body["value"] = .bool(value) }
        if let stringValue { body["stringValue"] = .string(stringValue) }
        return try await client.request("/auth/update/setting", method: "POST", body: body)
    }
    func sensitiveStatus() async throws -> SensitiveVerificationStatus { try await client.request("/auth/check-sensitive-verification") }
    func adaptiveStatus() async throws -> AdaptiveAuthStatus { try await client.request("/auth/adaptive-auth/status") }
    func verifySensitive(method: String, password: String?, code: String?, recoveryCode: String?) async throws {
        var body: [String: JSONValue] = ["method": .string(method)]
        if let password { body["password"] = .string(password) }; if let code { body["code"] = .string(trimmedUserInput(code)) }; if let recoveryCode { body["recoveryCode"] = .string(trimmedUserInput(recoveryCode).uppercased()) }
        let _: EmptyPayload = try await client.request("/auth/verify-sensitive", method: "POST", body: body)
    }
    func sensitivePasskeyOptions() async throws -> PasskeyAuthenticationOptions { try await client.request("/auth/passkey/sensitive-verification-options", method: "POST", body: [:]) }
    func verifySensitivePasskey(options: PasskeyAuthenticationOptions, payload: PasskeyAuthenticationPayload) async throws {
        let _: EmptyPayload = try await client.request("/auth/passkey/sensitive-verification-verify", method: "POST", query: ["challengeId": options.challengeId], body: assertionBody(payload))
    }
    func changeEmail(_ email: String, code: String) async throws -> UserProfile {
        let _: EmptyPayload = try await client.request("/auth/update/email", method: "POST", body: ["newEmail": .string(trimmedUserInput(email)), "code": .string(trimmedUserInput(code))])
        return try await currentUser()
    }
    func changePassword(_ password: String) async throws { let _: EmptyPayload = try await client.request("/auth/update/password", method: "POST", body: ["newPassword": .string(password)]) }
    func deleteAccount(confirmText: String) async throws { let _: EmptyPayload = try await client.request("/auth/delete", method: "POST", body: ["confirmText": .string(confirmText)]) }
    func sessions() async throws -> [SessionItem] { try await client.request("/auth/sessions") }
    func ksuserAuthorizations() async throws -> [KsuserAuthorizedApp] { try await client.request("/sso/authorizations") }
    func thirdPartyAuthorizations() async throws -> [ThirdPartyAuthorizedApp] { try await client.request("/oauth2/authorizations") }
    func revokeKsuserAuthorization(_ clientId: String) async throws {
        let _: EmptyPayload = try await client.request("/sso/authorizations/\(clientId)", method: "DELETE")
    }
    func revokeThirdPartyAuthorization(_ appId: String) async throws {
        let _: EmptyPayload = try await client.request("/oauth2/authorizations/\(appId)", method: "DELETE")
    }
    func revokeSession(_ id: Int64) async throws { let _: EmptyPayload = try await client.request("/auth/sessions/\(id)/revoke", method: "POST", body: [:]) }
    func logs(page: Int, operationType: String?, result: String?, startDate: String? = nil, endDate: String? = nil) async throws -> PaginatedSensitiveLogs {
        var query = ["page": String(page), "pageSize": "20"]
        if let operationType, !operationType.isEmpty { query["operationType"] = operationType }; if let result, !result.isEmpty { query["result"] = result }
        if let startDate { query["startDate"] = startDate }; if let endDate { query["endDate"] = endDate }
        return try await client.request("/auth/sensitive-logs", query: query)
    }
    func totpStatus() async throws -> TotpStatus { try await client.request("/auth/totp/status") }
    func startTOTP() async throws -> TotpRegistrationOptions { try await client.request("/auth/totp/registration-options", method: "POST", body: [:]) }
    func confirmTOTP(code: String, recoveryCodes: [String]) async throws {
        let _: EmptyPayload = try await client.request("/auth/totp/registration-verify", method: "POST", body: ["code": .string(trimmedUserInput(code)), "recoveryCodes": .array(recoveryCodes.map(JSONValue.string))])
    }
    func verifyTOTP(code: String?, recoveryCode: String?) async throws -> Bool {
        var body: [String: JSONValue] = [:]; if let code { body["code"] = .string(trimmedUserInput(code)) }; if let recoveryCode { body["recoveryCode"] = .string(trimmedUserInput(recoveryCode).uppercased()) }
        let result: JSONValue = try await client.request("/auth/totp/verify", method: "POST", body: body)
        return result.objectValue?["success"]?.boolValue ?? false
    }
    func recoveryCodes(regenerate: Bool = false) async throws -> [String] { try await client.request(regenerate ? "/auth/totp/recovery-codes/regenerate" : "/auth/totp/recovery-codes", method: regenerate ? "POST" : "GET", body: regenerate ? [:] : nil) }
    func disableTOTP() async throws { let _: EmptyPayload = try await client.request("/auth/totp/disable", method: "POST", body: [:]) }
    func passkeys() async throws -> [PasskeyListItem] { let list: PasskeyListResponse = try await client.request("/auth/passkey/list"); return list.passkeys }
    func passkeyRegistrationOptions(name: String) async throws -> PasskeyRegistrationOptions { try await client.request("/auth/passkey/registration-options", method: "POST", body: ["passkeyName": .string(trimmedUserInput(name)), "authenticatorType": .string("auto")]) }
    func createPasskey(name: String, payload: PasskeyRegistrationPayload) async throws {
        let _: JSONValue = try await client.request("/auth/passkey/registration-verify", method: "POST", body: ["passkeyName": .string(trimmedUserInput(name)), "credentialRawId": .string(payload.credentialRawId), "clientDataJSON": .string(payload.clientDataJSON), "attestationObject": .string(payload.attestationObject), "transports": .string(payload.transports)])
    }
    func renamePasskey(_ id: Int64, name: String) async throws { let _: EmptyPayload = try await client.request("/auth/passkey/\(id)/rename", method: "PUT", body: ["newName": .string(trimmedUserInput(name))]) }
    func deletePasskey(_ id: Int64) async throws { let _: EmptyPayload = try await client.request("/auth/passkey/\(id)", method: "DELETE") }
    func generateRecoveryTicket() async throws -> AccountRecoveryTicket { try await client.request("/auth/account-recovery/issue", method: "POST", body: [:]) }
    func previewQRCode(code: String, transfer: Bool) async throws -> QrScanPreview { try await client.request("/auth/qr/preview", query: [transfer ? "transferCode" : "approveCode": code]) }
    func approveQRCode(_ code: String) async throws { let _: EmptyPayload = try await client.request("/auth/qr/approve", method: "POST", body: ["approveCode": .string(code)]) }
    func exchangeTransfer(_ code: String) async throws -> String {
        let token: TokenPayload = try await client.request("/auth/session-transfer/exchange", method: "POST", body: ["transferCode": .string(code), "target": .string("mobile")], authenticated: false)
        return token.accessToken
    }
    func bridgeStatus(_ challengeId: String) async throws -> MobileBridgeStatusPayload { try await client.request("/auth/mobile-bridge/status", query: ["challengeId": challengeId], authenticated: false) }
    func approveBridge(_ challengeId: String) async throws -> MobileBridgeApproveResponse { try await client.request("/auth/mobile-bridge/approve", method: "POST", body: ["challengeId": .string(challengeId)]) }
    func cancelBridge(_ challengeId: String) async throws { let _: EmptyPayload = try await client.request("/auth/mobile-bridge/cancel", method: "POST", body: ["challengeId": .string(challengeId)], authenticated: false) }

    private func auth(_ path: String, query: [String: String] = [:], body: [String: JSONValue], source: String, authenticated: Bool = false) async throws -> AuthResult {
        let envelope: APIEnvelope<JSONValue> = try await client.envelope(path, method: "POST", query: query, body: body, authenticated: authenticated)
        guard let data = envelope.data?.objectValue else { throw APIError.server(envelope.code, envelope.msg ?? "认证失败") }
        if envelope.code == 201, let challengeId = data["challengeId"]?.stringValue {
            let method = data["method"]?.stringValue ?? "totp"
            let methods = data["methods"]?.arrayValue?.compactMap(\.stringValue) ?? [method]
            return .needsMFA(MFAChallenge(challengeId: challengeId, method: method, methods: methods.isEmpty ? [method] : methods, source: source))
        }
        if envelope.code == 200, let token = data["accessToken"]?.stringValue { return .success(token) }
        if envelope.code == 202, data["needBind"]?.boolValue == true, let bindToken = data["oauthBindToken"]?.stringValue {
            return .needsOAuth(PendingOAuth(provider: data["provider"]?.stringValue ?? source, bindToken: bindToken, message: data["message"]?.stringValue ?? envelope.msg, canRegister: data["canRegister"]?.boolValue ?? true, emailConflict: data["emailConflict"]?.boolValue ?? false))
        }
        throw APIError.server(envelope.code, envelope.msg ?? "认证失败")
    }
    private func assertionBody(_ payload: PasskeyAuthenticationPayload) -> [String: JSONValue] {
        ["credentialRawId": .string(payload.credentialRawId), "clientDataJSON": .string(payload.clientDataJSON), "authenticatorData": .string(payload.authenticatorData), "signature": .string(payload.signature)]
    }
    private func appleBody(_ challenge: AppleChallenge, _ credential: AppleCredential) -> [String: JSONValue] {
        var body: [String: JSONValue] = ["challengeId": .string(challenge.challengeId), "state": .string(challenge.state), "identityToken": .string(credential.identityToken), "authorizationCode": .string(credential.authorizationCode)]
        let name = [credential.givenName, credential.familyName].compactMap { $0 }.joined(separator: " ")
        if !name.isEmpty { body["fullName"] = .string(name) }
        return body
    }
    private func qqBody(_ credential: QQCredential) -> [String: JSONValue] {
        var body: [String: JSONValue] = ["appId": .string(credential.appId), "accessToken": .string(credential.accessToken), "openid": .string(credential.openid), "unionid": .string(credential.unionid)]
        if let expiresIn = credential.expiresIn { body["expiresIn"] = .string(expiresIn) }
        return body
    }
    private func trimmedUserInput(_ value: String) -> String {
        value.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
