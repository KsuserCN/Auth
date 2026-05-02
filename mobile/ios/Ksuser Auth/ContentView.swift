//
//  ContentView.swift
//  Ksuser Auth
//
//  Created by Ksuserkqy on 2026/4/25.
//

import SwiftUI
import Combine

private struct ApiEnvelope: Decodable {
    let code: Int?
    let msg: String?
}

private struct SendCodeRequest: Encodable {
    let email: String
    let type: String
}

private struct PasswordLoginRequest: Encodable {
    let email: String
    let password: String
}

private struct LoginWithCodeRequest: Encodable {
    let email: String
    let code: String
}

private struct UpdateProfileRequest: Encodable {
    let key: String
    let value: String
}

private struct UpdateSettingRequest: Encodable {
    let field: String
    let value: Bool?
    let stringValue: String?
}

private struct PasskeyRenameRequest: Encodable {
    let newName: String
}

private struct AuthResponsePayload: Decodable {
    let accessToken: String?
    let challengeId: String?
    let method: String?
    let methods: [String]?
}

private struct AuthEnvelope: Decodable {
    let code: Int?
    let msg: String?
    let data: AuthResponsePayload?
}

private struct UserInfoEnvelope: Decodable {
    let code: Int?
    let msg: String?
    let data: UserInfoPayload?
}

private struct UserInfoPayload: Decodable {
    let uuid: String
    let username: String
    let email: String
    let avatarUrl: String?
    let realName: String?
    let region: String?
    let bio: String?
    let settings: UserSettingsPayload?
}

private struct UserSettingsPayload: Decodable {
    let mfaEnabled: Bool
    let detectUnusualLogin: Bool
    let notifySensitiveActionEmail: Bool
    let subscribeNewsEmail: Bool
    let preferredMfaMethod: String?
    let preferredSensitiveMethod: String?
}

private struct ApiDataEnvelope<T: Decodable>: Decodable {
    let code: Int?
    let msg: String?
    let data: T?
}

private struct TotpStatusPayload: Decodable {
    let enabled: Bool
    let recoveryCodesCount: Int
}

private struct SensitiveVerificationStatusPayload: Decodable {
    let verified: Bool
    let remainingSeconds: Int
    let preferredMethod: String?
    let methods: [String]?
}

private struct AdaptiveAuthStatusPayload: Decodable {
    let riskScore: Int?
    let riskLevel: String?
    let policyDecision: String?
    let policyVersion: String?
    let trusted: Bool?
    let requiresStepUp: Bool?
    let sessionFrozen: Bool?
    let sensitiveVerified: Bool?
    let sensitiveVerificationRemainingSeconds: Int?
    let authAgeSeconds: Int?
    let idleSeconds: Int?
    let currentLocation: String?
    let deviceType: String?
    let multiEndpointAlert: Bool?
    let alertLevel: String?
    let alertTitle: String?
    let alertMessage: String?
    let alertRemainingSeconds: Int?
    let recommendedAction: String?
    let reasons: [String]?
}

private struct SessionItemPayload: Decodable, Identifiable {
    let id: Int64
    let ipAddress: String
    let ipLocation: String?
    let userAgent: String?
    let browser: String?
    let deviceType: String?
    let createdAt: String
    let lastSeenAt: String
    let expiresAt: String
    let revokedAt: String?
    let online: Bool
    let current: Bool
}

private struct SensitiveLogItemPayload: Decodable, Identifiable {
    let id: Int64
    let operationType: String
    let loginMethod: String?
    let loginMethods: [String]?
    let ipAddress: String
    let ipLocation: String?
    let browser: String?
    let deviceType: String?
    let result: String
    let failureReason: String?
    let riskScore: Int
    let actionTaken: String?
    let triggeredMultiErrorLock: Bool
    let triggeredRateLimitLock: Bool
    let durationMs: Int
    let createdAt: String
}

private struct PaginatedSensitiveLogsPayload: Decodable {
    let data: [SensitiveLogItemPayload]
    let page: Int
    let pageSize: Int
    let total: Int
    let totalPages: Int
}

private struct PasskeyItemPayload: Decodable, Identifiable {
    let id: Int64
    let name: String
    let transports: String
    let lastUsedAt: String?
    let createdAt: String
}

private struct PasskeyListPayload: Decodable {
    let passkeys: [PasskeyItemPayload]?
}

private struct SecuritySnapshot {
    let settings: UserSettingsPayload
    let totpStatus: TotpStatusPayload
    let sensitiveStatus: SensitiveVerificationStatusPayload
    let adaptiveStatus: AdaptiveAuthStatusPayload
    let passkeys: [PasskeyItemPayload]
}

private struct ProfileUser {
    var username: String
    var realName: String
    var region: String
    var bio: String
    var email: String
    var uuid: String
    var avatarURL: String?
    var settings: UserSettingsPayload?
}

private enum ProfileField: String, Identifiable {
    case username
    case realName
    case region
    case bio

    var id: String { rawValue }

    var title: String {
        switch self {
        case .username:
            return "用户名"
        case .realName:
            return "真实姓名"
        case .region:
            return "地区"
        case .bio:
            return "个人简介"
        }
    }
}

private enum AuthNetworkError: LocalizedError {
    case invalidBaseURL(String)
    case invalidResponse
    case httpError(status: Int, body: String)
    case businessError(code: Int, message: String)
    case unauthorized

    var errorDescription: String? {
        switch self {
        case let .invalidBaseURL(base):
            return "API_BASE_URL 无效: \(base)"
        case .invalidResponse:
            return "服务端响应格式无效"
        case let .httpError(status, body):
            return "HTTP \(status): \(body)"
        case let .businessError(code, message):
            return "业务错误(\(code)): \(message)"
        case .unauthorized:
            return "未登录或登录已过期"
        }
    }
}

