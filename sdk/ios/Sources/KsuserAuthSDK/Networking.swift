import Foundation

protocol SDKTransport: Sendable {
    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse)
}
private final class NoRedirects: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) { completionHandler(nil) }
}
struct SDKURLSessionTransport: SDKTransport {
    private let session: URLSession
    init() {
        let config = URLSessionConfiguration.ephemeral
        config.httpCookieStorage = nil; config.httpShouldSetCookies = false; config.urlCache = nil
        config.timeoutIntervalForRequest = 30
        session = URLSession(configuration: config, delegate: NoRedirects(), delegateQueue: nil)
    }
    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let (data, response) = try await session.data(for: request)
        guard let response = response as? HTTPURLResponse else { throw KsuserAuthError.invalidResponse }
        return (data, response)
    }
}
struct SDKHTTP {
    let configuration: KsuserAuthConfiguration
    let transport: any SDKTransport
    init(configuration: KsuserAuthConfiguration, transport: any SDKTransport = SDKURLSessionTransport()) {
        self.configuration = configuration; self.transport = transport
    }
    private struct Envelope<T: Decodable>: Decodable { let code: Int; let data: T?; let errorCode: String? }
    func create(scopes: [String], state: String, nonce: String?, verifier: String) async throws -> NativeCreated {
        var authorization = ["clientId": configuration.clientID, "redirectUri": configuration.redirectURI.absoluteString,
                             "responseType": "code", "scope": scopes.joined(separator: " "), "state": state,
                             "codeChallenge": PKCE.challenge(verifier), "codeChallengeMethod": "S256"]
        if let nonce { authorization["nonce"] = nonce }
        let body: [String: Any] = ["mode": configuration.mode.rawValue, "platform": "ios", "authorization": authorization]
        let data = try await request("auth/native-authorization/create", method: "POST", body: JSONSerialization.data(withJSONObject: body), contentType: "application/json")
        let response = try JSONDecoder().decode(Envelope<NativeCreated>.self, from: data)
        guard response.code == 200, let created = response.data else { throw KsuserAuthError.server(response.errorCode ?? "invalid_response") }
        try validate(created)
        return created
    }
    func validate(_ created: NativeCreated) throws {
        let pattern = #"^[A-Za-z0-9_-]{32}$"#
        guard created.ticket.range(of: pattern, options: .regularExpression) != nil,
              created.cancelSecret.range(of: pattern, options: .regularExpression) != nil,
              (1...600).contains(created.expiresInSeconds),
              trustedLink(created.appLink, path: "/app/sdk-authorize", ticket: created.ticket),
              trustedLink(created.webAuthorizationUrl, path: "/app/sdk-authorize/web", ticket: created.ticket) else { throw KsuserAuthError.invalidResponse }
    }
    private func trustedLink(_ url: URL, path: String, ticket: String) -> Bool {
        guard let c = URLComponents(url: url, resolvingAgainstBaseURL: false),
              c.scheme == "https", c.host == configuration.webBaseURL.host, c.port == configuration.webBaseURL.port,
              c.path == path, c.user == nil, c.password == nil, c.fragment == nil,
              let items = c.queryItems, items.count == 1, items[0].name == "ticket", items[0].value == ticket else { return false }
        return true
    }
    func cancel(_ transaction: AuthorizationTransaction) async throws {
        _ = try await request("auth/native-authorization/cancel", method: "POST",
                              body: JSONSerialization.data(withJSONObject: ["ticket": transaction.created.ticket, "cancelSecret": transaction.created.cancelSecret]), contentType: "application/json")
    }
    func exchange(_ transaction: AuthorizationTransaction) async throws -> KsuserTokenSet {
        guard let code = transaction.code else { throw KsuserAuthError.invalidCallback }
        let fields = ["grant_type": "authorization_code", "code": code, "client_id": configuration.clientID,
                      "redirect_uri": configuration.redirectURI.absoluteString, "code_verifier": transaction.verifier]
        let encoded = fields.sorted(by: { $0.key < $1.key }).map { Self.form($0.key) + "=" + Self.form($0.value) }.joined(separator: "&")
        let path = configuration.mode == .official ? "official/token" : "oauth2/token"
        let data = try await request(path, method: "POST", body: Data(encoded.utf8), contentType: "application/x-www-form-urlencoded")
        let tokens = try JSONDecoder().decode(KsuserTokenSet.self, from: data)
        guard !tokens.accessToken.isEmpty, tokens.tokenType.caseInsensitiveCompare("Bearer") == .orderedSame, tokens.expiresIn > 0 else { throw KsuserAuthError.invalidResponse }
        return tokens
    }
    func verifyOfficial(_ tokens: KsuserTokenSet, nonce: String) async throws -> String {
        guard let token = tokens.idToken else { throw KsuserAuthError.invalidIDToken }
        let data = try await request("official/jwks")
        return try IDTokenVerifier.subject(token: token, keys: JSONDecoder().decode(JWKSet.self, from: data),
                                           issuer: configuration.officialIssuer, clientID: configuration.clientID, nonce: nonce)
    }
    private func request(_ path: String, method: String = "GET", body: Data? = nil, contentType: String? = nil) async throws -> Data {
        var request = URLRequest(url: configuration.apiBaseURL.appendingPathComponent(path))
        request.httpMethod = method; request.httpBody = body
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let contentType { request.setValue(contentType, forHTTPHeaderField: "Content-Type") }
        let (data, response) = try await transport.data(for: request)
        guard (200..<300).contains(response.statusCode) else {
            let object = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
            let code = object?["errorCode"] as? String ?? object?["error"] as? String ?? "http_\(response.statusCode)"
            let endpoint = request.url.map { "\($0.scheme ?? "https")://\($0.host ?? "")\($0.path)" } ?? "(unknown URL)"
            let responseText: String?
            if let json = try? JSONSerialization.jsonObject(with: data), JSONSerialization.isValidJSONObject(json),
               let safeData = try? JSONSerialization.data(withJSONObject: Self.redacting(json), options: [.sortedKeys]) {
                responseText = String(data: safeData, encoding: .utf8)
            } else {
                responseText = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines)
            }
            let body = responseText.map { String($0.prefix(1200)) } ?? "(响应不是 UTF-8 文本，\(data.count) bytes)"
            throw KsuserAuthError.httpStatus(response.statusCode, "\(code)\n请求：\(request.httpMethod ?? "GET") \(endpoint)\n响应：\(body)")
        }
        return data
    }
    private static func redacting(_ value: Any) -> Any {
        let sensitiveKeys: Set<String> = ["access_token", "refresh_token", "id_token", "client_secret", "code_verifier", "cancelsecret", "ticket"]
        if let object = value as? [String: Any] {
            var sanitized: [String: Any] = [:]
            for (key, item) in object {
                sanitized[key] = sensitiveKeys.contains(key.lowercased()) ? "[REDACTED]" : redacting(item)
            }
            return sanitized
        }
        if let array = value as? [Any] { return array.map(redacting) }
        return value
    }
    private static func form(_ value: String) -> String {
        value.addingPercentEncoding(withAllowedCharacters: CharacterSet(charactersIn: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~"))!
    }
}

@MainActor enum SDKAuthorizationResolver {
    static func resolve(_ original: AuthorizationTransaction, http: SDKHTTP, persist: (AuthorizationTransaction) throws -> Void) async throws -> KsuserAuthorizationResult {
        var transaction = original
        if let error = transaction.callbackError { throw KsuserAuthError.server(error) }
        let tokens: KsuserTokenSet
        if let saved = transaction.tokens { tokens = saved }
        else {
            tokens = try await http.exchange(transaction)
            transaction.tokens = tokens; transaction.code = nil
            // Persist the token response before JWKS I/O, so a transient verifier outage cannot consume the code twice.
            try persist(transaction)
        }
        if http.configuration.mode == .official {
            guard let nonce = transaction.nonce else { throw KsuserAuthError.invalidIDToken }
            let subject = try await http.verifyOfficial(tokens, nonce: nonce)
            return .official(tokens: tokens, subject: subject)
        }
        return .oauth(tokens)
    }
}
