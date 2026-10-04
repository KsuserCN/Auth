import XCTest

@MainActor final class KsuserAuthUITests: XCTestCase {
    override func setUp() { continueAfterFailure = false }

    private func launch(authenticated: Bool = false, resetPreferences: Bool = true, arguments: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--ui-testing"]
        if authenticated { app.launchArguments.append("--ui-test-authenticated") }
        if resetPreferences { app.launchArguments.append("--ui-test-reset-preferences") }
        app.launchArguments += arguments
        app.launch()
        return app
    }

    func testSlowLoginShowsLoadingAndRestoresControls() {
        let app = launch(arguments: ["--ui-test-loading"])
        let email = app.textFields["邮箱"]
        XCTAssertTrue(email.waitForExistence(timeout: 10))
        email.tap(); email.typeText("fixture@example.invalid")
        replaceSecureText(app.secureTextFields["密码"], with: "Fixture1234")
        app.buttons["keyboardDone"].tap()
        app.switches["agreementToggle"].tap()
        let login = app.buttons["loginButton"]
        let loginFrame = login.frame
        login.tap()
        assertLoading(app, message: "正在登录…", name: "Login loading", anchor: login, originalFrame: loginFrame)
        XCTAssertTrue(login.isEnabled)
    }

    func testSlowProviderLoginShowsLoadingOnSelectedButton() {
        let app = launch(arguments: ["--ui-test-loading"])
        XCTAssertTrue(app.switches["agreementToggle"].waitForExistence(timeout: 10))
        app.switches["agreementToggle"].tap()
        let provider = app.buttons["qqLoginButton"]
        for _ in 0..<4 {
            if provider.isHittable { break }
            app.swipeUp()
        }
        let providerFrame = provider.frame
        provider.tap()
        assertLoading(app, message: "正在使用 QQ 登录…", name: "QQ login loading", anchor: provider, originalFrame: providerFrame)
        XCTAssertTrue(provider.isEnabled)
    }

    func testSlowLogoutShowsLoadingAboveScrolledContent() {
        let app = launch(authenticated: true, arguments: ["--ui-test-loading"])
        XCTAssertTrue(app.staticTexts["welcomeUsername"].waitForExistence(timeout: 10))
        navigate(app, to: "安全")
        let row = app.buttons["logoutCurrentRow"]
        for _ in 0..<6 {
            if row.isHittable { break }
            app.swipeUp()
        }
        row.tap()
        let rowFrame = row.frame
        app.buttons.matching(identifier: "confirmLogoutCurrent").firstMatch.tap()
        assertLoading(app, message: "正在退出登录…", name: "Logout loading", anchor: row, originalFrame: rowFrame)
        XCTAssertTrue(row.isEnabled)
    }

