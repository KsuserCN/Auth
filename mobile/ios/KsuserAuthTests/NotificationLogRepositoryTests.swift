import XCTest
@testable import KsuserAuth

final class NotificationLogRepositoryTests: XCTestCase {
    func testLogLookupUsesIDWithoutListFiltersOrPagination() async throws {
        let transport = NotificationLogTransport(status: 200)
        let repository = KsuserRepository(client: APIClient(storage: MemorySessionStore(), transport: transport))
        let item = try await repository.log(id: 41)
        XCTAssertEqual(item?.id, 41)
        let request = await transport.lastRequest
        XCTAssertEqual(request?.url?.path, "/auth/sensitive-logs/41")
        XCTAssertNil(request?.url?.query)
    }

    func testOnlyNotFoundIsTreatedAsAnAbsentLog() async throws {
        let missing = KsuserRepository(client: APIClient(storage: MemorySessionStore(), transport: NotificationLogTransport(status: 404)))
        let item = try await missing.log(id: 41)
        XCTAssertNil(item)
        let denied = KsuserRepository(client: APIClient(storage: MemorySessionStore(), transport: NotificationLogTransport(status: 403)))
        do {
            _ = try await denied.log(id: 41)
            XCTFail("Expected permission failure")
        } catch APIError.server(403, _) { }
    }
}

private actor NotificationLogTransport: HTTPTransport {
    let status: Int
    private(set) var lastRequest: URLRequest?
    init(status: Int) { self.status = status }
    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        lastRequest = request
        let body = status == 200
            ? #"{"code":200,"data":{"id":41,"operationType":"LOGIN","ipAddress":"192.0.2.1","result":"SUCCESS","riskScore":0,"triggeredMultiErrorLock":false,"triggeredRateLimitLock":false,"createdAt":"2026-10-24T09:05:00"}}"#
            : "{\"code\":\(status)}"
        return (Data(body.utf8), HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil, headerFields: nil)!)
    }
}
