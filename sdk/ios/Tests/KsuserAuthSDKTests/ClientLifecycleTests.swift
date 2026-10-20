#if canImport(UIKit)
import XCTest
import UIKit
import AuthenticationServices
@testable import KsuserAuthSDK

@MainActor final class ClientLifecycleTests: XCTestCase {
    private let configuration = KsuserAuthConfiguration(clientID: "lifecycle-client", mode: .oauth,
        redirectURI: URL(string: "https://login.client.example/ksuser/callback")!)

    private func transaction() -> AuthorizationTransaction {
        let ticket = String(repeating: "t", count: 32)
        let created = NativeCreated(ticket: ticket, cancelSecret: String(repeating: "s", count: 32),
            appLink: URL(string: "https://auth.ksuser.cn/app/sdk-authorize?ticket=" + ticket)!,
            webAuthorizationUrl: URL(string: "https://auth.ksuser.cn/app/sdk-authorize/web?ticket=" + ticket)!, expiresInSeconds: 300)
        return AuthorizationTransaction(clientID: configuration.clientID, mode: .oauth, redirectURI: configuration.redirectURI,
            verifier: String(repeating: "v", count: 43), state: "expected-state", nonce: nil, created: created,
            expiresAt: Date().addingTimeInterval(300), configurationID: configuration.transactionIdentifier)
    }

    func testColdStartReceivesCallbackAndRedeemsItExactlyOnce() async throws {
        let storage = LifecycleStore(transaction())
        let transport = LifecycleTransport()
        let client = try KsuserAuthClient(configuration: configuration, storage: storage, transport: transport)
        let callback = URL(string: configuration.redirectURI.absoluteString + "?code=one-code&state=expected-state")!
        XCTAssertFalse(client.handleCallback(URL(string: configuration.redirectURI.absoluteString + "?code=one-code&state=wrong")!))
        XCTAssertTrue(client.handleCallback(callback))
        XCTAssertTrue(client.handleCallback(callback))
        let result = try await client.resumePendingAuthorization(presentationContextProvider: LifecyclePresentation())
        guard case .oauth(let tokens)? = result else { return XCTFail("Expected OAuth result") }
        XCTAssertEqual(tokens.openID, "application-openid")
        XCTAssertEqual(tokens.unionID, "developer-unionid")
        XCTAssertNil(storage.value)
        XCTAssertFalse(client.handleCallback(callback))
        let repeated = try await client.resumePendingAuthorization(presentationContextProvider: LifecyclePresentation())
        XCTAssertNil(repeated)
        let counts = await transport.counts()
        XCTAssertEqual(counts.tokens, 1)
    }

    func testUserDenialNeverRedeemsTokenAndClearsPendingRequest() async throws {
        let storage = LifecycleStore(transaction())
        let transport = LifecycleTransport()
        let client = try KsuserAuthClient(configuration: configuration, storage: storage, transport: transport)
        XCTAssertTrue(client.handleCallback(URL(string: configuration.redirectURI.absoluteString + "?error=access_denied&state=expected-state")!))
        do {
            _ = try await client.resumePendingAuthorization(presentationContextProvider: LifecyclePresentation())
            XCTFail("Expected denial")
        } catch { XCTAssertEqual(error as? KsuserAuthError, .server("access_denied")) }
        XCTAssertNil(storage.value)
        let counts = await transport.counts()
        XCTAssertEqual(counts.tokens, 0)
    }

    func testCancellationUsesOnlyPrivateCancelCapabilityAndRejectsLateCallback() async throws {
        let storage = LifecycleStore(transaction())
        let transport = LifecycleTransport()
        let client = try KsuserAuthClient(configuration: configuration, storage: storage, transport: transport)
        await client.cancel()
        XCTAssertNil(storage.value)
        XCTAssertFalse(client.handleCallback(URL(string: configuration.redirectURI.absoluteString + "?code=late&state=expected-state")!))
        let counts = await transport.counts()
        XCTAssertEqual(counts.tokens, 0)
        XCTAssertEqual(counts.cancels, 1)
        let request = await transport.lastCancel()
        let body = try XCTUnwrap(request?.httpBody)
        let fields = try XCTUnwrap(JSONSerialization.jsonObject(with: body) as? [String: String])
        XCTAssertEqual(Set(fields.keys), ["ticket", "cancelSecret"])
        XCTAssertNil(request?.value(forHTTPHeaderField: "Authorization"))
        XCTAssertNil(request?.value(forHTTPHeaderField: "Cookie"))
    }

    func testExpiredConfigurationDoesNotPreventConstructingAClient() throws {
        let old = transaction()
        let expired = AuthorizationTransaction(clientID: "old-client", mode: old.mode, redirectURI: old.redirectURI,
            verifier: old.verifier, state: old.state, nonce: nil, created: old.created,
            expiresAt: Date().addingTimeInterval(-10), configurationID: "old-configuration")
        let storage = LifecycleStore(expired)
        _ = try KsuserAuthClient(configuration: configuration, storage: storage, transport: LifecycleTransport())
        XCTAssertNil(storage.value)
    }
}

private final class LifecycleStore: SDKTransactionStoring {
    var value: AuthorizationTransaction?
    init(_ value: AuthorizationTransaction?) { self.value = value }
    func load() throws -> AuthorizationTransaction? { value }
    func save(_ transaction: AuthorizationTransaction) throws { value = transaction }
    func clear() throws { value = nil }
}

@MainActor private final class LifecyclePresentation: NSObject, ASWebAuthenticationPresentationContextProviding {
    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor { ASPresentationAnchor() }
}

private actor LifecycleTransport: SDKTransport {
    private var tokens = 0
    private var cancels = 0
    private var cancellation: URLRequest?
    func counts() -> (tokens: Int, cancels: Int) { (tokens, cancels) }
    func lastCancel() -> URLRequest? { cancellation }
    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let body: String
        if request.url?.path == "/oauth2/token" {
            tokens += 1
            body = #"{"access_token":"application-token","token_type":"Bearer","expires_in":3600,"scope":"profile","openid":"application-openid","unionid":"developer-unionid"}"#
        } else if request.url?.path == "/auth/native-authorization/cancel" {
            cancels += 1; cancellation = request
            body = #"{"code":200,"data":{"status":"cancelled"}}"#
        } else { throw KsuserAuthError.invalidResponse }
        return (Data(body.utf8), HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!)
    }
}
#endif
