import XCTest
@testable import KsuserAuth

final class LogPresentationTests: XCTestCase {
    private let utc = TimeZone(secondsFromGMT: 0)!

    func testLogDateAndTimeAreFormattedIndependentlyInBothLanguages() throws {
        let instant = try XCTUnwrap(ISO8601DateFormatter().date(from: "2026-10-24T09:05:00Z"))
        for language in ["zh-Hans", "en"] {
            let locale = Locale(identifier: language)
            let presentation = DatePresentation("2026-10-24T09:05:00Z", locale: locale, timeZone: utc)
            let expected = DateFormatter()
            expected.locale = locale
            expected.timeZone = utc
            expected.dateStyle = .medium
            expected.timeStyle = .none
            XCTAssertEqual(presentation.dateText, expected.string(from: instant))
            XCTAssertTrue(presentation.dateText.contains("2026"))
            expected.dateStyle = .none
            expected.timeStyle = .short
            XCTAssertEqual(presentation.timeText, expected.string(from: instant))
            XCTAssertEqual(presentation.dayKey, "2026-10-24")
        }
        XCTAssertNotEqual(DatePresentation("2026-10-24").dayKey, DatePresentation("2027-10-24").dayKey)
    }

    func testLogGroupingUsesDeviceDayForInstantsAndOriginalDayForFloatingValues() throws {
        let shanghai = try XCTUnwrap(TimeZone(identifier: "Asia/Shanghai"))
        let losAngeles = try XCTUnwrap(TimeZone(identifier: "America/Los_Angeles"))
        let zoned = "2026-10-24T23:30:00-07:00"
        XCTAssertEqual(DatePresentation(zoned, timeZone: shanghai).dayKey, "2026-10-25")
        XCTAssertEqual(DatePresentation(zoned, timeZone: losAngeles).dayKey, "2026-10-24")
        for value in ["2026-10-24T23:30:00.123", "2026-10-24 23:30", "2026-10-24"] {
            let local = DatePresentation(value, locale: Locale(identifier: "en"), timeZone: shanghai)
            let distant = DatePresentation(value, locale: Locale(identifier: "en"), timeZone: losAngeles)
            XCTAssertEqual(local.dayKey, "2026-10-24")
            XCTAssertEqual(local.dateText, distant.dateText)
            XCTAssertEqual(local.timeText, distant.timeText)
            XCTAssertEqual(local.fullText, distant.fullText)
        }
    }

    func testInvalidDateIsKeptAndDateOnlyDoesNotInventMidnight() {
        for value in ["2026-02-30T10:20:00", "2026-10-24T25:00:00", "not-a-date"] {
            let date = DatePresentation(value)
            XCTAssertEqual(date.fullText, value)
            XCTAssertEqual(date.dateText, value)
            XCTAssertEqual(date.timeText, value)
        }
        let dateOnly = DatePresentation("2026-01-02", locale: Locale(identifier: "en"), timeZone: utc)
        XCTAssertEqual(dateOnly.fullText, dateOnly.dateText)
        XCTAssertEqual(dateOnly.timeText, dateOnly.dateText)
        XCTAssertEqual(dateOnly.dayKey, "2026-01-02")
    }
}
