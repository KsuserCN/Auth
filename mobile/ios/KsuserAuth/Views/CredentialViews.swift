import SwiftUI
import UIKit

struct PasskeysView: View {
    @Environment(\.locale) private var localizationLocale
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var newName = ""
    @State private var renameTarget: PasskeyListItem?
    @State private var renameName = ""
    @State private var deleteTarget: PasskeyListItem?
    @State private var detailTarget: PasskeyListItem?
    var body: some View {
        PageScroll {
            AppCard {
                CardHeader(title: L10n.text("用你的设备安全登录"), subtitle: L10n.text("Passkey 可用 Face ID、Touch ID 或外接安全密钥登录。"), icon: "person.badge.key.fill")
                FormField(title: L10n.text("新 Passkey 名称"), text: $newName)
                AsyncActionButton(title: L10n.text("添加 Passkey"), isBusy: model.isBusy, disabled: newName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) {
                    await model.addPasskey(name: newName)
                    if model.errorMessage == nil { newName = "" }
                }
            }
            if model.passkeys.isEmpty {
                ContentUnavailableView(L10n.text("还没有 Passkey"), systemImage: "key", description: Text(L10n.text("添加一个通行密钥，让登录更简单。")))
            }
            ForEach(model.passkeys) { key in
                AppCard {
                    HStack(alignment: .top) {
                        CardHeader(title: key.name, icon: "key.fill")
                        Spacer()
                        Menu {
                            Button(L10n.text("详情"), systemImage: "info.circle") { detailTarget = key }
                            Button(L10n.text("重命名"), systemImage: "pencil") { renameName = key.name; renameTarget = key }
                            Button(L10n.text("删除"), systemImage: "trash", role: .destructive) { deleteTarget = key }
                        } label: { Image(systemName: "ellipsis.circle").frame(minWidth: 44, minHeight: 44) }
                            .accessibilityLabel(L10n.format("管理 {0}", [String(describing: key.name)]))
                            .confirmationDialog(L10n.text("删除这个 Passkey？"), isPresented: Binding(get: { deleteTarget?.id == key.id }, set: { if !$0 { deleteTarget = nil } }), titleVisibility: .visible) {
                                Button(L10n.text("删除"), role: .destructive) {
                                    Task { await model.requireSensitive(title: "删除 Passkey") { await model.deletePasskey(id: key.id) } }
                                    deleteTarget = nil
                                }
                            } message: {
                                Text(L10n.text("删除后将无法再使用这个 Passkey 登录。"))
                            }
                    }
                    DateInfoRow(title: L10n.text("创建时间"), value: key.createdAt)
                    DateInfoRow(title: L10n.text("最近使用"), value: key.lastUsedAt)
                }
            }
        }.navigationTitle("Passkey").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L10n.text("完成")) { dismiss() } } }
            .refreshable { await model.refreshSecurity() }
            .sensitiveVerification()
            .alert(L10n.text("重命名 Passkey"), isPresented: Binding(get: { renameTarget != nil }, set: { if !$0 { renameTarget = nil } })) {
                TextField(L10n.text("名称"), text: $renameName)
                Button(L10n.text("保存")) {
                    if let key = renameTarget {
                        let name = renameName.trimmingCharacters(in: .whitespacesAndNewlines)
                        Task { await model.requireSensitive(title: "重命名 Passkey") { await model.renamePasskey(id: key.id, name: name) } }
                    }
                    renameTarget = nil
                }.disabled(renameName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                Button(L10n.text("取消"), role: .cancel) { renameTarget = nil }
            }
            .sheet(item: $detailTarget) { key in
                AppNavigationStack {
                    PageScroll {
                        AppCard {
                            InfoRow(title: L10n.text("名称"), value: key.name)
                            DateInfoRow(title: L10n.text("创建时间"), value: key.createdAt)
                            DateInfoRow(title: L10n.text("最近使用"), value: key.lastUsedAt)
                            InfoRow(title: L10n.text("连接方式"), value: key.transports ?? L10n.text("未知"))
                        }
                    }.navigationTitle(L10n.text("Passkey 详情")).toolbar { Button(L10n.text("完成")) { detailTarget = nil } }
                }
            }
    }

}

