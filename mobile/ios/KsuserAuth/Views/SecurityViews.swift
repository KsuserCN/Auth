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
    @State private var unbindQQ = false
    var body: some View {
        PageScroll(spacing: 16) {
            if let risk = model.adaptiveStatus { RiskStatusCard(status: risk) }
            SecuritySection(title: "验证方式", icon: "key.fill") {
                ActionRow(title: "Passkey", icon: "person.badge.key.fill", subtitle: "\(model.passkeys.count) 个通行密钥") { sheet = .passkeys }
                sectionDivider
                ActionRow(title: "验证器", icon: "number.square", subtitle: model.totpStatus.enabled ? "已启用" : "未启用") { protected(.totp) }
                if model.totpStatus.enabled {
                    sectionDivider
                    ActionRow(title: "TOTP 恢复码", icon: "key.horizontal", subtitle: "剩余 \(model.totpStatus.recoveryCodesCount) 个") {
                        Task { await model.requireSensitive(title: "查看恢复码") { await model.showRecoveryCodes(); if model.errorMessage == nil { present(.recoveryCodes) } } }
                    }
                }
                sectionDivider
                if model.appleBound {
                    ActionRow(title: "Apple 登录", icon: "apple.logo", subtitle: "已绑定") { unbindApple = true }
                        .confirmationDialog("解除 Apple 绑定？", isPresented: $unbindApple, titleVisibility: .visible) {
                            Button("解除绑定", role: .destructive) { Task { await model.requireSensitive(title: "解除 Apple 绑定") { await model.unbindApple() } } }
                        } message: { Text("解绑前请确保已经设置密码或其他可用的登录方式。") }
                } else {
                    ActionRow(title: "Apple 登录", icon: "apple.logo", subtitle: "未绑定") { Task { await model.requireSensitive(title: "绑定 Apple") { await model.bindApple() } } }
                }
                sectionDivider
                if model.qqBound {
                    ActionRow(title: "QQ 登录", icon: "person.crop.circle", subtitle: "已绑定") { unbindQQ = true }
                        .accessibilityIdentifier("qqBindingRow")
                        .confirmationDialog("解除 QQ 绑定？", isPresented: $unbindQQ, titleVisibility: .visible) {
                            Button("解除绑定", role: .destructive) { Task { await model.unbindQQ() } }
                        } message: { Text("解绑前请确保已经设置密码或其他可用的登录方式。") }
                } else {
                    ActionRow(title: "QQ 登录", icon: "person.crop.circle", subtitle: "未绑定") { Task { await model.bindQQ() } }
                        .accessibilityIdentifier("qqBindingRow")
                }
            }
            SecuritySection(title: "登录保护", icon: "lock.shield") {
                settingToggle("双重验证", icon: "checkmark.shield", field: "mfaEnabled", value: model.user?.settings?.mfaEnabled ?? false)
                sectionDivider
                settingToggle("异地登录检测", icon: "location.magnifyingglass", field: "detectUnusualLogin", value: model.user?.settings?.detectUnusualLogin ?? true)
                sectionDivider
                settingToggle("敏感操作邮件提醒", icon: "envelope.badge", field: "notifySensitiveActionEmail", value: model.user?.settings?.notifySensitiveActionEmail ?? true)
                sectionDivider
                settingToggle("订阅动态邮件", icon: "envelope", field: "subscribeNewsEmail", value: model.user?.settings?.subscribeNewsEmail ?? false)
                sectionDivider
                preference("首选双重验证", icon: "checkmark.shield", field: "preferredMfaMethod", selected: model.user?.settings?.preferredMfaMethod ?? "totp", methods: ["totp", "passkey"])
                sectionDivider
                preference("首选敏感验证", icon: "lock.shield", field: "preferredSensitiveMethod", selected: model.user?.settings?.preferredSensitiveMethod ?? (model.user?.hasPassword == false ? "apple" : "password"), methods: availableSensitiveMethods)
            }
            SecuritySection(title: "账号与会话", icon: "person.crop.circle") {
                ActionRow(title: model.user?.email.isEmpty == true ? "绑定邮箱" : "修改邮箱", icon: "envelope", subtitle: model.user?.email) { protected(.email) }
                sectionDivider
                ActionRow(title: model.user?.hasPassword == false ? "设置密码" : "修改密码", icon: "lock.rotation") { protected(.password) }
                sectionDivider
                ActionRow(title: "退出当前账号", icon: "rectangle.portrait.and.arrow.right") { logoutCurrent = true }
                    .accessibilityIdentifier("logoutCurrentRow")
                    .confirmationDialog("退出当前账号？", isPresented: $logoutCurrent, titleVisibility: .visible) {
                        Button("退出登录", role: .destructive) { Task { await model.logout() } }
                            .accessibilityIdentifier("confirmLogoutCurrent")
                    }
                sectionDivider
                ActionRow(title: "退出所有设备", icon: "power", destructive: true) { logoutAll = true }
                    .accessibilityIdentifier("logoutAllRow")
                    .confirmationDialog("退出所有设备？", isPresented: $logoutAll, titleVisibility: .visible) {
                        Button("退出所有设备", role: .destructive) { Task { await model.requireSensitive(title: "退出所有设备") { await model.logout(allDevices: true) } } }
                            .accessibilityIdentifier("confirmLogoutAll")
                    } message: { Text("所有登录会话都会被撤销，包括这台设备。") }
            }
            SecuritySection(title: "恢复与注销", icon: "lifepreserver") {
                ActionRow(title: "生成恢复授权", icon: "qrcode", subtitle: "供其他设备找回账号") {
                    Task { await model.requireSensitive(title: "生成账号恢复授权") { await model.generateRecoveryTicket(); if model.errorMessage == nil { present(.recovery) } } }
                }
                sectionDivider
                ActionRow(title: "注销我的账号", icon: "trash", subtitle: "永久删除，无法恢复", destructive: true) { protected(.deleteAccount) }
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
    private var sectionDivider: some View { Divider().padding(.leading, 37) }
    private func settingToggle(_ title: String, icon: String, field: String, value: Bool) -> some View {
        Toggle(isOn: Binding(get: { value }, set: { newValue in Task { await model.updateSetting(field: field, value: newValue) } })) {
            Label(title, systemImage: icon).labelStyle(SecurityRowLabelStyle())
        }
        .tint(Brand.gold).disabled(model.isBusy).frame(minHeight: 50)
    }
    private func preference(_ title: String, icon: String, field: String, selected: String, methods: [String]) -> some View {
        Menu {
            ForEach(methods, id: \.self) { choice in
                Button {
                    Task { await model.updateSetting(field: field, stringValue: choice) }
                } label: {
                    if choice == selected { Label(methodLabel(choice), systemImage: "checkmark") }
                    else { Text(methodLabel(choice)) }
                }
            }
        } label: {
            HStack(spacing: 8) {
                Label(title, systemImage: icon).labelStyle(SecurityRowLabelStyle())
                    .foregroundStyle(.primary)
                Spacer(minLength: 0)
                Text(methodLabel(selected)).font(.subheadline).foregroundStyle(.secondary)
                    .lineLimit(1)
                Image(systemName: "chevron.up.chevron.down").font(.caption2.weight(.semibold)).foregroundStyle(.tertiary)
            }
            .contentShape(Rectangle())
        }
        .disabled(model.isBusy).frame(minHeight: 50)
    }
}

private struct SecuritySection<Content: View>: View {
    let title: String
    let icon: String
    let content: Content

    init(title: String, icon: String, @ViewBuilder content: () -> Content) {
        self.title = title
        self.icon = icon
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(title, systemImage: icon)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.secondary)
                .padding(.horizontal, 4)
            VStack(spacing: 0) { content }
                .padding(.horizontal, 15).padding(.vertical, 4)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Brand.card, in: RoundedRectangle(cornerRadius: 18))
        }
    }
}

