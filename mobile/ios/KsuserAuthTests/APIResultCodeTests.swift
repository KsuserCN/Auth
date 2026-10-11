import Foundation
import XCTest
@testable import KsuserAuth

final class APIResultCodeTests: XCTestCase {
    private var previousLanguage: String?
    override func setUp() {
        super.setUp()
        previousLanguage = UserDefaults.standard.string(forKey: "language")
        UserDefaults.standard.set("zh-Hans", forKey: "language")
    }
    override func tearDown() {
        if let previousLanguage { UserDefaults.standard.set(previousLanguage, forKey: "language") }
        else { UserDefaults.standard.removeObject(forKey: "language") }
        super.tearDown()
    }

    func testDetailedCodeAndNumericParametersResolveLocally() throws {
        let envelope = try JSONDecoder().decode(APIEnvelope<EmptyPayload>.self, from: Data(#"{"code":400,"errorCode":"AUTH_020","params":{"min":8,"max":64},"msg":"服务端原始错误"}"#.utf8))
        XCTAssertEqual(envelope.resultMessage, "密码长度必须在8-64个字符之间")
    }

    func testUnknownCodeNeverUsesLegacyServerText() throws {
        let envelope = try JSONDecoder().decode(APIEnvelope<EmptyPayload>.self, from: Data(#"{"code":401,"errorCode":"NEW_UNKNOWN_CODE","msg":"服务端原始错误","error_description":"协议原始错误"}"#.utf8))
        XCTAssertEqual(envelope.resultMessage, APIResultCodeResolver.message(errorCode: nil, statusCode: 401))
        XCTAssertFalse(envelope.resultMessage.contains("原始错误"))
    }

    func testMissingTemplateParameterUsesSafeFallback() {
        XCTAssertEqual(APIResultCodeResolver.message(errorCode: "AUTH_020", params: ["min": .number(8)], statusCode: 400), APIResultCodeResolver.message(errorCode: nil, statusCode: 400))
        XCTAssertEqual(APIResultCodeResolver.message(errorCode: "AUTH_020", params: ["min": .object([:]), "max": .number(64)], statusCode: 400), APIResultCodeResolver.message(errorCode: nil, statusCode: 400))
    }

    func testHTTPErrorKeepsDetailedCodeWhenDataHasDifferentType() async throws {
        let transport = ResultCodeFixtureTransport(status: 400, body: #"{"code":400,"errorCode":"AUTH_020","params":{"min":8,"max":64},"data":{"unexpected":true}}"#)
        let client = APIClient(storage: MemorySessionStore(), transport: transport)
        do {
            let _: TokenPayload = try await client.request("/fixture", authenticated: false)
            XCTFail("Expected API error")
        } catch let error as APIError {
            XCTAssertEqual(error, .server(400, LocalizedMessage(errorCode: "AUTH_020", params: ["min": .number(8), "max": .number(64)], statusCode: 400)))
        }
    }

    func testFlowCodesKeepTheirStateAndUseCatalogueMessage() throws {
        for (code, resultCode) in [(201, "AUTH_MFA_REQUIRED"), (202, "OAUTH_BIND_REQUIRED")] {
            let body = "{\"code\":\(code),\"errorCode\":\"\(resultCode)\",\"data\":{}}"
            let envelope = try JSONDecoder().decode(APIEnvelope<JSONValue>.self, from: Data(body.utf8))
            XCTAssertEqual(envelope.code, code)
            XCTAssertEqual(envelope.resultMessage, GeneratedAPIResultCodes.messages[resultCode])
        }
    }

    func testPolicyAndAdaptiveReasonsIgnoreLegacyProse() throws {
        let policy = try JSONDecoder().decode(PasswordRequirement.self, from: Data(#"{"minLength":8,"maxLength":64,"requireUppercase":true,"requireLowercase":true,"requireDigits":true,"requireSpecialChars":false,"requirementCode":"AUTH_020","requirementParams":{"min":8,"max":64},"requirementMessage":"服务端原始策略"}"#.utf8))
        XCTAssertEqual(policy.requirementMessage, "密码长度必须在8-64个字符之间")
        let reason = try JSONDecoder().decode(APIResultDescriptor.self, from: Data(#"{"errorCode":"AUTH_VERIFICATION_CODE_INVALID","message":"服务端原始原因"}"#.utf8))
        XCTAssertEqual(reason.message(statusCode: 400), GeneratedAPIResultCodes.messages["AUTH_VERIFICATION_CODE_INVALID"])
    }

    func testAuditLogsTranslateOnlyCompleteKnownCodes() throws {
        for (stored, expected) in [("AUTH_VERIFICATION_CODE_INVALID", GeneratedAPIResultCodes.messages["AUTH_VERIFICATION_CODE_INVALID"]!), ("AUTH_020", GeneratedAPIResultCodes.messages["BAD_REQUEST"]!), ("历史审计：验证码错误", "历史审计：验证码错误"), ("error AUTH_VERIFICATION_CODE_INVALID", "error AUTH_VERIFICATION_CODE_INVALID")] {
            let body = #"{"id":1,"operationType":"LOGIN","ipAddress":"192.0.2.1","result":"failure","failureReason":""# + stored + #"","riskScore":0,"triggeredMultiErrorLock":false,"triggeredRateLimitLock":false,"createdAt":"2026-10-11T10:00:00"}"#
            let log = try JSONDecoder().decode(SensitiveLogItem.self, from: Data(body.utf8))
            XCTAssertEqual(log.displayFailureReason, expected)
        }
    }
}

private struct ResultCodeFixtureTransport: HTTPTransport {
    let status: Int
    let body: String
    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        (Data(body.utf8), HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil, headerFields: ["Content-Type": "application/json"])!)
    }
}
