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
}

private struct ProfileUser {
    var username: String
    var realName: String
    var region: String
    var bio: String
    var email: String
    var uuid: String
    var avatarURL: String?
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
            avatarURL: user.avatarUrl
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
        avatarURL: nil
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
                    if tab == .profile {
                        ProfileScreenView(
                            profile: $viewModel.profileUser,
                            isBusy: viewModel.isBusy,
                            onRefresh: { await viewModel.refreshProfile() },
                            onSaveField: { field, value in
                                await viewModel.saveProfileField(field: field, value: value)
                            }
                        )
                    } else {
                        TabPlaceholderView(tab: tab, onLogout: viewModel.logout)
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

private struct TabPlaceholderView: View {
    let tab: MainTab
    let onLogout: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: tab.systemImageName)
                .imageScale(.large)
                .font(.system(size: 34))
            Text(tab.title)
                .font(.title3.weight(.semibold))
            Text("iOS \(tab.title) 页面待接入")
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .navigationTitle(tab.title)
        .toolbar {
            if tab == .home {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("退出") {
                        onLogout()
                    }
                }
            }
        }
    }
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

#Preview {
    ContentView()
}