private actor AuthAPIClient {
    private let baseURL: URL
    private let session: URLSession
    private let log: @Sendable (String) -> Void
    private var accessToken: String?

    private static let accessTokenStorageKey = "ksuser.auth.ios.access_token"

    init(baseURLString: String, log: @escaping @Sendable (String) -> Void) throws {
        guard let parsed = URL(string: baseURLString.trimmingCharacters(in: .whitespacesAndNewlines)),
              parsed.scheme != nil,
              parsed.host != nil else {
            throw AuthNetworkError.invalidBaseURL(baseURLString)
        }
        self.baseURL = parsed
        self.log = log

        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 30
        configuration.timeoutIntervalForResource = 30
        configuration.httpCookieStorage = HTTPCookieStorage.shared
        configuration.httpShouldSetCookies = true
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        self.session = URLSession(configuration: configuration)
        self.accessToken = UserDefaults.standard.string(forKey: Self.accessTokenStorageKey)
    }

    static func resolvedBaseURLString() -> String {
        let env = ProcessInfo.processInfo.environment["API_BASE_URL"]?.trimmingCharacters(in: .whitespacesAndNewlines)
        if let env, !env.isEmpty {
            return env
        }
        return "https://api.ksuser.cn"
    }

    func sendLoginCode(email: String) async throws {
        let normalizedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        let csrfToken = try await bootstrapCsrfTokenIfNeeded()
        let endpoint = makeURL(path: "/auth/send-code")

        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("KsuserAuthiOS/1.0", forHTTPHeaderField: "User-Agent")
        if let csrfToken, !csrfToken.isEmpty {
            request.setValue(csrfToken, forHTTPHeaderField: "X-XSRF-TOKEN")
        }

        let body = SendCodeRequest(email: normalizedEmail, type: "login")
        let bodyData = try JSONEncoder().encode(body)
        request.httpBody = bodyData

        log("➡️ [POST] \(endpoint.absoluteString)")
        log("   headers: \(request.allHTTPHeaderFields ?? [:])")
        log("   body: \(String(data: bodyData, encoding: .utf8) ?? "<non-utf8>")")

        let (data, response) = try await session.data(for: request)
        try logResponse(prefix: "⬅️", response: response, data: data)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw AuthNetworkError.invalidResponse
        }
        let responseText = String(data: data, encoding: .utf8) ?? "<\(data.count) bytes>"
        guard (200...299).contains(httpResponse.statusCode) else {
            throw AuthNetworkError.httpError(status: httpResponse.statusCode, body: responseText)
        }

        let envelope = try decodeEnvelope(from: data)
        let businessCode = envelope.code ?? -1
        if businessCode != 200 {
            throw AuthNetworkError.businessError(code: businessCode, message: envelope.msg ?? "请求失败")
        }
    }

    func loginWithPassword(email: String, password: String) async throws -> Bool {
        let body = PasswordLoginRequest(
            email: email.trimmingCharacters(in: .whitespacesAndNewlines),
            password: password
        )
        return try await performLogin(path: "/auth/login", body: body)
    }

    func loginWithCode(email: String, code: String) async throws -> Bool {
        let body = LoginWithCodeRequest(
            email: email.trimmingCharacters(in: .whitespacesAndNewlines),
            code: code.trimmingCharacters(in: .whitespacesAndNewlines)
        )
        return try await performLogin(path: "/auth/login-with-code", body: body)
    }

    func fetchCurrentUser() async throws -> ProfileUser {
        var components = URLComponents(url: makeURL(path: "/auth/info"), resolvingAgainstBaseURL: false)
        components?.queryItems = [URLQueryItem(name: "type", value: "details")]
        guard let endpoint = components?.url else {
            throw AuthNetworkError.invalidResponse
        }

        var request = URLRequest(url: endpoint)
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("KsuserAuthiOS/1.0", forHTTPHeaderField: "User-Agent")
        let (data, _) = try await performAuthorizedRequest(request: request, bodyData: nil)

        let envelope = try JSONDecoder().decode(UserInfoEnvelope.self, from: data)
        let code = envelope.code ?? -1
        guard code == 200, let user = envelope.data else {
            throw AuthNetworkError.businessError(code: code, message: envelope.msg ?? "获取用户信息失败")
        }
        return ProfileUser(
            username: user.username,
            realName: user.realName ?? "",
            region: user.region ?? "",
            bio: user.bio ?? "",
            email: user.email,
            uuid: user.uuid,
            avatarURL: user.avatarUrl,
            settings: user.settings
        )
    }

    func updateProfileField(fieldKey: String, value: String) async throws {
        let csrfToken = try await bootstrapCsrfTokenIfNeeded()
        let endpoint = makeURL(path: "/auth/update/profile")

        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("KsuserAuthiOS/1.0", forHTTPHeaderField: "User-Agent")
        if let csrfToken, !csrfToken.isEmpty {
            request.setValue(csrfToken, forHTTPHeaderField: "X-XSRF-TOKEN")
        }

        let requestBody = UpdateProfileRequest(key: fieldKey, value: value)
        let bodyData = try JSONEncoder().encode(requestBody)
        request.httpBody = bodyData

        let (data, _) = try await performAuthorizedRequest(request: request, bodyData: bodyData)

        let envelope = try decodeEnvelope(from: data)
        let businessCode = envelope.code ?? -1
        if businessCode != 200 {
            throw AuthNetworkError.businessError(code: businessCode, message: envelope.msg ?? "更新资料失败")
        }
    }

    func logoutAll() async throws {
        let csrfToken = try await bootstrapCsrfTokenIfNeeded()
        var request = URLRequest(url: makeURL(path: "/auth/logout/all"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("KsuserAuthiOS/1.0", forHTTPHeaderField: "User-Agent")
        if let csrfToken, !csrfToken.isEmpty {
            request.setValue(csrfToken, forHTTPHeaderField: "X-XSRF-TOKEN")
        }
        let (data, _) = try await performAuthorizedRequest(request: request, bodyData: nil)
        let envelope = try decodeEnvelope(from: data)
        let businessCode = envelope.code ?? -1
        if businessCode != 200 {
            throw AuthNetworkError.businessError(code: businessCode, message: envelope.msg ?? "退出所有设备失败")
        }
    }

    func updateBooleanSetting(field: String, value: Bool) async throws -> UserSettingsPayload {
        try await updateSetting(field: field, value: value, stringValue: nil)
    }

    func updateStringSetting(field: String, value: String) async throws -> UserSettingsPayload {
        try await updateSetting(field: field, value: nil, stringValue: value)
    }

    func fetchTotpStatus() async throws -> TotpStatusPayload {
        var request = URLRequest(url: makeURL(path: "/auth/totp/status"))
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("KsuserAuthiOS/1.0", forHTTPHeaderField: "User-Agent")
        let (data, _) = try await performAuthorizedRequest(request: request, bodyData: nil)
        let envelope = try decodeDataEnvelope(TotpStatusPayload.self, from: data)
        let businessCode = envelope.code ?? -1
        guard businessCode == 200, let payload = envelope.data else {
            throw AuthNetworkError.businessError(code: businessCode, message: envelope.msg ?? "加载 TOTP 状态失败")
        }
        return payload
    }

    func fetchSensitiveVerificationStatus() async throws -> SensitiveVerificationStatusPayload {
        var request = URLRequest(url: makeURL(path: "/auth/check-sensitive-verification"))
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("KsuserAuthiOS/1.0", forHTTPHeaderField: "User-Agent")
        let (data, _) = try await performAuthorizedRequest(request: request, bodyData: nil)
        let envelope = try decodeDataEnvelope(SensitiveVerificationStatusPayload.self, from: data)
        let businessCode = envelope.code ?? -1
        guard businessCode == 200, let payload = envelope.data else {
            throw AuthNetworkError.businessError(code: businessCode, message: envelope.msg ?? "加载敏感验证状态失败")
        }
        return payload
    }

    func fetchAdaptiveStatus() async throws -> AdaptiveAuthStatusPayload {
        var request = URLRequest(url: makeURL(path: "/auth/adaptive-auth/status"))
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("KsuserAuthiOS/1.0", forHTTPHeaderField: "User-Agent")
        let (data, _) = try await performAuthorizedRequest(request: request, bodyData: nil)
        let envelope = try decodeDataEnvelope(AdaptiveAuthStatusPayload.self, from: data)
        let businessCode = envelope.code ?? -1
        guard businessCode == 200, let payload = envelope.data else {
            throw AuthNetworkError.businessError(code: businessCode, message: envelope.msg ?? "加载连续认证状态失败")
        }
        return payload
    }

    func fetchPasskeyList() async throws -> [PasskeyItemPayload] {
        var request = URLRequest(url: makeURL(path: "/auth/passkey/list"))
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("KsuserAuthiOS/1.0", forHTTPHeaderField: "User-Agent")
        let (data, _) = try await performAuthorizedRequest(request: request, bodyData: nil)
        let envelope = try decodeDataEnvelope(PasskeyListPayload.self, from: data)
        let businessCode = envelope.code ?? -1
        guard businessCode == 200 else {
            throw AuthNetworkError.businessError(code: businessCode, message: envelope.msg ?? "加载 Passkey 列表失败")
        }
        return envelope.data?.passkeys ?? []
    }

    func fetchSessions() async throws -> [SessionItemPayload] {
        var request = URLRequest(url: makeURL(path: "/auth/sessions"))
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("KsuserAuthiOS/1.0", forHTTPHeaderField: "User-Agent")
        let (data, _) = try await performAuthorizedRequest(request: request, bodyData: nil)
        let envelope = try decodeDataEnvelope([SessionItemPayload].self, from: data)
        let businessCode = envelope.code ?? -1
        guard businessCode == 200 else {
            throw AuthNetworkError.businessError(code: businessCode, message: envelope.msg ?? "加载会话失败")
        }
        return envelope.data ?? []
    }

    func revokeSession(sessionId: Int64) async throws {
        let csrfToken = try await bootstrapCsrfTokenIfNeeded()
        var request = URLRequest(url: makeURL(path: "/auth/sessions/\(sessionId)/revoke"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("KsuserAuthiOS/1.0", forHTTPHeaderField: "User-Agent")
        if let csrfToken, !csrfToken.isEmpty {
            request.setValue(csrfToken, forHTTPHeaderField: "X-XSRF-TOKEN")
        }
        let (data, _) = try await performAuthorizedRequest(request: request, bodyData: nil)
        let envelope = try decodeEnvelope(from: data)
        let businessCode = envelope.code ?? -1
        if businessCode != 200 {
            throw AuthNetworkError.businessError(code: businessCode, message: envelope.msg ?? "撤销会话失败")
        }
    }

    func fetchSensitiveLogs(
        page: Int = 1,
        pageSize: Int = 20,
        operationType: String?,
        result: String?,
    ) async throws -> PaginatedSensitiveLogsPayload {
        var components = URLComponents(url: makeURL(path: "/auth/sensitive-logs"), resolvingAgainstBaseURL: false)
        components?.queryItems = [
            URLQueryItem(name: "page", value: String(page)),
            URLQueryItem(name: "pageSize", value: String(pageSize)),
            URLQueryItem(name: "operationType", value: operationType),
            URLQueryItem(name: "result", value: result),
        ].filter { $0.value != nil }
        guard let endpoint = components?.url else {
            throw AuthNetworkError.invalidResponse
        }

        var request = URLRequest(url: endpoint)
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("KsuserAuthiOS/1.0", forHTTPHeaderField: "User-Agent")
        let (data, _) = try await performAuthorizedRequest(request: request, bodyData: nil)
        let envelope = try decodeDataEnvelope(PaginatedSensitiveLogsPayload.self, from: data)
        let businessCode = envelope.code ?? -1
        guard businessCode == 200 else {
            throw AuthNetworkError.businessError(code: businessCode, message: envelope.msg ?? "加载敏感日志失败")
        }
        return envelope.data ?? PaginatedSensitiveLogsPayload(data: [], page: page, pageSize: pageSize, total: 0, totalPages: 0)
    }

    func clearAuth() {
        accessToken = nil
        UserDefaults.standard.removeObject(forKey: Self.accessTokenStorageKey)
        clearCookies()
    }

    private func performLogin<T: Encodable>(path: String, body: T) async throws -> Bool {
        let csrfToken = try await bootstrapCsrfTokenIfNeeded()
        let endpoint = makeURL(path: path)

        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("KsuserAuthiOS/1.0", forHTTPHeaderField: "User-Agent")
        if let csrfToken, !csrfToken.isEmpty {
            request.setValue(csrfToken, forHTTPHeaderField: "X-XSRF-TOKEN")
        }

        let bodyData = try JSONEncoder().encode(body)
        request.httpBody = bodyData
        log("➡️ [POST] \(endpoint.absoluteString)")
        log("   headers: \(request.allHTTPHeaderFields ?? [:])")
        log("   body: \(String(data: bodyData, encoding: .utf8) ?? "<non-utf8>")")

        let (data, response) = try await session.data(for: request)
        try logResponse(prefix: "⬅️", response: response, data: data)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw AuthNetworkError.invalidResponse
        }
        let responseText = String(data: data, encoding: .utf8) ?? "<\(data.count) bytes>"
        guard (200...299).contains(httpResponse.statusCode) else {
            throw AuthNetworkError.httpError(status: httpResponse.statusCode, body: responseText)
        }

        let envelope = try JSONDecoder().decode(AuthEnvelope.self, from: data)
        let businessCode = envelope.code ?? -1
        if businessCode == 201 || envelope.data?.challengeId != nil {
            return false
        }
        guard businessCode == 200 else {
            throw AuthNetworkError.businessError(code: businessCode, message: envelope.msg ?? "登录失败")
        }
        guard let token = envelope.data?.accessToken, !token.isEmpty else {
            throw AuthNetworkError.invalidResponse
        }

        accessToken = token
        UserDefaults.standard.set(token, forKey: Self.accessTokenStorageKey)
        return true
    }

    private func performAuthorizedRequest(
        request: URLRequest,
        bodyData: Data?,
    ) async throws -> (Data, HTTPURLResponse) {
        if accessToken == nil || accessToken?.isEmpty == true {
            log("ℹ️ 当前无 accessToken，尝试使用 refreshToken bootstrap 会话")
            let bootstrapped = try await refreshAccessToken(previousToken: nil)
            if !bootstrapped {
                throw AuthNetworkError.unauthorized
            }
        }
        guard let token = accessToken, !token.isEmpty else {
            throw AuthNetworkError.unauthorized
        }

        var authorizedRequest = request
        authorizedRequest.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        if let bodyData {
            authorizedRequest.httpBody = bodyData
        }

        log("➡️ [\(authorizedRequest.httpMethod ?? "GET")] \(authorizedRequest.url?.absoluteString ?? "<unknown-url>")")
        log("   headers: \(authorizedRequest.allHTTPHeaderFields ?? [:])")
        if let bodyData {
            log("   body: \(String(data: bodyData, encoding: .utf8) ?? "<non-utf8>")")
        }

        let (data, response) = try await session.data(for: authorizedRequest)
        try logResponse(prefix: "⬅️", response: response, data: data)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw AuthNetworkError.invalidResponse
        }

        if httpResponse.statusCode == 401 {
            log("⚠️ accessToken 已失效，准备调用 /auth/refresh 刷新")
            let refreshed = try await refreshAccessToken(previousToken: token)
            guard refreshed, let newToken = accessToken, !newToken.isEmpty else {
                throw AuthNetworkError.unauthorized
            }

            var retryRequest = request
            retryRequest.setValue("Bearer \(newToken)", forHTTPHeaderField: "Authorization")
            if let bodyData {
                retryRequest.httpBody = bodyData
            }

            log("🔁 重试请求: [\(retryRequest.httpMethod ?? "GET")] \(retryRequest.url?.absoluteString ?? "<unknown-url>")")
            let (retryData, retryResponse) = try await session.data(for: retryRequest)
            try logResponse(prefix: "⬅️", response: retryResponse, data: retryData)
            guard let retryHTTP = retryResponse as? HTTPURLResponse else {
                throw AuthNetworkError.invalidResponse
            }

            if retryHTTP.statusCode == 401 {
                throw AuthNetworkError.unauthorized
            }
            let retryText = String(data: retryData, encoding: .utf8) ?? "<\(retryData.count) bytes>"
            guard (200...299).contains(retryHTTP.statusCode) else {
                throw AuthNetworkError.httpError(status: retryHTTP.statusCode, body: retryText)
            }
            return (retryData, retryHTTP)
        }

        let responseText = String(data: data, encoding: .utf8) ?? "<\(data.count) bytes>"
        guard (200...299).contains(httpResponse.statusCode) else {
            throw AuthNetworkError.httpError(status: httpResponse.statusCode, body: responseText)
        }
        return (data, httpResponse)
    }

    private func refreshAccessToken(previousToken: String?) async throws -> Bool {
        if !hasRefreshTokenCookie() {
            log("❌ 未检测到 refreshToken cookie，无法刷新 accessToken")
            clearAuth()
            return false
        }

        let csrfToken = try await bootstrapCsrfTokenIfNeeded()
        let endpoint = makeURL(path: "/auth/refresh")
        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("KsuserAuthiOS/1.0", forHTTPHeaderField: "User-Agent")
        if let csrfToken, !csrfToken.isEmpty {
            request.setValue(csrfToken, forHTTPHeaderField: "X-XSRF-TOKEN")
        }
        if let previousToken, !previousToken.isEmpty {
            request.setValue("Bearer \(previousToken)", forHTTPHeaderField: "Authorization")
        }

        log("➡️ [POST] \(endpoint.absoluteString)")
        log("   headers: \(request.allHTTPHeaderFields ?? [:])")

        do {
            let (data, response) = try await session.data(for: request)
            try logResponse(prefix: "⬅️", response: response, data: data)
            guard let httpResponse = response as? HTTPURLResponse else {
                clearAuth()
                throw AuthNetworkError.invalidResponse
            }
            let responseText = String(data: data, encoding: .utf8) ?? "<\(data.count) bytes>"
            guard (200...299).contains(httpResponse.statusCode) else {
                if httpResponse.statusCode == 401 || httpResponse.statusCode == 403 {
                    clearAuth()
                    return false
                }
                clearAuth()
                throw AuthNetworkError.httpError(status: httpResponse.statusCode, body: responseText)
            }

            let envelope = try JSONDecoder().decode(AuthEnvelope.self, from: data)
            let code = envelope.code ?? -1
            guard code == 200, let token = envelope.data?.accessToken, !token.isEmpty else {
                clearAuth()
                return false
            }

            accessToken = token
            UserDefaults.standard.set(token, forKey: Self.accessTokenStorageKey)
            log("✅ /auth/refresh 成功，accessToken 已更新")
            return true
        } catch {
            clearAuth()
            throw error
        }
    }

    private func hasRefreshTokenCookie() -> Bool {
        guard let host = baseURL.host else { return false }
        let allCookies = HTTPCookieStorage.shared.cookies ?? []
        let matched = allCookies.filter { cookie in
            cookie.name == "refreshToken" &&
            (cookie.domain == host || cookie.domain.hasSuffix(".\(host)") || host.hasSuffix(cookie.domain))
        }
        if matched.isEmpty {
            let cookieDomains = allCookies.map { "\($0.name)@\($0.domain)" }.joined(separator: ", ")
            log("ℹ️ refreshToken cookie 未命中，当前 cookie 域列表: [\(cookieDomains)]")
        } else {
            log("ℹ️ 命中 refreshToken cookie 域: \(matched.map { $0.domain })")
        }
        return !matched.isEmpty
    }

    private func clearCookies() {
        let storage = HTTPCookieStorage.shared
        guard let host = baseURL.host else { return }
        for cookie in storage.cookies ?? [] where cookie.domain == host || cookie.domain.hasSuffix(".\(host)") || host.hasSuffix(cookie.domain) {
            storage.deleteCookie(cookie)
        }
    }

    private func bootstrapCsrfTokenIfNeeded() async throws -> String? {
        let existing = currentCookieValue(name: "XSRF-TOKEN")
        if let existing, !existing.isEmpty {
            log("ℹ️ 复用现有 XSRF-TOKEN")
            return existing
        }

        let endpoint = makeURL(path: "/auth/csrf-token")
        var request = URLRequest(url: endpoint)
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("KsuserAuthiOS/1.0", forHTTPHeaderField: "User-Agent")

        log("➡️ [GET] \(endpoint.absoluteString)")
        log("   headers: \(request.allHTTPHeaderFields ?? [:])")

        let (data, response) = try await session.data(for: request)
        try logResponse(prefix: "⬅️", response: response, data: data)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw AuthNetworkError.invalidResponse
        }
        let responseText = String(data: data, encoding: .utf8) ?? "<\(data.count) bytes>"
        guard (200...299).contains(httpResponse.statusCode) else {
            throw AuthNetworkError.httpError(status: httpResponse.statusCode, body: responseText)
        }

        let token = currentCookieValue(name: "XSRF-TOKEN")
        if token == nil {
            log("⚠️ 未在 Cookie 中找到 XSRF-TOKEN，后续请求可能被拒绝")
        }
        return token
    }

    private func currentCookieValue(name: String) -> String? {
        HTTPCookieStorage.shared.cookies(for: baseURL)?.first(where: { $0.name == name })?.value
    }

    private func makeURL(path: String) -> URL {
        let normalizedPath = path.hasPrefix("/") ? String(path.dropFirst()) : path
        return baseURL.appendingPathComponent(normalizedPath)
    }

    private func decodeEnvelope(from data: Data) throws -> ApiEnvelope {
        do {
            return try JSONDecoder().decode(ApiEnvelope.self, from: data)
        } catch {
            log("❌ 解码响应失败: \(error.localizedDescription)")
            throw AuthNetworkError.invalidResponse
        }
    }

    private func decodeDataEnvelope<T: Decodable>(_ type: T.Type, from data: Data) throws -> ApiDataEnvelope<T> {
        do {
            return try JSONDecoder().decode(ApiDataEnvelope<T>.self, from: data)
        } catch {
            log("❌ 解码响应失败(\(T.self)): \(error.localizedDescription)")
            throw AuthNetworkError.invalidResponse
        }
    }

    private func updateSetting(
        field: String,
        value: Bool?,
        stringValue: String?,
    ) async throws -> UserSettingsPayload {
        let csrfToken = try await bootstrapCsrfTokenIfNeeded()
        var request = URLRequest(url: makeURL(path: "/auth/update/setting"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("KsuserAuthiOS/1.0", forHTTPHeaderField: "User-Agent")
        if let csrfToken, !csrfToken.isEmpty {
            request.setValue(csrfToken, forHTTPHeaderField: "X-XSRF-TOKEN")
        }

        let body = UpdateSettingRequest(field: field, value: value, stringValue: stringValue)
        let bodyData = try JSONEncoder().encode(body)
        request.httpBody = bodyData
        let (data, _) = try await performAuthorizedRequest(request: request, bodyData: bodyData)
        let envelope = try decodeDataEnvelope(UserSettingsPayload.self, from: data)
        let businessCode = envelope.code ?? -1
        guard businessCode == 200, let payload = envelope.data else {
            throw AuthNetworkError.businessError(code: businessCode, message: envelope.msg ?? "更新安全设置失败")
        }
        return payload
    }

    private func logResponse(prefix: String, response: URLResponse, data: Data) throws {
        guard let httpResponse = response as? HTTPURLResponse else {
            log("❌ 非 HTTP 响应: \(response)")
            throw AuthNetworkError.invalidResponse
        }

        let body = String(data: data, encoding: .utf8) ?? "<\(data.count) bytes>"
        log("\(prefix) [\(httpResponse.statusCode)] \(httpResponse.url?.absoluteString ?? "<unknown-url>")")
        log("   headers: \(httpResponse.allHeaderFields)")
        log("   body: \(body)")
    }
}

private enum LoginMethod: String, CaseIterable, Identifiable {
    case password
    case code

    var id: String { rawValue }

    var title: String {
        switch self {
        case .password:
            return "密码登录"
        case .code:
            return "验证码登录"
        }
    }
}

private enum MainTab: String, CaseIterable, Hashable {
    case home
    case profile
    case security
    case sessions
    case logs

    var title: String {
        switch self {
        case .home:
            return "概览"
        case .profile:
            return "资料"
        case .security:
            return "安全"
        case .sessions:
            return "会话"
        case .logs:
            return "日志"
        }
    }

    var systemImageName: String {
        switch self {
        case .home:
            return "house"
        case .profile:
            return "person"
        case .security:
            return "lock.shield"
        case .sessions:
            return "rectangle.on.rectangle"
        case .logs:
            return "doc.text"
        }
    }
}

@MainActor
private final class AuthFlowViewModel: ObservableObject {
    @Published var isAuthenticated = false
    @Published var isBusy = false
    @Published var loginMethod: LoginMethod = .password
    @Published var email = ""
    @Published var password = ""
    @Published var code = ""
    @Published var toastMessage: String?
    @Published var requestLogs: [String] = []
    @Published var apiBaseURL: String = AuthAPIClient.resolvedBaseURLString()
    @Published var profileUser = ProfileUser(
        username: "",
        realName: "",
        region: "",
        bio: "",
        email: "",
        uuid: UUID().uuidString.lowercased(),
        avatarURL: nil,
        settings: nil
    )
    private var cachedClient: AuthAPIClient?

    var canSubmitLogin: Bool {
        if isBusy {
            return false
        }
        if email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return false
        }
        switch loginMethod {
        case .password:
            return !password.isEmpty
        case .code:
            return !code.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        }
    }

    var canSendCode: Bool {
        !isBusy && !email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    init() {
        appendLog("🧭 API_BASE_URL = \(apiBaseURL)")
        Task {
            do {
                let client = try apiClient()
                let user = try await client.fetchCurrentUser()
                profileUser = user
                isAuthenticated = true
                appendLog("✅ 使用本地会话恢复登录成功")
            } catch {
                appendLog("ℹ️ 当前无可用会话，等待登录")
            }
        }
    }

    func login() async {
        guard canSubmitLogin else { return }

        isBusy = true
        defer { isBusy = false }

        do {
            let client = try apiClient()
            let success: Bool
            if loginMethod == .password {
                success = try await client.loginWithPassword(
                    email: email.trimmingCharacters(in: .whitespacesAndNewlines),
                    password: password
                )
            } else {
                success = try await client.loginWithCode(
                    email: email.trimmingCharacters(in: .whitespacesAndNewlines),
                    code: code.trimmingCharacters(in: .whitespacesAndNewlines)
                )
            }

            guard success else {
                toastMessage = "账号需要二次验证（MFA），iOS 端将在后续版本接入。"
                appendLog("⚠️ 登录返回 MFA 挑战，当前版本未接入 MFA 流程")
                return
            }

            let user = try await client.fetchCurrentUser()
            profileUser = user
            isAuthenticated = true
            toastMessage = "登录成功"
            appendLog("✅ 登录成功，已获取用户资料: \(user.username)")
        } catch {
            let description = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
            toastMessage = "登录失败: \(description)"
            appendLog("❌ 登录失败: \(description)")
        }
    }

    func sendLoginCode() async {
        guard canSendCode else { return }

        isBusy = true
        defer { isBusy = false }

        let targetEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        appendLog("📨 准备发送登录验证码: \(targetEmail)")

        do {
            let client = try apiClient()
            try await client.sendLoginCode(email: targetEmail)
            toastMessage = "验证码已发送"
            appendLog("✅ 验证码发送成功: \(targetEmail)")
        } catch {
            let description = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
            toastMessage = "发送失败: \(description)"
            appendLog("❌ 验证码发送失败: \(description)")
        }
    }

    func showNotImplemented(_ message: String) {
        toastMessage = message
    }

    func logout() {
        isAuthenticated = false
        password = ""
        code = ""
        Task {
            do {
                let client = try apiClient()
                await client.clearAuth()
            } catch {
                appendLog("⚠️ 清理会话失败: \(error.localizedDescription)")
            }
        }
        toastMessage = "已退出登录"
    }

    func refreshProfile() async {
        isBusy = true
        defer { isBusy = false }
        do {
            let client = try apiClient()
            let user = try await client.fetchCurrentUser()
            profileUser = user
            appendLog("🔄 资料刷新成功: \(user.username)")
        } catch {
            let description = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
            toastMessage = "刷新资料失败: \(description)"
            appendLog("❌ 刷新资料失败: \(description)")
        }
    }

    func refreshHomeData() async {
        await refreshProfile()
    }

    func fetchSecuritySnapshot() async -> SecuritySnapshot? {
        do {
            let client = try apiClient()
            async let totp = client.fetchTotpStatus()
            async let sensitive = client.fetchSensitiveVerificationStatus()
            async let adaptive = client.fetchAdaptiveStatus()
            async let passkeys = client.fetchPasskeyList()
            let settings = profileUser.settings ?? UserSettingsPayload(
                mfaEnabled: false,
                detectUnusualLogin: true,
                notifySensitiveActionEmail: true,
                subscribeNewsEmail: false,
                preferredMfaMethod: "totp",
                preferredSensitiveMethod: "password"
            )
            return try await SecuritySnapshot(
                settings: settings,
                totpStatus: totp,
                sensitiveStatus: sensitive,
                adaptiveStatus: adaptive,
                passkeys: passkeys
            )
        } catch {
            handleAsyncError(prefix: "加载安全状态失败", error: error)
            return nil
        }
    }

    func updateSecurityBooleanSetting(field: String, value: Bool) async -> UserSettingsPayload? {
        do {
            let client = try apiClient()
            let settings = try await client.updateBooleanSetting(field: field, value: value)
            profileUser.settings = settings
            toastMessage = "设置已更新"
            return settings
        } catch {
            handleAsyncError(prefix: "更新安全设置失败", error: error)
            return nil
        }
    }

    func updateSecurityStringSetting(field: String, value: String) async -> UserSettingsPayload? {
        do {
            let client = try apiClient()
            let settings = try await client.updateStringSetting(field: field, value: value)
            profileUser.settings = settings
            toastMessage = "设置已更新"
            return settings
        } catch {
            handleAsyncError(prefix: "更新安全设置失败", error: error)
            return nil
        }
    }

    func fetchSessions() async -> [SessionItemPayload] {
        do {
            let client = try apiClient()
            return try await client.fetchSessions()
        } catch {
            handleAsyncError(prefix: "加载会话失败", error: error)
            return []
        }
    }

    func revokeSession(sessionId: Int64) async -> Bool {
        do {
            let client = try apiClient()
            try await client.revokeSession(sessionId: sessionId)
            toastMessage = "会话已撤销"
            return true
        } catch {
            handleAsyncError(prefix: "撤销会话失败", error: error)
            return false
        }
    }

    func logoutAllDevices() async -> Bool {
        do {
            let client = try apiClient()
            try await client.logoutAll()
            await client.clearAuth()
            isAuthenticated = false
            toastMessage = "已退出所有设备"
            return true
        } catch {
            handleAsyncError(prefix: "退出所有设备失败", error: error)
            return false
        }
    }

    func fetchSensitiveLogs(
        page: Int,
        pageSize: Int = 20,
        operationType: String?,
        result: String?,
    ) async -> PaginatedSensitiveLogsPayload {
        do {
            let client = try apiClient()
            return try await client.fetchSensitiveLogs(
                page: page,
                pageSize: pageSize,
                operationType: operationType,
                result: result
            )
        } catch {
            handleAsyncError(prefix: "加载敏感日志失败", error: error)
            return PaginatedSensitiveLogsPayload(data: [], page: page, pageSize: pageSize, total: 0, totalPages: 0)
        }
    }

    func saveProfileField(field: ProfileField, value: String) async -> Bool {
        let cleaned = value.trimmingCharacters(in: .whitespacesAndNewlines)
        do {
            let client = try apiClient()
            try await client.updateProfileField(fieldKey: field.rawValue, value: cleaned)
            var updated = profileUser
            switch field {
            case .username:
                updated.username = cleaned
            case .realName:
                updated.realName = cleaned
            case .region:
                updated.region = cleaned
            case .bio:
                updated.bio = cleaned
            }
            profileUser = updated
            appendLog("✅ 资料字段更新成功: \(field.rawValue)")
            return true
        } catch {
            let description = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
            toastMessage = "更新失败: \(description)"
            appendLog("❌ 资料字段更新失败(\(field.rawValue)): \(description)")
            return false
        }
    }

    func clearLogs() {
        requestLogs.removeAll(keepingCapacity: true)
        appendLog("🧹 已清空日志")
    }

    private func appendLog(_ line: String) {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm:ss.SSS"
        let timestamp = formatter.string(from: Date())
        requestLogs.append("[\(timestamp)] \(line)")
        if requestLogs.count > 200 {
            requestLogs.removeFirst(requestLogs.count - 200)
        }
    }

    private func handleAsyncError(prefix: String, error: Error) {
        let description = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
        toastMessage = "\(prefix): \(description)"
        appendLog("❌ \(prefix): \(description)")
    }

    private func apiClient() throws -> AuthAPIClient {
        if let cachedClient {
            return cachedClient
        }
        let client = try AuthAPIClient(baseURLString: apiBaseURL) { [weak self] line in
            Task { @MainActor in
                self?.appendLog(line)
            }
        }
        cachedClient = client
        return client
    }
}

struct ContentView: View {
    @StateObject private var viewModel = AuthFlowViewModel()

    var body: some View {
        Group {
            if viewModel.isAuthenticated {
                MainTabView(viewModel: viewModel)
            } else {
                LoginFlowView(viewModel: viewModel)
            }
        }
        .alert("提示", isPresented: Binding(
            get: { viewModel.toastMessage != nil },
            set: { visible in
                if !visible {
                    viewModel.toastMessage = nil
                }
            }
        )) {
            Button("知道了", role: .cancel) {}
        } message: {
            Text(viewModel.toastMessage ?? "")
        }
    }
}

private struct LoginFlowView: View {
    @ObservedObject var viewModel: AuthFlowViewModel

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    headerSection
                    loginMethodSection
                    credentialSection
                    passkeySection
                    footerHint
                    requestLogSection
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 24)
            }
            .background(Color(uiColor: .systemGroupedBackground))
            .navigationTitle("登录")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private var headerSection: some View {
        HStack(alignment: .top) {
            HStack(spacing: 12) {
                Image(systemName: "shield.fill")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(.blue)
                    .frame(width: 34, height: 34)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                VStack(alignment: .leading, spacing: 6) {
                    Text("登录")
                        .font(.title2.bold())
                    Text("使用你的 Ksuser 账号继续。手机端当前仅开放登录，不提供注册入口。")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }

            Spacer()

            Button {
                viewModel.showNotImplemented("扫码登录将在后续版本接入")
            } label: {
                Image(systemName: "qrcode.viewfinder")
                    .font(.system(size: 18, weight: .medium))
                    .frame(width: 40, height: 40)
            }
            .buttonStyle(.bordered)
            .disabled(viewModel.isBusy)
        }
    }

    private var loginMethodSection: some View {
        GroupBox {
            Picker("登录方式", selection: $viewModel.loginMethod) {
                ForEach(LoginMethod.allCases) { method in
                    Text(method.title).tag(method)
                }
            }
            .pickerStyle(.segmented)
            .disabled(viewModel.isBusy)
        } label: {
            Text("账号登录")
                .font(.headline)
        }
    }

    private var credentialSection: some View {
        GroupBox {
            VStack(spacing: 12) {
                TextField("邮箱", text: $viewModel.email)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled(true)
                    .keyboardType(.emailAddress)
                    .textContentType(.username)
                    .padding(12)
                    .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

                switch viewModel.loginMethod {
                case .password:
                    SecureField("密码", text: $viewModel.password)
                        .textContentType(.password)
                        .padding(12)
                        .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

                case .code:
                    HStack(spacing: 8) {
                        TextField("验证码", text: $viewModel.code)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled(true)
                            .keyboardType(.numberPad)
                            .padding(12)
                            .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

                        Button("发送") {
                            Task { await viewModel.sendLoginCode() }
                        }
                        .buttonStyle(.bordered)
                        .disabled(!viewModel.canSendCode)
                    }
                }

                Button {
                    Task { await viewModel.login() }
                } label: {
                    HStack(spacing: 8) {
                        if viewModel.isBusy {
                            ProgressView()
                                .progressViewStyle(.circular)
                                .tint(.white)
                        }
                        Text(viewModel.isBusy ? "处理中..." : "继续")
                            .fontWeight(.semibold)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                }
                .buttonStyle(.borderedProminent)
                .disabled(!viewModel.canSubmitLogin)
            }
        }
    }

    private var passkeySection: some View {
        GroupBox {
            VStack(alignment: .leading, spacing: 10) {
                Text("使用通行密钥(Passkey)登录")
                    .font(.headline)
                Text("当前 iOS 端基础流程已就绪，Passkey 登录能力将在后续接入。")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Button {
                    viewModel.showNotImplemented("Passkey 登录暂未接入")
                } label: {
                    Label("使用通行密钥(Passkey)登录", systemImage: "key")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                }
                .buttonStyle(.bordered)
                .disabled(viewModel.isBusy)
            }
        }
    }

    private var footerHint: some View {
        HStack(spacing: 8) {
            Image(systemName: "info.circle")
                .foregroundStyle(.secondary)
            Text("登录成功后将进入主页面，可在“安全”中继续完善认证能力。")
                .font(.footnote)
                .foregroundStyle(.secondary)
            Spacer()
        }
        .padding(.horizontal, 4)
    }

    private var requestLogSection: some View {
        GroupBox {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("请求日志")
                        .font(.headline)
                    Spacer()
                    Button("清空") {
                        viewModel.clearLogs()
                    }
                    .buttonStyle(.bordered)
                }
                Text("API: \(viewModel.apiBaseURL)")
                    .font(.footnote)
                    .foregroundStyle(.secondary)

                ScrollView {
                    Text(viewModel.requestLogs.isEmpty ? "暂无日志，点击“发送”后会显示请求明细。" : viewModel.requestLogs.joined(separator: "\n"))
                        .font(.system(.caption, design: .monospaced))
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .frame(minHeight: 120, maxHeight: 220)
                .padding(8)
                .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
            }
        }
    }
}

private struct MainTabView: View {
    @ObservedObject var viewModel: AuthFlowViewModel
    @State private var selectedTab: MainTab = .home

    var body: some View {
        TabView(selection: $selectedTab) {
            ForEach(MainTab.allCases, id: \.self) { tab in
                NavigationStack {
                    switch tab {
                    case .home:
                        HomeScreenView(
                            profile: viewModel.profileUser,
                            onRefresh: { await viewModel.refreshHomeData() },
                            onOpenProfile: { selectedTab = .profile },
                            onOpenSecurity: { selectedTab = .security },
                            onOpenSessions: { selectedTab = .sessions }
                        )
                    case .profile:
                        ProfileScreenView(
                            profile: $viewModel.profileUser,
                            isBusy: viewModel.isBusy,
                            onRefresh: { await viewModel.refreshProfile() },
                            onSaveField: { field, value in
                                await viewModel.saveProfileField(field: field, value: value)
                            }
                        )
                    case .security:
                        SecurityScreenView(viewModel: viewModel)
                    case .sessions:
                        SessionsScreenView(viewModel: viewModel)
                    case .logs:
                        LogsScreenView(viewModel: viewModel)
                    }
                }
                .tabItem {
                    Image(systemName: tab.systemImageName)
                    Text(tab.title)
                }
                .tag(tab)
            }
        }
        .environment(\.symbolVariants, .none)
        .task {
            await viewModel.refreshProfile()
        }
    }
}

private struct ProfileScreenView: View {
    @Binding var profile: ProfileUser
    let isBusy: Bool
    let onRefresh: () async -> Void
    let onSaveField: (ProfileField, String) async -> Bool
    @State private var editingField: ProfileField?

    private var completionStats: (completed: Int, total: Int, percent: Int) {
        let checks = [
            profile.username.takeMeaningfulProfileText() != nil,
            profile.avatarURL.takeMeaningfulProfileText() != nil,
            profile.realName.takeMeaningfulProfileText() != nil,
            profile.region.takeMeaningfulProfileText() != nil,
            profile.bio.takeMeaningfulProfileText() != nil,
        ]
        let completed = checks.filter { $0 }.count
        let total = checks.count
        let percent = total == 0 ? 0 : completed * 100 / total
        return (completed, total, percent)
    }

    var body: some View {
        List {
            profileHeaderSection
            profileInfoSection
            profileCompletionSection
            accountIdentitySection
            aboutSection
        }
        .listStyle(.insetGrouped)
        .navigationTitle("资料")
        .sheet(item: $editingField) { field in
            ProfileEditView(
                field: field,
                value: binding(for: field),
                isBusy: isBusy,
                onSave: { editedValue in
                    await onSaveField(field, editedValue)
                }
            )
        }
        .refreshable {
            await onRefresh()
        }
    }

    private var profileHeaderSection: some View {
        Section {
            HStack(spacing: 12) {
                avatarView
                VStack(alignment: .leading, spacing: 4) {
                    Text(displayValue(profile.username))
                        .font(.headline)
                    Text(profile.email.isEmpty ? "暂无邮箱" : profile.email)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Button("编辑头像") {
                    // 与 Android 一致：头像单独编辑上传，当前 iOS 版本先保留入口。
                }
                .buttonStyle(.bordered)
                .disabled(true)
            }
            Button("编辑用户名") {
                editingField = .username
            }
        }
    }

    private var profileInfoSection: some View {
        Section("资料信息") {
            ProfileInfoRow(title: "真实姓名", value: displayValue(profile.realName)) {
                editingField = .realName
            }
            ProfileInfoRow(title: "地区", value: displayValue(profile.region)) {
                editingField = .region
            }
            ProfileInfoRow(title: "个人简介", value: displayValue(profile.bio)) {
                editingField = .bio
            }
        }
    }

    private var profileCompletionSection: some View {
        let stats = completionStats
        return Section("资料完善度") {
            Text("已完成 \(stats.completed)/\(stats.total) 项，当前完善度 \(stats.percent)%。")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            ProgressView(value: Double(stats.percent), total: 100)
            completionItem("用户名", done: profile.username.takeMeaningfulProfileText() != nil)
            completionItem("头像", done: profile.avatarURL.takeMeaningfulProfileText() != nil)
            completionItem("真实姓名", done: profile.realName.takeMeaningfulProfileText() != nil)
            completionItem("地区", done: profile.region.takeMeaningfulProfileText() != nil)
            completionItem("个人简介", done: profile.bio.takeMeaningfulProfileText() != nil)
        }
    }

    private var accountIdentitySection: some View {
        Section("账号标识") {
            LabeledContent("邮箱", value: profile.email.isEmpty ? "暂无邮箱" : profile.email)
            LabeledContent("UUID", value: profile.uuid)
                .font(.footnote.monospaced())
        }
    }

    private var aboutSection: some View {
        Section {
            NavigationLink("关于应用") {
                AboutAppView()
            }
        }
    }

    private var avatarView: some View {
        ZStack {
            Circle()
                .fill(Color.blue.opacity(0.16))
                .frame(width: 56, height: 56)
            if let avatar = profile.avatarURL.takeMeaningfulProfileText(), let avatarURL = URL(string: avatar) {
                AsyncImage(url: avatarURL) { image in
                    image
                        .resizable()
                        .scaledToFill()
                } placeholder: {
                    ProgressView()
                }
                .frame(width: 56, height: 56)
                .clipShape(Circle())
            } else {
                Text(String(displayValue(profile.username).prefix(1)))
                    .font(.title3.weight(.bold))
                    .foregroundStyle(.blue)
            }
        }
    }

    private func completionItem(_ title: String, done: Bool) -> some View {
        HStack {
            Text(title)
            Spacer()
            Text(done ? "已完成" : "待完善")
                .foregroundStyle(done ? .blue : .secondary)
                .font(.footnote.weight(.semibold))
        }
    }

    private func displayValue(_ raw: String) -> String {
        raw.takeMeaningfulProfileText() ?? "未设置"
    }

    private func binding(for field: ProfileField) -> Binding<String> {
        switch field {
        case .username:
            return $profile.username
        case .realName:
            return $profile.realName
        case .region:
            return $profile.region
        case .bio:
            return $profile.bio
        }
    }
}

private struct ProfileInfoRow: View {
    let title: String
    let value: String
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.body)
                        .foregroundStyle(.primary)
                    Text(value)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.footnote)
                    .foregroundStyle(.tertiary)
            }
        }
        .buttonStyle(.plain)
    }
}