private struct SecurityRowLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: 13) {
            configuration.icon.frame(width: 24).foregroundStyle(Brand.gold)
            configuration.title.font(.body)
        }
    }
}

struct RiskStatusCard: View {
    @Environment(AppModel.self) private var model
    let status: AdaptiveAuthStatus
    @State private var showDetails = false
    private var title: String { status.sessionFrozen ? "当前会话已冻结" : status.requiresStepUp ? "需要再次验证身份" : "当前会话安全状态" }
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                Image(systemName: status.sessionFrozen ? "exclamationmark.shield.fill" : "checkmark.shield.fill")
                    .font(.system(size: 19, weight: .medium)).foregroundStyle(status.sessionFrozen ? Brand.danger : Brand.success)
                    .frame(width: 40, height: 40)
                    .background((status.sessionFrozen ? Brand.danger : Brand.success).opacity(0.10), in: RoundedRectangle(cornerRadius: 12))
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 3) {
                    Text(title).font(.headline)
                    Text(status.recommendedAction.isEmpty ? "持续检查登录环境与近期活动" : status.recommendedAction)
                        .font(.caption).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
            HStack(spacing: 8) {
                StatusPill(title: "风险：\(riskLabel)", positive: status.riskLevel == "low")
                if status.sensitiveVerified { StatusPill(title: "已验证") }
                Spacer(minLength: 0)
                Text("\(status.riskScore) / 100").font(.caption.monospacedDigit()).foregroundStyle(.secondary)
            }
            if let alert = status.alertMessage, !alert.isEmpty {
                Label(alert, systemImage: "exclamationmark.triangle.fill").font(.subheadline).foregroundStyle(Brand.gold).fixedSize(horizontal: false, vertical: true)
            }
            DisclosureGroup("会话详情与风险原因", isExpanded: $showDetails) {
                VStack(spacing: 10) {
                    InfoRow(title: "位置", value: status.currentLocation ?? "未知")
                    InfoRow(title: "IP", value: status.currentIp ?? "未知")
                    InfoRow(title: "设备", value: [status.deviceType, status.browser].compactMap { $0 }.joined(separator: " · "))
                    InfoRow(title: "最近认证", value: "\(status.authAgeSeconds) 秒前")
                    InfoRow(title: "空闲时间", value: "\(status.idleSeconds) 秒")
                    if status.sensitiveVerified { InfoRow(title: "敏感验证有效期", value: "\(status.sensitiveVerificationRemainingSeconds) 秒") }
                    ForEach(status.reasons, id: \.self) { Text($0).font(.caption).foregroundStyle(.secondary).frame(maxWidth: .infinity, alignment: .leading) }
                }.padding(.top, 10)
            }
            .font(.subheadline)
            .tint(Brand.gold)
            if status.requiresStepUp && !status.sessionFrozen {
                AsyncActionButton(title: "立即验证", isBusy: model.isBusy) { await model.requireSensitive(title: "提升会话安全等级") { await model.refreshSecurity() } }
            }
            if status.sessionFrozen { AsyncActionButton(title: "重新登录", isBusy: model.isBusy) { await model.logout() } }
        }
        .padding(16).frame(maxWidth: .infinity, alignment: .leading)
        .background(Brand.card, in: RoundedRectangle(cornerRadius: 18))
    }
    private var riskLabel: String { switch status.riskLevel { case "low": "低"; case "medium": "中"; case "high": "高"; default: status.riskLevel } }
}

