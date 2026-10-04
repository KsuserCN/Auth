import SwiftUI

private enum SecuritySheet: String, Identifiable {
    case passkeys, totp, recoveryCodes, recovery, email, password, deleteAccount
    var id: String { rawValue }
}

struct SecurityView: View {
    @Environment(AppModel.self) private var model
    @State private var sheet: SecuritySheet?
    @State private var pendingSheet: SecuritySheet?
    @State private var sensitivePresented = false
    @State private var logoutAll = false
    @State private var logoutCurrent = false
    @State private var unbindApple = false
    var body: some View {
        PageScroll {
            if let risk = model.adaptiveStatus { RiskStatusCard(status: risk) }
            AppCard {
                CardHeader(title: "验证方式", subtitle: "为账号准备多种安全登录方式", icon: "key.fill")
                ActionRow(title: "Passkey", icon: "person.badge.key.fill", subtitle: "\(model.passkeys.count) 个通行密钥") { sheet = .passkeys }
                Divider()
                ActionRow(title: "验证器与 TOTP", icon: "number.square", subtitle: model.totpStatus.enabled ? "已启用" : "未启用") { protected(.totp) }
                if model.totpStatus.enabled {
                    Divider()
                    ActionRow(title: "TOTP 恢复码", icon: "key.horizontal", subtitle: "剩余 \(model.totpStatus.recoveryCodesCount) 个") {
                        Task { await model.requireSensitive(title: "查看恢复码") { await model.showRecoveryCodes(); if model.errorMessage == nil { present(.recoveryCodes) } } }
                    }
                }
                Divider()
                if model.appleBound {
                    ActionRow(title: "Apple 已绑定", icon: "apple.logo", subtitle: "管理 Apple 登录身份") { unbindApple = true }
                        .confirmationDialog("解除 Apple 绑定？", isPresented: $unbindApple, titleVisibility: .visible) {
                            Button("解除绑定", role: .destructive) { Task { await model.requireSensitive(title: "解除 Apple 绑定") { await model.unbindApple() } } }
                        } message: { Text("解绑前请确保已经设置密码或其他可用的登录方式。") }
                } else {
                    ActionRow(title: "绑定 Apple", icon: "apple.logo", subtitle: "使用 Apple 安全登录当前账号") { Task { await model.requireSensitive(title: "绑定 Apple") { await model.bindApple() } } }
                }
            }
            AppCard {
                CardHeader(title: "登录保护", icon: "lock.shield")
                settingToggle("双重验证", field: "mfaEnabled", value: model.user?.settings?.mfaEnabled ?? false)
                settingToggle("异地登录检测", field: "detectUnusualLogin", value: model.user?.settings?.detectUnusualLogin ?? true)
                settingToggle("敏感操作邮件提醒", field: "notifySensitiveActionEmail", value: model.user?.settings?.notifySensitiveActionEmail ?? true)
                settingToggle("订阅动态邮件", field: "subscribeNewsEmail", value: model.user?.settings?.subscribeNewsEmail ?? false)
                Divider()
                preference("首选双重验证", field: "preferredMfaMethod", selected: model.user?.settings?.preferredMfaMethod ?? "totp", methods: ["totp", "passkey"])
                preference("首选敏感验证", field: "preferredSensitiveMethod", selected: model.user?.settings?.preferredSensitiveMethod ?? (model.user?.hasPassword == false ? "apple" : "password"), methods: availableSensitiveMethods)
            }
            AppCard {
                CardHeader(title: "账号管理", icon: "person.crop.circle.badge.checkmark")
                ActionRow(title: model.user?.email.isEmpty == true ? "绑定邮箱" : "修改邮箱", icon: "envelope", subtitle: model.user?.email) { protected(.email) }
                Divider()
                ActionRow(title: model.user?.hasPassword == false ? "设置密码" : "修改密码", icon: "lock.rotation") { protected(.password) }
                Divider()
                ActionRow(title: "退出当前账号", icon: "rectangle.portrait.and.arrow.right") { logoutCurrent = true }
                    .accessibilityIdentifier("logoutCurrentRow")
                    .confirmationDialog("退出当前账号？", isPresented: $logoutCurrent, titleVisibility: .visible) {
                        Button("退出登录", role: .destructive) { Task { await model.logout() } }
                            .accessibilityIdentifier("confirmLogoutCurrent")
                    }
                ActionRow(title: "退出所有设备", icon: "power", destructive: true) { logoutAll = true }
                    .accessibilityIdentifier("logoutAllRow")
                    .confirmationDialog("退出所有设备？", isPresented: $logoutAll, titleVisibility: .visible) {
                        Button("退出所有设备", role: .destructive) { Task { await model.requireSensitive(title: "退出所有设备") { await model.logout(allDevices: true) } } }
                            .accessibilityIdentifier("confirmLogoutAll")
                    } message: { Text("所有登录会话都会被撤销，包括这台设备。") }
            }
            AppCard {
                CardHeader(title: "恢复授权", subtitle: "在其他设备无法登录时，使用当前可信设备生成一次性恢复授权。", icon: "lifepreserver")
                ActionRow(title: "生成恢复码与二维码", icon: "qrcode") {
                    Task { await model.requireSensitive(title: "生成账号恢复授权") { await model.generateRecoveryTicket(); if model.errorMessage == nil { present(.recovery) } } }
                }
            }
            AppCard {
                CardHeader(title: "注销账号", subtitle: "永久删除账号及相关资料。这项操作无法恢复。", icon: "exclamationmark.triangle")
                ActionRow(title: "注销我的账号", icon: "trash", destructive: true) { protected(.deleteAccount) }
            }
        }.refreshable { await model.refreshSecurity() }.task { await model.refreshSecurity() }
            .sheet(item: $sheet) { page in
                AppNavigationStack {
                    switch page {
                    case .passkeys: PasskeysView()
                    case .totp: TOTPView()
                    case .recoveryCodes: RecoveryCodesView()
                    case .recovery: AccountRecoveryTicketView()
                    case .email: ChangeEmailView()
                    case .password: ChangePasswordView()
                    case .deleteAccount: DeleteAccountView()
                    }
                }.presentationDragIndicator(.visible)
            }
            .sensitiveVerification(enabled: sheet == nil, onPresent: { sensitivePresented = true }, onDismiss: {
                sensitivePresented = false
                if let next = pendingSheet { pendingSheet = nil; sheet = next }
            })
    }
    private var availableSensitiveMethods: [String] {
        var methods = ["passkey", "totp"]
        if model.user?.hasPassword != false { methods.insert("password", at: 0) }
        if model.user?.email.isEmpty == false { methods.append("email-code") }
        if model.appleBound { methods.append("apple") }
        return methods
    }
    private func protected(_ route: SecuritySheet) {
        Task { await model.requireSensitive { present(route) } }
    }
    private func present(_ route: SecuritySheet) { if sensitivePresented { pendingSheet = route } else { sheet = route } }
    private func settingToggle(_ title: String, field: String, value: Bool) -> some View {
        Toggle(title, isOn: Binding(get: { value }, set: { newValue in Task { await model.updateSetting(field: field, value: newValue) } })).disabled(model.isBusy).frame(minHeight: 44)
    }
    private func preference(_ title: String, field: String, selected: String, methods: [String]) -> some View {
        Picker(title, selection: Binding(get: { selected }, set: { choice in Task { await model.updateSetting(field: field, stringValue: choice) } })) {
            ForEach(methods, id: \.self) { Text(methodLabel($0)).tag($0) }
        }.disabled(model.isBusy).frame(minHeight: 44)
    }
}

