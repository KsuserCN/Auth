import AuthenticationServices
import Foundation
import UIKit
import Security

enum NativeAuthenticationError: LocalizedError, Equatable {
    case cancelled, busy, invalidOptions, invalidCredential, unavailableWindow, qqUnavailable, timedOut, appleAuthorizationFailed, authorizationFailed
    var errorDescription: String? {
        switch self {
        case .cancelled: return L10n.text("已取消验证")
        case .busy: return L10n.text("请先完成当前验证")
        case .invalidOptions: return L10n.text("验证选项无效，请重新发起")
        case .invalidCredential: return L10n.text("未取得完整的验证凭据，请重试")
        case .unavailableWindow: return L10n.text("请返回应用后重新验证")
        case .qqUnavailable: return L10n.text("无法发起 QQ 授权，请检查网络与 QQ 应用配置")
        case .timedOut: return L10n.text("QQ 授权已超时，请重试")
        case .appleAuthorizationFailed: return L10n.text("Apple 身份验证未能完成，请重试")
        case .authorizationFailed: return L10n.text("验证未能完成，请重试")
        }
    }
}

extension Data {
    init?(base64URL: String) {
        var value = base64URL.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        value += String(repeating: "=", count: (4 - value.count % 4) % 4)
        self.init(base64Encoded: value)
    }
    var base64URL: String { base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "") }
}

@MainActor final class NativeAuthenticationProvider: NSObject, NativeAuthenticationProviding, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding, TencentSessionDelegate {
    private enum Pending {
        case assertion(CheckedContinuation<PasskeyAuthenticationPayload, Error>)
        case registration(CheckedContinuation<PasskeyRegistrationPayload, Error>)
        case apple(CheckedContinuation<AppleCredential, Error>)
    }
    private var pending: Pending?
    private var controller: ASAuthorizationController?
    private var anchor: ASPresentationAnchor?
    private var qqContinuation: CheckedContinuation<QQCredential, Error>?
    private var qqTimeout: Task<Void, Never>?
    private var qq: TencentOAuth?
    private var stagedAppleIdentifier: String?
    private let environment: AppEnvironment
    init(environment: AppEnvironment = .current) { self.environment = environment; super.init() }

