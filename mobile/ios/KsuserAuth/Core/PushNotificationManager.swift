import Foundation
import Observation
import UIKit
import UserNotifications

enum SecurityPushMode: String, CaseIterable, Identifiable, Sendable {
    case all = "ALL", loginAndAbnormal = "LOGIN_AND_ABNORMAL", off = "OFF"
    var id: String { rawValue }
    var title: String {
        switch self { case .all: "全部敏感操作"; case .loginAndAbnormal: "仅登录或异常请求"; case .off: "不推送" }
    }
}

struct PushRegistrationResponse: Decodable, Sendable { let enabled: Bool }

@MainActor protocol PushNotificationSystem {
    var deviceToken: String? { get }
    func authorizationStatus() async -> UNAuthorizationStatus
    func requestPermission() async throws
    func register(accountID: String)
    func disable()
}

@MainActor struct SystemPushNotifications: PushNotificationSystem {
    var deviceToken: String? { AppDelegate.pushDeviceToken }
    func authorizationStatus() async -> UNAuthorizationStatus { await UNUserNotificationCenter.current().notificationSettings().authorizationStatus }
    func requestPermission() async throws { _ = try await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) }
    func register(accountID: String) {
        AppDelegate.pushAccountID = accountID
        UIApplication.shared.registerForRemoteNotifications()
    }
    func disable() {
        AppDelegate.pushAccountID = nil
        UIApplication.shared.unregisterForRemoteNotifications()
        UNUserNotificationCenter.current().removeAllDeliveredNotifications()
    }
}

@MainActor @Observable final class PushNotificationManager {
    private(set) var mode: SecurityPushMode = .all
    private(set) var authorizationStatus: UNAuthorizationStatus = .notDetermined
    private(set) var statusMessage = "登录后接收账号安全提醒"
    private(set) var isSyncing = false
    @ObservationIgnored private let client: APIClient
    @ObservationIgnored private let defaults: UserDefaults
    @ObservationIgnored private let nativeEnabled: Bool
    @ObservationIgnored private let system: any PushNotificationSystem
    @ObservationIgnored private var accountID: String?
    @ObservationIgnored private var generation = 0
    @ObservationIgnored private var syncTask: Task<Void, Never>?

    init(client: APIClient, defaults: UserDefaults = .standard, nativeEnabled: Bool = true, system: (any PushNotificationSystem)? = nil) {
        self.client = client; self.defaults = defaults; self.nativeEnabled = nativeEnabled
        self.system = system ?? SystemPushNotifications()
    }

    func signOut() {
        generation += 1; accountID = nil; isSyncing = false
        statusMessage = "登录后接收账号安全提醒"
        guard nativeEnabled else { return }
        system.disable()
    }

    func synchronize(accountID: String, requestPermission: Bool = true) async {
        if self.accountID != accountID {
            generation += 1; self.accountID = accountID
            mode = SecurityPushMode(rawValue: defaults.string(forKey: preferenceKey(accountID)) ?? "") ?? .all
        }
        await enqueueSync(requestPermission: requestPermission)
    }

    func setMode(_ value: SecurityPushMode) async {
        guard let accountID else { return }
        mode = value; defaults.set(value.rawValue, forKey: preferenceKey(accountID))
        if value == .off, nativeEnabled {
            system.disable()
        }
        await enqueueSync(requestPermission: true)
    }

    func registrationFailed() { statusMessage = "无法注册系统推送，请检查网络后重新打开应用" }

    private func preferenceKey(_ accountID: String) -> String { "securityPushMode.\(accountID)" }

    private func enqueueSync(requestPermission: Bool) async {
        guard nativeEnabled, accountID != nil else { return }
        generation += 1
        let expectedGeneration = generation
        let previous = syncTask
        // Serialize mutations so a delayed registration cannot overwrite a subsequent OFF/delete request.
        let task = Task { [weak self] in
            await previous?.value
            guard let self, expectedGeneration == self.generation else { return }
            await self.sync(expectedGeneration: expectedGeneration, requestPermission: requestPermission)
        }
        syncTask = task
        await task.value
        if generation == expectedGeneration { syncTask = nil }
    }

    private func sync(expectedGeneration: Int, requestPermission: Bool) async {
        isSyncing = true
        defer { if generation == expectedGeneration { isSyncing = false } }
        do {
            var status = await system.authorizationStatus()
            guard generation == expectedGeneration, accountID != nil else { return }
            if mode != .off && status == .notDetermined && requestPermission {
                try await system.requestPermission()
                status = await system.authorizationStatus()
            }
            guard generation == expectedGeneration, let accountID else { return }
            authorizationStatus = status
            guard mode != .off && [.authorized, .provisional, .ephemeral].contains(status) else {
                system.disable()
                let _: EmptyPayload = try await client.request("/auth/push/device", method: "DELETE")
                guard generation == expectedGeneration else { return }
                statusMessage = mode == .off ? "此设备已关闭推送" : "系统通知未开启，请在 iOS 设置中允许通知"
                return
            }
            // Always ask APNs for the current token, including at launch; do not persist device tokens.
            system.register(accountID: accountID)
            guard let token = system.deviceToken else { statusMessage = "正在注册系统推送…"; return }
            let response: PushRegistrationResponse = try await client.request("/auth/push/device", method: "POST", body: [
                "deviceToken": .string(token), "environment": .string(Self.apnsEnvironment), "mode": .string(mode.rawValue)
            ])
            guard generation == expectedGeneration else { return }
            statusMessage = response.enabled ? "已开启：\(mode.title)" : "推送偏好已保存，服务端尚未启用推送"
        } catch {
            guard generation == expectedGeneration else { return }
            if let apiError = error as? APIError, apiError.isUnauthorized { signOut(); return }
            statusMessage = "推送设置待同步：\(error.localizedDescription)"
        }
    }

    static var apnsEnvironment: String {
        let value = Bundle.main.object(forInfoDictionaryKey: "KSUSER_APNS_ENVIRONMENT") as? String
        return value == "production" ? "PRODUCTION" : "SANDBOX"
    }
}