private struct ProfileEditView: View {
    let field: ProfileField
    @Binding var value: String
    let isBusy: Bool
    let onSave: (String) async -> Bool
    @Environment(\.dismiss) private var dismiss
    @State private var draft = ""
    @State private var isSaving = false

    var body: some View {
        NavigationStack {
            Form {
                Section(field.title) {
                    if field == .bio {
                        TextEditor(text: $draft)
                            .frame(minHeight: 140)
                    } else {
                        TextField("请输入\(field.title)", text: $draft)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled(true)
                    }
                }
            }
            .navigationTitle("编辑\(field.title)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("保存") {
                        Task {
                            isSaving = true
                            defer { isSaving = false }
                            let cleaned = draft.trimmingCharacters(in: .whitespacesAndNewlines)
                            let saved = await onSave(cleaned)
                            if saved {
                                value = cleaned
                                dismiss()
                            }
                        }
                    }
                    .disabled(isBusy || isSaving)
                }
            }
            .onAppear {
                draft = value
            }
        }
    }
}

private struct AboutAppView: View {
    var body: some View {
        List {
            Section {
                LabeledContent("软件名称", value: "ksuser安全")
                LabeledContent("版本号", value: "1.0")
                LabeledContent("备案号", value: "沪ICP备2025144703号-2")
            } header: {
                Text("应用信息")
            }

            Section {
                LabeledContent("API 前缀", value: AuthAPIClient.resolvedBaseURLString())
                LabeledContent("平台", value: "iOS")
            } header: {
                Text("环境信息")
            }

            Section {
                Link("服务条款", destination: URL(string: "https://www.ksuser.cn/agreement/user.html")!)
                Link("隐私协议", destination: URL(string: "https://www.ksuser.cn/agreement/privacy.html")!)
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("关于应用")
    }
}

private struct HomeScreenView: View {
    let profile: ProfileUser
    let onRefresh: () async -> Void
    let onOpenProfile: () -> Void
    let onOpenSecurity: () -> Void
    let onOpenSessions: () -> Void

    private var completionStats: (completed: Int, total: Int, percent: Int) {
        let checks = [
            profile.username.takeMeaningfulProfileText() != nil,
            profile.avatarURL.takeMeaningfulProfileText() != nil,
            profile.realName.takeMeaningfulProfileText() != nil,
            profile.region.takeMeaningfulProfileText() != nil,
            profile.bio.takeMeaningfulProfileText() != nil,
        ]
        let completed = checks.filter { $0 }.count
        let total = checks.count
        return (completed, total, total == 0 ? 0 : completed * 100 / total)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                GroupBox {
                    VStack(alignment: .leading, spacing: 10) {
                        HStack(spacing: 12) {
                            AvatarPill(name: profile.username, avatarURL: profile.avatarURL)
                            VStack(alignment: .leading, spacing: 4) {
                                Text("欢迎回来，\(profile.username.takeMeaningfulProfileText() ?? "你好")")
                                    .font(.headline)
                                Text(profile.email.takeMeaningfulProfileText() ?? "当前账号未绑定邮箱")
                                    .font(.footnote)
                                    .foregroundStyle(.secondary)
                                Text(homeSecuritySummary(profile.settings))
                                    .font(.footnote.weight(.medium))
                                    .foregroundStyle(.blue)
                            }
                            Spacer()
                        }
                        HStack {
                            Button("查看资料", action: onOpenProfile)
                                .buttonStyle(.borderedProminent)
                            Button("刷新状态") {
                                Task { await onRefresh() }
                            }
                            .buttonStyle(.bordered)
                        }
                    }
                }

                GroupBox {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("账号安全")
                            .font(.headline)
                        statusRow("双重验证", profile.settings?.mfaEnabled == true ? "已开启" : "未开启")
                        statusRow("异常登录提醒", profile.settings?.detectUnusualLogin == true ? "已开启" : "未开启")
                        statusRow("敏感操作邮件提醒", profile.settings?.notifySensitiveActionEmail == true ? "已开启" : "未开启")
                        Button("前往安全设置", action: onOpenSecurity)
                            .buttonStyle(.bordered)
                    }
                }

                GroupBox {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("常用功能")
                            .font(.headline)
                        quickAction("编辑资料", "修改头像、昵称、地区和简介", systemImage: "person.text.rectangle", onTap: onOpenProfile)
                        quickAction("安全设置", "管理双重验证和敏感操作保护", systemImage: "lock.shield", onTap: onOpenSecurity)
                        quickAction("设备会话", "查看当前登录设备并处理异常登录", systemImage: "rectangle.on.rectangle", onTap: onOpenSessions)
                    }
                }

                GroupBox {
                    let stats = completionStats
                    VStack(alignment: .leading, spacing: 10) {
                        Text("资料完善度")
                            .font(.headline)
                        Text("已完成 \(stats.completed)/\(stats.total) 项，当前完善度 \(stats.percent)%。")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        ProgressView(value: Double(stats.percent), total: 100)
                        statusRow("用户名", profile.username.takeMeaningfulProfileText() != nil ? "已完成" : "待完善")
                        statusRow("头像", profile.avatarURL.takeMeaningfulProfileText() != nil ? "已完成" : "待完善")
                        statusRow("真实姓名", profile.realName.takeMeaningfulProfileText() != nil ? "已完成" : "待完善")
                        statusRow("地区", profile.region.takeMeaningfulProfileText() != nil ? "已完成" : "待完善")
                        statusRow("个人简介", profile.bio.takeMeaningfulProfileText() != nil ? "已完成" : "待完善")
                    }
                }
            }
            .padding(16)
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .navigationTitle("概览")
        .refreshable {
            await onRefresh()
        }
    }

    private func statusRow(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label)
                .foregroundStyle(.primary)
            Spacer()
            Text(value)
                .font(.footnote.weight(.semibold))
                .foregroundStyle(value == "待完善" || value == "未开启" ? Color.secondary : Color.blue)
        }
    }

    private func quickAction(_ title: String, _ subtitle: String, systemImage: String, onTap: @escaping () -> Void) -> some View {
        Button(action: onTap) {
            HStack(spacing: 10) {
                Image(systemName: systemImage)
                    .frame(width: 28, height: 28)
                    .background(Color.blue.opacity(0.12), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .foregroundStyle(.primary)
                    Text(subtitle)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.footnote)
                    .foregroundStyle(.tertiary)
            }
        }
        .buttonStyle(.plain)
    }
}

