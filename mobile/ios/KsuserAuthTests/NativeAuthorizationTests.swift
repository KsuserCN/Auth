import XCTest
@testable import KsuserAuth

final class NativeAuthorizationTests: XCTestCase {
    private let environment = AppEnvironment(apiBaseURL: URL(string: "https://api.ksuser.cn")!, passkeyRPID: "auth.ksuser.cn", qqAppID: "test", webURL: URL(string: "https://auth.ksuser.cn")!)
    private let ticket = String(repeating: "a", count: 32)
    func testNativeLaunchRequiresTrustedHTTPSAndSingleTicket() {
        let valid = "https://auth.ksuser.cn/app/sdk-authorize?ticket=" + ticket
        XCTAssertEqual(NativeAuthorizationLink.ticket(from: URL(string: valid)!, environment: environment), ticket)
        for value in [valid + "&ticket=" + ticket, valid + "&returnUrl=https://evil.example", valid + "#fragment", valid.replacingOccurrences(of: "auth.ksuser.cn", with: "evil.example"), "ksuserauth://sdk-authorize?ticket=" + ticket] {
            XCTAssertNil(NativeAuthorizationLink.ticket(from: URL(string: value)!, environment: environment))
        }
    }
    func testNativeReturnRequiresRegisteredHTTPSCallbackAndCodeOrErrorState() {
        let registered = "https://login.thirdparty.example/callback"
        for outcome in ["code=code", "error=access_denied"] {
            XCTAssertNotNil(NativeAuthorizationLink.callback(registered + "?" + outcome + "&state=state", registered: registered))
        }
        for value in [registered + "?code=code", registered + "?code=c&state=s&error=e", registered + "?code=c&state=s&state=s", "https://evil.example/callback?code=c&state=s", "sample://callback?code=c&state=s"] {
            XCTAssertNil(NativeAuthorizationLink.callback(value, registered: registered))
        }
        XCTAssertNil(MobileAuthorizationLink.browserReturn(registered + "?code=c&state=s", environment: environment))
    }
    @MainActor func testNativeAuthorizationUsesIsolatedEndpointsAndDoesNotResignCodeOnRetry() async throws {
        let transport = NativeFixtureTransport(ticket: ticket)
        let cookie = HTTPCookie(properties: [.name: "XSRF-TOKEN", .value: "csrf", .domain: "api.ksuser.cn", .path: "/", .secure: "TRUE"])!
        let client = APIClient(environment: environment, storage: MemorySessionStore(SessionSnapshot(accessToken: "app-session", cookies: [StoredCookie(cookie)])), transport: transport)
        let model = AppModel(native: NativeAuthenticationProvider(environment: environment), repository: KsuserRepository(client: client), environment: environment)
        await model.handleURL(URL(string: "https://auth.ksuser.cn/app/sdk-authorize?ticket=" + ticket)!)
        XCTAssertNil(model.mobileAuthorization)
        model.user = UserProfile(uuid: "current", username: "current", email: "current@example.invalid")
        await model.loadMobileAuthorization()
        XCTAssertTrue(model.mobileAuthorization?.nativeAuthorization == true)
        let first = await model.decideMobileAuthorization(approve: true, mode: "PERSISTENT", ttl: 3600)
        let second = await model.decideMobileAuthorization(approve: true, mode: "PERSISTENT", ttl: 3600)
        XCTAssertEqual(first?.absoluteString, "https://login.thirdparty.example/callback?code=single-code&state=state")
        XCTAssertEqual(first, second)
        let decisions = await transport.decisions
        XCTAssertEqual(decisions, 1)
        let session = await client.snapshot()
        XCTAssertEqual(session.accessToken, "app-session")
        XCTAssertEqual(model.user?.uuid, "current")
    }
    @MainActor func testNativeDenyUsesErrorCallbackAndRetryKeepsOriginalDecision() async throws {
        let transport = NativeFixtureTransport(ticket: ticket)
        let cookie = HTTPCookie(properties: [.name: "XSRF-TOKEN", .value: "csrf", .domain: "api.ksuser.cn", .path: "/", .secure: "TRUE"])!
        let client = APIClient(environment: environment, storage: MemorySessionStore(SessionSnapshot(accessToken: "app-session", cookies: [StoredCookie(cookie)])), transport: transport)
        let model = AppModel(native: NativeAuthenticationProvider(environment: environment), repository: KsuserRepository(client: client), environment: environment)
        model.user = UserProfile(uuid: "current", username: "current", email: "current@example.invalid")
        await model.handleURL(URL(string: "https://auth.ksuser.cn/app/sdk-authorize?ticket=" + ticket)!)
        let first = await model.decideMobileAuthorization(approve: false, mode: "PERSISTENT", ttl: 3600)
        let retry = await model.decideMobileAuthorization(approve: true, mode: "PERSISTENT", ttl: 3600)
        XCTAssertEqual(first?.absoluteString, "https://login.thirdparty.example/callback?error=access_denied&state=state")
        XCTAssertEqual(first, retry)
        let decisions = await transport.decisions
        XCTAssertEqual(decisions, 1)
    }

}
private actor NativeFixtureTransport: HTTPTransport {
    let ticket: String
    private(set) var decisions = 0
    init(ticket: String) { self.ticket = ticket }
    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let body: String
        switch request.url!.path {
        case "/auth/native-authorization/context":
            body = "{\"code\":200,\"data\":{\"ticket\":\"\(ticket)\",\"appName\":\"Native app\",\"redirectUri\":\"https://login.thirdparty.example/callback\",\"requestedScopes\":[\"openid\",\"profile\"],\"alreadyAuthorized\":false,\"existingGrantMode\":\"PERSISTENT\",\"expiresInSeconds\":300,\"nativeAuthorization\":true}}"
        case "/auth/native-authorization/decide":
            decisions += 1
            XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer app-session")
            let input = try XCTUnwrap(try JSONSerialization.jsonObject(with: request.httpBody!) as? [String: Any])
            XCTAssertNil(input["redirectUri"]); XCTAssertNil(input["loginWeb"])
            let outcome = input["approve"] as? Bool == true ? "code=single-code" : "error=access_denied"
            body = "{\"code\":200,\"data\":{\"returnUrl\":\"https://login.thirdparty.example/callback?\(outcome)&state=state\",\"completed\":true}}"
        default: throw APIError.invalidResponse
        }
        return (Data(body.utf8), HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!)
    }
}
