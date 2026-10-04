import SwiftUI
import AuthenticationServices

struct AuthenticationView: View {
    @Environment(AppModel.self) private var model
    @AppStorage("agreementAccepted") private var agreementAccepted = false
    @State private var email = ""
    @State private var password = ""
    @State private var code = ""
    @State private var useCode = false
    @State private var showAbout = false
    @State private var showScanner = false
    @State private var resendAt = Date.distantPast
    var isLinking = false

    var body: some View {
        PageScroll {
            VStack(alignment: .leading, spacing: 18) {
                ZStack {
                    RoundedRectangle(cornerRadius: 24).fill(Brand.subtleGold).frame(width: 86, height: 86)
                    Image(systemName: "shield.lefthalf.filled").font(.system(size: 40, weight: .medium)).foregroundStyle(Brand.gold)
                }.accessibilityHidden(true)
                Text(isLinking ? "绑定已有账号" : "安全，从这里开始。")
                    .font(.largeTitle.weight(.bold)).fixedSize(horizontal: false, vertical: true)
                Text(isLinking ? "登录你已有的 Ksuser 账号，完成身份验证后绑定。" : "一个账号，连接你的数字生活。")
                    .font(.subheadline).foregroundStyle(.secondary)
            }.padding(.top, 22).padding(.bottom, 8)

            AppCard {
                AdaptivePicker("登录方式", selection: $useCode) { Text("密码登录").tag(false); Text("验证码登录").tag(true) }
                FormField(title: "邮箱", text: $email, keyboard: .emailAddress, contentType: .username)
                if useCode {
                    FormField(title: "验证码", text: $code, keyboard: .numberPad, contentType: .oneTimeCode)
                    CodeSendButton(email: email, type: "login", resendAt: $resendAt, allowed: agreementAccepted)
                } else { FormField(title: "密码", text: $password, secure: true, contentType: .password) }
                AsyncActionButton(title: "登录", isBusy: model.isBusy, disabled: !agreementAccepted || !validEmail(email) || (useCode ? code.isEmpty : password.isEmpty)) {
                    if useCode { await model.loginWithCode(email: email.trimmingCharacters(in: .whitespacesAndNewlines), code: code) }
                    else { await model.login(email: email.trimmingCharacters(in: .whitespacesAndNewlines), password: password) }
                    if isLinking, model.isAuthenticated { await model.bindPendingOAuth() }
                }.accessibilityIdentifier("loginButton")
                if !isLinking {
                    NavigationLink { RegistrationView() } label: { Text("还没有账号？注册") }.frame(maxWidth: .infinity, minHeight: 44)
                }
            }

            AgreementView(accepted: $agreementAccepted)

            AppCard {
                CardHeader(title: "快捷登录", subtitle: "使用设备上已有的安全凭据", icon: "key.fill")
                Button { Task { await model.loginWithPasskey(); if isLinking, model.isAuthenticated { await model.bindPendingOAuth() } } } label: {
                    LoginProviderLabel(title: "使用 Passkey", icon: "person.badge.key.fill")
                }.buttonStyle(LoginProviderButtonStyle()).disabled(!agreementAccepted || model.isBusy).accessibilityIdentifier("passkeyLoginButton")
                Button { Task { await model.loginWithQQ(); if isLinking, model.isAuthenticated { await model.bindPendingOAuth() } } } label: {
                    LoginProviderLabel(title: "使用 QQ 登录", imageAsset: "QQLogo")
                }.buttonStyle(LoginProviderButtonStyle()).disabled(!agreementAccepted || model.isBusy).accessibilityIdentifier("qqLoginButton")
                Button { Task { await model.loginWithApple(); if isLinking, model.isAuthenticated { await model.bindPendingOAuth() } } } label: {
                    LoginProviderLabel(title: "通过 Apple 登录", icon: "apple.logo")
                }.buttonStyle(LoginProviderButtonStyle()).disabled(!agreementAccepted || model.isBusy).accessibilityIdentifier("appleLoginButton")
            }

            if !isLinking {
                NavigationLink { AccountRecoveryEntryView() } label: { Label("无法登录？恢复账号", systemImage: "lifepreserver") }.frame(maxWidth: .infinity, minHeight: 44)
            }
        }
        .navigationTitle(isLinking ? "绑定账号" : "Ksuser 安全")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if !isLinking {
                Button("扫码", systemImage: "qrcode.viewfinder") { showScanner = true }
                Button("外观与关于", systemImage: "gearshape") { showAbout = true }.accessibilityIdentifier("aboutButton")
            }
        }
        .sheet(isPresented: $showAbout) { AppNavigationStack { AboutView() } }
        .sheet(isPresented: $showScanner) { ScannerSheet { value in showScanner = false; Task { await model.previewQRCode(value) } } }
    }
}

