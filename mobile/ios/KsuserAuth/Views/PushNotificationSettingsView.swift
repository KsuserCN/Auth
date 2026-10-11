import SwiftUI
import UIKit

struct PushNotificationSettingsView: View {
    @Environment(\.locale) private var localizationLocale
    @Environment(AppModel.self) private var model
    @Environment(LanguageSettings.self) private var language
    @Environment(\.openURL) private var openURL
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            SettingsSectionHeading(title: L10n.text("通知推送"), icon: "bell.badge")
            VStack(alignment: .leading, spacing: 12) {
                Picker(L10n.text("此设备推送范围"), selection: Binding(get: { model.pushNotifications.mode }, set: { value in
                    Task { await model.pushNotifications.setMode(value) }
                })) {
                    ForEach(SecurityPushMode.allCases) { Text(verbatim: $0.title(language: language.effectiveLanguage)).tag($0) }
                }
                .id(language.effectiveLanguage)
                .tint(Brand.gold).disabled(model.pushNotifications.isSyncing)
                .accessibilityIdentifier("securityPushModePicker")
                Text(L10n.text("仅在此设备保持登录时接收提醒。异常请求包括失败、锁定、风控拦截及中高风险操作。"))
                    .font(.footnote).foregroundStyle(.secondary)
                Text(model.pushNotifications.statusMessage).font(.footnote).foregroundStyle(.secondary)
                    .accessibilityIdentifier("securityPushStatus")
                if model.pushNotifications.authorizationStatus == .denied {
                    Button(L10n.text("打开系统通知设置"), systemImage: "gearshape") {
                        if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                    }.frame(minHeight: 44)
                }
                Button(L10n.text("同步推送设置"), systemImage: "arrow.triangle.2.circlepath") {
                    Task { await model.synchronizePushNotifications() }
                }.disabled(model.pushNotifications.isSyncing).frame(minHeight: 44)
            }
            .padding(16).frame(maxWidth: .infinity, alignment: .leading)
            .background(Brand.card, in: RoundedRectangle(cornerRadius: 22))
        }
    }
}