private struct SecurityScreenView: View {
    @ObservedObject var viewModel: AuthFlowViewModel
    @State private var snapshot: SecuritySnapshot?
    @State private var localSettings: UserSettingsPayload?
    @State private var loading = true

    var body: some View {
        List {
            Section("安全偏好") {
                Toggle(
                    "启用 MFA",
                    isOn: Binding(
                        get: { localSettings?.mfaEnabled ?? false },
                        set: { newValue in
                            updateLocalSettings(field: "mfaEnabled", value: newValue)
                            Task {
                                if let settings = await viewModel.updateSecurityBooleanSetting(field: "mfaEnabled", value: newValue) {
                                    localSettings = settings
                                } else {
                                    await reload()
                                }
                            }
                        }
                    )
                )
                Toggle(
                    "异地登录检测",
                    isOn: Binding(
                        get: { localSettings?.detectUnusualLogin ?? true },
                        set: { newValue in
                            updateLocalSettings(field: "detectUnusualLogin", value: newValue)
                            Task {
                                if let settings = await viewModel.updateSecurityBooleanSetting(field: "detectUnusualLogin", value: newValue) {
                                    localSettings = settings
                                } else {
                                    await reload()
                                }
                            }
                        }
                    )
                )
                Toggle(
                    "敏感操作邮件提醒",
                    isOn: Binding(
                        get: { localSettings?.notifySensitiveActionEmail ?? true },
                        set: { newValue in
                            updateLocalSettings(field: "notifySensitiveActionEmail", value: newValue)
                            Task {
                                if let settings = await viewModel.updateSecurityBooleanSetting(field: "notifySensitiveActionEmail", value: newValue) {
                                    localSettings = settings
                                } else {
                                    await reload()
                                }
                            }
                        }
                    )
                )
                Picker(
                    "首选 MFA",
                    selection: Binding(
                        get: { localSettings?.preferredMfaMethod ?? "totp" },
                        set: { newValue in
                            Task {
                                if let settings = await viewModel.updateSecurityStringSetting(field: "preferredMfaMethod", value: newValue) {
                                    localSettings = settings
                                }
                            }
                        }
                    )
                ) {
                    Text("TOTP").tag("totp")
                    Text("Passkey").tag("passkey")
                }
                Picker(
                    "首选敏感验证",
                    selection: Binding(
                        get: { localSettings?.preferredSensitiveMethod ?? "password" },
                        set: { newValue in
                            Task {
                                if let settings = await viewModel.updateSecurityStringSetting(field: "preferredSensitiveMethod", value: newValue) {
                                    localSettings = settings
                                }
                            }
                        }
                    )
                ) {
                    Text("密码").tag("password")
                    Text("邮箱验证码").tag("email-code")
                    Text("Passkey").tag("passkey")
                    Text("TOTP").tag("totp")
                }
            }

            if let snapshot {
                Section("自适应连续认证") {
                    statusLine("策略决策", adaptivePolicyLabel(snapshot.adaptiveStatus.policyDecision ?? "ALLOW"))
                    statusLine("风险等级", adaptiveRiskLabel(snapshot.adaptiveStatus.riskLevel ?? "low"))
                    statusLine("风险分", "\(snapshot.adaptiveStatus.riskScore ?? 0)")
                    statusLine(
                        "敏感验证",
                        snapshot.adaptiveStatus.sensitiveVerified == true
                        ? "有效 \(snapshot.adaptiveStatus.sensitiveVerificationRemainingSeconds ?? 0) 秒"
                        : "未验证"
                    )
                    statusLine("当前环境", [snapshot.adaptiveStatus.currentLocation, snapshot.adaptiveStatus.deviceType].compactMap { $0 }.joined(separator: " / ").ifEmpty("-"))
                    if snapshot.adaptiveStatus.sessionFrozen == true {
                        Text("当前会话已被冻结，请重新登录后继续操作")
                            .font(.footnote)
                            .foregroundStyle(.red)
                    }
                    if !(snapshot.adaptiveStatus.reasons ?? []).isEmpty {
                        ForEach(snapshot.adaptiveStatus.reasons ?? [], id: \.self) { reason in
                            Text("• \(reason)")
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                }

                Section("TOTP 验证器") {
                    statusLine("状态", snapshot.totpStatus.enabled ? "已启用" : "未启用")
                    statusLine("恢复码剩余", "\(snapshot.totpStatus.recoveryCodesCount)")
                    Text(snapshot.totpStatus.enabled ? "使用验证器 App 与恢复码保护账号" : "尚未启用，可作为常用多因素验证方式")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                Section("Passkey") {
                    if snapshot.passkeys.isEmpty {
                        Text("还没有添加 Passkey，可用于更快捷的无密码登录。")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(snapshot.passkeys) { passkey in
                            VStack(alignment: .leading, spacing: 4) {
                                Text(passkey.name)
                                Text("最近使用: \(formatAbsoluteTime(passkey.lastUsedAt))")
                                    .font(.footnote)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }

                Section("敏感操作验证") {
                    statusLine("当前状态", snapshot.sensitiveStatus.verified ? "已验证（剩余 \(snapshot.sensitiveStatus.remainingSeconds) 秒）" : "未验证")
                    statusLine("首选方式", sensitiveMethodDisplayName(snapshot.sensitiveStatus.preferredMethod))
                    statusLine("可用方式", (snapshot.sensitiveStatus.methods ?? []).map(sensitiveMethodDisplayName).joined(separator: " / ").ifEmpty("-"))
                }
            } else if loading {
                Section {
                    ProgressView("加载中...")
                }
            } else {
                Section {
                    Text("安全数据加载失败，请下拉重试。")
                        .foregroundStyle(.secondary)
                }
            }

            Section {
                Button("退出全部设备", role: .destructive) {
                    Task { _ = await viewModel.logoutAllDevices() }
                }
            } footer: {
                Text("该操作会撤销所有会话（包括当前设备）。")
            }
        }
        .navigationTitle("安全")
        .task {
            await reload()
        }
        .refreshable {
            await reload()
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    Task { await reload() }
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
            }
        }
    }

    private func statusLine(_ title: String, _ value: String) -> some View {
        LabeledContent(title, value: value)
            .font(.footnote)
    }

    private func updateLocalSettings(field: String, value: Bool) {
        guard let localSettings else { return }
        let updated = UserSettingsPayload(
            mfaEnabled: field == "mfaEnabled" ? value : localSettings.mfaEnabled,
            detectUnusualLogin: field == "detectUnusualLogin" ? value : localSettings.detectUnusualLogin,
            notifySensitiveActionEmail: field == "notifySensitiveActionEmail" ? value : localSettings.notifySensitiveActionEmail,
            subscribeNewsEmail: localSettings.subscribeNewsEmail,
            preferredMfaMethod: localSettings.preferredMfaMethod,
            preferredSensitiveMethod: localSettings.preferredSensitiveMethod
        )
        self.localSettings = updated
    }

    private func reload() async {
        loading = true
        defer { loading = false }
        snapshot = await viewModel.fetchSecuritySnapshot()
        localSettings = snapshot?.settings ?? viewModel.profileUser.settings
    }
}

private struct SessionsScreenView: View {
    @ObservedObject var viewModel: AuthFlowViewModel
    @State private var sessions: [SessionItemPayload] = []
    @State private var loading = true
    @State private var targetSession: SessionItemPayload?

    var body: some View {
        List {
            Section {
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("设备与登录")
                            .font(.headline)
                        Text("查看和管理已连接设备")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Button {
                        Task { await reload() }
                    } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                }
            }

            if loading && sessions.isEmpty {
                Section {
                    ProgressView("加载会话中...")
                }
            } else if sessions.isEmpty {
                Section {
                    Text("当前没有活跃会话记录")
                        .foregroundStyle(.secondary)
                }
            } else {
                Section {
                    ForEach(sessions) { session in
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Text(sessionClientDisplayName(session))
                                    .font(.headline)
                                if session.current {
                                    TagView(text: "当前", fg: .blue, bg: Color.blue.opacity(0.14))
                                }
                                TagView(
                                    text: session.online ? "在线" : "离线",
                                    fg: session.online ? .green : .secondary,
                                    bg: session.online ? Color.green.opacity(0.16) : Color.secondary.opacity(0.1)
                                )
                                Spacer()
                                if !session.current {
                                    Button("撤销", role: .destructive) {
                                        targetSession = session
                                    }
                                    .font(.footnote)
                                }
                            }
                            Text("\(sessionSystemDisplayName(session)) · \(session.ipAddress)")
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                            Text(session.ipLocation ?? "未知位置")
                                .font(.footnote)
                            Text("最近活动: \(formatAbsoluteTime(session.lastSeenAt))")
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                        .padding(.vertical, 4)
                    }
                }
            }

            Section {
                Button("退出所有设备", role: .destructive) {
                    Task { _ = await viewModel.logoutAllDevices() }
                }
            } footer: {
                Text("该操作会撤销所有会话（包括当前设备），需要重新登录。")
            }
        }
        .navigationTitle("会话")
        .task {
            await reload()
        }
        .refreshable {
            await reload()
        }
        .alert("确认撤销会话", isPresented: Binding(
            get: { targetSession != nil },
            set: { visible in
                if !visible {
                    targetSession = nil
                }
            })
        ) {
            Button("取消", role: .cancel) {}
            Button("确认", role: .destructive) {
                guard let target = targetSession else { return }
                targetSession = nil
                Task {
                    if await viewModel.revokeSession(sessionId: target.id) {
                        await reload()
                    }
                }
            }
        } message: {
            if let target = targetSession {
                Text("将撤销 \(sessionClientDisplayName(target))（\(target.ipAddress)）的登录状态，确定继续吗？")
            }
        }
    }

    private func reload() async {
        loading = true
        defer { loading = false }
        sessions = await viewModel.fetchSessions()
    }
}

private struct LogsScreenView: View {
    @ObservedObject var viewModel: AuthFlowViewModel
    @State private var logs: [SensitiveLogItemPayload] = []
    @State private var page = 1
    @State private var total = 0
    @State private var totalPages = 1
    @State private var busy = true
    @State private var selectedOperationType: String?
    @State private var selectedResult: String?
    @State private var appliedOperationType: String?
    @State private var appliedResult: String?

    var body: some View {
        List {
            Section {
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("近期敏感操作")
                            .font(.headline)
                        Text("查看安全相关操作记录与风险分数")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Button {
                        Task { await reload() }
                    } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                }
            }

            Section("筛选条件") {
                Picker("操作类型", selection: $selectedOperationType) {
                    Text("全部操作").tag(String?.none)
                    Text("登录").tag(String?.some("LOGIN"))
                    Text("注册").tag(String?.some("REGISTER"))
                    Text("敏感验证").tag(String?.some("SENSITIVE_VERIFY"))
                    Text("修改密码").tag(String?.some("CHANGE_PASSWORD"))
                    Text("修改邮箱").tag(String?.some("CHANGE_EMAIL"))
                    Text("新增 Passkey").tag(String?.some("ADD_PASSKEY"))
                    Text("删除 Passkey").tag(String?.some("DELETE_PASSKEY"))
                    Text("启用 TOTP").tag(String?.some("ENABLE_TOTP"))
                    Text("禁用 TOTP").tag(String?.some("DISABLE_TOTP"))
                }
                Picker("结果", selection: $selectedResult) {
                    Text("全部结果").tag(String?.none)
                    Text("成功").tag(String?.some("SUCCESS"))
                    Text("失败").tag(String?.some("FAILURE"))
                }
                HStack {
                    Button("查询") {
                        page = 1
                        appliedOperationType = selectedOperationType
                        appliedResult = selectedResult
                        Task { await reload() }
                    }
                    .buttonStyle(.borderedProminent)
                    Button("重置") {
                        page = 1
                        selectedOperationType = nil
                        selectedResult = nil
                        appliedOperationType = nil
                        appliedResult = nil
                        Task { await reload() }
                    }
                    .buttonStyle(.bordered)
                    Spacer()
                    Text("共 \(total) 条")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }

            if busy && logs.isEmpty {
                Section {
                    ProgressView("加载日志中...")
                }
            } else if logs.isEmpty {
                Section {
                    Text("当前筛选条件下没有匹配记录")
                        .foregroundStyle(.secondary)
                }
            } else {
                Section {
                    ForEach(logs) { log in
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Text(logOperationTitle(log.operationType))
                                    .font(.headline)
                                ForEach(logOperationTags(log), id: \.self) { tag in
                                    TagView(text: tag, fg: .blue, bg: Color.blue.opacity(0.12))
                                }
                                Spacer()
                                RiskTag(score: log.riskScore)
                            }
                            HStack {
                                TagView(
                                    text: log.result.uppercased() == "SUCCESS" ? "成功" : "失败",
                                    fg: log.result.uppercased() == "SUCCESS" ? .green : .red,
                                    bg: log.result.uppercased() == "SUCCESS" ? Color.green.opacity(0.14) : Color.red.opacity(0.14)
                                )
                                Text("\(log.ipLocation ?? "未知位置") · \(log.ipAddress)")
                                    .font(.footnote)
                                    .foregroundStyle(.secondary)
                            }
                            Text("设备: \(log.deviceType ?? "未知设备") · 浏览器: \(log.browser ?? "未知浏览器")")
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                            Text("时间: \(formatAbsoluteTime(log.createdAt))")
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                            if let reason = log.failureReason.takeMeaningfulProfileText() {
                                Text("失败原因: \(reason)")
                                    .font(.footnote)
                                    .foregroundStyle(.red)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }
            }

            Section {
                HStack {
                    Button("上一页") {
                        guard page > 1 else { return }
                        page -= 1
                        Task { await reload() }
                    }
                    .disabled(page <= 1)
                    Spacer()
                    Text("第 \(page) / \(max(totalPages, 1)) 页")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    Spacer()
                    Button("下一页") {
                        guard page < totalPages else { return }
                        page += 1
                        Task { await reload() }
                    }
                    .disabled(page >= totalPages)
                }
            }
        }
        .navigationTitle("日志")
        .task {
            await reload()
        }
        .refreshable {
            await reload()
        }
    }

    private func reload() async {
        busy = true
        defer { busy = false }
        let payload = await viewModel.fetchSensitiveLogs(
            page: page,
            operationType: appliedOperationType,
            result: appliedResult
        )
        logs = payload.data
        total = payload.total
        totalPages = max(payload.totalPages, 1)
    }
}

private struct AvatarPill: View {
    let name: String
    let avatarURL: String?

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color.blue.opacity(0.12))
                .frame(width: 56, height: 56)
            if let avatar = avatarURL.takeMeaningfulProfileText(), let url = URL(string: avatar) {
                AsyncImage(url: url) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    ProgressView()
                }
                .frame(width: 56, height: 56)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            } else {
                Text(String((name.takeMeaningfulProfileText() ?? "我").prefix(1)))
                    .font(.headline.bold())
                    .foregroundStyle(.blue)
            }
        }
    }
}