struct TOTPView: View {
    @Environment(\.locale) private var localizationLocale
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var code = ""
    @State private var disableConfirmation = false
    @State private var savedCodes = false
    @State private var showOtpauthUnavailable = false
    var body: some View {
        PageScroll {
            if let setup = model.totpSetup {
                AppCard {
                    CardHeader(title: L10n.text("添加到你的验证器"), subtitle: L10n.text("使用验证器扫描二维码，或手动输入密钥。"), icon: "number.square")
                    LocalQRView(value: setup.qrCodeUrl).frame(maxWidth: .infinity)
                    Button(L10n.text("通过 otpauth:// 快捷添加"), systemImage: "arrow.up.forward.app") {
                        openAuthenticator(using: setup.qrCodeUrl)
                    }
                    .frame(maxWidth: .infinity, minHeight: 44)
                    Button(L10n.text("复制 otpauth:// 添加链接"), systemImage: "doc.on.doc") {
                        UIPasteboard.general.string = setup.qrCodeUrl
                    }
                    .frame(maxWidth: .infinity, minHeight: 44)
                    Text(L10n.text("添加链接包含密钥，请只在信任的验证器中打开或分享。"))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Text(setup.secret).font(.system(.body, design: .monospaced)).textSelection(.enabled).frame(maxWidth: .infinity)
                    Button(L10n.text("复制密钥"), systemImage: "doc.on.doc") { UIPasteboard.general.string = setup.secret }.frame(minHeight: 44)
                }
                AppCard {
                    CardHeader(title: L10n.text("保存恢复码"), subtitle: L10n.text("丢失验证器时可用于登录。每个恢复码只能使用一次。"), icon: "key.horizontal")
                    codesList(setup.recoveryCodes)
                    Button(L10n.text("复制全部恢复码"), systemImage: "doc.on.doc") { UIPasteboard.general.string = setup.recoveryCodes.joined(separator: "\n") }.frame(minHeight: 44)
                    Toggle(L10n.text("我已安全保存这些恢复码"), isOn: $savedCodes).frame(minHeight: 44)
                    FormField(title: L10n.text("验证器的 6 位验证码"), text: $code, keyboard: .numberPad, contentType: .oneTimeCode)
                    AsyncActionButton(title: L10n.text("验证并启用"), isBusy: model.isBusy, disabled: code.count != 6 || !savedCodes) {
                        await model.confirmTOTP(code: code)
                        if model.errorMessage == nil { dismiss() }
                    }
                }
            } else {
                AppCard {
                    CardHeader(title: model.totpStatus.enabled ? L10n.text("验证器已启用") : L10n.text("启用验证器"), subtitle: L10n.text("使用支持 TOTP 的验证器应用，每次生成一个短期有效的验证码。"), icon: "number.square")
                    if model.totpStatus.enabled {
                        StatusPill(title: L10n.text("已启用"))
                        InfoRow(title: L10n.text("可用恢复码"), value: L10n.format("{0} 个", [String(describing: model.totpStatus.recoveryCodesCount)]))
                        Button(L10n.text("停用验证器"), role: .destructive) { disableConfirmation = true }
                            .frame(minHeight: 44)
                            .confirmationDialog(L10n.text("停用验证器？"), isPresented: $disableConfirmation, titleVisibility: .visible) {
                                Button("停用", role: .destructive) { Task { await model.requireSensitive(title: "停用验证器") { await model.disableTOTP() } } }
                            } message: {
                                Text(L10n.text("停用后，验证器及其恢复码将无法再用于双重验证。"))
                            }
                    } else {
                        AsyncActionButton(title: L10n.text("开始设置"), isBusy: model.isBusy) { await model.requireSensitive(title: "启用验证器") { await model.startTOTP() } }
                    }
                }
            }
        }.navigationTitle(L10n.text("验证器")).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L10n.text("完成")) { model.totpSetup = nil; dismiss() } } }
            .sensitiveVerification()
            .confirmationDialog(L10n.text("没有可打开此链接的验证器"), isPresented: $showOtpauthUnavailable, titleVisibility: .visible) {
                if let setup = model.totpSetup {
                    Button(L10n.text("复制 otpauth:// 添加链接")) { UIPasteboard.general.string = setup.qrCodeUrl }
                }
                Button(L10n.text("取消"), role: .cancel) { }
            } message: {
                Text(L10n.text("可以复制链接并在兼容的验证器中导入，也可以扫描上方二维码或手动输入密钥。"))
            }
            .onDisappear { model.totpSetup = nil; model.recoveryCodes = [] }
    }

    private func openAuthenticator(using value: String) {
        guard let components = URLComponents(string: value),
              components.scheme?.lowercased() == "otpauth",
              components.host?.lowercased() == "totp",
              components.queryItems?.contains(where: { $0.name == "secret" && !($0.value ?? "").isEmpty }) == true,
              let url = components.url else {
            showOtpauthUnavailable = true
            return
        }

        Task { @MainActor in
            if !(await UIApplication.shared.open(url)) {
                showOtpauthUnavailable = true
            }
        }
    }
}

