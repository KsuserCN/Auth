import SwiftUI
import UIKit

struct PasskeysView: View {
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
                CardHeader(title: "用你的设备安全登录", subtitle: "Passkey 使用 Face ID、Touch ID 或设备密码，无需记住密码。", icon: "person.badge.key.fill")
                FormField(title: "新 Passkey 名称", text: $newName)
                AsyncActionButton(title: "添加 Passkey", isBusy: model.isBusy, disabled: newName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) {
                    await model.requireSensitive(title: "添加 Passkey") { await model.addPasskey(name: newName); if model.errorMessage == nil { newName = "" } }
                }
            }
            if model.passkeys.isEmpty {
                ContentUnavailableView("还没有 Passkey", systemImage: "key", description: Text("添加一个通行密钥，让登录更简单。"))
            }
            ForEach(model.passkeys) { key in
                AppCard {
                    HStack(alignment: .top) {
                        CardHeader(title: key.name, icon: "key.fill")
                        Spacer()
                        Menu {
                            Button("详情", systemImage: "info.circle") { detailTarget = key }
                            Button("重命名", systemImage: "pencil") { renameName = key.name; renameTarget = key }
                            Button("删除", systemImage: "trash", role: .destructive) { deleteTarget = key }
                        } label: { Image(systemName: "ellipsis.circle").frame(minWidth: 44, minHeight: 44) }
                            .accessibilityLabel("管理 \(key.name)")
                            .confirmationDialog("删除这个 Passkey？", isPresented: Binding(get: { deleteTarget?.id == key.id }, set: { if !$0 { deleteTarget = nil } }), titleVisibility: .visible) {
                                Button("删除", role: .destructive) {
                                    Task { await model.requireSensitive(title: "删除 Passkey") { await model.deletePasskey(id: key.id) } }
                                    deleteTarget = nil
                                }
                            } message: {
                                Text("删除后将无法再使用这个 Passkey 登录。")
                            }
                    }
                    DateInfoRow(title: "创建时间", value: key.createdAt)
                    DateInfoRow(title: "最近使用", value: key.lastUsedAt)
                }
            }
        }.navigationTitle("Passkey").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("完成") { dismiss() } } }
            .refreshable { await model.refreshSecurity() }
            .sensitiveVerification()
            .alert("重命名 Passkey", isPresented: Binding(get: { renameTarget != nil }, set: { if !$0 { renameTarget = nil } })) {
                TextField("名称", text: $renameName)
                Button("保存") {
                    if let key = renameTarget {
                        let name = renameName.trimmingCharacters(in: .whitespacesAndNewlines)
                        Task { await model.requireSensitive(title: "重命名 Passkey") { await model.renamePasskey(id: key.id, name: name) } }
                    }
                    renameTarget = nil
                }.disabled(renameName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                Button("取消", role: .cancel) { renameTarget = nil }
            }
            .sheet(item: $detailTarget) { key in
                AppNavigationStack {
                    PageScroll {
                        AppCard {
                            InfoRow(title: "名称", value: key.name)
                            DateInfoRow(title: "创建时间", value: key.createdAt)
                            DateInfoRow(title: "最近使用", value: key.lastUsedAt)
                            InfoRow(title: "连接方式", value: key.transports ?? "未知")
                        }
                    }.navigationTitle("Passkey 详情").toolbar { Button("完成") { detailTarget = nil } }
                }
            }
    }
}