private struct TagView: View {
    let text: String
    let fg: Color
    let bg: Color

    var body: some View {
        Text(text)
            .font(.caption.weight(.semibold))
            .foregroundStyle(fg)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(bg, in: Capsule())
    }
}

private struct RiskTag: View {
    let score: Int

    var body: some View {
        let fg: Color
        let bg: Color
        if score >= 70 {
            fg = .red
            bg = Color.red.opacity(0.16)
        } else if score >= 40 {
            fg = .orange
            bg = Color.orange.opacity(0.16)
        } else {
            fg = .green
            bg = Color.green.opacity(0.16)
        }
        return TagView(text: "风险 \(score)", fg: fg, bg: bg)
    }
}

private func homeSecuritySummary(_ settings: UserSettingsPayload?) -> String {
    guard let settings else { return "建议继续完善安全设置" }
    if settings.mfaEnabled && settings.detectUnusualLogin {
        return "账号保护较完整"
    }
    if settings.mfaEnabled {
        return "双重验证已开启"
    }
    return "建议继续完善安全设置"
}

private func sensitiveMethodDisplayName(_ raw: String?) -> String {
    switch raw?.lowercased() {
    case "password":
        return "密码"
    case "email-code":
        return "邮箱验证码"
    case "passkey":
        return "Passkey"
    case "totp":
        return "TOTP"
    default:
        return raw?.ifEmpty("-") ?? "-"
    }
}