    private func assertLoading(_ app: XCUIApplication, message: String, name: String, anchor: XCUIElement, originalFrame: CGRect) {
        let banner = app.descendants(matching: .any).matching(identifier: "loadingBanner").firstMatch
        XCTAssertTrue(banner.waitForExistence(timeout: 2))
        XCTAssertTrue(banner.isHittable)
        XCTAssertTrue(banner.label.contains(message))
        XCTAssertGreaterThanOrEqual(banner.frame.minY, app.navigationBars.firstMatch.frame.maxY)
        assertPosition(anchor, matches: originalFrame)
        attach(app, name: name)
        let finished = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in !banner.exists }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [finished], timeout: 8), .completed)
        assertPosition(anchor, matches: originalFrame)
    }

    private func assertPosition(_ element: XCUIElement, matches expected: CGRect, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertEqual(element.frame.minX, expected.minX, accuracy: 1, file: file, line: line)
        XCTAssertEqual(element.frame.minY, expected.minY, accuracy: 1, file: file, line: line)
    }

    func testLoginRequiresAgreementAndValidInput() {
        let app = launch()
        let login = app.buttons["loginButton"]
        XCTAssertTrue(login.waitForExistence(timeout: 10))
        XCTAssertFalse(login.isEnabled)
        let email = app.textFields["邮箱"]
        email.tap(); email.typeText("invalid")
        XCTAssertFalse(login.isEnabled)
        email.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 7))
        email.typeText("ios.fixture@example.invalid")
        let password = app.secureTextFields["密码"]
        password.tap(); password.typeText("Example123")
        app.buttons["keyboardDone"].tap()
        let toggle = app.switches["agreementToggle"]
        XCTAssertTrue(toggle.waitForExistence(timeout: 5))
        toggle.tap()
        if toggle.value as? String != "1" {
            XCTAssertTrue(toggle.isHittable)
            toggle.coordinate(withNormalizedOffset: CGVector(dx: 0.95, dy: 0.5)).tap()
        }
        attach(app, name: "Login input and agreement")
        XCTAssertEqual(email.value as? String, "ios.fixture@example.invalid")
        XCTAssertEqual(toggle.value as? String, "1")
        XCTAssertTrue(login.isEnabled)
        XCTAssertTrue(app.buttons["passkeyLoginButton"].isEnabled)
        XCTAssertTrue(app.buttons["qqLoginButton"].isEnabled)
        XCTAssertTrue(app.buttons["appleLoginButton"].isEnabled)
        attach(app, name: "Login")
        for _ in 0..<4 {
            if app.buttons["appleLoginButton"].isHittable { break }
            app.swipeUp()
        }
        attach(app, name: "Quick login light")
        app.buttons["aboutButton"].tap()
        app.segmentedControls["themePicker"].buttons["深色"].tap()
        app.buttons["完成"].firstMatch.tap()
        attach(app, name: "Quick login dark")
    }

    func testLoginMethodSwitchingPreservesInputAndPasswordVisibility() {
        let app = launch()
        XCTAssertTrue(app.staticTexts["loginHeading"].waitForExistence(timeout: 10))
        attach(app, name: "Login redesigned light")
        let email = app.textFields["邮箱"]
        email.tap(); email.typeText("fixture@example.invalid")
        replaceSecureText(app.secureTextFields["密码"], with: "Fixture1234")
        let visibility = app.buttons["passwordVisibilityButton"]
        visibility.tap()
        XCTAssertEqual(app.textFields["密码"].value as? String, "Fixture1234")
        visibility.tap()
        XCTAssertTrue(app.secureTextFields["密码"].exists)
        app.buttons["keyboardDone"].tap()
        app.buttons["codeLoginTab"].tap()
        let send = app.buttons["sendLoginCodeButton"]
        XCTAssertTrue(send.waitForExistence(timeout: 5))
        XCTAssertFalse(send.isEnabled)
        XCTAssertFalse(app.buttons["loginButton"].isEnabled)
        app.switches["agreementToggle"].tap()
        XCTAssertTrue(send.isEnabled)
        let code = app.textFields["验证码"]
        code.tap(); code.typeText("123456")
        app.buttons["keyboardDone"].tap()
        XCTAssertTrue(app.buttons["loginButton"].isEnabled)
        attach(app, name: "Login redesigned email code")
        app.buttons["passwordLoginTab"].tap()
        XCTAssertEqual(email.value as? String, "fixture@example.invalid")
        XCTAssertTrue(app.buttons["loginButton"].isEnabled)
        visibility.tap()
        XCTAssertEqual(app.textFields["密码"].value as? String, "Fixture1234")
    }

    func testRegistrationUsernameValidationAndFiveSteps() {
        let app = launch()
        let registration = app.buttons["registrationLink"]
        for _ in 0..<4 {
            if registration.isHittable { break }
            app.swipeUp()
        }
        registration.tap()
        let next = app.buttons["registrationContinue"]
        XCTAssertTrue(next.waitForExistence(timeout: 5))
        XCTAssertFalse(next.isEnabled)
        let username = app.textFields["用户名"]
        username.tap(); username.typeText("ab")
        XCTAssertFalse(next.isEnabled)
        username.typeText("c")
        XCTAssertTrue(next.isEnabled)
        next.tap()
        let password = app.secureTextFields["设置密码"]
        XCTAssertTrue(password.waitForExistence(timeout: 5))
        replaceSecureText(password, with: "Weak")
        XCTAssertFalse(next.isEnabled)
        password.typeText("Password123")
        XCTAssertTrue(next.isEnabled)
        next.tap()
        let confirmation = app.secureTextFields["确认密码"]
        XCTAssertTrue(confirmation.waitForExistence(timeout: 5))
        replaceSecureText(confirmation, with: "Wrong")
        XCTAssertFalse(next.isEnabled)
        attach(app, name: "Registration validation")
        replaceSecureText(confirmation, with: "WeakPassword123")
        app.buttons["keyboardDone"].tap()
        XCTAssertTrue(next.isEnabled)
        next.tap()
        let email = app.textFields["注册邮箱"]
        XCTAssertTrue(email.waitForExistence(timeout: 5))
        XCTAssertFalse(next.isEnabled)
        email.tap(); email.typeText("ios.fixture@example.invalid")
        app.buttons["keyboardDone"].tap()
        let agreement = app.switches["agreementToggle"]
        agreement.tap()
        if agreement.value as? String != "1" { agreement.coordinate(withNormalizedOffset: CGVector(dx: 0.95, dy: 0.5)).tap() }
        XCTAssertTrue(next.isEnabled)
        next.tap()
        let code = app.textFields["邮箱验证码"]
        XCTAssertTrue(code.waitForExistence(timeout: 5))
        XCTAssertFalse(next.isEnabled)
        code.tap(); code.typeText("123456")
        app.buttons["keyboardDone"].tap()
        XCTAssertTrue(next.isEnabled)
        attach(app, name: "Registration fifth step")
    }

    func testAuthenticatedNavigationAndLocalLogDetails() {
        let app = launch(authenticated: true)
        XCTAssertTrue(app.staticTexts["welcomeUsername"].waitForExistence(timeout: 10))
        navigate(app, to: "资料")
        XCTAssertTrue(app.buttons["avatarPicker"].waitForExistence(timeout: 5))
        navigate(app, to: "安全")
        XCTAssertTrue(app.staticTexts["当前会话安全状态"].waitForExistence(timeout: 5))
        attach(app, name: "Security light")
        navigate(app, to: "会话")
        XCTAssertTrue(app.staticTexts["当前设备"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["撤销设备"].exists)
        navigate(app, to: "日志")
        let log = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "登录，成功")).firstMatch
        XCTAssertTrue(log.waitForExistence(timeout: 5))
        log.tap()
        XCTAssertTrue(app.staticTexts["日志详情"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["192.0.2.1"].exists)
    }

    func testThemeChoicePersistsAndRendersDarkMode() {
        var app = launch(authenticated: true)
        app.buttons["aboutButton"].tap()
        let picker = app.segmentedControls["themePicker"]
        XCTAssertTrue(picker.waitForExistence(timeout: 5))
        picker.buttons["深色"].tap()
        XCTAssertTrue(picker.buttons["深色"].isSelected)
        attach(app, name: "Settings dark")
        app.buttons["完成"].firstMatch.tap()
        navigate(app, to: "安全")
        attach(app, name: "Security dark")
        app.terminate()
        app = launch(authenticated: true, resetPreferences: false)
        app.buttons["aboutButton"].tap()
        XCTAssertTrue(app.segmentedControls["themePicker"].buttons["深色"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.segmentedControls["themePicker"].buttons["深色"].isSelected)
    }

    func testStatusMessagesFloatBelowNavigationWithoutMovingContent() {
        let baseline = launch()
        let baselineHeading = baseline.staticTexts["loginHeading"]
        XCTAssertTrue(baselineHeading.waitForExistence(timeout: 10))
        let headingFrame = baselineHeading.frame
        let emailFrame = baseline.textFields["邮箱"].frame
        let loginFrame = baseline.buttons["loginButton"].frame
        baseline.terminate()
        for argument in ["--ui-test-notice", "--ui-test-error"] {
            let app = launch(arguments: [argument])
            let banner = app.otherElements["statusBanner"]
            XCTAssertTrue(banner.waitForExistence(timeout: 10))
            XCTAssertEqual(app.buttons.matching(identifier: "dismissStatusBanner").count, 1)
            XCTAssertGreaterThanOrEqual(banner.frame.minY, app.navigationBars.firstMatch.frame.maxY)
            assertPosition(app.staticTexts["loginHeading"], matches: headingFrame)
            assertPosition(app.textFields["邮箱"], matches: emailFrame)
            assertPosition(app.buttons["loginButton"], matches: loginFrame)
            attach(app, name: argument == "--ui-test-notice" ? "Success banner" : "Error banner")
            app.buttons["dismissStatusBanner"].tap()
            XCTAssertFalse(banner.exists)
            assertPosition(app.staticTexts["loginHeading"], matches: headingFrame)
            assertPosition(app.textFields["邮箱"], matches: emailFrame)
            assertPosition(app.buttons["loginButton"], matches: loginFrame)
            XCTAssertFalse(app.buttons["loginButton"].isEnabled)
            app.terminate()
        }
    }

    func testSecurityLogoutConfirmationsUseTheirRowsAsAnchors() {
        let app = launch(authenticated: true)
        XCTAssertTrue(app.staticTexts["welcomeUsername"].waitForExistence(timeout: 10))
        navigate(app, to: "安全")
        for (rowID, confirmationID) in [("logoutCurrentRow", "confirmLogoutCurrent"), ("logoutAllRow", "confirmLogoutAll")] {
            let row = app.buttons[rowID]
            for _ in 0..<6 {
                if row.isHittable { break }
                app.swipeUp()
            }
            XCTAssertTrue(row.isHittable)
            let source = row.frame
            row.tap()
            let confirmation = app.buttons.matching(identifier: confirmationID).firstMatch
            XCTAssertTrue(confirmation.waitForExistence(timeout: 5))
            // iPad uses a popover; iPhone can use a bottom action sheet depending on iOS.
            if app.frame.width > 600 {
                XCTAssertLessThan(abs(confirmation.frame.midY - source.midY), 220)
                XCTAssertTrue(confirmation.frame.midX >= source.minX && confirmation.frame.midX <= source.maxX)
            }
            attach(app, name: rowID == "logoutCurrentRow" ? "Current account logout confirmation" : "All devices logout confirmation")
            if app.buttons["取消"].exists { app.buttons["取消"].tap() }
            else { app.coordinate(withNormalizedOffset: CGVector(dx: 0.05, dy: 0.15)).tap() }
            XCTAssertFalse(confirmation.exists)
            XCTAssertTrue(row.isHittable)
        }
    }

    func testOverviewUsesUsernameAndDatesCanScrollAtLargeTextSize() {
        let app = launch(authenticated: true, arguments: ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"])
        let username = app.staticTexts["welcomeUsername"]
        XCTAssertTrue(username.waitForExistence(timeout: 10))
        XCTAssertEqual(username.label, "ios_test_user")
        navigate(app, to: "会话")
        let date = app.descendants(matching: .any).matching(identifier: "dateScroll-登录时间").firstMatch
        for _ in 0..<8 {
            if date.isHittable { break }
            app.swipeUp()
        }
        XCTAssertTrue(date.isHittable)
        XCTAssertTrue(app.staticTexts["2026-10-01 10:00:00"].exists)
        date.swipeLeft()
        XCTAssertTrue(app.staticTexts["2026-10-01 10:00:00"].exists)
        attach(app, name: "Complete dates at large text size")
    }

    func testAboutShowsLogoAndOpensAgreementInApp() {
        let app = launch()
        XCTAssertTrue(app.buttons["aboutButton"].waitForExistence(timeout: 10))
        app.buttons["aboutButton"].tap()
        XCTAssertTrue(app.images["appLogo"].waitForExistence(timeout: 5))
        attach(app, name: "About with app logo")
        let agreement = app.buttons["服务协议"]
        for _ in 0..<5 {
            if agreement.isHittable { break }
            app.swipeUp()
        }
        agreement.tap()
        let browser = app.descendants(matching: .any).matching(identifier: "inAppSafari").firstMatch
        XCTAssertTrue(browser.waitForExistence(timeout: 10))
        XCTAssertEqual(app.state, .runningForeground)
        let doneQuery = browser.buttons.matching(NSPredicate(format: "label == %@ OR label == %@", "完成", "关闭"))
        let ready = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            doneQuery.allElementsBoundByIndex.contains { $0.isHittable }
        }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [ready], timeout: 20), .completed)
        attach(app, name: "Agreement in Safari controller")
        guard let done = doneQuery.allElementsBoundByIndex.first(where: { $0.isHittable }) else { return XCTFail("Safari close button is unavailable") }
        done.tap()
        let closed = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            app.navigationBars["关于与设置"].buttons["完成"].isHittable
        }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [closed], timeout: 5), .completed)
        XCTAssertTrue(app.navigationBars["关于与设置"].waitForExistence(timeout: 5))
        attach(app, name: "Return from in-app browser")
    }

    func testAvatarCropCanCancelOrConfirm() {
        for confirm in [false, true] {
            let app = launch(authenticated: true, arguments: ["--ui-test-avatar-crop"])
            XCTAssertTrue(app.staticTexts["welcomeUsername"].waitForExistence(timeout: 10))
            navigate(app, to: "资料")
            let viewport = app.descendants(matching: .any).matching(identifier: "avatarCropViewport").firstMatch
            XCTAssertTrue(viewport.waitForExistence(timeout: 5))
            viewport.pinch(withScale: 2, velocity: 1)
            viewport.swipeLeft()
            attach(app, name: confirm ? "Avatar crop before confirmation" : "Avatar crop before cancellation")
            if confirm { app.buttons["confirmAvatarCrop"].tap() }
            else { app.buttons["取消"].firstMatch.tap() }
            XCTAssertFalse(app.buttons["confirmAvatarCrop"].exists)
            XCTAssertTrue(app.buttons["avatarPicker"].waitForExistence(timeout: 5))
            app.terminate()
        }
    }

    func testScannerPhotoPickerUsesAppTheme() {
        let app = launch(authenticated: true)
        XCTAssertTrue(app.buttons["scanButton"].waitForExistence(timeout: 10))
        app.buttons["scanButton"].tap()
        XCTAssertTrue(app.buttons["qrPhotoPicker"].waitForExistence(timeout: 5))
        attach(app, name: "Scanner with themed photo picker")
        app.buttons["取消"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["welcomeUsername"].waitForExistence(timeout: 5))
    }

    private func attach(_ app: XCUIApplication, name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func navigate(_ app: XCUIApplication, to title: String) {
        let tab = app.tabBars.buttons[title]
        if tab.exists { tab.tap() }
        else {
            let sidebar = app.buttons[title].firstMatch
            if !sidebar.isHittable, app.buttons["显示边栏"].exists { app.buttons["显示边栏"].tap() }
            sidebar.tap()
        }
    }

    private func replaceSecureText(_ field: XCUIElement, with text: String) {
        field.tap()
        // Secure fields may be populated by the system's password AutoFill. Clear the
        // full supported fixture length instead of assuming the visible bullet count.
        field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 64))
        field.typeText(text)
    }
}