struct RecoveryCodesView: View {
    @Environment(\.locale) private var localizationLocale
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var regenerate = false
    var body: some View {
        PageScroll {
            AppCard {
                CardHeader(title: L10n.text("你的恢复码"), subtitle: L10n.text("每个恢复码仅可使用一次，请保存在安全的位置。"), icon: "key.horizontal")
                if model.recoveryCodes.isEmpty { Text(L10n.text("暂无可用恢复码")).foregroundStyle(.secondary) }
                else { codesList(model.recoveryCodes) }
                Button(L10n.text("复制全部"), systemImage: "doc.on.doc") { UIPasteboard.general.string = model.recoveryCodes.joined(separator: "\n") }.frame(minHeight: 44).disabled(model.recoveryCodes.isEmpty)
                Button(L10n.text("重新生成恢复码"), role: .destructive) { regenerate = true }
                    .frame(minHeight: 44)
                    .confirmationDialog(L10n.text("重新生成恢复码？"), isPresented: $regenerate, titleVisibility: .visible) {
                        Button("重新生成", role: .destructive) { Task { await model.requireSensitive(title: "重新生成恢复码") { await model.regenerateRecoveryCodes() } } }
                    } message: {
                        Text(L10n.text("原有恢复码会立即失效，请保存新生成的恢复码。"))
                    }
            }
        }.navigationTitle(L10n.text("恢复码")).navigationBarTitleDisplayMode(.inline)
            .toolbar { Button(L10n.text("完成")) { model.recoveryCodes = []; dismiss() } }
            .sensitiveVerification()
            .onDisappear { model.recoveryCodes = [] }
    }
}

@ViewBuilder private func codesList(_ codes: [String]) -> some View {
    VStack(alignment: .leading, spacing: 9) {
        ForEach(Array(codes.enumerated()), id: \.offset) { index, code in
            HStack { Text("\(index + 1)").foregroundStyle(.secondary).frame(width: 24); Text(code).font(.system(.body, design: .monospaced)).textSelection(.enabled) }
        }
    }.padding(14).frame(maxWidth: .infinity, alignment: .leading).background(Brand.background, in: RoundedRectangle(cornerRadius: 12))
}

struct ChangeEmailView: View {
    @Environment(\.locale) private var localizationLocale
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var email = ""
    @State private var code = ""
    @State private var resendAt = Date.distantPast
    var body: some View {
        PageScroll {
            AppCard {
                CardHeader(title: L10n.text("绑定新的邮箱"), subtitle: L10n.text("验证新邮箱后，用它接收登录验证码和安全提醒。"), icon: "envelope")
                FormField(title: L10n.text("新邮箱"), text: $email, keyboard: .emailAddress, contentType: .emailAddress)
                FormField(title: L10n.text("验证码"), text: $code, keyboard: .numberPad, contentType: .oneTimeCode)
                CodeSendButton(email: email, type: "change-email", resendAt: $resendAt)
                AsyncActionButton(title: L10n.text("保存邮箱"), isBusy: model.isBusy, disabled: !validEmail(email) || code.isEmpty) {
                    await model.requireSensitive(title: "修改邮箱") { await model.changeEmail(newEmail: email, code: code); if model.errorMessage == nil { dismiss() } }
                }
            }
        }.navigationTitle(L10n.text("修改邮箱")).navigationBarTitleDisplayMode(.inline)
            .toolbar { Button(L10n.text("取消")) { dismiss() } }.sensitiveVerification()
    }
}