private func adaptivePolicyLabel(_ decision: String) -> String {
    switch decision.uppercased() {
    case "ALLOW":
        return "放行"
    case "STEP_UP":
        return "补验"
    case "FREEZE":
        return "冻结"
    default:
        return decision
    }
}

private func adaptiveRiskLabel(_ level: String) -> String {
    switch level.lowercased() {
    case "low":
        return "低"
    case "medium":
        return "中"
    case "high":
        return "高"
    default:
        return level
    }
}

private func sessionClientDisplayName(_ session: SessionItemPayload) -> String {
    let ua = (session.userAgent ?? "").lowercased()
    if ua.contains("ksuserauthdesktop") {
        return "桌面端"
    }
    if ua.contains("ksuserauthmobile") {
        return "移动端"
    }
    return browserDisplayName(session.browser ?? session.userAgent ?? "")
}

private func sessionSystemDisplayName(_ session: SessionItemPayload) -> String {
    let ua = "\(session.userAgent ?? "") \(session.deviceType ?? "")".lowercased()
    if ua.contains("android") { return "Android" }
    if ua.contains("iphone") || ua.contains("ipad") || ua.contains("ios") { return "iOS" }
    if ua.contains("windows") { return "Windows" }
    if ua.contains("mac os") || ua.contains("macintosh") || ua.contains("mac") { return "macOS" }
    if ua.contains("linux") { return "Linux" }
    return session.deviceType?.ifEmpty("未知设备") ?? "未知设备"
}