struct RiskStatusCard: View {
    @Environment(AppModel.self) private var model
    let status: AdaptiveAuthStatus
    @State private var showDetails = false
    private var title: String { status.sessionFrozen ? "当前会话已冻结" : status.requiresStepUp ? "需要再次验证身份" : "当前会话安全状态" }
    var body: some View {
        AppCard {
            CardHeader(title: title, subtitle: status.recommendedAction.isEmpty ? "根据登录环境和近期活动持续检查账号安全。" : status.recommendedAction, icon: status.sessionFrozen ? "exclamationmark.shield" : "checkmark.shield")
            HStack { StatusPill(title: "风险：\(riskLabel)", positive: status.riskLevel == "low"); Spacer(); Text("\(status.riskScore) / 100").font(.caption).foregroundStyle(.secondary) }
            if let alert = status.alertMessage, !alert.isEmpty { Label(alert, systemImage: "exclamationmark.triangle.fill").font(.subheadline).foregroundStyle(Brand.gold).fixedSize(horizontal: false, vertical: true) }
            if status.sensitiveVerified { Text("敏感验证有效期：\(status.sensitiveVerificationRemainingSeconds) 秒").font(.caption).foregroundStyle(.secondary) }
            DisclosureGroup("会话详情与风险原因", isExpanded: $showDetails) {
                VStack(spacing: 12) {
                    InfoRow(title: "位置", value: status.currentLocation ?? "未知")
                    InfoRow(title: "IP", value: status.currentIp ?? "未知")
                    InfoRow(title: "设备", value: [status.deviceType, status.browser].compactMap { $0 }.joined(separator: " · "))
                    InfoRow(title: "最近认证", value: "\(status.authAgeSeconds) 秒前")
                    InfoRow(title: "空闲时间", value: "\(status.idleSeconds) 秒")
                    ForEach(status.reasons, id: \.self) { Text($0).font(.caption).foregroundStyle(.secondary).frame(maxWidth: .infinity, alignment: .leading) }
                }.padding(.top, 12)
            }
            if status.requiresStepUp && !status.sessionFrozen {
                AsyncActionButton(title: "立即验证", isBusy: model.isBusy) { await model.requireSensitive(title: "提升会话安全等级") { await model.refreshSecurity() } }
            }
            if status.sessionFrozen { AsyncActionButton(title: "重新登录", isBusy: model.isBusy) { await model.logout() } }
        }
    }
    private var riskLabel: String { switch status.riskLevel { case "low": "低"; case "medium": "中"; case "high": "高"; default: status.riskLevel } }
}

