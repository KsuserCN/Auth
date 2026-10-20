import XCTest
import Security
@testable import KsuserAuthSDK

final class ProtocolTests: XCTestCase {
    private let redirect = URL(string: "https://login.example.app/ksuser/callback")!
    private var configuration: KsuserAuthConfiguration { .init(clientID: "native-client", mode: .oauth, redirectURI: redirect) }
    @MainActor func testSharedLeaseRejectsAnotherSDKInstance() {
        let first = UUID(), second = UUID()
        XCTAssertTrue(AuthorizationLease.claim(first))
        XCTAssertFalse(AuthorizationLease.claim(second))
        AuthorizationLease.release(second)
        XCTAssertFalse(AuthorizationLease.claim(second))
        AuthorizationLease.release(first)
        XCTAssertTrue(AuthorizationLease.claim(second))
        AuthorizationLease.release(second)
    }
    func testS256UsesRFC7636VectorAndSecureRandomValues() throws {
        XCTAssertEqual(PKCE.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"), "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
        let values = try (0..<20).map { _ in try PKCE.random() }
        XCTAssertEqual(Set(values).count, values.count)
        XCTAssertTrue(values.allSatisfy { $0.count == 43 && Data(base64URL: $0)?.count == 32 })
    }
    func testCallbackRequiresExactHTTPSLocationStateAndSingleOutcome() {
        func parse(_ text: String) -> CallbackParser.Outcome? { CallbackParser.parse(URL(string: text)!, redirectURI: redirect, state: "expected") }
        XCTAssertEqual(parse(redirect.absoluteString + "?code=abc&state=expected"), .code("abc"))
        XCTAssertEqual(parse(redirect.absoluteString + "?error=access_denied&state=expected"), .error("access_denied"))
        for suffix in ["?code=abc&state=wrong", "?code=abc&state=expected&state=expected", "?code=abc&error=denied&state=expected", "?code=&state=expected", "?code=abc&state=expected#fragment"] {
            XCTAssertNil(parse(redirect.absoluteString + suffix))
        }
        for prefix in ["http://login.example.app/ksuser/callback", "https://evil.example/ksuser/callback", "https://login.example.app/ksuser/callback/extra", "https://user@login.example.app/ksuser/callback", "https://login.example.app:444/ksuser/callback"] {
            XCTAssertNil(parse(prefix + "?code=abc&state=expected"))
        }
    }
    func testConfigurationRejectsSchemesQueriesFragmentsCredentialsAndPorts() throws {
        try configuration.validate()
        try KsuserAuthConfiguration(clientID: "native", mode: .oauth, redirectURI: URL(string: "https://app.example:443/callback")!).validate()
        XCTAssertEqual(configuration.defaultScopes, ["profile"])
        XCTAssertEqual(KsuserAuthConfiguration(clientID: "official", mode: .official, redirectURI: redirect).defaultScopes, ["openid", "profile"])
        for url in ["sample://callback", "http://app.example/callback", "https://user@app.example/callback", "https://app.example/callback?x=1", "https://app.example/callback#f", "https://app.example:444/callback", "https://app.example", "https://app.example/a/../callback", "https://auth.ksuser.cn/callback"] {
            XCTAssertThrowsError(try KsuserAuthConfiguration(clientID: "native", mode: .oauth, redirectURI: URL(string: url)!).validate())
        }
    }
    func testCreatedRequestCannotReplaceTrustedLaunchOrFallbackHosts() throws {
        let http = SDKHTTP(configuration: configuration)
        let ticket = String(repeating: "a", count: 32)
        func created(_ app: String, _ web: String) -> NativeCreated {
            .init(ticket: ticket, cancelSecret: ticket, appLink: URL(string: app)!, webAuthorizationUrl: URL(string: web)!, expiresInSeconds: 300)
        }
        let app = "https://auth.ksuser.cn/app/sdk-authorize?ticket=" + ticket
        let web = "https://auth.ksuser.cn/app/sdk-authorize/web?ticket=" + ticket
        try http.validate(created(app, web))
        XCTAssertThrowsError(try http.validate(created(app.replacingOccurrences(of: "auth.ksuser.cn", with: "evil.example"), web)))
        XCTAssertThrowsError(try http.validate(created(app, web + "&returnUrl=https://evil.example")))
        XCTAssertThrowsError(try http.validate(created(app.replacingOccurrences(of: "https:", with: "ksuserauth:"), web)))
    }
    func testNativeCreateIdentifiesIOSForOAuthAndOfficialModes() async throws {
        let verifier = String(repeating: "v", count: 43)
        for mode in [KsuserAuthorizationMode.oauth, .official] {
            let fixture = NativeCreateTransport()
            let config = KsuserAuthConfiguration(clientID: "mobile-app-id", mode: mode, redirectURI: redirect)
            let http = SDKHTTP(configuration: config, transport: fixture)
            _ = try await http.create(scopes: config.defaultScopes, state: "request-state", nonce: mode == .official ? "request-nonce" : nil, verifier: verifier)
            let captured = await fixture.captured()
            let request = try XCTUnwrap(captured)
            let body = try XCTUnwrap(try JSONSerialization.jsonObject(with: XCTUnwrap(request.httpBody)) as? [String: Any])
            XCTAssertEqual(request.url?.path, "/auth/native-authorization/create")
            XCTAssertEqual(request.value(forHTTPHeaderField: "Content-Type"), "application/json")
            XCTAssertEqual(Set(body.keys), ["mode", "platform", "authorization"])
            XCTAssertEqual(body["platform"] as? String, "ios")
            XCTAssertEqual(body["mode"] as? String, mode.rawValue)
            let authorization = try XCTUnwrap(body["authorization"] as? [String: String])
            XCTAssertEqual(authorization["clientId"], "mobile-app-id")
            XCTAssertEqual(authorization["redirectUri"], redirect.absoluteString)
            XCTAssertEqual(authorization["scope"], config.defaultScopes.joined(separator: " "))
            XCTAssertEqual(authorization["state"], "request-state")
            XCTAssertEqual(authorization["nonce"], mode == .official ? "request-nonce" : nil)
            XCTAssertEqual(authorization["codeChallenge"], PKCE.challenge(verifier))
            XCTAssertEqual(authorization["codeChallengeMethod"], "S256")
            XCTAssertNil(authorization["codeVerifier"])
            XCTAssertNil(authorization["clientSecret"])
            XCTAssertNil(request.value(forHTTPHeaderField: "Authorization"))
            XCTAssertNil(request.value(forHTTPHeaderField: "Cookie"))
        }
    }
    func testHTTPFailureIncludesEndpointAndSanitizedResponseBody() async throws {
        let http = SDKHTTP(configuration: configuration, transport: FailureTransport())
        do {
            _ = try await http.create(scopes: ["profile"], state: "state", nonce: nil, verifier: String(repeating: "v", count: 43))
            XCTFail("Expected HTTP failure")
        } catch KsuserAuthError.httpStatus(let status, let details) {
            XCTAssertEqual(status, 502)
            XCTAssertTrue(details.contains("POST https://api.ksuser.cn/auth/native-authorization/create"))
            XCTAssertTrue(details.contains("upstream unavailable"))
            XCTAssertTrue(details.contains("[REDACTED]"))
            XCTAssertFalse(details.contains("secret-token"))
        }
    }
    func testOAuthTokenWithoutApprovedUnionIDStillDecodesAndPersists() throws {
        let data = Data(#"{"access_token":"application-token","token_type":"Bearer","expires_in":3600,"scope":"profile","openid":"pairwise-id"}"#.utf8)
        let tokens = try JSONDecoder().decode(KsuserTokenSet.self, from: data)
        XCTAssertEqual(tokens.openID, "pairwise-id")
        XCTAssertNil(tokens.unionID)
        let restored = try JSONDecoder().decode(KsuserTokenSet.self, from: JSONEncoder().encode(tokens))
        XCTAssertEqual(restored.accessToken, tokens.accessToken)
        XCTAssertNil(restored.unionID)
    }
    func testPublicTokenExchangeUsesPKCEWithoutClientSecretOrCookies() async throws {
        let fixture = CaptureTransport()
        let http = SDKHTTP(configuration: configuration, transport: fixture)
        let created = NativeCreated(ticket: String(repeating: "a", count: 32), cancelSecret: String(repeating: "b", count: 32), appLink: URL(string: "https://auth.ksuser.cn")!, webAuthorizationUrl: URL(string: "https://auth.ksuser.cn")!, expiresInSeconds: 300)
        let transaction = AuthorizationTransaction(clientID: "native-client", mode: .oauth, redirectURI: redirect, verifier: "verifier+with&symbols", state: "state", nonce: nil, created: created, expiresAt: Date().addingTimeInterval(300), code: "code+with&symbols")
        let tokens = try await http.exchange(transaction)
        XCTAssertEqual(tokens.accessToken, "application-token")
        XCTAssertEqual(tokens.openID, "pairwise-id"); XCTAssertEqual(tokens.unionID, "union-id")
        let captured = await fixture.captured()
        let request = try XCTUnwrap(captured)
        XCTAssertEqual(request.url?.path, "/oauth2/token")
        XCTAssertNil(request.value(forHTTPHeaderField: "Cookie"))
        XCTAssertNil(request.value(forHTTPHeaderField: "Authorization"))
        let body = String(data: request.httpBody!, encoding: .utf8)!
        XCTAssertTrue(body.contains("code_verifier=verifier%2Bwith%26symbols"))
        XCTAssertTrue(body.contains("code=code%2Bwith%26symbols"))
        XCTAssertFalse(body.contains("client_secret"))
    }
    func testExpiredTransactionIsClearedBeforeChangedConfigurationIsCompared() throws {
        let created = NativeCreated(ticket: String(repeating: "a", count: 32), cancelSecret: String(repeating: "b", count: 32), appLink: URL(string: "https://auth.ksuser.cn")!, webAuthorizationUrl: URL(string: "https://auth.ksuser.cn")!, expiresInSeconds: 300)
        var old = AuthorizationTransaction(clientID: "old-client", mode: .oauth, redirectURI: redirect, verifier: "verifier", state: "state", nonce: nil, created: created, expiresAt: Date(timeIntervalSince1970: 100))
        old.configurationID = "obsolete-client-and-domain"
        let expired = MemoryTransactionStore(old)
        XCTAssertNil(try PendingAuthorizationRecovery.load(from: expired, configuration: configuration, now: Date(timeIntervalSince1970: 200)))
        XCTAssertNil(expired.value)
        XCTAssertEqual(expired.clears, 1)
        let live = AuthorizationTransaction(clientID: "old-client", mode: .oauth, redirectURI: redirect, verifier: "verifier", state: "state", nonce: nil, created: created, expiresAt: Date(timeIntervalSince1970: 300), configurationID: "different-config")
        let occupied = MemoryTransactionStore(live)
        XCTAssertThrowsError(try PendingAuthorizationRecovery.load(from: occupied, configuration: configuration, now: Date(timeIntervalSince1970: 200)))
        XCTAssertEqual(occupied.clears, 0)
    }
    @MainActor func testOfficialJWKSTemporaryOutageResumesWithoutRedeemingCodeTwice() async throws {
        let fixture = try RSAFixture()
        let now = Date().timeIntervalSince1970
        let token = try fixture.token(["iss": "https://api.ksuser.cn/official", "sub": "subject", "aud": "native-client", "nonce": "nonce", "iat": now - 10, "exp": now + 300])
        let tokens = try JSONSerialization.data(withJSONObject: ["access_token": "official-token", "token_type": "Bearer", "expires_in": 300, "id_token": token])
        let transport = JWKSRetryTransport(tokens: tokens, jwks: try JSONEncoder().encode(JWKSetEnvelope(keys: fixture.jwks.keys)))
        let config = KsuserAuthConfiguration(clientID: "native-client", mode: .official, redirectURI: redirect)
        let http = SDKHTTP(configuration: config, transport: transport)
        let created = NativeCreated(ticket: String(repeating: "a", count: 32), cancelSecret: String(repeating: "b", count: 32), appLink: URL(string: "https://auth.ksuser.cn")!, webAuthorizationUrl: URL(string: "https://auth.ksuser.cn")!, expiresInSeconds: 300)
        let original = AuthorizationTransaction(clientID: "native-client", mode: .official, redirectURI: redirect, verifier: "verifier", state: "state", nonce: "nonce", created: created, expiresAt: Date().addingTimeInterval(300), code: "one-use-code")
        let storage = MemoryTransactionStore(original)
        do { _ = try await SDKAuthorizationResolver.resolve(original, http: http, persist: storage.save); XCTFail("Expected temporary JWKS failure") }
        catch { XCTAssertTrue(KsuserAuthError.preservesPendingTransaction(error)) }
        let saved = try XCTUnwrap(storage.value)
        XCTAssertNotNil(saved.tokens); XCTAssertNil(saved.code)
        let result = try await SDKAuthorizationResolver.resolve(saved, http: http, persist: storage.save)
        guard case .official(_, let subject) = result else { return XCTFail("Expected official result") }
        XCTAssertEqual(subject, "subject")
        let counts = await transport.counts()
        XCTAssertEqual(counts.0, 1); XCTAssertEqual(counts.1, 2)
        XCTAssertFalse(KsuserAuthError.preservesPendingTransaction(KsuserAuthError.invalidIDToken))
        XCTAssertTrue(KsuserAuthError.preservesPendingTransaction(KsuserAuthError.httpStatus(429, "rate_limited")))
    }
    func testOfficialIDTokenVerifiesRS256AndEveryRequiredClaim() throws {
        let fixture = try RSAFixture()
        let claims: [String: Any] = ["iss": "https://api.ksuser.cn/official", "sub": "user-uuid", "aud": ["native-client"], "nonce": "nonce", "iat": 100, "exp": 300]
        func verify(_ claims: [String: Any], alg: String = "RS256") throws -> String {
            try IDTokenVerifier.subject(token: fixture.token(claims, alg: alg), keys: fixture.jwks, issuer: "https://api.ksuser.cn/official", clientID: "native-client", nonce: "nonce", now: Date(timeIntervalSince1970: 200))
        }
        XCTAssertEqual(try verify(claims), "user-uuid")
        for (key, value): (String, Any) in [("iss", "https://evil.example"), ("aud", ["other-client"]), ("nonce", "wrong"), ("exp", 199), ("iat", 500), ("sub", "")] {
            var invalid = claims; invalid[key] = value
            XCTAssertThrowsError(try verify(invalid), key)
        }
        XCTAssertThrowsError(try verify(claims, alg: "HS256"))
        var wrongAuthorizedParty = claims; wrongAuthorizedParty["azp"] = "other-client"
        XCTAssertThrowsError(try verify(wrongAuthorizedParty))
        var notBefore = claims; notBefore["nbf"] = 500
        XCTAssertThrowsError(try verify(notBefore))
        var multi = claims; multi["aud"] = ["native-client", "other"]
        XCTAssertThrowsError(try verify(multi))
        multi["azp"] = "native-client"; XCTAssertEqual(try verify(multi), "user-uuid")
        let token = try fixture.token(claims)
        let corrupted = token.dropLast(4) + "AAAA"
        XCTAssertThrowsError(try IDTokenVerifier.subject(token: String(corrupted), keys: fixture.jwks, issuer: "https://api.ksuser.cn/official", clientID: "native-client", nonce: "nonce", now: Date(timeIntervalSince1970: 200)))
    }
}
private actor NativeCreateTransport: SDKTransport {
    private var request: URLRequest?
    func captured() -> URLRequest? { request }
    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        self.request = request
        let ticket = String(repeating: "a", count: 32)
        let response = NativeCreated(ticket: ticket, cancelSecret: String(repeating: "b", count: 32),
            appLink: URL(string: "https://auth.ksuser.cn/app/sdk-authorize?ticket=" + ticket)!,
            webAuthorizationUrl: URL(string: "https://auth.ksuser.cn/app/sdk-authorize/web?ticket=" + ticket)!, expiresInSeconds: 300)
        let data = try JSONSerialization.data(withJSONObject: ["code": 200, "data": try JSONSerialization.jsonObject(with: JSONEncoder().encode(response))])
        return (data, HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!)
    }
}
private actor CaptureTransport: SDKTransport {
    private var request: URLRequest?
    func captured() -> URLRequest? { request }
    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        self.request = request
        return (Data(#"{"access_token":"application-token","token_type":"Bearer","expires_in":3600,"scope":"profile","openid":"pairwise-id","unionid":"union-id"}"#.utf8), HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!)
    }
}
private struct FailureTransport: SDKTransport {
    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let data = Data(#"{"errorCode":"http_502","message":"upstream unavailable","access_token":"secret-token"}"#.utf8)
        return (data, HTTPURLResponse(url: request.url!, statusCode: 502, httpVersion: nil, headerFields: ["Content-Type": "application/json"])!)
    }
}
private struct RSAFixture {
    let key: SecKey
    let jwks: JWKSet
    init() throws {
        // Public test-only fixture: importing a nonpersistent key also works without a login Keychain.
        let privateDER = Data(base64Encoded: "MIIEogIBAAKCAQEAjiUzhRdSUFAkSgPN75i5gGARWIOFvwurh8b5Q4TGwXQUlTtZeBKOzHWDlE9cCv3eo+es+E+7isatYIFc9tbKQnFlXEc0/Y4LWXe3kQ9WwexIxvqkvRmcnCcjxU/7NTWG5G1qgF6lZRuq9VPxUyb6RQmRQJLxWgot6EwrFCI6nw0zlVHq7R5skJdEZUlfPFuxgzY1OsRnUYXkIXhbmogasZZSaGtSBUA+OyG3OeVJ4uzYgArsb6g71aYYVzts8iEx4XyxgqafVDnag5WNIqiGgr3z35CRhiYd7b5tmLCzlTLrbwx8SO1Kz6Ltz7g2D9UtL1w7wOimiU3gt1/1DNZVnwIDAQABAoIBABeIDmYIslZxWKXtc04oLlgTj0uOmmRCKPyqnTaRkzBmExzmslevsCF7jXN5SneCB4rYHkkNNLLd/nMXd1hgaSNkD6+dv3v/jolvyYfOI5f+5x1pqOlVMBlPBsZHXgLfqvEYhqUHBhfIOMkG7dB3h2meZ9lqJLe52le2eaXfn2zCTNO9KJd5aCR+ziC8c6rMNXD2NElS88V/OXAcWJOG5sWjQkl++IXcNdXpj90BSLt57YSJtuQyOQYUlG4OqmPbtqQeongS/5vVmSuxYmJeWor74SGJ5sZbq50SdI3HsFXPLIzvaAx7FMk2szMkp4qY9UFGgoK1ctVRh4a98/oAvIECgYEAx/GtFYNu8osc0vx+ftwMC/Ni3zpPdobsI6jvpEkTqZqluIJeLDAe35GhBLR8did2kKyyjEfKx7s07IJB8lWxlDRSNcKTFIwmQcFXj+LxBWpsgWuDagL7ezXS8C4pQDjk3zTYS5KHFikw+GJSBB0XQfKLRoKBAFyYN5Qcd8XWdu8CgYEAtf82TXQ1ksynFmcgiS7zjdgJk71mQPm4xCrgiiSSbtfX+ANH1pfz0jNZPAped4BgxOOAwPrRV/hE0Ig7AIt93y7Bj+Jtx8fsFtMrwXJqNsiR2HLY4807Q3fAykU12mx0wJeM40HzR4UbvsRklxUN5ZWzI0Y1TmU8pBDrFQQQjFECgYBgsBIPMTvZtkP+E3PoTOjWbqAFauE6ZAyVQgCA5YXIoqw5CXIY1DxZyMagy9bUHElNtPWyuJTq+TCinmrdkWsKTDLlJG6TpSJq8eAyCICdKdXG0AMApOiPsHuNAgWYtOqYtalTAkyo/spbktAuhRLWJUAMnNddIWnKlqkdkmIfqQKBgB1qt/tC+lnVDHjZiG/EEAfwUHP53CpKcuv6Rjrt+GFEMBtdZGIyQ23JcPX8cxJ7xKhuYWRP5kCC15ShQHAxG9JUO2MnZTkyfl2eGmd1N+fM+Y3z2kzP4nFgTOAk2bSJbUGn/VTKVylLQZXPJrco5wsh7EeaVIlsYaXfOjA2bPpRAoGAVBSItFkf9GQVLesLH0Gi/NsMLs76LJ8kUZvhbGT/OGxZgWOoC/DipagPXYntJSTSybkPAtV9eYT8Pmoz+psdjZ8BEPotCTqsU//zLMUrSr6WJYnicCAd4uuVWuz399+7mz27HzpFDDy+HF/8fRRRPRXs5iG+NItnOrHHiEH8YFs=")!
        key = try XCTUnwrap(SecKeyCreateWithData(privateDER as CFData, [kSecAttrKeyType: kSecAttrKeyTypeRSA, kSecAttrKeyClass: kSecAttrKeyClassPrivate] as CFDictionary, nil))
        let publicKey = try XCTUnwrap(SecKeyCopyPublicKey(key))
        let encoded = try XCTUnwrap(SecKeyCopyExternalRepresentation(publicKey, nil)) as Data
        var offset = 0
        func read(_ bytes: Data, _ offset: inout Int) -> Data {
            offset += 1
            var length = Int(bytes[offset]); offset += 1
            if length & 0x80 != 0 {
                let count = length & 0x7f; length = 0
                for _ in 0..<count { length = (length << 8) | Int(bytes[offset]); offset += 1 }
            }
            let value = bytes.subdata(in: offset..<(offset + length)); offset += length
            return value
        }
        let sequence = read(encoded, &offset); var cursor = 0
        let n = Data(read(sequence, &cursor).drop(while: { $0 == 0 }))
        let e = Data(read(sequence, &cursor).drop(while: { $0 == 0 }))
        jwks = JWKSet(keys: [JWK(kty: "RSA", kid: "test-rsa", alg: "RS256", use: "sig", n: n.base64URL, e: e.base64URL)])
    }
    func token(_ claims: [String: Any], alg: String = "RS256") throws -> String {
        let header = try JSONSerialization.data(withJSONObject: ["alg": alg, "kid": "test-rsa"]).base64URL
        let body = try JSONSerialization.data(withJSONObject: claims).base64URL
        let signing = header + "." + body
        let signature = try XCTUnwrap(SecKeyCreateSignature(key, .rsaSignatureMessagePKCS1v15SHA256, Data(signing.utf8) as CFData, nil)) as Data
        return signing + "." + signature.base64URL
    }
}

private final class MemoryTransactionStore: SDKTransactionStoring {
    var value: AuthorizationTransaction?
    private(set) var clears = 0
    init(_ value: AuthorizationTransaction?) { self.value = value }
    func load() -> AuthorizationTransaction? { value }
    func save(_ transaction: AuthorizationTransaction) { value = transaction }
    func clear() { value = nil; clears += 1 }
}
private struct JWKSetEnvelope: Encodable { let keys: [JWK] }
private actor JWKSRetryTransport: SDKTransport {
    let tokens: Data; let jwks: Data
    private var tokenCount = 0, jwksCount = 0
    init(tokens: Data, jwks: Data) { self.tokens = tokens; self.jwks = jwks }
    func counts() -> (Int, Int) { (tokenCount, jwksCount) }
    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let data: Data, status: Int
        if request.url!.path == "/official/token" { tokenCount += 1; data = tokens; status = 200 }
        else { jwksCount += 1; data = jwksCount == 1 ? Data(#"{"error":"temporarily_unavailable"}"#.utf8) : jwks; status = jwksCount == 1 ? 503 : 200 }
        return (data, HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil, headerFields: nil)!)
    }
}