private func browserDisplayName(_ raw: String) -> String {
    let value = raw.lowercased()
    if value.contains("edge") || value.contains("edg/") { return "Edge" }
    if value.contains("firefox") { return "Firefox" }
    if value.contains("opera") || value.contains("opr/") { return "Opera" }
    if value.contains("chrome") { return "Chrome" }
    if value.contains("safari") { return "Safari" }
    return raw.ifEmpty("未知浏览器")
}

private func logOperationTitle(_ operationType: String) -> String {
    switch operationType.uppercased() {
    case "LOGIN":
        return "登录"
    case "REGISTER":
        return "注册"
    default:
        return "敏感操作"
    }
}

private func logOperationTags(_ log: SensitiveLogItemPayload) -> [String] {
    let op = log.operationType.uppercased()
    if op == "REGISTER" {
        return []
    }
    if op == "LOGIN" {
        let methods = (log.loginMethods ?? []).map(normalizeLoginMethod).filter { !$0.isEmpty }
        if !methods.isEmpty {
            return Array(Set(methods)).sorted()
        }
        if let loginMethod = log.loginMethod?.takeMeaningfulProfileText() {
            return [normalizeLoginMethod(loginMethod)]
        }
        return []
    }
    return [operationDisplayName(log.operationType)]
}

