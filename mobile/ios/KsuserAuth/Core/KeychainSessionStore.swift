import Foundation
import Security

struct StoredCookie: Codable, Sendable {
    let name: String; let value: String; let domain: String; let path: String; let secure: Bool; let expiresAt: Date?
    init(_ cookie: HTTPCookie) { name = cookie.name; value = cookie.value; domain = cookie.domain; path = cookie.path; secure = cookie.isSecure; expiresAt = cookie.expiresDate }
    func matches(_ url: URL, now: Date = Date()) -> Bool {
        guard let host = url.host?.lowercased(), expiresAt.map({ $0 > now }) ?? true, !secure || url.scheme == "https" else { return false }
        let normalized = domain.lowercased().trimmingCharacters(in: CharacterSet(charactersIn: "."))
        let domainMatches = domain.hasPrefix(".") ? (host == normalized || host.hasSuffix("." + normalized)) : host == normalized
        let requestPath = url.path.isEmpty ? "/" : url.path
        let pathMatches = requestPath == path || (requestPath.hasPrefix(path) && (path.hasSuffix("/") || requestPath.dropFirst(path.count).hasPrefix("/")))
        return domainMatches && pathMatches
    }
}
struct SessionSnapshot: Codable, Sendable {
    var accessToken: String?
    var cookies: [StoredCookie] = []
    var profile: UserProfile?
    var authSource: String?
}
protocol SessionStoring: Sendable { func load() throws -> SessionSnapshot?; func save(_ session: SessionSnapshot) throws; func clear() throws }
struct KeychainSessionStore: SessionStoring {
    private let service: String
    init(service: String = (Bundle.main.bundleIdentifier ?? "cn.ksuser.auth") + ".session") { self.service = service }
    private var query: [String: Any] { [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: "authenticated-session"] }
    func load() throws -> SessionSnapshot? {
        var q = query; q[kSecReturnData as String] = true; q[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(q as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = result as? Data else { throw APIError.storage(status) }
        return try JSONDecoder().decode(SessionSnapshot.self, from: data)
    }
    func save(_ session: SessionSnapshot) throws {
        let data = try JSONEncoder().encode(session)
        let attrs: [String: Any] = [kSecValueData as String: data, kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]
        let status = SecItemUpdate(query as CFDictionary, attrs as CFDictionary)
        if status == errSecItemNotFound {
            var q = query; attrs.forEach { q[$0.key] = $0.value }
            let created = SecItemAdd(q as CFDictionary, nil)
            guard created == errSecSuccess else { throw APIError.storage(created) }
        } else if status != errSecSuccess { throw APIError.storage(status) }
    }
    func clear() throws {
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw APIError.storage(status) }
    }
}

/// Staged login and transfer requests cannot replace the live session until user loading succeeds.
final class MemorySessionStore: SessionStoring, @unchecked Sendable {
    private let lock = NSLock()
    private var value: SessionSnapshot?
    init(_ value: SessionSnapshot? = nil) { self.value = value }
    func load() throws -> SessionSnapshot? { lock.lock(); defer { lock.unlock() }; return value }
    func save(_ session: SessionSnapshot) throws { lock.lock(); defer { lock.unlock() }; value = session }
    func clear() throws { lock.lock(); defer { lock.unlock() }; value = nil }
}
