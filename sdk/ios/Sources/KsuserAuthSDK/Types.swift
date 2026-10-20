import Foundation

public enum KsuserAuthorizationMode: String, Codable, Sendable { case oauth, official }

/// Register this application's own HTTPS callback host; unrelated applications must not share it.
public struct KsuserAuthConfiguration: Sendable {
    public let clientID: String
    public let mode: KsuserAuthorizationMode
    public let redirectURI: URL
    public let apiBaseURL: URL
    public let webBaseURL: URL
    public let officialIssuer: String

    public init(clientID: String, mode: KsuserAuthorizationMode, redirectURI: URL,
                apiBaseURL: URL = URL(string: "https://api.ksuser.cn")!,
                webBaseURL: URL = URL(string: "https://auth.ksuser.cn")!,
                officialIssuer: String = "https://api.ksuser.cn/official") {
        self.clientID = clientID; self.mode = mode; self.redirectURI = redirectURI
        self.apiBaseURL = apiBaseURL; self.webBaseURL = webBaseURL; self.officialIssuer = officialIssuer
    }
    var transactionIdentifier: String {
        apiBaseURL.absoluteString + "|" + webBaseURL.absoluteString + "|" + officialIssuer + "|" + mode.rawValue + "|" + clientID + "|" + redirectURI.absoluteString
    }
    var defaultScopes: [String] { mode == .official ? ["openid", "profile"] : ["profile"] }
    func validate() throws {
        guard !clientID.isEmpty, Self.isHTTPS(redirectURI), (redirectURI.port == nil || redirectURI.port == 443),
              !redirectURI.path.isEmpty, !redirectURI.path.split(separator: "/").contains(where: { $0 == "." || $0 == ".." }),
              redirectURI.host?.lowercased() != webBaseURL.host?.lowercased(),
              URLComponents(url: redirectURI, resolvingAgainstBaseURL: false)?.query == nil,
              Self.isHTTPS(apiBaseURL), Self.isHTTPS(webBaseURL),
              apiBaseURL.query == nil, webBaseURL.query == nil,
              let issuer = URL(string: officialIssuer), Self.isHTTPS(issuer), issuer.query == nil else { throw KsuserAuthError.invalidConfiguration }
    }
    static func isHTTPS(_ url: URL) -> Bool {
        url.scheme == "https" && url.host?.isEmpty == false && url.user == nil && url.password == nil && url.fragment == nil
    }
}

public struct KsuserTokenSet: Codable, Sendable {
    public let accessToken: String
    public let tokenType: String
    public let expiresIn: Int
    public let scope: String?
    public let idToken: String?
    public let openID: String?
    /// Present only while this application has an approved UnionID permission.
    public let unionID: String?
    enum CodingKeys: String, CodingKey {
        case accessToken = "access_token", tokenType = "token_type", expiresIn = "expires_in", scope, idToken = "id_token", openID = "openid", unionID = "unionid"
    }
}
public enum KsuserAuthorizationResult: Sendable {
    case oauth(KsuserTokenSet)
    /// subject is extracted only after RS256 signature and OIDC claim validation.
    case official(tokens: KsuserTokenSet, subject: String)
}
public enum KsuserAuthError: Error, LocalizedError, Equatable, Sendable {
    case invalidConfiguration, authorizationInProgress, cancelled, expired, invalidResponse, invalidCallback, invalidIDToken, storage(Int32)
    case server(String)
    case httpStatus(Int, String)
    public var errorDescription: String? {
        switch self {
        case .invalidConfiguration: return "Invalid Ksuser client configuration or HTTPS callback."
        case .authorizationInProgress: return "Resume or cancel the existing authorization first."
        case .cancelled: return "Authorization cancelled."
        case .expired: return "Authorization expired. Start again."
        case .invalidResponse: return "Invalid authorization server response."
        case .invalidCallback: return "The callback does not match the pending authorization."
        case .invalidIDToken: return "The official application's identity token could not be verified."
        case .storage: return "Unable to securely store the authorization transaction."
        case .httpStatus(let status, let code): return "Authorization server returned HTTP \(status): \(code)"
        case .server(let code): return "Authorization failed: \(code)"
        }
    }
}

struct NativeCreated: Codable, Sendable {
    let ticket: String
    let cancelSecret: String
    let appLink: URL
    let webAuthorizationUrl: URL
    let expiresInSeconds: Int
}
struct AuthorizationTransaction: Codable, Sendable {
    let clientID: String
    let mode: KsuserAuthorizationMode
    let redirectURI: URL
    let verifier: String
    let state: String
    let nonce: String?
    let created: NativeCreated
    let expiresAt: Date
    var configurationID: String?
    var code: String?
    var callbackError: String?
    var tokens: KsuserTokenSet?
}

enum CallbackParser {
    enum Outcome: Equatable { case code(String), error(String) }
    static func parse(_ url: URL, redirectURI: URL, state: String) -> Outcome? {
        guard let actual = URLComponents(url: url, resolvingAgainstBaseURL: false),
              let expected = URLComponents(url: redirectURI, resolvingAgainstBaseURL: false),
              actual.scheme == "https", actual.scheme == expected.scheme, actual.host == expected.host,
              actual.port == expected.port, actual.percentEncodedPath == expected.percentEncodedPath,
              actual.user == nil, actual.password == nil, actual.fragment == nil,
              let items = actual.queryItems, items.count == 2,
              items.filter({ $0.name == "state" && $0.value == state }).count == 1 else { return nil }
        if let code = items.first(where: { $0.name == "code" })?.value, !code.isEmpty { return .code(code) }
        if let error = items.first(where: { $0.name == "error" })?.value, !error.isEmpty { return .error(error) }
        return nil
    }
}

/// Only one outstanding SDK operation is allowed per calling application, even across client instances.
@MainActor enum AuthorizationLease {
    private static var owner: UUID?
    static func claim(_ candidate: UUID) -> Bool {
        guard owner == nil || owner == candidate else { return false }
        owner = candidate; return true
    }
    static func release(_ candidate: UUID) { if owner == candidate { owner = nil } }
}

extension KsuserAuthError {
    static func preservesPendingTransaction(_ error: Error) -> Bool {
        if error is URLError { return true }
        if case .httpStatus(let status, _) = error as? KsuserAuthError { return status == 429 || (500...599).contains(status) }
        return false
    }
}
