import SwiftUI
import AuthenticationServices

struct AuthenticationView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @AppStorage("agreementAccepted") private var agreementAccepted = false
    @State private var email = ""
    @State private var password = ""
    @State private var code = ""
    @State private var useCode = false
    @State private var showAbout = false
    @State private var showScanner = false
    @State private var resendAt = Date.distantPast
    @FocusState private var focusedField: LoginField?
    var isLinking = false

    private var canLogin: Bool {
        agreementAccepted && validEmail(email) && !(useCode ? code : password).isEmpty
    }

    var body: some View {
        PageScroll(maxWidth: 460, spacing: 18) {
            header

            VStack(alignment: .leading, spacing: 16) {
                LoginMethodPicker(useCode: $useCode).disabled(model.isBusy)
                VStack(spacing: 16) {
                    LoginInputField(title: "邮箱", placeholder: "输入你的邮箱", icon: "envelope", text: $email,
                                    field: .email, focus: $focusedField, keyboard: .emailAddress, contentType: .username) {
                        focusedField = useCode ? .code : .password
                    }
                    if useCode {
                        let layout = dynamicTypeSize.isAccessibilitySize
                            ? AnyLayout(VStackLayout(alignment: .leading, spacing: 8))
                            : AnyLayout(HStackLayout(alignment: .bottom, spacing: 10))
                        layout {
                            LoginInputField(title: "验证码", placeholder: "输入验证码", icon: "number", text: $code,
                                            field: .code, focus: $focusedField, keyboard: .numberPad, contentType: .oneTimeCode, submitLabel: .go) {
                                Task { await authenticate() }
                            }
                            CodeSendButton(email: email, type: "login", resendAt: $resendAt, allowed: agreementAccepted)
                                .font(.caption.weight(.semibold)).padding(.horizontal, 12).padding(.vertical, 5)
                                .fixedSize(horizontal: true, vertical: false)
                                .background(Brand.subtleGold, in: RoundedRectangle(cornerRadius: 14))
                                .accessibilityIdentifier("sendLoginCodeButton")
                        }
                    } else {
                        LoginInputField(title: "密码", placeholder: "输入你的密码", icon: "lock", text: $password,
                                        field: .password, focus: $focusedField, secure: true, contentType: .password, submitLabel: .go) {
                            Task { await authenticate() }
                        }
                    }
                }.disabled(model.isBusy)
                AsyncActionButton(title: isLinking ? "登录并绑定" : "登录", isBusy: model.isBusy, disabled: !canLogin) {
                    await authenticate()
                }.accessibilityIdentifier("loginButton")
                LoginAgreementView(accepted: $agreementAccepted)
            }
            .padding(20).background(Brand.card, in: RoundedRectangle(cornerRadius: 24))
            .overlay { RoundedRectangle(cornerRadius: 24).strokeBorder(Color.primary.opacity(0.04), lineWidth: 1) }

            VStack(spacing: 14) {
                HStack(spacing: 14) {
                    Rectangle().fill(Color.primary.opacity(0.08)).frame(height: 1)
                    Text("其他登录方式").font(.caption).foregroundStyle(.secondary).fixedSize()
                    Rectangle().fill(Color.primary.opacity(0.08)).frame(height: 1)
                }.accessibilityHidden(true)
                AsyncLoginProviderButton(title: "使用 Passkey 登录", icon: "person.badge.key.fill", disabled: !agreementAccepted || model.isBusy) {
                    focusedField = nil
                    await model.loginWithPasskey(); if isLinking, model.isAuthenticated { await model.bindPendingOAuth() }
                }.accessibilityIdentifier("passkeyLoginButton")
                let layout = dynamicTypeSize.isAccessibilitySize
                    ? AnyLayout(VStackLayout(spacing: 12)) : AnyLayout(HStackLayout(spacing: 12))
                layout {
                    AsyncLoginProviderButton(title: "Apple", icon: "apple.logo", outlined: true, disabled: !agreementAccepted || model.isBusy) {
                        focusedField = nil
                        await model.loginWithApple(); if isLinking, model.isAuthenticated { await model.bindPendingOAuth() }
                    }.accessibilityLabel("通过 Apple 登录").accessibilityIdentifier("appleLoginButton")
                    AsyncLoginProviderButton(title: "QQ", imageAsset: "QQLogo", outlined: true, disabled: !agreementAccepted || model.isBusy) {
                        focusedField = nil
                        await model.loginWithQQ(); if isLinking, model.isAuthenticated { await model.bindPendingOAuth() }
                    }.accessibilityLabel("使用 QQ 登录").accessibilityIdentifier("qqLoginButton")
                }
            }

            if !isLinking {
                let layout = dynamicTypeSize.isAccessibilitySize
                    ? AnyLayout(VStackLayout(spacing: 0)) : AnyLayout(HStackLayout(spacing: 16))
                layout {
                    NavigationLink { RegistrationView() } label: { Text("注册账号").frame(minHeight: 44) }
                        .accessibilityLabel("还没有账号？注册").accessibilityIdentifier("registrationLink")
                    if !dynamicTypeSize.isAccessibilitySize {
                        Circle().fill(Color.secondary.opacity(0.4)).frame(width: 3, height: 3).accessibilityHidden(true)
                    }
                    NavigationLink { AccountRecoveryEntryView() } label: { Text("恢复账号").frame(minHeight: 44) }
                        .foregroundStyle(.secondary).accessibilityLabel("无法登录？恢复账号")
                }.font(.footnote).frame(maxWidth: .infinity)
            }
        }
        .navigationTitle(isLinking ? "绑定账号" : "")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if !isLinking {
                Button("扫码", systemImage: "qrcode.viewfinder") { showScanner = true }
                Button("外观与关于", systemImage: "gearshape") { showAbout = true }.accessibilityIdentifier("aboutButton")
            }
        }
        .sheet(isPresented: $showAbout) { AppNavigationStack { AboutView() } }
        .sheet(isPresented: $showScanner) { ScannerSheet { value in showScanner = false; Task { await model.previewQRCode(value) } } }
        .onChange(of: useCode) { _, _ in
            if focusedField != nil { focusedField = useCode ? .code : .password }
        }
    }

    private var header: some View {
        VStack(spacing: 10) {
            HStack(spacing: 10) {
                Image("AppLogo").resizable().scaledToFit().frame(width: 44, height: 44)
                    .clipShape(RoundedRectangle(cornerRadius: 13)).accessibilityHidden(true)
                Text(isLinking ? "绑定已有账号" : "登录 Ksuser")
                    .font(.title.weight(.bold)).accessibilityAddTraits(.isHeader).accessibilityIdentifier("loginHeading")
            }.padding(.bottom, 4)
            Text(isLinking ? "登录已有账号，安全关联你的身份。" : "一个账号，连接你的数字生活。")
                .font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }.frame(maxWidth: .infinity).padding(.top, 6).padding(.bottom, 2)
    }

    private func authenticate() async {
        guard canLogin, !model.isBusy else { return }
        focusedField = nil
        let address = email.trimmingCharacters(in: .whitespacesAndNewlines)
        if useCode { await model.loginWithCode(email: address, code: code) }
        else { await model.login(email: address, password: password) }
        if isLinking, model.isAuthenticated { await model.bindPendingOAuth() }
    }
}

