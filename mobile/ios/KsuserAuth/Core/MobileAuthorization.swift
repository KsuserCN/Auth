import Foundation

struct MobileAuthorizationContext: Decodable, Sendable, Identifiable {
    let ticket: String
    let appName: String
    let logoUrl: String?
    let contactInfo: String?
    let redirectUri: String
    let requestedScopes: [String]
    let alreadyAuthorized: Bool
    let existingGrantMode: String
    let expiresInSeconds: Int
    var id: String { ticket }
}
struct MobileAuthorizationReturn: Decodable, Sendable { let returnUrl: String }

enum MobileAuthorizationReturnBrowser {
    case system
    case chrome
}

enum MobileAuthorizationLink {
    static func ticket(from url: URL, environment: AppEnvironment) -> String? {
        let custom = url.scheme == "ksuserauth" && url.host == "authorize" && url.path.isEmpty
        let universal = url.scheme == "https" && url.host == environment.webURL.host && url.path == "/app/authorize"
        guard custom || universal, url.user == nil, url.password == nil,
              let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems,
              items.filter({ $0.name == "ticket" }).count == 1,
              let ticket = items.first(where: { $0.name == "ticket" })?.value,
              ticket.range(of: #"^[A-Za-z0-9_-]{32}$"#, options: .regularExpression) != nil else { return nil }
        return ticket
    }
    static func returnBrowser(from url: URL) -> MobileAuthorizationReturnBrowser {
        guard url.scheme == "ksuserauth", url.host == "authorize",
              let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems else { return .system }
        let browsers = items.filter { $0.name == "returnBrowser" }
        return browsers.count == 1 && browsers[0].value == "chrome" ? .chrome : .system
    }
    static func preferredReturnURL(_ url: URL, browser: MobileAuthorizationReturnBrowser) -> URL {
        guard browser == .chrome, var components = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return url }
        switch components.scheme?.lowercased() {
        case "https": components.scheme = "googlechromes"
        case "http": components.scheme = "googlechrome"
        default: return url
        }
        return components.url ?? url
    }
    static func browserReturn(_ value: String, environment: AppEnvironment) -> URL? {
        let trustedHosts: Set<String> = environment.webURL.host == "auth.ksuser.cn"
            ? ["auth.ksuser.cn", "www.ksuser.cn"] : [environment.webURL.host ?? ""]
        guard let url = URL(string: value), let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              url.scheme == environment.webURL.scheme, trustedHosts.contains(url.host ?? ""),
              url.port == environment.webURL.port, url.user == nil, url.password == nil,
              url.path == "/app/authorize-return", components.query == nil,
              let fragment = components.fragment else { return nil }
        let items = URLComponents(string: "https://return.invalid/?" + fragment)?.queryItems ?? []
        guard ["ticket", "secret"].allSatisfy({ name in
            items.filter { $0.name == name }.count == 1 &&
            items.first { $0.name == name }?.value?.range(of: #"^[A-Za-z0-9_-]{32}$"#, options: .regularExpression) != nil
        }) else { return nil }
        return url
    }
}

extension KsuserRepositoryProviding {
    func mobileAuthorization(_ ticket: String) async throws -> MobileAuthorizationContext {
        try await client.request("/auth/mobile-authorization/context", query: ["ticket": ticket])
    }
    func decideMobileAuthorization(_ ticket: String, approve: Bool, mode: String, ttl: Int) async throws -> MobileAuthorizationReturn {
        var body: [String: JSONValue] = ["ticket": .string(ticket), "approve": .bool(approve), "grantMode": .string(mode)]
        if mode == "TIME_LIMITED" { body["grantTtlSeconds"] = .number(Double(ttl)) }
        return try await client.request("/auth/mobile-authorization/decide", method: "POST", body: body)
    }
}