private func operationDisplayName(_ raw: String) -> String {
    switch raw.uppercased() {
    case "SENSITIVE_VERIFY":
        return "敏感验证"
    case "CHANGE_PASSWORD":
        return "修改密码"
    case "CHANGE_EMAIL":
        return "修改邮箱"
    case "ADD_PASSKEY":
        return "新增 Passkey"
    case "DELETE_PASSKEY":
        return "删除 Passkey"
    case "ENABLE_TOTP":
        return "启用 TOTP"
    case "DISABLE_TOTP":
        return "禁用 TOTP"
    default:
        return raw
    }
}

private func normalizeLoginMethod(_ raw: String) -> String {
    switch raw.uppercased() {
    case "PASSKEY":
        return "Passkey"
    case "MFA":
        return "MFA"
    case "PASSWORD":
        return "密码"
    case "EMAIL", "EMAIL_CODE":
        return "验证码"
    case "TOTP":
        return "TOTP"
    case "QR":
        return "扫码"
    case "GOOGLE":
        return "Google"
    case "GITHUB":
        return "GitHub"
    case "MICROSOFT":
        return "Microsoft"
    case "QQ":
        return "QQ"
    case "WECHAT", "WEIXIN":
        return "微信"
    default:
        return raw
    }
}

private func formatAbsoluteTime(_ value: String?) -> String {
    guard let value, !value.isEmpty else { return "-" }
    let output = DateFormatter()
    output.locale = Locale(identifier: "zh_CN")
    output.dateFormat = "yyyy-MM-dd HH:mm:ss"
    let iso = ISO8601DateFormatter()
    iso.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    if let date = iso.date(from: value) {
        return output.string(from: date)
    }
    let isoNoMs = ISO8601DateFormatter()
    isoNoMs.formatOptions = [.withInternetDateTime]
    if let date = isoNoMs.date(from: value) {
        return output.string(from: date)
    }
    return value.replacingOccurrences(of: "T", with: " ")
}

private extension String {
    func takeMeaningfulProfileText() -> String? {
        let normalized = trimmingCharacters(in: .whitespacesAndNewlines)
        if normalized.isEmpty { return nil }
        if ["无", "未设置", "暂无", "null", "NULL", "-"].contains(normalized) {
            return nil
        }
        return normalized
    }

    func ifEmpty(_ fallback: String) -> String {
        isEmpty ? fallback : self
    }
}

private extension Optional where Wrapped == String {
    func takeMeaningfulProfileText() -> String? {
        switch self {
        case .none:
            return nil
        case let .some(value):
            return value.takeMeaningfulProfileText()
        }
    }
}