struct SensitiveVerificationView: View {
    @Environment(AppModel.self) private var model
    let request: SensitiveRequest
    @State private var method = ""
    @State private var password = ""
    @State private var code = ""
    @State private var recovery = false
    @State private var resendAt = Date.distantPast
    private var methods: [String] { request.status.methods ?? [] }
    private var canVerify: Bool { switch method { case "password": !password.isEmpty; case "email-code", "totp": !code.isEmpty; case "passkey", "apple": true; default: false } }
    var body: some View {
        PageScroll {
            AppCard {
                CardHeader(title: request.title, subtitle: "为保护账号，请先完成身份验证。验证成功后会继续刚才的操作。", icon: "lock.shield")
                Picker("验证方式", selection: $method) { ForEach(methods, id: \.self) { Text(methodLabel($0)).tag($0) } }.pickerStyle(.menu)
                switch method {
                case "password": FormField(title: "当前密码", text: $password, secure: true, contentType: .password)
                case "email-code":
                    FormField(title: "邮箱验证码", text: $code, keyboard: .numberPad, contentType: .oneTimeCode)
                    CodeSendButton(email: nil, type: "sensitive-verification", resendAt: $resendAt)
                case "totp":
                    AdaptivePicker("验证器或恢复码", selection: $recovery) { Text("验证器").tag(false); Text("恢复码").tag(true) }
                    FormField(title: recovery ? "恢复码" : "6 位验证码", text: $code, keyboard: recovery ? .default : .numberPad, contentType: .oneTimeCode)
                case "passkey": Text("使用 Face ID、Touch ID 或设备密码，验证已绑定的 Passkey。").font(.subheadline).foregroundStyle(.secondary)
                case "apple": Text("重新验证当前绑定的 Apple 身份。").font(.subheadline).foregroundStyle(.secondary)
                default: Text("正在读取可用验证方式…").foregroundStyle(.secondary)
                }
                AsyncActionButton(title: "验证并继续", isBusy: model.isBusy, disabled: !canVerify) {
                    await model.verifySensitive(method: method, password: method == "password" ? password : nil, code: method == "totp" && recovery ? nil : code, recoveryCode: method == "totp" && recovery ? code : nil)
                }
            }
        }.navigationTitle("验证身份").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("取消") { model.cancelSensitive() } } }
            .onAppear { method = request.status.preferredMethod.flatMap { methods.contains($0) ? $0 : nil } ?? methods.first ?? "" }
    }
}
