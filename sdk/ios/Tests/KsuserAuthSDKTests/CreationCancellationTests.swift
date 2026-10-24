#if canImport(UIKit)
import XCTest
import UIKit
import AuthenticationServices
import Security
@testable import KsuserAuthSDK

@MainActor final class CreationCancellationTests: XCTestCase {
    private let configuration = KsuserAuthConfiguration(clientID: "creation-client", mode: .oauth,
        redirectURI: URL(string: "https://login.client.example/ksuser/callback")!)

    func testCancelDuringCreateAllowsResumeBeforeOldResponseArrives() async throws {
        let started = expectation(description: "First create started")
        let transport = CreationTransport(started: [started])
        let storage = CreationStore()
        let client = try KsuserAuthClient(configuration: configuration, storage: storage, transport: transport)
        let first = authorize(client)
        await fulfillment(of: [started], timeout: 2)

        await client.cancel()
        do {
            let resumed = try await client.resumePendingAuthorization(presentationContextProvider: CreationPresentation())
            XCTAssertNil(resumed)
        } catch { XCTFail("The cancelled create must not remain busy: \(error)") }
        XCTAssertNil(storage.value)

        await transport.releaseCreate(1)
        await assertCancelled(first)
        let cancelled = await transport.cancelledTickets
        XCTAssertEqual(cancelled, [CreationTransport.ticket(1)])
    }

    func testLateCancelledCreateDoesNotClearOrUnlockNewCreate() async throws {
        let firstStarted = expectation(description: "First create started")
        let secondStarted = expectation(description: "Second create started")
        let transport = CreationTransport(started: [firstStarted, secondStarted])
        let storage = CreationStore()
        let client = try KsuserAuthClient(configuration: configuration, storage: storage, transport: transport)
        let first = authorize(client)
        await fulfillment(of: [firstStarted], timeout: 2)
        await client.cancel()

        let second = authorize(client)
        await fulfillment(of: [secondStarted], timeout: 2)
        await transport.releaseCreate(1)
        await assertCancelled(first)

        // The old request's deferred cleanup must neither clear the new busy flag nor release its lease.
        do {
            _ = try await client.resumePendingAuthorization(presentationContextProvider: CreationPresentation())
            XCTFail("The second create is still in progress")
        } catch { XCTAssertEqual(error as? KsuserAuthError, .authorizationInProgress) }
        let otherLease = UUID()
        XCTAssertFalse(AuthorizationLease.claim(otherLease))
        AuthorizationLease.release(otherLease)
        XCTAssertNil(storage.value)

        await client.cancel()
        await transport.releaseCreate(2)
        await assertCancelled(second)
        let cancelled = await transport.cancelledTickets
        XCTAssertEqual(cancelled, [CreationTransport.ticket(1), CreationTransport.ticket(2)])
        let resumed = try await client.resumePendingAuthorization(presentationContextProvider: CreationPresentation())
        XCTAssertNil(resumed)
    }

    func testCancelDuringCreateStillCancelsLocallyWhenStorageReloadFails() async throws {
        let started = expectation(description: "Create started")
        let transport = CreationTransport(started: [started])
        let storage = CreationStore()
        let client = try KsuserAuthClient(configuration: configuration, storage: storage, transport: transport)
        let first = authorize(client)
        await fulfillment(of: [started], timeout: 2)

        storage.loadError = KsuserAuthError.storage(errSecInteractionNotAllowed)
        await client.cancel()
        storage.loadError = nil
        do {
            let resumed = try await client.resumePendingAuthorization(presentationContextProvider: CreationPresentation())
            XCTAssertNil(resumed)
        } catch { XCTFail("An unreadable Keychain must not leave the cancelled create busy: \(error)") }

        // Also invalidate the request in the regression case, so a failure never opens a browser or hangs the test.
        await client.cancel()
        await transport.releaseCreate(1)
        await assertCancelled(first)
        XCTAssertNil(storage.value)
    }

    private func authorize(_ client: KsuserAuthClient) -> Task<Result<KsuserAuthorizationResult, Error>, Never> {
        Task {
            do { return .success(try await client.authorize(presentationContextProvider: CreationPresentation())) }
            catch { return .failure(error) }
        }
    }

    private func assertCancelled(_ task: Task<Result<KsuserAuthorizationResult, Error>, Never>,
                                 file: StaticString = #filePath, line: UInt = #line) async {
        switch await task.value {
        case .success: XCTFail("Expected cancellation", file: file, line: line)
        case .failure(let error): XCTAssertEqual(error as? KsuserAuthError, .cancelled, file: file, line: line)
        }
    }
}

private final class CreationStore: SDKTransactionStoring {
    var value: AuthorizationTransaction?
    var loadError: Error?
    func load() throws -> AuthorizationTransaction? {
        if let loadError { throw loadError }
        return value
    }
    func save(_ transaction: AuthorizationTransaction) throws { value = transaction }
    func clear() throws { value = nil }
}

@MainActor private final class CreationPresentation: NSObject, ASWebAuthenticationPresentationContextProviding {
    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor { ASPresentationAnchor() }
}

private actor CreationTransport: SDKTransport {
    private let started: [XCTestExpectation]
    private var creates = 0
    private var responses: [Int: CheckedContinuation<Void, Never>] = [:]
    private(set) var cancelledTickets: [String] = []

    init(started: [XCTestExpectation]) { self.started = started }
    nonisolated static func ticket(_ index: Int) -> String { String(repeating: index == 1 ? "a" : "b", count: 32) }
    func releaseCreate(_ index: Int) { responses.removeValue(forKey: index)?.resume() }

    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let body: String
        switch request.url!.path {
        case "/auth/native-authorization/create":
            creates += 1
            let index = creates
            await withCheckedContinuation { continuation in
                responses[index] = continuation
                if started.indices.contains(index - 1) { started[index - 1].fulfill() }
            }
            let ticket = Self.ticket(index)
            body = "{\"code\":200,\"data\":{\"ticket\":\"\(ticket)\",\"cancelSecret\":\"\(String(repeating: "s", count: 32))\",\"appLink\":\"https://auth.ksuser.cn/app/sdk-authorize?ticket=\(ticket)\",\"webAuthorizationUrl\":\"https://auth.ksuser.cn/app/sdk-authorize/web?ticket=\(ticket)\",\"expiresInSeconds\":300}}"
        case "/auth/native-authorization/cancel":
            let input = try JSONDecoder().decode([String: String].self, from: request.httpBody!)
            cancelledTickets.append(try XCTUnwrap(input["ticket"]))
            body = "{}"
        default: throw KsuserAuthError.invalidResponse
        }
        return (Data(body.utf8), HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!)
    }
}
#endif