    private func prepare() throws {
        guard pending == nil, qqContinuation == nil else { throw NativeAuthenticationError.busy }
        anchor = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            .filter { $0.activationState == .foregroundActive }.flatMap(\.windows).first { $0.isKeyWindow }
        guard anchor != nil else { throw NativeAuthenticationError.unavailableWindow }
    }
    private func present(_ request: ASAuthorizationRequest) { present([request]) }
    private func present(_ requests: [ASAuthorizationRequest]) {
        let controller = ASAuthorizationController(authorizationRequests: requests)
        self.controller = controller
        controller.delegate = self
        controller.presentationContextProvider = self
        controller.performRequests()
    }
    func authenticatePasskey(options: PasskeyAuthenticationOptions) async throws -> PasskeyAuthenticationPayload {
        try prepare()
        guard options.rpId == environment.passkeyRPID, let challenge = Data(base64URL: options.challenge), !challenge.isEmpty else { throw NativeAuthenticationError.invalidOptions }
        let platformRequest = ASAuthorizationPlatformPublicKeyCredentialProvider(relyingPartyIdentifier: options.rpId).createCredentialAssertionRequest(challenge: challenge)
        platformRequest.userVerificationPreference = ASAuthorizationPublicKeyCredentialUserVerificationPreference(rawValue: options.userVerification ?? "preferred")
        platformRequest.allowedCredentials = (options.allowCredentials?.arrayValue ?? []).compactMap {
            guard let id = $0.objectValue?["id"]?.stringValue, let data = Data(base64URL: id) else { return nil }
            return ASAuthorizationPlatformPublicKeyCredentialDescriptor(credentialID: data)
        }

        let securityKeyProvider = ASAuthorizationSecurityKeyPublicKeyCredentialProvider(relyingPartyIdentifier: options.rpId)
        let securityKeyRequest = securityKeyProvider.createCredentialAssertionRequest(challenge: challenge)
        securityKeyRequest.userVerificationPreference = ASAuthorizationPublicKeyCredentialUserVerificationPreference(rawValue: options.userVerification ?? "preferred")
        securityKeyRequest.allowedCredentials = (options.allowCredentials?.arrayValue ?? []).compactMap { credential in
            guard let object = credential.objectValue,
                  let id = object["id"]?.stringValue,
                  let data = Data(base64URL: id) else { return nil }
            let transports = object["transports"]?.arrayValue?.compactMap { $0.stringValue }.map(securityKeyTransport)
                ?? ASAuthorizationSecurityKeyPublicKeyCredentialDescriptor.Transport.allSupported
            return ASAuthorizationSecurityKeyPublicKeyCredentialDescriptor(credentialID: data, transports: transports)
        }
        return try await withCheckedThrowingContinuation {
            pending = .assertion($0)
            present([platformRequest, securityKeyRequest])
        }
    }
    func registerPasskey(options: PasskeyRegistrationOptions) async throws -> PasskeyRegistrationPayload {
        try prepare()
        guard let rp = options.rp.objectValue, let user = options.user.objectValue,
              let rpID = rp["id"]?.stringValue, rpID == environment.passkeyRPID,
              let name = user["name"]?.stringValue, let id = user["id"]?.stringValue,
              let userID = Data(base64URL: id), !userID.isEmpty,
              let challenge = Data(base64URL: options.challenge), !challenge.isEmpty else { throw NativeAuthenticationError.invalidOptions }
        let selection = options.authenticatorSelection?.objectValue ?? [:]
        let platformProvider = ASAuthorizationPlatformPublicKeyCredentialProvider(relyingPartyIdentifier: rpID)
        let platformRequest = platformProvider.createCredentialRegistrationRequest(challenge: challenge, name: name, userID: userID)
        platformRequest.displayName = user["displayName"]?.stringValue ?? name
        platformRequest.userVerificationPreference = ASAuthorizationPublicKeyCredentialUserVerificationPreference(rawValue: selection["userVerification"]?.stringValue ?? "required")

        let securityKeyProvider = ASAuthorizationSecurityKeyPublicKeyCredentialProvider(relyingPartyIdentifier: rpID)
        let securityKeyRequest = securityKeyProvider.createCredentialRegistrationRequest(
            challenge: challenge,
            displayName: user["displayName"]?.stringValue ?? name,
            name: name,
            userID: userID
        )
        securityKeyRequest.credentialParameters = (options.pubKeyCredParams.arrayValue ?? []).compactMap { parameter in
            guard let algorithm = parameter.objectValue?["alg"]?.intValue else { return nil }
            return ASAuthorizationPublicKeyCredentialParameters(algorithm: ASCOSEAlgorithmIdentifier(algorithm))
        }
        securityKeyRequest.userVerificationPreference = ASAuthorizationPublicKeyCredentialUserVerificationPreference(rawValue: selection["userVerification"]?.stringValue ?? "required")
        securityKeyRequest.residentKeyPreference = ASAuthorizationPublicKeyCredentialResidentKeyPreference(rawValue: selection["residentKey"]?.stringValue ?? "required")

        return try await withCheckedThrowingContinuation {
            pending = .registration($0)
            present([platformRequest, securityKeyRequest])
        }
    }
    func signInWithApple(challenge: AppleChallenge) async throws -> AppleCredential {
        try prepare()
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = challenge.nonce
        request.state = challenge.state
        return try await withCheckedThrowingContinuation { pending = .apple($0); present(request) }
    }
    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor { anchor ?? ASPresentationAnchor() }
    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        let active = pending; pending = nil; self.controller = nil
        switch (active, authorization.credential) {
        case (.assertion(let continuation), let credential as ASAuthorizationPublicKeyCredentialAssertion):
            continuation.resume(returning: PasskeyAuthenticationPayload(credentialRawId: credential.credentialID.base64URL, clientDataJSON: credential.rawClientDataJSON.base64URL, authenticatorData: credential.rawAuthenticatorData.base64URL, signature: credential.signature.base64URL))
        case (.registration(let continuation), let credential as ASAuthorizationPublicKeyCredentialRegistration):
            guard let attestation = credential.rawAttestationObject else { continuation.resume(throwing: NativeAuthenticationError.invalidCredential); return }
            let transports: String
            if let securityKey = credential as? ASAuthorizationSecurityKeyPublicKeyCredentialRegistration {
                if #available(iOS 17.5, *) {
                    transports = securityKey.transports.map { $0.rawValue == "bluetooth" ? "ble" : $0.rawValue }.joined(separator: ",")
                } else {
                    transports = "usb,nfc"
                }
            } else {
                transports = "internal"
            }
            continuation.resume(returning: PasskeyRegistrationPayload(credentialRawId: credential.credentialID.base64URL, clientDataJSON: credential.rawClientDataJSON.base64URL, attestationObject: attestation.base64URL, transports: transports))
        case (.apple(let continuation), let credential as ASAuthorizationAppleIDCredential):
            guard let token = credential.identityToken.flatMap({ String(data: $0, encoding: .utf8) }), let code = credential.authorizationCode.flatMap({ String(data: $0, encoding: .utf8) }), !token.isEmpty, !code.isEmpty else { continuation.resume(throwing: NativeAuthenticationError.invalidCredential); return }
            stagedAppleIdentifier = credential.user
            // Keep first-authorization name data available until the server accepts it.
            continuation.resume(returning: AppleCredential(identityToken: token, authorizationCode: code, givenName: credential.fullName?.givenName, familyName: credential.fullName?.familyName))
        default:
            if let active { fail(active, NativeAuthenticationError.invalidCredential) }
        }
    }
    private func securityKeyTransport(_ value: String) -> ASAuthorizationSecurityKeyPublicKeyCredentialDescriptor.Transport {
        ASAuthorizationSecurityKeyPublicKeyCredentialDescriptor.Transport(rawValue: value == "ble" ? "bluetooth" : value)
    }
    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        if let active = pending {
            pending = nil; self.controller = nil
            let nsError = error as NSError
            guard nsError.domain == ASAuthorizationError.errorDomain else { fail(active, error); return }
            let mappedError: NativeAuthenticationError
            if nsError.code == ASAuthorizationError.canceled.rawValue {
                mappedError = .cancelled
            } else if case .apple = active {
                mappedError = .appleAuthorizationFailed
            } else {
                mappedError = .authorizationFailed
            }
            fail(active, mappedError)
        }
    }
    private func fail(_ active: Pending, _ error: Error) {
        switch active {
        case .assertion(let c): c.resume(throwing: error)
        case .registration(let c): c.resume(throwing: error)
        case .apple(let c): c.resume(throwing: error)
        }
    }

    func signInWithQQ() async throws -> QQCredential {
        try prepare()
        // The UI checks the service/privacy agreement before calling this method.
        TencentOAuth.setIsUserAgreedAuthorization(true)
        let sdk = TencentOAuth.sharedInstance()!
        let link = Bundle.main.object(forInfoDictionaryKey: "KSUSER_QQ_UNIVERSAL_LINK") as? String ?? "https://auth.ksuser.cn/app/qq/"
        sdk.setupAppId(environment.qqAppID, enableUniveralLink: true, universalLink: link, delegate: self)
        qq = sdk
        return try await withCheckedThrowingContinuation { continuation in
            qqContinuation = continuation
            qqTimeout = Task { [weak self] in
                try? await Task.sleep(for: .seconds(120))
                guard !Task.isCancelled else { return }
                self?.finishQQ(.failure(NativeAuthenticationError.timedOut))
            }
            if !sdk.authorize(["get_user_info", "get_simple_userinfo"]) { finishQQ(.failure(NativeAuthenticationError.qqUnavailable)) }
        }
    }
    func tencentDidLogin() {
        guard let sdk = qq, !(sdk.accessToken ?? "").isEmpty, !(sdk.openId ?? "").isEmpty else { finishQQ(.failure(NativeAuthenticationError.invalidCredential)); return }
        if !(sdk.unionid ?? "").isEmpty { deliverQQ() }
        else if !sdk.requestUnionId() { finishQQ(.failure(NativeAuthenticationError.invalidCredential)) }
    }
    func didGetUnionID() { deliverQQ() }
    func tencentDidNotLogin(_ cancelled: Bool) { finishQQ(.failure(cancelled ? NativeAuthenticationError.cancelled : NativeAuthenticationError.qqUnavailable)) }
    func tencentDidNotNetWork() { finishQQ(.failure(URLError(.notConnectedToInternet))) }
    private func deliverQQ() {
        guard let sdk = qq, let token = sdk.accessToken, let openID = sdk.openId, let unionID = sdk.unionid, !token.isEmpty, !openID.isEmpty, !unionID.isEmpty else { finishQQ(.failure(NativeAuthenticationError.invalidCredential)); return }
        let expires = sdk.expirationDate.map { String(max(0, Int($0.timeIntervalSinceNow))) }
        finishQQ(.success(QQCredential(appId: environment.qqAppID, accessToken: token, openid: openID, unionid: unionID, expiresIn: expires)))
    }
    private func finishQQ(_ result: Result<QQCredential, Error>) {
        let continuation = qqContinuation; qqContinuation = nil; qqTimeout?.cancel(); qqTimeout = nil
        continuation?.resume(with: result)
    }
    static func handleCallback(_ url: URL) -> Bool {
        if TencentOAuth.canHandleUniversalLink(url) { return TencentOAuth.handleUniversalLink(url) }
        if TencentOAuth.canHandleOpen(url) { return TencentOAuth.handleOpen(url) }
        return false
    }

    func acceptAppleCredential() throws {
        guard let identifier = stagedAppleIdentifier else { return }
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "cn.ksuser.auth.apple-identity", kSecAttrAccount as String: "accepted-subject"]
        let attributes: [String: Any] = [kSecValueData as String: Data(identifier.utf8), kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]
        var result = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if result == errSecItemNotFound { result = SecItemAdd(query.merging(attributes) { _, new in new } as CFDictionary, nil) }
        guard result == errSecSuccess else { throw NSError(domain: NSOSStatusErrorDomain, code: Int(result)) }
        stagedAppleIdentifier = nil
    }
    func clearAppleCredential() throws {
        stagedAppleIdentifier = nil
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "cn.ksuser.auth.apple-identity", kSecAttrAccount as String: "accepted-subject"]
        let result = SecItemDelete(query as CFDictionary)
        guard result == errSecSuccess || result == errSecItemNotFound else { throw NSError(domain: NSOSStatusErrorDomain, code: Int(result)) }
    }
    func appleCredentialRevoked() async -> Bool {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "cn.ksuser.auth.apple-identity", kSecAttrAccount as String: "accepted-subject", kSecReturnData as String: true, kSecMatchLimit as String: kSecMatchLimitOne]
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess, let data = result as? Data, let identifier = String(data: data, encoding: .utf8) else { return false }
        return await withCheckedContinuation { continuation in
            ASAuthorizationAppleIDProvider().getCredentialState(forUserID: identifier) { state, error in
                continuation.resume(returning: error == nil && (state == .revoked || state == .notFound))
            }
        }
    }
}
