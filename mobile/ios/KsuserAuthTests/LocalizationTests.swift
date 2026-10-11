import Foundation
import UIKit
import XCTest
@testable import KsuserAuth

final class LocalizationTests: XCTestCase {
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

    func testLanguageResolutionUsesTopDeviceLanguageAndManualPreference() {
        for language in ["zh", "zh-CN", "zh-TW", "zh-Hant-HK", "ZH_hans"] {
            XCTAssertEqual(AppLanguage.resolve(preference: "system", preferredLanguages: [language]), "zh-Hans")
        }
        for languages in [["en-GB"], ["fr-FR", "zh-CN"], ["ja-JP"], []] {
            XCTAssertEqual(AppLanguage.resolve(preference: nil, preferredLanguages: languages), "en")
        }
        XCTAssertEqual(AppLanguage.resolve(preference: "en", preferredLanguages: ["zh-CN"]), "en")
        XCTAssertEqual(AppLanguage.resolve(preference: "zh-Hans", preferredLanguages: ["en-US"]), "zh-Hans")
        XCTAssertEqual(AppLanguage.resolve(preference: "invalid", preferredLanguages: ["en-US"]), "en")
    }

    @MainActor func testPreferencePersistsAndSystemCanBeRestored() throws {
        let name = "LocalizationTests-\(UUID())"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: name))
        defer { defaults.removePersistentDomain(forName: name) }
        let settings = LanguageSettings(defaults: defaults, preferredLanguages: ["en-GB"])
        XCTAssertEqual(settings.preference, .system)
        XCTAssertEqual(settings.effectiveLanguage, "en")
        settings.preference = .simplifiedChinese
        XCTAssertEqual(LanguageSettings(defaults: defaults, preferredLanguages: ["en-US"]).effectiveLanguage, "zh-Hans")
        settings.preference = .system
        XCTAssertEqual(settings.effectiveLanguage, "en")
    }

    func testLoadedAPIAndNestedMessagesFollowLanguageChangesWithoutLosingParameters() {
        let message = LocalizedMessage(errorCode: "AUTH_020", params: ["min": .number(8), "max": .number(64)], statusCode: 400)
        let apiError = APIError.server(400, message)
        let pending = PendingOAuth(provider: "apple", bindToken: "fixture", localizedMessage: message)
        let nested: LocalizedMessage = "正在\(LocalizedMessage(key: "验证身份"))…"
        XCTAssertEqual(apiError.localizedDescription, "密码长度必须在8-64个字符之间")
        XCTAssertEqual(pending.message, apiError.localizedDescription)
        XCTAssertEqual(nested.rendered, "正在验证身份…")
        UserDefaults.standard.set("en", forKey: "language")
        XCTAssertEqual(apiError.localizedDescription, GeneratedAPIResultCodes.messagesByLocale["en"]?["AUTH_020"]?.replacingOccurrences(of: "{min}", with: "8").replacingOccurrences(of: "{max}", with: "64"))
        XCTAssertEqual(pending.message, apiError.localizedDescription)
        XCTAssertEqual(nested.rendered, "Verify Identity…")
        XCTAssertEqual(L10n.format("验证码已发送至 {0}", ["user{0}@example.invalid"]), "Code sent to user{0}@example.invalid")
    }

    @MainActor func testRetainedLocalMessagesFollowLanguageWithoutChangingBusinessData() {
        let model = AppModel(native: NativeAuthenticationProvider())
        model.noticeMessage = "资料已保存"
        model.user = UserProfile(uuid: "fixture", username: "中文用户", email: "fixture@example.invalid", realName: "张三", region: "上海")
        model.reportError(APIError.server(400, "操作已取消"))
        UserDefaults.standard.set("en", forKey: "language")
        XCTAssertEqual(model.noticeMessage, "Profile saved")
        XCTAssertEqual(model.errorMessage, "Operation cancelled")
        XCTAssertEqual(model.user?.displayName, "张三")
        XCTAssertEqual(model.user?.region, "上海")
    }

    @MainActor func testNativeShortcutsUseTheCurrentPreferenceWithoutStaticOverrides() throws {
        XCTAssertNil(Bundle.main.object(forInfoDictionaryKey: "UIApplicationShortcutItems"))
        let previous = UIApplication.shared.shortcutItems
        defer { UIApplication.shared.shortcutItems = previous }
        AppDelegate.refreshShortcuts()
        let chinese = try XCTUnwrap(UIApplication.shared.shortcutItems)
        UserDefaults.standard.set("en", forKey: "language")
        AppDelegate.refreshShortcuts()
        let english = try XCTUnwrap(UIApplication.shared.shortcutItems)
        XCTAssertEqual(chinese.map(\.type), english.map(\.type))
        XCTAssertEqual(chinese.first?.localizedTitle, "扫码授权")
        XCTAssertEqual(english.first?.localizedTitle, "Scan to Authorize")
    }

    func testZonedDatesUseSelectedLocaleAndZoneLessDatesKeepWallClock() throws {
        let input = "2026-10-11T10:02:03Z"
        let instant = try XCTUnwrap(ISO8601DateFormatter().date(from: input))
        let output = DateFormatter()
        output.dateStyle = .medium; output.timeStyle = .medium
        output.locale = Locale(identifier: "zh-Hans")
        let chinese = displayDate(input)
        XCTAssertEqual(chinese, output.string(from: instant))
        UserDefaults.standard.set("en", forKey: "language")
        output.locale = Locale(identifier: "en")
        XCTAssertEqual(displayDate(input), output.string(from: instant))
        XCTAssertNotEqual(displayDate(input), chinese)
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        output.calendar = calendar
        output.timeZone = calendar.timeZone
        let wallClock = try XCTUnwrap(calendar.date(from: DateComponents(year: 2026, month: 10, day: 11, hour: 10, minute: 2, second: 3)))
        let englishLocal = displayDate("2026-10-11T10:02:03.123")
        XCTAssertEqual(englishLocal, output.string(from: wallClock))
        let displayedComponents = calendar.dateComponents([.year, .month, .day, .hour, .minute, .second], from: try XCTUnwrap(output.date(from: englishLocal)))
        XCTAssertEqual(displayedComponents.hour, 10)
        XCTAssertEqual(displayedComponents.minute, 2)
        XCTAssertEqual(displayedComponents.second, 3)
        UserDefaults.standard.set("zh-Hans", forKey: "language")
        output.locale = Locale(identifier: "zh-Hans")
        let chineseLocal = displayDate("2026-10-11T10:02:03.123")
        XCTAssertEqual(chineseLocal, output.string(from: wallClock))
        XCTAssertNotEqual(chineseLocal, englishLocal)
        output.timeStyle = .none
        let chineseDay = displayDate("2026-10-11")
        XCTAssertEqual(chineseDay, output.string(from: wallClock))
        UserDefaults.standard.set("en", forKey: "language")
        output.locale = Locale(identifier: "en")
        XCTAssertEqual(displayDate("2026-10-11"), output.string(from: wallClock))
        XCTAssertNotEqual(displayDate("2026-10-11"), chineseDay)
    }

    func testEveryRequestUsesTheCurrentLanguage() async throws {
        let transport = LocalizationCaptureTransport()
        let client = APIClient(storage: MemorySessionStore(), transport: transport)
        let _: EmptyPayload = try await client.request("/fixture", authenticated: false)
        UserDefaults.standard.set("en", forKey: "language")
        let _: EmptyPayload = try await client.request("/fixture", authenticated: false)
        let languages = await transport.languages
        XCTAssertEqual(languages, ["zh-Hans", "en"])
    }

    func testBothNativeResourcesExistAndUnknownAPICodesIgnoreServerProse() throws {
        for language in ["zh-Hans", "en"] {
            let path = try XCTUnwrap(Bundle.main.path(forResource: language, ofType: "lproj"))
            let bundle = try XCTUnwrap(Bundle(path: path))
            XCTAssertNotEqual(bundle.localizedString(forKey: "语言", value: "missing", table: "Localizable"), "missing")
            XCTAssertNotEqual(bundle.localizedString(forKey: "NSCameraUsageDescription", value: "missing", table: "InfoPlist"), "missing")
        }
        UserDefaults.standard.set("en", forKey: "language")
        XCTAssertEqual(APIResultCodeResolver.message(errorCode: "UNKNOWN", statusCode: 401), GeneratedAPIResultCodes.messagesByLocale["en"]?["UNAUTHENTICATED"])
        XCTAssertEqual(APIResultCodeResolver.message(errorCode: "AUTH_020", params: [:], statusCode: 400), GeneratedAPIResultCodes.messagesByLocale["en"]?["BAD_REQUEST"])
    }
}

private actor LocalizationCaptureTransport: HTTPTransport {
    var languages: [String] = []
    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        languages.append(request.value(forHTTPHeaderField: "Accept-Language") ?? "")
        return (Data(#"{"code":200,"data":{}}"#.utf8), HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!)
    }
}
