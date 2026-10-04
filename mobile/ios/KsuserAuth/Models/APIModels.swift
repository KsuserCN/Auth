import Foundation

/// JSON values retain the existing Android API's string-encoded WebAuthn options.
enum JSONValue: Codable, Sendable, Equatable {
    case string(String), number(Double), bool(Bool), object([String: JSONValue]), array([JSONValue]), null
    init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if c.decodeNil() { self = .null }
        else if let value = try? c.decode(Bool.self) { self = .bool(value) }
        else if let value = try? c.decode(String.self) { self = .string(value) }
        else if let value = try? c.decode(Double.self) { self = .number(value) }
        else if let value = try? c.decode([String: JSONValue].self) { self = .object(value) }
        else { self = .array(try c.decode([JSONValue].self)) }
    }
    func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        switch self {
        case .string(let v): try c.encode(v)
        case .number(let v): try c.encode(v)
        case .bool(let v): try c.encode(v)
        case .object(let v): try c.encode(v)
        case .array(let v): try c.encode(v)
        case .null: try c.encodeNil()
        }
    }
    var stringValue: String? { if case .string(let v) = self { return v }; return nil }
    var boolValue: Bool? { if case .bool(let v) = self { return v }; return nil }
    var objectValue: [String: JSONValue]? {
        if case .object(let v) = self { return v }
        if case .string(let v) = self, let d = v.data(using: .utf8), let decoded = try? JSONDecoder().decode(JSONValue.self, from: d) { return decoded.objectValue }
        return nil
    }
    var arrayValue: [JSONValue]? {
        if case .array(let v) = self { return v }
        if case .string(let v) = self, let d = v.data(using: .utf8), let decoded = try? JSONDecoder().decode(JSONValue.self, from: d) { return decoded.arrayValue }
        return nil
    }
    var intValue: Int? {
        if case .number(let v) = self { return Int(v) }
        return stringValue.flatMap(Int.init)
    }
}

struct APIEnvelope<T: Decodable & Sendable>: Decodable, Sendable { let code: Int; let msg: String?; let data: T? }
/// Successful mutation payloads vary between null, a message string and an object.
struct EmptyPayload: Codable, Sendable {
    init() {}
    init(from decoder: Decoder) throws {}
}
struct TokenPayload: Codable, Sendable { let accessToken: String }
struct PasswordRequirement: Codable, Sendable {
    let minLength: Int; let maxLength: Int
    let requireUppercase: Bool; let requireLowercase: Bool; let requireDigits: Bool; let requireSpecialChars: Bool
    let rejectCommonWeakPasswords: Bool?; let requirementMessage: String?
    func validationError(for password: String) -> String? {
        if password.utf16.count < minLength || password.utf16.count > maxLength { return "密码需要 \(minLength)–\(maxLength) 个字符" }
        if password.rangeOfCharacter(from: .newlines) != nil { return "密码不能包含换行符" }
        if requireUppercase && password.range(of: "[A-Z]", options: .regularExpression) == nil { return "密码需包含大写字母 A–Z" }
        if requireLowercase && password.range(of: "[a-z]", options: .regularExpression) == nil { return "密码需包含小写字母 a–z" }
        if requireDigits && password.range(of: "[0-9]", options: .regularExpression) == nil { return "密码需包含数字 0–9" }
        if requireSpecialChars && password.rangeOfCharacter(from: CharacterSet(charactersIn: "@$!%*?&")) == nil { return "密码需包含特殊字符 @$!%*?&" }
        return nil
    }
}
struct UserSettings: Codable, Sendable {
    var mfaEnabled = false; var detectUnusualLogin = true; var notifySensitiveActionEmail = true; var subscribeNewsEmail = false
    var preferredMfaMethod: String?; var preferredSensitiveMethod: String?
}
struct UserProfile: Codable, Sendable, Identifiable {
    var uuid: String; var username: String; var email: String
    var avatarUrl: String?; var realName: String?; var gender: String?; var birthDate: String?; var region: String?; var bio: String?
    var verificationType: String?; var updatedAt: String?; var settings: UserSettings?; var hasPassword: Bool?; var appleBound: Bool?
    var id: String { uuid }
    var meaningfulRealName: String? {
        guard let name = realName?.trimmingCharacters(in: .whitespacesAndNewlines),
              !name.isEmpty,
              !["无", "未设置", "暂无", "null", "-"].contains(name.lowercased()) else { return nil }
        return name
    }
    var displayName: String { meaningfulRealName ?? username }
    var profileCompleteness: Int {
        let fields: [String?] = [username, avatarUrl, meaningfulRealName, region, bio]
        return Int(Double(fields.filter { !($0 ?? "").isEmpty }.count) / Double(fields.count) * 100)
    }
}
struct MFAChallenge: Identifiable, Sendable { let challengeId: String; let method: String; let methods: [String]; let source: String; var id: String { challengeId } }
struct PendingOAuth: Identifiable, Sendable {
    let provider: String; let bindToken: String; let message: String?; var canRegister = true; var emailConflict = false
    var id: String { bindToken }
}
enum AuthResult: Sendable { case success(String), needsMFA(MFAChallenge), needsOAuth(PendingOAuth) }