struct AgreementView: View {
    @Binding var accepted: Bool
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Toggle(isOn: $accepted) { Text("我已阅读并同意以下条款").font(.subheadline) }.toggleStyle(.switch).frame(minHeight: 44).accessibilityIdentifier("agreementToggle")
            HStack(spacing: 14) {
                Link("服务协议", destination: URL(string: "https://www.ksuser.cn/agreement/user.html")!)
                Link("隐私政策", destination: URL(string: "https://www.ksuser.cn/agreement/privacy.html")!)
            }.font(.caption)
            Link("第三方信息共享清单", destination: URL(string: "https://www.ksuser.cn/agreement/third-party-information-sharing.html")!).font(.caption)
        }.padding(.horizontal, 4)
    }
}

struct CodeSendButton: View {
    @Environment(AppModel.self) private var model
    let email: String?
    let type: String
    @Binding var resendAt: Date
    var allowed = true
    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            let remaining = max(0, Int(resendAt.timeIntervalSince(context.date).rounded(.up)))
            Button {
                Task {
                    await model.sendCode(email: email, type: type)
                    if model.errorMessage == nil { resendAt = Date().addingTimeInterval(60) }
                }
            } label: { Text(remaining > 0 ? "\(remaining) 秒后重发" : "发送验证码").frame(minHeight: 44) }
                .disabled(remaining > 0 || model.isBusy || !allowed || (email != nil && !validEmail(email!)))
        }
    }
}

struct RegistrationView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @AppStorage("agreementAccepted") private var agreementAccepted = false
    @State private var step = 0
    @State private var username = ""
    @State private var password = ""
    @State private var confirmation = ""
    @State private var email = ""
    @State private var code = ""
    @State private var resendAt = Date.distantPast
    private let titles = ["选择用户名", "设置密码", "确认密码", "输入邮箱", "输入验证码"]
    private var passwordError: String? { model.passwordRequirement?.validationError(for: password) }
    private var canContinue: Bool {
        switch step {
        case 0: validUsername(username)
        case 1: model.passwordRequirement != nil && passwordError == nil && !password.isEmpty
        case 2: confirmation == password && !confirmation.isEmpty
        case 3: validEmail(email) && agreementAccepted
        default: !code.isEmpty && agreementAccepted
        }
    }
    var body: some View {
        PageScroll {
            VStack(alignment: .leading, spacing: 12) {
                Text("创建你的账号").font(.largeTitle.weight(.bold))
                HStack { Text(titles[step]).font(.headline); Spacer(); Text("\(step + 1) / 5").font(.subheadline).foregroundStyle(.secondary) }
                ProgressView(value: Double(step + 1), total: 5).tint(Brand.gold).accessibilityLabel("注册第 \(step + 1) 步，共 5 步")
            }.padding(.vertical, 8)
            AppCard {
                switch step {
                case 0:
                    FormField(title: "用户名", text: $username, contentType: .nickname)
                    Text("3–20 个字符，支持汉字、字母、数字、下划线和连字符。").font(.caption).foregroundStyle(.secondary)
                case 1:
                    FormField(title: "设置密码", text: $password, secure: true, contentType: .newPassword)
                    if let rules = model.passwordRequirement {
                        Text(rules.requirementMessage ?? "请设置符合要求的安全密码").font(.caption).foregroundStyle(.secondary)
                        if let passwordError, !password.isEmpty { Label(passwordError, systemImage: "info.circle").font(.caption).foregroundStyle(Brand.gold) }
                    } else {
                        Text("正在获取密码要求…").foregroundStyle(.secondary)
                        Button("重试获取密码要求") { Task { await model.loadPasswordRequirement() } }
                    }
                case 2:
                    FormField(title: "确认密码", text: $confirmation, secure: true, contentType: .newPassword)
                    if !confirmation.isEmpty && confirmation != password { Text("两次输入的密码不一致").font(.caption).foregroundStyle(Brand.danger) }
                case 3:
                    FormField(title: "注册邮箱", text: $email, keyboard: .emailAddress, contentType: .emailAddress)
                    Text("我们会向这个邮箱发送验证码。").font(.caption).foregroundStyle(.secondary)
                default:
                    Text("验证码已发送至 \(email)").font(.subheadline).foregroundStyle(.secondary)
                    FormField(title: "邮箱验证码", text: $code, keyboard: .numberPad, contentType: .oneTimeCode)
                    CodeSendButton(email: email, type: "register", resendAt: $resendAt, allowed: agreementAccepted)
                }
                AsyncActionButton(title: step == 4 ? "注册并登录" : step == 3 ? "发送验证码并继续" : "继续", isBusy: model.isBusy, disabled: !canContinue) {
                    switch step {
                    case 0: if await model.checkUsername(username.trimmingCharacters(in: .whitespacesAndNewlines)) { step += 1 }
                    case 3:
                        await model.sendCode(email: email, type: "register")
                        if model.errorMessage == nil { resendAt = Date().addingTimeInterval(60); step += 1 }
                    case 4: await model.register(username: username.trimmingCharacters(in: .whitespacesAndNewlines), email: email.trimmingCharacters(in: .whitespacesAndNewlines), password: password, code: code)
                    default: step += 1
                    }
                }.accessibilityIdentifier("registrationContinue")
                if step > 0 { Button("上一步") { step -= 1 }.frame(maxWidth: .infinity, minHeight: 44) }
            }
            AgreementView(accepted: $agreementAccepted)
            Button("已有账号，返回登录") { dismiss() }.frame(maxWidth: .infinity, minHeight: 44)
        }.navigationTitle("注册").navigationBarTitleDisplayMode(.inline)
            .task { await model.loadPasswordRequirement() }
    }
}