struct TOTPView: View {
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
                    CardHeader(title: "添加到你的验证器", subtitle: "使用验证器扫描二维码，或手动输入密钥。", icon: "number.square")
                    LocalQRView(value: setup.qrCodeUrl).frame(maxWidth: .infinity)
                    Button("通过 otpauth:// 快捷添加", systemImage: "arrow.up.forward.app") {
                        openAuthenticator(using: setup.qrCodeUrl)
                    }
                    .frame(maxWidth: .infinity, minHeight: 44)
                    Button("复制 otpauth:// 添加链接", systemImage: "doc.on.doc") {
                        UIPasteboard.general.string = setup.qrCodeUrl
                    }
                    .frame(maxWidth: .infinity, minHeight: 44)
                    Text("添加链接包含密钥，请只在信任的验证器中打开或分享。")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Text(setup.secret).font(.system(.body, design: .monospaced)).textSelection(.enabled).frame(maxWidth: .infinity)
                    Button("复制密钥", systemImage: "doc.on.doc") { UIPasteboard.general.string = setup.secret }.frame(minHeight: 44)
                }
                AppCard {
                    CardHeader(title: "保存恢复码", subtitle: "丢失验证器时可用于登录。每个恢复码只能使用一次。", icon: "key.horizontal")
                    codesList(setup.recoveryCodes)
                    Button("复制全部恢复码", systemImage: "doc.on.doc") { UIPasteboard.general.string = setup.recoveryCodes.joined(separator: "\n") }.frame(minHeight: 44)
                    Toggle("我已安全保存这些恢复码", isOn: $savedCodes).frame(minHeight: 44)
                    FormField(title: "验证器的 6 位验证码", text: $code, keyboard: .numberPad, contentType: .oneTimeCode)
                    AsyncActionButton(title: "验证并启用", isBusy: model.isBusy, disabled: code.count != 6 || !savedCodes) {
                        await model.confirmTOTP(code: code)
                        if model.errorMessage == nil { dismiss() }
                    }
                }
            } else {
                AppCard {
                    CardHeader(title: model.totpStatus.enabled ? "验证器已启用" : "启用验证器", subtitle: "使用支持 TOTP 的验证器应用，每次生成一个短期有效的验证码。", icon: "number.square")
                    if model.totpStatus.enabled {
                        StatusPill(title: "已启用")
                        InfoRow(title: "可用恢复码", value: "\(model.totpStatus.recoveryCodesCount) 个")
                        Button("停用验证器", role: .destructive) { disableConfirmation = true }
                            .frame(minHeight: 44)
                            .confirmationDialog("停用验证器？", isPresented: $disableConfirmation, titleVisibility: .visible) {
                                Button("停用", role: .destructive) { Task { await model.requireSensitive(title: "停用验证器") { await model.disableTOTP() } } }
                            } message: {
                                Text("停用后，验证器及其恢复码将无法再用于双重验证。")
                            }
                    } else {
                        AsyncActionButton(title: "开始设置", isBusy: model.isBusy) { await model.requireSensitive(title: "启用验证器") { await model.startTOTP() } }
                    }
                }
            }
        }.navigationTitle("验证器").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("完成") { model.totpSetup = nil; dismiss() } } }
            .sensitiveVerification()
            .confirmationDialog("没有可打开此链接的验证器", isPresented: $showOtpauthUnavailable, titleVisibility: .visible) {
                if let setup = model.totpSetup {
                    Button("复制 otpauth:// 添加链接") { UIPasteboard.general.string = setup.qrCodeUrl }
                }
                Button("取消", role: .cancel) { }
            } message: {
                Text("可以复制链接并在兼容的验证器中导入，也可以扫描上方二维码或手动输入密钥。")
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
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var regenerate = false
    var body: some View {
        PageScroll {
            AppCard {
                CardHeader(title: "你的恢复码", subtitle: "每个恢复码仅可使用一次，请保存在安全的位置。", icon: "key.horizontal")
                if model.recoveryCodes.isEmpty { Text("暂无可用恢复码").foregroundStyle(.secondary) }
                else { codesList(model.recoveryCodes) }
                Button("复制全部", systemImage: "doc.on.doc") { UIPasteboard.general.string = model.recoveryCodes.joined(separator: "\n") }.frame(minHeight: 44).disabled(model.recoveryCodes.isEmpty)
                Button("重新生成恢复码", role: .destructive) { regenerate = true }
                    .frame(minHeight: 44)
                    .confirmationDialog("重新生成恢复码？", isPresented: $regenerate, titleVisibility: .visible) {
                        Button("重新生成", role: .destructive) { Task { await model.requireSensitive(title: "重新生成恢复码") { await model.regenerateRecoveryCodes() } } }
                    } message: {
                        Text("原有恢复码会立即失效，请保存新生成的恢复码。")
                    }
            }
        }.navigationTitle("恢复码").navigationBarTitleDisplayMode(.inline)
            .toolbar { Button("完成") { model.recoveryCodes = []; dismiss() } }
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
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var email = ""
    @State private var code = ""
    @State private var resendAt = Date.distantPast
    var body: some View {
        PageScroll {
            AppCard {
                CardHeader(title: "绑定新的邮箱", subtitle: "验证新邮箱后，用它接收登录验证码和安全提醒。", icon: "envelope")
                FormField(title: "新邮箱", text: $email, keyboard: .emailAddress, contentType: .emailAddress)
                FormField(title: "验证码", text: $code, keyboard: .numberPad, contentType: .oneTimeCode)
                CodeSendButton(email: email, type: "change-email", resendAt: $resendAt)
                AsyncActionButton(title: "保存邮箱", isBusy: model.isBusy, disabled: !validEmail(email) || code.isEmpty) {
                    await model.requireSensitive(title: "修改邮箱") { await model.changeEmail(newEmail: email, code: code); if model.errorMessage == nil { dismiss() } }
                }
            }
        }.navigationTitle("修改邮箱").navigationBarTitleDisplayMode(.inline)
            .toolbar { Button("取消") { dismiss() } }.sensitiveVerification()
    }
}

struct ChangePasswordView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var password = ""
    @State private var confirmation = ""
    var body: some View {
        PageScroll {
            AppCard {
                CardHeader(title: "设置安全的新密码", subtitle: model.passwordRequirement?.requirementMessage, icon: "lock.rotation")
                FormField(title: "新密码", text: $password, secure: true, contentType: .newPassword)
                FormField(title: "确认新密码", text: $confirmation, secure: true, contentType: .newPassword)
                if let error = model.passwordRequirement?.validationError(for: password), !password.isEmpty { Text(error).font(.caption).foregroundStyle(Brand.gold) }
                if !confirmation.isEmpty && password != confirmation { Text("两次输入的密码不一致").font(.caption).foregroundStyle(Brand.danger) }
                AsyncActionButton(title: "保存密码", isBusy: model.isBusy, disabled: password.isEmpty || password != confirmation || model.passwordRequirement == nil || model.passwordRequirement?.validationError(for: password) != nil) {
                    await model.requireSensitive(title: "修改密码") { await model.changePassword(newPassword: password); if model.errorMessage == nil { dismiss() } }
                }
                if model.passwordRequirement == nil { Button("重试获取密码要求") { Task { await model.loadPasswordRequirement() } } }
            }
        }.navigationTitle(model.user?.hasPassword == false ? "设置密码" : "修改密码").navigationBarTitleDisplayMode(.inline)
            .toolbar { Button("取消") { dismiss() } }.sensitiveVerification()
            .task { await model.loadPasswordRequirement() }
    }
}