private enum LoginField: Hashable { case email, password, visiblePassword, code }

private struct LoginMethodPicker: View {
    @Binding var useCode: Bool

    var body: some View {
        HStack(spacing: 0) {
            method("密码登录", code: false)
            method("验证码登录", code: true)
        }.overlay(alignment: .bottom) {
            Rectangle().fill(Color.primary.opacity(0.06)).frame(height: 1)
        }
    }

    private func method(_ title: String, code: Bool) -> some View {
        Button { withAnimation(.easeInOut(duration: 0.18)) { useCode = code } } label: {
            Text(title).font(.subheadline.weight(.semibold))
                .foregroundStyle(useCode == code ? Color.primary : Color.secondary)
                .padding(.horizontal, 8).padding(.bottom, 8).frame(maxWidth: .infinity, minHeight: 44)
                .overlay(alignment: .bottom) {
                    if useCode == code { Capsule().fill(Brand.gold).frame(width: 28, height: 3).padding(.bottom, 1) }
                }.contentShape(Rectangle())
        }.buttonStyle(.plain).accessibilityAddTraits(useCode == code ? .isSelected : [])
            .accessibilityIdentifier(code ? "codeLoginTab" : "passwordLoginTab")
    }
}

private struct LoginInputField: View {
    let title: String
    let placeholder: String
    let icon: String
    @Binding var text: String
    let field: LoginField
    var focus: FocusState<LoginField?>.Binding
    var secure = false
    var keyboard: UIKeyboardType = .default
    var contentType: UITextContentType? = nil
    var submitLabel: SubmitLabel = .next
    var onSubmit: () -> Void
    @State private var revealed = false
    private var focusTarget: LoginField { secure && revealed ? .visiblePassword : field }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.caption.weight(.medium)).foregroundStyle(.secondary)
            HStack(spacing: 10) {
                Image(systemName: icon).font(.system(size: 17)).foregroundStyle(.secondary)
                    .frame(width: 20).accessibilityHidden(true)
                Group {
                    if secure && !revealed { SecureField(placeholder, text: $text) }
                    else { TextField(placeholder, text: $text).keyboardType(keyboard) }
                }
                .font(.body).textContentType(contentType).textInputAutocapitalization(.never).autocorrectionDisabled()
                .focused(focus, equals: focusTarget).submitLabel(submitLabel).onSubmit(onSubmit)
                .accessibilityLabel(title).accessibilityIdentifier(title)
                if secure {
                    Button {
                        let wasFocused = focus.wrappedValue == focusTarget
                        revealed.toggle()
                        if wasFocused { focus.wrappedValue = focusTarget }
                    } label: {
                        Image(systemName: revealed ? "eye.slash" : "eye").font(.system(size: 16))
                            .foregroundStyle(.secondary).frame(width: 44, height: 44)
                    }.buttonStyle(.plain).accessibilityLabel(revealed ? "隐藏密码" : "显示密码")
                        .accessibilityIdentifier("passwordVisibilityButton")
                }
            }
            .padding(.leading, 14).padding(.trailing, secure ? 4 : 14).padding(.vertical, secure ? 5 : 16)
            .frame(minHeight: 54).background(Brand.background, in: RoundedRectangle(cornerRadius: 14))
            .overlay {
                RoundedRectangle(cornerRadius: 14)
                    .strokeBorder(focus.wrappedValue == focusTarget ? Brand.gold.opacity(0.5) : Color.clear, lineWidth: 1)
            }
        }
    }
}