struct MFAVerificationView: View {
    @Environment(AppModel.self) private var model
    let challenge: MFAChallenge
    @State private var recovery = false
    @State private var code = ""
    var body: some View {
        PageScroll {
            AppCard {
                CardHeader(title: "再确认一次，是你本人", subtitle: "账号已启用双重验证，请使用已绑定的验证方式。", icon: "lock.shield")
                if challenge.methods.contains("totp") || challenge.method == "totp" {
                    AdaptivePicker("验证方式", selection: $recovery) { Text("验证器").tag(false); Text("恢复码").tag(true) }
                    FormField(title: recovery ? "恢复码" : "6 位验证码", text: $code, keyboard: recovery ? .default : .numberPad, contentType: .oneTimeCode)
                    AsyncActionButton(title: "完成验证", isBusy: model.isBusy, disabled: code.isEmpty) { await model.verifyMFA(code: recovery ? nil : code, recoveryCode: recovery ? code : nil) }
                }
                if challenge.methods.contains("passkey") || challenge.method == "passkey" {
                    AsyncActionButton(title: "使用 Passkey 验证", isBusy: model.isBusy) { await model.verifyMFAPasskey() }
                }
                if !challenge.methods.contains("totp") && !challenge.methods.contains("passkey") {
                    Text("当前没有可用的本机双重验证方式。请返回并使用其他登录方式，或在已登录设备上配置验证器、生成账号恢复授权。").font(.subheadline).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
                }
            }
        }.navigationTitle("双重验证").toolbar { ToolbarItem(placement: .cancellationAction) { Button("取消") { model.cancelMFA() } } }
    }
}

struct PendingOAuthView: View {
    @Environment(AppModel.self) private var model
    let pending: PendingOAuth
    var body: some View {
        PageScroll {
            AppCard {
                CardHeader(title: "完成账号关联", subtitle: pending.message ?? "这个 \(methodLabel(pending.provider)) 身份尚未关联 Ksuser 账号。", icon: pending.provider == "apple" ? "apple.logo" : "person.crop.circle.badge.plus")
                NavigationLink { AuthenticationView(isLinking: true) } label: { Label("绑定已有 Ksuser 账号", systemImage: "link").frame(maxWidth: .infinity, minHeight: 50) }.buttonStyle(.bordered)
                Text("已有账号建议先绑定，方便保留资料和安全设置。").font(.caption).foregroundStyle(.secondary)
                if pending.provider == "apple" {
                    Divider()
                    Text("也可以创建新账号。无需设置密码，之后可随时补充其他登录方式。").font(.subheadline).foregroundStyle(.secondary)
                    AsyncActionButton(title: "创建新账号", isBusy: model.isBusy, disabled: !pending.canRegister) { await model.createPendingAccount() }
                    if pending.emailConflict { Text("这个邮箱已有账号，请先登录并绑定已有账号。").font(.caption).foregroundStyle(Brand.gold) }
                } else {
                    NavigationLink { RegistrationView() } label: { Text("注册并绑定 QQ").frame(maxWidth: .infinity, minHeight: 50) }.buttonStyle(GoldButtonStyle())
                }
            }
        }.navigationTitle("关联账号").toolbar { ToolbarItem(placement: .cancellationAction) { Button("取消") { model.cancelPendingOAuth() } } }
            .sensitiveVerification()
    }
}

struct AccountRecoveryEntryView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.openURL) private var openURL
    @State private var recoveryCode = ""
    var body: some View {
        PageScroll {
            AppCard {
                CardHeader(title: "通过已登录设备恢复", subtitle: "在已登录设备的安全页生成恢复授权，再输入恢复码或打开网页版继续。", icon: "lifepreserver")
                FormField(title: "账号恢复码", text: $recoveryCode)
                AsyncActionButton(title: "打开恢复授权", disabled: recoveryCode.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) {
                    openURL(accountRecoveryURL(code: recoveryCode.trimmingCharacters(in: .whitespacesAndNewlines), environment: model.environment))
                }
                Button("打开网页恢复") { openURL(accountRecoveryURL(environment: model.environment)) }.frame(minHeight: 44)
            }
        }.navigationTitle("恢复账号")
    }
}