struct ChangePasswordView: View {
    @Environment(\.locale) private var localizationLocale
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var password = ""
    @State private var confirmation = ""
    var body: some View {
        PageScroll {
            AppCard {
                CardHeader(title: L10n.text("设置安全的新密码"), subtitle: model.passwordRequirement?.requirementMessage, icon: "lock.rotation")
                FormField(title: L10n.text("新密码"), text: $password, secure: true, contentType: .newPassword)
                FormField(title: L10n.text("确认新密码"), text: $confirmation, secure: true, contentType: .newPassword)
                if let error = model.passwordRequirement?.validationError(for: password), !password.isEmpty { Text(error).font(.caption).foregroundStyle(Brand.gold) }
                if !confirmation.isEmpty && password != confirmation { Text(L10n.text("两次输入的密码不一致")).font(.caption).foregroundStyle(Brand.danger) }
                AsyncActionButton(title: L10n.text("保存密码"), isBusy: model.isBusy, disabled: password.isEmpty || password != confirmation || model.passwordRequirement == nil || model.passwordRequirement?.validationError(for: password) != nil) {
                    await model.requireSensitive(title: "修改密码") { await model.changePassword(newPassword: password); if model.errorMessage == nil { dismiss() } }
                }
                if model.passwordRequirement == nil { Button(L10n.text("重试获取密码要求")) { Task { await model.loadPasswordRequirement() } } }
            }
        }.navigationTitle(model.user?.hasPassword == false ? L10n.text("设置密码") : L10n.text("修改密码")).navigationBarTitleDisplayMode(.inline)
            .toolbar { Button(L10n.text("取消")) { dismiss() } }.sensitiveVerification()
            .task { await model.loadPasswordRequirement() }
    }
}

struct DeleteAccountView: View {
    @Environment(\.locale) private var localizationLocale
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var understood = false
    @State private var finalConfirmation = false
    var body: some View {
        PageScroll {
            AppCard {
                CardHeader(title: L10n.text("注销前，请了解这些后果"), subtitle: L10n.text("账号注销后无法恢复，请确认已经备份需要的信息。"), icon: "exclamationmark.triangle")
                Label(L10n.text("个人资料及账号数据将被删除"), systemImage: "person.crop.circle.badge.minus")
                Label(L10n.text("所有设备登录和登录凭据将失效"), systemImage: "lock.slash")
                Label(L10n.text("绑定的 Apple 授权将被撤销"), systemImage: "apple.logo")
                Toggle(L10n.text("我已了解后果，确认注销"), isOn: $understood).frame(minHeight: 44)
                Button(L10n.text("继续注销"), role: .destructive) { finalConfirmation = true }
                    .buttonStyle(.borderedProminent)
                    .tint(Brand.danger)
                    .frame(minHeight: 50)
                    .disabled(!understood || model.isBusy)
                    .confirmationDialog(L10n.text("永久注销这个账号？"), isPresented: $finalConfirmation, titleVisibility: .visible) {
                        Button("永久注销账号", role: .destructive) { Task { await model.requireSensitive(title: "注销账号") { await model.deleteAccount(); if !model.isAuthenticated { dismiss() } } } }
                    } message: {
                        Text(L10n.text("此操作无法撤销。"))
                    }
            }
        }.navigationTitle(L10n.text("注销账号")).navigationBarTitleDisplayMode(.inline)
            .toolbar { Button(L10n.text("取消")) { dismiss() } }.sensitiveVerification()
    }
}