private struct LoginAgreementView: View {
    @Binding var accepted: Bool

    var body: some View {
        HStack(alignment: .top, spacing: 2) {
            Toggle("我已阅读并同意相关条款", isOn: $accepted)
                .toggleStyle(LoginAgreementToggleStyle()).accessibilityIdentifier("agreementToggle")
                .frame(width: 36)
            Text("我已阅读并同意 [服务协议](https://www.ksuser.cn/agreement/user.html) 和 [隐私政策](https://www.ksuser.cn/agreement/privacy.html)，以及 [第三方信息共享清单](https://www.ksuser.cn/agreement/third-party-information-sharing.html)。")
                .font(.caption).foregroundStyle(.secondary).tint(Brand.gold).lineSpacing(4)
                .fixedSize(horizontal: false, vertical: true).padding(.top, 6)
        }
    }
}

private struct LoginAgreementToggleStyle: ToggleStyle {
    func makeBody(configuration: Configuration) -> some View {
        Button { configuration.isOn.toggle() } label: {
            Image(systemName: configuration.isOn ? "checkmark.square.fill" : "square")
                .font(.system(size: 20, weight: .regular))
                .foregroundStyle(configuration.isOn ? Brand.gold : Color.secondary.opacity(0.6))
                .frame(width: 44, height: 44).contentShape(Rectangle())
        }.buttonStyle(.plain)
            .accessibilityRepresentation { Toggle("我已阅读并同意相关条款", isOn: configuration.$isOn).toggleStyle(.switch) }
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
    @State private var isSending = false
    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            let remaining = max(0, Int(resendAt.timeIntervalSince(context.date).rounded(.up)))
            Button {
                guard !isSending, !model.isBusy else { return }
                isSending = true
                Task {
                    defer { isSending = false }
                    await model.sendCode(email: email, type: type)
                    if model.errorMessage == nil { resendAt = Date().addingTimeInterval(60) }
                }
            } label: {
                HStack(spacing: 8) {
                    if isSending { ProgressView().tint(Brand.gold) }
                    Text(isSending ? "正在发送…" : remaining > 0 ? "\(remaining) 秒后重发" : "发送验证码")
                }.frame(minHeight: 44)
            }
                .disabled(isSending || remaining > 0 || model.isBusy || !allowed || (email != nil && !validEmail(email!)))
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