struct DeleteAccountView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var understood = false
    @State private var finalConfirmation = false
    var body: some View {
        PageScroll {
            AppCard {
                CardHeader(title: "注销前，请了解这些后果", subtitle: "账号注销后无法恢复，请确认已经备份需要的信息。", icon: "exclamationmark.triangle")
                Label("个人资料及账号数据将被删除", systemImage: "person.crop.circle.badge.minus")
                Label("所有设备登录和登录凭据将失效", systemImage: "lock.slash")
                Label("绑定的 Apple 授权将被撤销", systemImage: "apple.logo")
                Toggle("我已了解后果，确认注销", isOn: $understood).frame(minHeight: 44)
                Button("继续注销", role: .destructive) { finalConfirmation = true }
                    .buttonStyle(.borderedProminent)
                    .tint(Brand.danger)
                    .frame(minHeight: 50)
                    .disabled(!understood || model.isBusy)
                    .confirmationDialog("永久注销这个账号？", isPresented: $finalConfirmation, titleVisibility: .visible) {
                        Button("永久注销账号", role: .destructive) { Task { await model.requireSensitive(title: "注销账号") { await model.deleteAccount(); if !model.isAuthenticated { dismiss() } } } }
                    } message: {
                        Text("此操作无法撤销。")
                    }
            }
        }.navigationTitle("注销账号").navigationBarTitleDisplayMode(.inline)
            .toolbar { Button("取消") { dismiss() } }.sensitiveVerification()
    }
}