struct SensitiveVerificationView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    let request: SensitiveRequest
    @State private var method = ""
    @State private var password = ""
    @State private var code = ""
    @State private var recovery = false
    @State private var resendAt = Date.distantPast
    private var methods: [String] { request.status.methods ?? [] }
    private let columns = [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)]
    private var canVerify: Bool { switch method { case "password": !password.isEmpty; case "email-code", "totp": !code.isEmpty; case "passkey", "apple": true; default: false } }
    var body: some View {
        PageScroll(spacing: 16) {
            HStack(alignment: .center, spacing: 13) {
                Image(systemName: "lock.shield.fill")
                    .font(.system(size: 22, weight: .medium)).foregroundStyle(Brand.gold)
                    .frame(width: 46, height: 46)
                    .background(Brand.subtleGold, in: RoundedRectangle(cornerRadius: 14))
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 3) {
                    Text(request.title).font(.title3.weight(.semibold))
                    Text("验证成功后继续当前操作").font(.subheadline).foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 2)

            VStack(alignment: .leading, spacing: 10) {
                Text("选择验证方式").font(.subheadline.weight(.semibold)).foregroundStyle(.secondary)
                if dynamicTypeSize.isAccessibilitySize {
                    Picker("验证方式", selection: $method) {
                        ForEach(methods, id: \.self) { Text(methodLabel($0)).tag($0) }
                    }
                    .pickerStyle(.menu).frame(minHeight: 44)
                } else {
                    LazyVGrid(columns: columns, spacing: 8) {
                        ForEach(methods, id: \.self) { option in
                            methodButton(option)
                        }
                    }
                }
            }
            .padding(15).frame(maxWidth: .infinity, alignment: .leading)
            .background(Brand.card, in: RoundedRectangle(cornerRadius: 18))

            VStack(alignment: .leading, spacing: 12) {
                Text("完成验证").font(.subheadline.weight(.semibold)).foregroundStyle(.secondary)
                switch method {
                case "password": FormField(title: "当前密码", text: $password, secure: true, contentType: .password)
                case "email-code":
                    FormField(title: "邮箱验证码", text: $code, keyboard: .numberPad, contentType: .oneTimeCode)
                    CodeSendButton(email: nil, type: "sensitive-verification", resendAt: $resendAt)
                case "totp":
                    AdaptivePicker("验证器或恢复码", selection: $recovery) { Text("验证器").tag(false); Text("恢复码").tag(true) }
                    FormField(title: recovery ? "恢复码" : "6 位验证码", text: $code, keyboard: recovery ? .default : .numberPad, contentType: .oneTimeCode)
                case "passkey": verificationHint("person.badge.key.fill", "使用 Face ID、Touch ID 或设备密码验证 Passkey")
                case "apple": verificationHint("apple.logo", "重新验证已绑定的 Apple 身份")
                default: Text("正在读取可用验证方式…").font(.subheadline).foregroundStyle(.secondary)
                }
            }
            .padding(15).frame(maxWidth: .infinity, alignment: .leading)
            .background(Brand.card, in: RoundedRectangle(cornerRadius: 18))
        }.navigationTitle("验证身份").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("取消") { model.cancelSensitive() } } }
            .onAppear { method = request.status.preferredMethod.flatMap { methods.contains($0) ? $0 : nil } ?? methods.first ?? "" }
            .onChange(of: method) { _, _ in password = ""; code = ""; recovery = false }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                AsyncActionButton(title: "验证并继续", isBusy: model.isBusy, disabled: !canVerify) {
                    await model.verifySensitive(method: method, password: method == "password" ? password : nil, code: method == "totp" && recovery ? nil : code, recoveryCode: method == "totp" && recovery ? code : nil)
                }
                .padding(.horizontal, 20).padding(.top, 12).padding(.bottom, 8)
                .frame(maxWidth: 760)
                .frame(maxWidth: .infinity)
                .background(.regularMaterial)
            }
            .presentationDetents(dynamicTypeSize.isAccessibilitySize ? [.large] : [.height(620), .large])
    }

    private func methodButton(_ option: String) -> some View {
        let selected = method == option
        return Button { method = option } label: {
            HStack(spacing: 8) {
                Image(systemName: methodIcon(option)).frame(width: 18)
                Text(methodLabel(option)).lineLimit(1).minimumScaleFactor(0.85)
                Spacer(minLength: 0)
                if selected { Image(systemName: "checkmark.circle.fill").accessibilityHidden(true) }
            }
            .font(.subheadline.weight(selected ? .semibold : .medium))
            .foregroundStyle(selected ? Brand.gold : Color.primary)
            .padding(.horizontal, 12).frame(minHeight: 46)
            .background(selected ? Brand.subtleGold : Brand.background, in: RoundedRectangle(cornerRadius: 12))
            .overlay { RoundedRectangle(cornerRadius: 12).strokeBorder(selected ? Brand.gold.opacity(0.45) : Color.clear, lineWidth: 1) }
            .contentShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
        .accessibilityIdentifier("sensitiveMethod-\(option)")
    }

    private func verificationHint(_ icon: String, _ message: String) -> some View {
        Label(message, systemImage: icon)
            .font(.subheadline).foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(Brand.background, in: RoundedRectangle(cornerRadius: 12))
    }

    private func methodIcon(_ value: String) -> String {
        switch value {
        case "password": "lock.fill"
        case "email-code": "envelope.fill"
        case "totp": "number.square.fill"
        case "passkey": "person.badge.key.fill"
        case "apple": "apple.logo"
        default: "checkmark.shield"
        }
    }
}
