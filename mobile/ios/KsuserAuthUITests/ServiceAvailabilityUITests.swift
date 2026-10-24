import XCTest

@MainActor final class ServiceAvailabilityUITests: XCTestCase {
    func testServiceFailurePreservesRegistrationInputAndNavigation() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["--ui-testing", "--ui-test-reset-preferences", "--ui-test-service-failure",
                               "-AppleLanguages", "(zh-Hans)", "-AppleLocale", "zh_CN"]
        app.launch()
        let registration = app.buttons["registrationLink"]
        XCTAssertTrue(registration.waitForExistence(timeout: 10))
        for _ in 0..<4 {
            if registration.isHittable { break }
            app.swipeUp()
        }
        registration.tap()
        let username = app.textFields["用户名"]
        XCTAssertTrue(username.waitForExistence(timeout: 5))
        username.tap(); username.typeText("retained_user")
        app.buttons["keyboardDone"].tap()
        app.buttons["registrationContinue"].tap()

        let banner = app.otherElements["serviceAvailabilityBanner"]
        XCTAssertTrue(banner.waitForExistence(timeout: 5))
        XCTAssertEqual(username.value as? String, "retained_user")
        XCTAssertTrue(app.buttons["registrationContinue"].isEnabled)
        banner.buttons["重试"].tap()
        let recovered = expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: banner)
        XCTAssertEqual(XCTWaiter.wait(for: [recovered], timeout: 5), .completed)
        XCTAssertEqual(username.value as? String, "retained_user")
        let back = app.buttons["已有账号，返回登录"]
        for _ in 0..<4 {
            if back.isHittable { break }
            app.swipeUp()
        }
        back.tap()
        XCTAssertTrue(app.buttons["loginButton"].waitForExistence(timeout: 5))
    }
}