struct PasskeyAuthenticationOptions: Codable, Sendable {
    let challenge: String; let challengeId: String; let timeout: JSONValue?; let rpId: String; let userVerification: String?; let allowCredentials: JSONValue?
}
struct PasskeyRegistrationOptions: Codable, Sendable {
    let challenge: String; let rp: JSONValue; let user: JSONValue; let pubKeyCredParams: JSONValue
    let timeout: JSONValue?; let attestation: String?; let authenticatorSelection: JSONValue?
}
struct PasskeyAuthenticationPayload: Codable, Sendable {
    let credentialRawId: String; let clientDataJSON: String; let authenticatorData: String; let signature: String
}
struct PasskeyRegistrationPayload: Codable, Sendable {
    let credentialRawId: String; let clientDataJSON: String; let attestationObject: String; let transports: String
}
struct PasskeyListItem: Codable, Sendable, Identifiable {
    let id: Int64; let name: String; let transports: String?; let lastUsedAt: String?; let createdAt: String
}
struct PasskeyListResponse: Codable, Sendable { let passkeys: [PasskeyListItem] }
struct TotpStatus: Codable, Sendable { var enabled: Bool; var recoveryCodesCount: Int }
struct TotpRegistrationOptions: Codable, Sendable { let secret: String; let qrCodeUrl: String; let recoveryCodes: [String] }
struct SensitiveVerificationStatus: Codable, Sendable { let verified: Bool; let remainingSeconds: Int; let preferredMethod: String?; let methods: [String]? }
struct AdaptiveAuthStatus: Codable, Sendable {
    let sessionId: Int64?; let riskScore: Int; let riskLevel: String; let policyDecision: String?; let policyVersion: String?
    let trusted: Bool; let requiresStepUp: Bool; let sessionFrozen: Bool; let sensitiveVerified: Bool; let sensitiveVerificationRemainingSeconds: Int
    let authAgeSeconds: Int; let idleSeconds: Int; let currentIp: String?; let currentLocation: String?; let sessionIp: String?; let sessionLocation: String?
    let browser: String?; let deviceType: String?; let multiEndpointAlert: Bool; let alertLevel: String?; let alertTitle: String?; let alertMessage: String?
    let alertRemainingSeconds: Int; let recommendedAction: String; let reasons: [String]
}
struct SessionItem: Codable, Sendable, Identifiable {
    let id: Int64; let ipAddress: String; let ipLocation: String?; let userAgent: String?; let browser: String?; let deviceType: String?
    let createdAt: String; let lastSeenAt: String; let expiresAt: String; let revokedAt: String?; let online: Bool; let current: Bool
}
struct KsuserAuthorizedApp: Decodable, Sendable, Identifiable {
    let clientId: String; let clientName: String; let logoUrl: String?; let redirectUri: String?
    let scopes: [String]; let authorizedAt: String?; let lastAuthorizedAt: String?
    let grantMode: String; let expiresAt: String?
    var id: String { clientId }
}
struct ThirdPartyAuthorizedApp: Decodable, Sendable, Identifiable {
    let appId: String; let appName: String; let logoUrl: String?; let creatorName: String?
    let creatorVerificationType: String?; let contactInfo: String?; let redirectUri: String?
    let scopes: [String]; let authorizedAt: String?; let lastAuthorizedAt: String?
    let grantMode: String; let expiresAt: String?
    var id: String { appId }
}
struct AuthorizedAppsSnapshot: Sendable {
    let ksuserApps: [KsuserAuthorizedApp]
    let thirdPartyApps: [ThirdPartyAuthorizedApp]
}
struct SensitiveLogItem: Codable, Sendable, Identifiable {
    let id: Int64; let operationType: String; let loginMethod: String?; let loginMethods: [String]?; let ipAddress: String; let ipLocation: String?
    let browser: String?; let deviceType: String?; let result: String; let failureReason: String?; let riskScore: Int; let actionTaken: String?
    let triggeredMultiErrorLock: Bool; let triggeredRateLimitLock: Bool; let durationMs: Int?; let createdAt: String
}
struct PaginatedSensitiveLogs: Codable, Sendable { let data: [SensitiveLogItem]; let page: Int; let pageSize: Int; let total: Int; let totalPages: Int }
struct QrScanPreview: Codable, Sendable {
    let codeType: String; let clientName: String?; let browser: String?; let system: String?; let ipAddress: String?; let ipLocation: String?; let expiresInSeconds: Int
}
struct QRConfirmation: Identifiable, Sendable { let code: String; let isTransfer: Bool; let preview: QrScanPreview; let expiresAt: Date; var id: String { code } }
struct AccountRecoveryTicket: Codable, Sendable {
    let recoveryCode: String?; let expiresInSeconds: Int; let username: String?; let maskedEmail: String?; let sponsorClientName: String?
    let sponsorBrowser: String?; let sponsorSystem: String?; let sponsorIpLocation: String?
    var qrContent: String? {
        guard let recoveryCode else { return nil }
        var components = URLComponents(string: "https://auth.ksuser.cn/forgot-password")!
        components.queryItems = [URLQueryItem(name: "recoveryCode", value: recoveryCode), URLQueryItem(name: "source", value: "mobile")]
        return components.url?.absoluteString
    }
}
struct MobileBridgeStatusPayload: Codable, Sendable { let status: String; let transferCode: String?; let returnUrl: String?; let returnOrigin: String?; let expiresInSeconds: Int }
struct MobileBridgeApproveResponse: Codable, Sendable { let challengeId: String; let returnUrl: String; let returnOrigin: String?; let expiresInSeconds: Int }
struct BridgeConfirmation: Identifiable, Sendable { let challengeId: String; let status: MobileBridgeStatusPayload; let expiresAt: Date; var id: String { challengeId } }
struct AppleChallenge: Codable, Sendable { let challengeId: String; let nonce: String; let state: String; let expiresInSeconds: Int }
struct AppleCredential: Codable, Sendable {
    let identityToken: String; let authorizationCode: String; var givenName: String?; var familyName: String?
}
struct QQCredential: Codable, Sendable { let appId: String; let accessToken: String; let openid: String; let unionid: String; let expiresIn: String? }
struct OAuthAccountStatusItem: Codable, Sendable {
    let provider: String; let bound: Bool; let lastLoginAt: String?
}
struct OAuthAccountStatus: Codable, Sendable { let appleBound: Bool?; let qqBound: Bool?; let hasPassword: Bool? }
struct SensitiveRequest: Identifiable { let id = UUID(); let title: String; let status: SensitiveVerificationStatus }
typealias TOTPSetup = TotpRegistrationOptions

@MainActor protocol NativeAuthenticationProviding {
    func authenticatePasskey(options: PasskeyAuthenticationOptions) async throws -> PasskeyAuthenticationPayload
    func registerPasskey(options: PasskeyRegistrationOptions) async throws -> PasskeyRegistrationPayload
    func signInWithApple(challenge: AppleChallenge) async throws -> AppleCredential
    func signInWithQQ() async throws -> QQCredential
    func appleCredentialRevoked() async -> Bool
    func acceptAppleCredential() throws
    func clearAppleCredential() throws
}
