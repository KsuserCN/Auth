import Foundation

enum APIError: Error, LocalizedError, Equatable {
    case server(Int, String), invalidResponse, storage(Int32), invalidQRCode, cancelled, unsupported(String)
    var errorDescription: String? {
        switch self {
        case .server(_, let message), .unsupported(let message): return message
        case .invalidResponse: return "服务器返回了无法识别的数据"
        case .storage: return "无法安全保存登录凭据，请解锁设备后重试"
        case .invalidQRCode: return "这不是有效的 Ksuser 授权二维码"
        case .cancelled: return "操作已取消"
        }
    }
    var isUnauthorized: Bool { if case .server(401, _) = self { return true }; return false }
}

protocol HTTPTransport: Sendable { func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) }
struct URLSessionTransport: HTTPTransport {
    let session: URLSession
    init() {
        let config = URLSessionConfiguration.ephemeral
        config.httpCookieStorage = nil; config.httpShouldSetCookies = false; config.timeoutIntervalForRequest = 30
        session = URLSession(configuration: config)
    }
    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw APIError.invalidResponse }
        return (data, http)
    }
}

actor APIClient {
    let environment: AppEnvironment
    private let transport: any HTTPTransport
    private let storage: any SessionStoring
    private var session: SessionSnapshot
    private var refreshTask: Task<String, Error>?
    private var refreshTaskID: UUID?
    private var csrfTask: Task<Void, Error>?
    private var csrfTaskID: UUID?
    private var generation = 0
    init(environment: AppEnvironment = .current, storage: any SessionStoring = KeychainSessionStore(), transport: any HTTPTransport = URLSessionTransport()) {
        self.environment = environment; self.storage = storage; self.transport = transport
        self.session = (try? storage.load()) ?? SessionSnapshot()
    }
    func snapshot() -> SessionSnapshot { session }
    func hasSession() -> Bool { session.accessToken != nil || session.cookies.contains { $0.name == "refreshToken" && ($0.expiresAt.map { $0 > Date() } ?? true) } }
    func setAccessToken(_ token: String, source: String? = nil) throws {
        session.accessToken = token
        if let source { session.authSource = source }
        try storage.save(session)
    }
    func cacheUser(_ profile: UserProfile) throws { session.profile = profile; try storage.save(session) }
    func replaceSession(_ snapshot: SessionSnapshot) throws {
        try storage.save(snapshot); generation += 1; refreshTask?.cancel(); refreshTask = nil; refreshTaskID = nil; csrfTask?.cancel(); csrfTask = nil; csrfTaskID = nil; session = snapshot
    }
    func clearSession() throws {
        generation += 1; refreshTask?.cancel(); refreshTask = nil; refreshTaskID = nil; csrfTask?.cancel(); csrfTask = nil; csrfTaskID = nil
        session = SessionSnapshot(); try storage.clear()
    }
    func stagedClient() -> APIClient { APIClient(environment: environment, storage: MemorySessionStore(), transport: transport) }

    func request<T: Decodable & Sendable>(_ path: String, method: String = "GET", query: [String: String] = [:], body: [String: JSONValue]? = nil, authenticated: Bool = true, acceptedCodes: Set<Int> = [200]) async throws -> T {
        let envelope: APIEnvelope<T> = try await envelope(path, method: method, query: query, body: body, authenticated: authenticated)
        guard acceptedCodes.contains(envelope.code) else { throw APIError.server(envelope.code, envelope.msg ?? "请求失败") }
        guard let payload = envelope.data else {
            if T.self == EmptyPayload.self { return EmptyPayload() as! T }
            throw APIError.invalidResponse
        }
        return payload
    }
    func envelope<T: Decodable & Sendable>(_ path: String, method: String = "GET", query: [String: String] = [:], body: [String: JSONValue]? = nil, authenticated: Bool = true) async throws -> APIEnvelope<T> {
        if method != "GET" && method != "HEAD" { try await ensureCSRF() }
        let data = try body.map { try JSONEncoder().encode($0) }
        return try await perform(path, method: method, query: query, data: data, contentType: "application/json", authenticated: authenticated, canReplay: true)
    }
    func upload<T: Decodable & Sendable>(_ path: String, data: Data, mimeType: String) async throws -> T {
        guard ["image/jpeg", "image/png", "image/webp"].contains(mimeType), data.count <= 10 * 1024 * 1024 else { throw APIError.server(400, "请选择不超过 10 MB 的 JPEG、PNG 或 WebP 图片") }
        try await ensureCSRF()
        let boundary = "Ksuser-\(UUID().uuidString)"
        let fileExtension = mimeType == "image/png" ? "png" : mimeType == "image/webp" ? "webp" : "jpg"
        var body = Data("--\(boundary)\r\nContent-Disposition: form-data; name=\"file\"; filename=\"avatar.\(fileExtension)\"\r\nContent-Type: \(mimeType)\r\n\r\n".utf8)
        body.append(data); body.append(Data("\r\n--\(boundary)--\r\n".utf8))
        let envelope: APIEnvelope<T> = try await perform(path, method: "POST", query: [:], data: body, contentType: "multipart/form-data; boundary=\(boundary)", authenticated: true, canReplay: true)
        guard envelope.code == 200, let payload = envelope.data else { throw APIError.server(envelope.code, envelope.msg ?? "上传失败") }
        return payload
    }
    private func ensureCSRF() async throws {
        let url = environment.apiBaseURL.appendingPathComponent("auth/csrf-token")
        if session.cookies.contains(where: { $0.name == "XSRF-TOKEN" && $0.matches(url) }) { return }
        if let csrfTask { return try await csrfTask.value }
        let task = Task { try await self.bootstrapCSRF() }
        let id = UUID(); csrfTask = task; csrfTaskID = id
        defer { if csrfTaskID == id { csrfTask = nil; csrfTaskID = nil } }
        try await task.value
    }
    private func bootstrapCSRF() async throws {
        let _: APIEnvelope<JSONValue> = try await perform("/auth/csrf-token", method: "GET", query: [:], data: nil, contentType: nil, authenticated: false, canReplay: false)
        guard session.cookies.contains(where: { $0.name == "XSRF-TOKEN" }) else { throw APIError.server(403, "无法取得安全验证令牌，请稍后重试") }
    }
    private func refresh(failedToken: String?) async throws -> String {
        if let token = session.accessToken, token != failedToken { return token }
        if let refreshTask { return try await refreshTask.value }
        let task = Task { try await self.performRefresh() }
        let id = UUID(); refreshTask = task; refreshTaskID = id
        defer { if refreshTaskID == id { refreshTask = nil; refreshTaskID = nil } }
        return try await task.value
    }
    private func performRefresh() async throws -> String {
        let startedGeneration = generation
        do {
            try await ensureCSRF()
            let envelope: APIEnvelope<TokenPayload> = try await perform("/auth/refresh", method: "POST", query: [:], data: Data("{}".utf8), contentType: "application/json", authenticated: false, canReplay: false)
            guard envelope.code == 200, let token = envelope.data?.accessToken else { throw APIError.server(envelope.code, envelope.msg ?? "登录已失效，请重新登录") }
            guard generation == startedGeneration else { throw APIError.cancelled }
            try setAccessToken(token); return token
        } catch {
            if let error = error as? APIError, error.isUnauthorized, generation == startedGeneration { try clearSession() }
            throw error
        }
    }
    func restoreAccessToken() async throws -> String { try await refresh(failedToken: session.accessToken) }

    private func perform<T: Decodable & Sendable>(_ path: String, method: String, query: [String: String], data: Data?, contentType: String?, authenticated: Bool, canReplay: Bool) async throws -> APIEnvelope<T> {
        var components = URLComponents(url: environment.apiBaseURL.appendingPathComponent(path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))), resolvingAgainstBaseURL: false)!
        if !query.isEmpty { components.queryItems = query.sorted { $0.key < $1.key }.map { URLQueryItem(name: $0.key, value: $0.value) } }
        guard let url = components.url else { throw APIError.invalidResponse }
        var request = URLRequest(url: url); request.httpMethod = method; request.httpBody = data
        request.setValue("application/json", forHTTPHeaderField: "Accept"); request.setValue(environment.userAgent, forHTTPHeaderField: "User-Agent")
        if let contentType { request.setValue(contentType, forHTTPHeaderField: "Content-Type") }
        let requestToken = authenticated ? session.accessToken : nil
        if let requestToken { request.setValue("Bearer \(requestToken)", forHTTPHeaderField: "Authorization") }
        let cookies = session.cookies.filter { $0.matches(url) }
        if !cookies.isEmpty { request.setValue(cookies.map { "\($0.name)=\($0.value)" }.joined(separator: "; "), forHTTPHeaderField: "Cookie") }
        if method != "GET" && method != "HEAD", let csrf = cookies.first(where: { $0.name == "XSRF-TOKEN" }) { request.setValue(csrf.value.removingPercentEncoding ?? csrf.value, forHTTPHeaderField: "X-XSRF-TOKEN") }
        let startedGeneration = generation
        let (bytes, response) = try await transport.data(for: request)
        guard startedGeneration == generation else { throw APIError.cancelled }
        try receiveCookies(response, url: url)
        let parsed = try? JSONDecoder().decode(APIEnvelope<T>.self, from: bytes)
        let status = parsed?.code ?? response.statusCode
        if (response.statusCode == 401 || status == 401), authenticated, canReplay, requestToken != nil {
            _ = try await refresh(failedToken: requestToken)
            return try await perform(path, method: method, query: query, data: data, contentType: contentType, authenticated: true, canReplay: false)
        }
        if (response.statusCode == 401 || status == 401), authenticated, !canReplay {
            try clearSession()
            throw APIError.server(401, parsed?.msg ?? "登录已失效，请重新登录")
        }
        guard (200..<300).contains(response.statusCode) else {
            let message = (try? JSONDecoder().decode(APIEnvelope<JSONValue>.self, from: bytes))?.msg
            throw APIError.server(response.statusCode, message ?? "请求失败（\(response.statusCode)）")
        }
        guard let parsed else { throw APIError.invalidResponse }
        return parsed
    }
    private func receiveCookies(_ response: HTTPURLResponse, url: URL) throws {
        var headers: [String: String] = [:]
        for (key, value) in response.allHeaderFields { headers[String(describing: key)] = String(describing: value) }
        let cookies = HTTPCookie.cookies(withResponseHeaderFields: headers, for: url)
        guard !cookies.isEmpty else { return }
        for cookie in cookies {
            // Ignore cookies aimed at unrelated domains even if a proxy returned them.
            let host = url.host ?? ""; let domain = cookie.domain.trimmingCharacters(in: CharacterSet(charactersIn: "."))
            guard host == domain || host.hasSuffix("." + domain) else { continue }
            session.cookies.removeAll { $0.name == cookie.name && $0.domain == cookie.domain && $0.path == cookie.path }
            if cookie.expiresDate.map({ $0 > Date() }) ?? true { session.cookies.append(StoredCookie(cookie)) }
        }
        try storage.save(session)
    }
}
