import SwiftUI
import UIKit
import UserNotifications

@MainActor final class AppDelegate: NSObject, UIApplicationDelegate {
    static let shortcutNotification = Notification.Name("KsuserAuthShortcutAction")
    static let scanShortcut = "cn.ksuser.auth.shortcut.scan"
    static let securityShortcut = "cn.ksuser.auth.shortcut.security"
    static let sessionsShortcut = "cn.ksuser.auth.shortcut.sessions"
    static let profileShortcut = "cn.ksuser.auth.shortcut.profile"
    static var pendingShortcutType: String?
    static let pushTokenNotification = Notification.Name("KsuserAuthPushToken")
    static let pushOpenedNotification = Notification.Name("KsuserAuthPushOpened")
    static let pushFailedNotification = Notification.Name("KsuserAuthPushFailed")
    static var pushDeviceToken: String?
    static var pushAccountID: String?
    static var pendingPushAccountID: String?
    static var pendingPushEventID: Int64?

    func application(_ application: UIApplication, willFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        rememberShortcut(in: launchOptions)
        return true
    }

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        rememberShortcut(in: launchOptions)
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("--ui-testing") {
            if ProcessInfo.processInfo.arguments.contains("--ui-test-push-log") {
                Self.pendingPushAccountID = "ui-fixture-only"
                Self.pendingPushEventID = 1
            }
            if ProcessInfo.processInfo.arguments.contains("--ui-test-security-notification") {
                Task { @MainActor in
                    let center = UNUserNotificationCenter.current()
                    guard (try? await center.requestAuthorization(options: [.alert, .sound])) == true else { return }
                    Self.pushAccountID = "ui-fixture-only"
                    let content = UNMutableNotificationContent()
                    content.title = "Ksuser 推送回归测试"
                    content.body = "点按查看安全日志详情"
                    content.userInfo = ["kind": "security", "accountId": "ui-fixture-only", "eventId": "1"]
                    let request = UNNotificationRequest(identifier: "security-ui-test", content: content,
                                                        trigger: UNTimeIntervalNotificationTrigger(timeInterval: 5, repeats: false))
                    try? await center.add(request)
                }
            }
        }
        #endif
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        let token = deviceToken.map { String(format: "%02x", $0) }.joined()
        // A repeated callback for the same token must not start a registration loop.
        guard token != Self.pushDeviceToken else { return }
        Self.pushDeviceToken = token
        NotificationCenter.default.post(name: Self.pushTokenNotification, object: nil)
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        NotificationCenter.default.post(name: Self.pushFailedNotification, object: nil)
    }

    func application(_ application: UIApplication, performActionFor shortcutItem: UIApplicationShortcutItem, completionHandler: @escaping (Bool) -> Void) {
        Self.receiveShortcut(type: shortcutItem.type)
        completionHandler(true)
    }

    func application(_ application: UIApplication, configurationForConnecting connectingSceneSession: UISceneSession, options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        if let shortcut = options.shortcutItem { Self.pendingShortcutType = shortcut.type }
        let configuration = UISceneConfiguration(name: nil, sessionRole: connectingSceneSession.role)
        configuration.delegateClass = ShortcutSceneDelegate.self
        return configuration
    }

    static func takePendingShortcutType() -> String? {
        defer { pendingShortcutType = nil }
        return pendingShortcutType
    }

    private func rememberShortcut(in launchOptions: [UIApplication.LaunchOptionsKey: Any]?) {
        if let shortcut = launchOptions?[.shortcutItem] as? UIApplicationShortcutItem {
            Self.pendingShortcutType = shortcut.type
        }
    }

    static func receiveShortcut(type: String) {
        pendingShortcutType = type
        NotificationCenter.default.post(name: shortcutNotification, object: type)
    }
}

extension AppDelegate: UNUserNotificationCenterDelegate {
    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                           withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        let accountID = notification.request.content.userInfo["accountId"] as? String
        DispatchQueue.main.async {
            let options: UNNotificationPresentationOptions = accountID != nil && accountID == Self.pushAccountID ? [.banner, .list, .sound] : []
            completionHandler(options)
        }
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                           withCompletionHandler completionHandler: @escaping () -> Void) {
        Self.handlePushResponse(userInfo: response.notification.request.content.userInfo,
                                actionIdentifier: response.actionIdentifier, completionHandler: completionHandler)
    }

    nonisolated static func handlePushResponse(userInfo: [AnyHashable: Any], actionIdentifier: String,
                                              completionHandler: @escaping () -> Void) {
        let accountID = userInfo["kind"] as? String == "security" ? userInfo["accountId"] as? String : nil
        let rawEventID = userInfo["eventId"]
        let eventID = (rawEventID as? String).flatMap(Int64.init) ?? (rawEventID as? NSNumber)?.int64Value
        // UIKit's response completion updates scene snapshots and must execute on the main thread.
        // The async delegate bridge can invoke that completion on a cooperative executor instead.
        DispatchQueue.main.async {
            defer { completionHandler() }
            guard actionIdentifier == UNNotificationDefaultActionIdentifier, let accountID else { return }
            Self.pendingPushAccountID = accountID
            Self.pendingPushEventID = eventID
            NotificationCenter.default.post(name: Self.pushOpenedNotification, object: nil)
        }
    }
}

@MainActor final class ShortcutSceneDelegate: NSObject, UIWindowSceneDelegate {
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        if let shortcut = connectionOptions.shortcutItem {
            AppDelegate.pendingShortcutType = shortcut.type
        }
    }

    func windowScene(_ windowScene: UIWindowScene, performActionFor shortcutItem: UIApplicationShortcutItem, completionHandler: @escaping (Bool) -> Void) {
        AppDelegate.receiveShortcut(type: shortcutItem.type)
        completionHandler(true)
    }
}

@main struct MyApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @State private var model = AppModel(native: NativeAuthenticationProvider())
    init() {
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("--ui-test-reset-preferences") {
            UserDefaults.standard.removeObject(forKey: "agreementAccepted")
            UserDefaults.standard.removeObject(forKey: "appearance")
        }
        #endif
    }
    var body: some Scene {
        WindowGroup {
            ContentView().environment(model)
        }
    }
}
