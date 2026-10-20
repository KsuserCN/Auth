import Foundation
import CryptoKit
import Security

extension Data {
    var base64URL: String { base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "") }
    init?(base64URL: String) {
        guard !base64URL.isEmpty, base64URL.range(of: #"^[A-Za-z0-9_-]+$"#, options: .regularExpression) != nil, base64URL.count % 4 != 1 else { return nil }
        let padded = base64URL.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/") + String(repeating: "=", count: (4 - base64URL.count % 4) % 4)
        self.init(base64Encoded: padded)
    }
}
enum PKCE {
    static func random() throws -> String {
        var bytes = [UInt8](repeating: 0, count: 32)
        guard SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes) == errSecSuccess else { throw KsuserAuthError.invalidResponse }
        return Data(bytes).base64URL
    }
    static func challenge(_ verifier: String) -> String { Data(SHA256.hash(data: Data(verifier.utf8))).base64URL }
}

protocol SDKTransactionStoring {
    func load() throws -> AuthorizationTransaction?
    func save(_ transaction: AuthorizationTransaction) throws
    func clear() throws
}

struct TransactionStore: SDKTransactionStoring {
    let service: String
    init(configuration: KsuserAuthConfiguration) {
        service = (Bundle.main.bundleIdentifier ?? "KsuserAuthSDK") + ".ksuser.native.transaction"
    }
    private var query: [String: Any] { [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: "pending"] }
    func load() throws -> AuthorizationTransaction? {
        var q = query; q[kSecReturnData as String] = true; q[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(q as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = result as? Data else { throw KsuserAuthError.storage(status) }
        return try JSONDecoder().decode(AuthorizationTransaction.self, from: data)
    }
    func save(_ transaction: AuthorizationTransaction) throws {
        let attributes: [String: Any] = [kSecValueData as String: try JSONEncoder().encode(transaction), kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly]
        let status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            var q = query; attributes.forEach { q[$0.key] = $0.value }
            let added = SecItemAdd(q as CFDictionary, nil)
            guard added == errSecSuccess else { throw KsuserAuthError.storage(added) }
        } else if status != errSecSuccess { throw KsuserAuthError.storage(status) }
    }
    func clear() throws {
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw KsuserAuthError.storage(status) }
    }
}

struct JWKSet: Decodable { let keys: [JWK] }
struct JWK: Codable {
    let kty: String; let kid: String?; let alg: String?; let use: String?; let n: String?; let e: String?
}
enum IDTokenVerifier {
    static func subject(token: String, keys: JWKSet, issuer: String, clientID: String, nonce: String, now: Date = Date()) throws -> String {
        let parts = token.split(separator: ".", omittingEmptySubsequences: false).map(String.init)
        guard parts.count == 3, let headerData = Data(base64URL: parts[0]), let payloadData = Data(base64URL: parts[1]),
              let signature = Data(base64URL: parts[2]),
              let header = try? JSONSerialization.jsonObject(with: headerData) as? [String: Any],
              header["alg"] as? String == "RS256", header["crit"] == nil,
              let kid = header["kid"] as? String, !kid.isEmpty,
              let claims = try? JSONSerialization.jsonObject(with: payloadData) as? [String: Any] else { throw KsuserAuthError.invalidIDToken }
        let matching = keys.keys.filter { $0.kid == kid && $0.kty == "RSA" && ($0.alg == nil || $0.alg == "RS256") && ($0.use == nil || $0.use == "sig") }
        guard matching.count == 1, let n = matching[0].n.flatMap(Data.init(base64URL:)), let e = matching[0].e.flatMap(Data.init(base64URL:)),
              n.count >= 256, n.count <= 1024, !e.isEmpty, e.count <= 8 else { throw KsuserAuthError.invalidIDToken }
        let keyData = der(0x30, derInteger(n) + derInteger(e))
        guard let key = SecKeyCreateWithData(keyData as CFData, [kSecAttrKeyType: kSecAttrKeyTypeRSA, kSecAttrKeyClass: kSecAttrKeyClassPublic] as CFDictionary, nil),
              SecKeyVerifySignature(key, .rsaSignatureMessagePKCS1v15SHA256, Data((parts[0] + "." + parts[1]).utf8) as CFData, signature as CFData, nil),
              claims["iss"] as? String == issuer, claims["nonce"] as? String == nonce,
              let subject = claims["sub"] as? String, !subject.isEmpty,
              let expiration = numeric(claims["exp"]), expiration > now.timeIntervalSince1970,
              let issued = numeric(claims["iat"]), issued <= now.timeIntervalSince1970 + 60, issued < expiration else { throw KsuserAuthError.invalidIDToken }
        let audience = (claims["aud"] as? [String]) ?? (claims["aud"] as? String).map { [$0] } ?? []
        guard audience.contains(clientID), audience.count == 1 || claims["azp"] as? String == clientID,
              claims["azp"] == nil || claims["azp"] as? String == clientID else { throw KsuserAuthError.invalidIDToken }
        if let notBefore = claims["nbf"] {
            guard let time = numeric(notBefore), time <= now.timeIntervalSince1970 + 60 else { throw KsuserAuthError.invalidIDToken }
        }
        return subject
    }
    private static func numeric(_ value: Any?) -> Double? {
        guard let number = value as? NSNumber, CFGetTypeID(number) != CFBooleanGetTypeID() else { return nil }
        return number.doubleValue.isFinite ? number.doubleValue : nil
    }
    private static func derInteger(_ bytes: Data) -> Data {
        var value = Data(bytes.drop(while: { $0 == 0 }))
        if value.isEmpty { value = Data([0]) }
        if value[0] & 0x80 != 0 { value.insert(0, at: 0) }
        return der(0x02, value)
    }
    private static func der(_ tag: UInt8, _ value: Data) -> Data {
        var count = value.count; var length = [UInt8]()
        if count < 128 { length = [UInt8(count)] }
        else { while count > 0 { length.insert(UInt8(count & 0xff), at: 0); count >>= 8 }; length.insert(0x80 | UInt8(length.count), at: 0) }
        return Data([tag] + length) + value
    }
}

enum PendingAuthorizationRecovery {
    static func load(from storage: any SDKTransactionStoring, configuration: KsuserAuthConfiguration, now: Date = Date()) throws -> AuthorizationTransaction? {
        guard let transaction = try storage.load() else { return nil }
        // Expiry precedes configuration matching so an obsolete client ID/domain never blocks a new configuration.
        guard transaction.expiresAt > now else { try storage.clear(); return nil }
        guard transaction.configurationID == configuration.transactionIdentifier else { throw KsuserAuthError.authorizationInProgress }
        return transaction
    }
}
