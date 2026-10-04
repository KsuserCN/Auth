import SwiftUI
import UIKit

@MainActor final class AppDelegate: NSObject, UIApplicationDelegate {
    static let shortcutNotification = Notification.Name("KsuserAuthShortcutAction")
    static let scanShortcut = "cn.ksuser.auth.shortcut.scan"
    static let securityShortcut = "cn.ksuser.auth.shortcut.security"
    static let sessionsShortcut = "cn.ksuser.auth.shortcut.sessions"
    static let profileShortcut = "cn.ksuser.auth.shortcut.profile"
    static var pendingShortcutType: String?

    func application(_ application: UIApplication, willFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        rememberShortcut(in: launchOptions)
        return true
    }

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        rememberShortcut(in: launchOptions)
        return true
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
