import SwiftUI
import PhotosUI
import Vision

struct ScannerSheet: View {
    @Environment(\.locale) private var localizationLocale
    @Environment(\.dismiss) private var dismiss
    let onDetected: (String) -> Void
    @State private var photo: PhotosPickerItem?
    @State private var error: LocalizedMessage?
    @State private var processing = false
    @State private var delivered = false
    var body: some View {
        AppNavigationStack {
            VStack(spacing: 18) {
                QRScannerView(onDetected: deliver, onError: { error = $0 })
                    .overlay { ScanLineOverlay().allowsHitTesting(false) }
                    .clipShape(RoundedRectangle(cornerRadius: 24)).padding(.horizontal, 20)
                    .accessibilityLabel(L10n.text("二维码取景器"))
                Text(L10n.text("将二维码对准屏幕中央")).font(.headline)
                Text(L10n.text("确认授权前，你可以查看设备、位置和请求类型。")).font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center).padding(.horizontal)
                if let error { Label(error.rendered, systemImage: "exclamationmark.circle").foregroundStyle(Brand.danger).font(.subheadline).padding(.horizontal).fixedSize(horizontal: false, vertical: true) }
                PhotosPicker(selection: $photo, matching: .images) { Label(processing ? L10n.text("识别中…") : L10n.text("从相册选择二维码"), systemImage: "photo").frame(maxWidth: .infinity, minHeight: 50) }
                    .buttonStyle(.bordered).tint(Brand.gold).padding(.horizontal, 20).disabled(processing)
                    .accessibilityIdentifier("qrPhotoPicker")
            }.padding(.bottom, 24).background(Brand.background).navigationTitle(L10n.text("扫码授权")).navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L10n.text("取消")) { dismiss() } } }
                .onChange(of: photo) { _, selected in
                    guard let selected else { return }
                    Task {
                        processing = true
                        defer { processing = false; photo = nil }
                        do {
                            guard let data = try await selected.loadTransferable(type: Data.self) else { throw ScanError.noCode }
                            let codes = try await Task.detached(priority: .userInitiated) {
                                try QRImageDecoder.decode(data)
                            }.value
                            guard let result = codes.first else { throw ScanError.noCode }
                            deliver(result)
                        } catch { self.error = LocalizedMessage(error: error) }
                    }
                }
        }
    }
    private func deliver(_ value: String) { guard !delivered else { return }; delivered = true; onDetected(value) }
    private enum ScanError: LocalizedError { case noCode; var errorDescription: String? { L10n.text("没有找到可识别的二维码，请选择另一张图片。") } }
}

private struct ScanLineOverlay: View {
    @Environment(\.locale) private var localizationLocale
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var scanning = false

    var body: some View {
        GeometryReader { geometry in
            let scanSize = max(0, min(230, min(geometry.size.width, geometry.size.height) - 32))
            let travel = max(0, scanSize - 24)

            Capsule()
                .fill(LinearGradient(
                    colors: [.clear, Brand.buttonGold.opacity(0.45), Brand.buttonGold, .white, Brand.buttonGold, Brand.buttonGold.opacity(0.45), .clear],
                    startPoint: .leading,
                    endPoint: .trailing
                ))
                .frame(width: max(0, scanSize - 24), height: 3)
                .shadow(color: Brand.buttonGold.opacity(0.9), radius: 8)
                .shadow(color: Brand.buttonGold.opacity(0.45), radius: 16)
                .position(
                    x: geometry.size.width / 2,
                    y: geometry.size.height / 2 + (reduceMotion ? 0 : (scanning ? travel / 2 : -travel / 2))
                )
                .animation(reduceMotion ? nil : .linear(duration: 2.2).repeatForever(autoreverses: true), value: scanning)
        }
        .onAppear { scanning = true }
        .onDisappear { scanning = false }
        .accessibilityHidden(true)
    }
}

struct QRConfirmationView: View {
    @Environment(\.locale) private var localizationLocale
    @Environment(AppModel.self) private var model
    let confirmation: QRConfirmation
    @State private var confirmTransfer = false
    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            let remaining = max(0, Int(confirmation.expiresAt.timeIntervalSince(context.date)))
            PageScroll {
                confirmationHero(remaining: remaining)
                if confirmation.preview.isConsoleLogin || confirmation.preview.isOfficialAuthorization {
                    applicationRequestCard
                }
                requestSourceCard
                if let user = model.user, !confirmation.isTransfer {
                    AppCard {
                        CardHeader(title: L10n.text("授权账号"), subtitle: L10n.text("请确认正在使用正确的账号"), icon: "person.crop.circle")
                        HStack(spacing: 12) {
                            AvatarView(url: user.avatarUrl, name: user.displayName, size: 48)
                            VStack(alignment: .leading, spacing: 4) {
                                Text(user.displayName).font(.headline)
                                Text(user.email).font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            Image(systemName: "checkmark.circle.fill").foregroundStyle(Brand.success)
                        }
                    }
                }
                Label(L10n.text("只批准你本人发起的请求。陌生二维码可能导致其他设备访问你的账号。"), systemImage: "exclamationmark.shield.fill")
                    .font(.caption).foregroundStyle(Brand.gold).fixedSize(horizontal: false, vertical: true)
                    .padding(14).frame(maxWidth: .infinity, alignment: .leading)
                    .background(Brand.subtleGold, in: RoundedRectangle(cornerRadius: 16))
            }
            .safeAreaInset(edge: .bottom, spacing: 0) { confirmationActions(remaining: remaining) }
        }.navigationTitle(title).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L10n.text("取消")) { model.dismissQR() } } }
            .sensitiveVerification()
    }
    private func confirmationHero(remaining: Int) -> some View {
        AppCard {
            VStack(spacing: 14) {
                ZStack {
                    Circle().fill(Brand.subtleGold).frame(width: 76, height: 76)
                    Image(systemName: confirmation.isTransfer ? "person.crop.circle.badge.checkmark" : "qrcode.viewfinder")
                        .font(.system(size: 34, weight: .medium)).foregroundStyle(Brand.gold)
                }
                Text(title).font(.title2.bold()).multilineTextAlignment(.center)
                Text(confirmation.isTransfer ? L10n.text("即将把这台设备切换到二维码所属的账号。") : L10n.text("请核对请求来源，确认是你正在使用的设备。"))
                    .font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true)
                HStack(spacing: 8) {
                    StatusPill(title: typeLabel, positive: true)
                    StatusPill(title: remaining > 0 ? L10n.format("{0} 秒", [String(describing: remaining)]) : L10n.text("已过期"), positive: remaining > 0)
                }
            }.frame(maxWidth: .infinity).padding(.vertical, 8)
        }
    }
    private var applicationRequestCard: some View {
        AppCard {
            CardHeader(title: L10n.text("应用信息"), subtitle: L10n.text("确认应用身份和授权资料"), icon: "square.stack.3d.up")
            HStack(spacing: 12) {
                targetApplicationLogo
                VStack(alignment: .leading, spacing: 4) {
                    Text(confirmation.preview.targetName ?? "Ksuser Console").font(.headline)
                    if let origin = confirmation.preview.targetOrigin {
                        Text(origin).font(.caption).foregroundStyle(.secondary).textSelection(.enabled)
                    }
                }
            }
            VStack(alignment: .leading, spacing: 10) {
                Text(L10n.text("授权资料")).font(.subheadline.weight(.semibold)).foregroundStyle(.secondary)
                if let scopes = confirmation.preview.requestedScopes, !scopes.isEmpty {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 112), alignment: .leading)], alignment: .leading, spacing: 8) {
                        ForEach(scopes, id: \.self) { scope in
                            Text(scopeLabel(scope)).font(.caption.weight(.medium))
                                .padding(.horizontal, 10).padding(.vertical, 7)
                                .background(Brand.subtleGold, in: Capsule()).foregroundStyle(Brand.gold)
                        }
                    }
                } else {
                    Text(L10n.text("账号信息、邮箱")).font(.subheadline)
                }
            }
            Label(confirmation.preview.isConsoleLogin ? L10n.text("仅超级管理员可登录管理中心。确认后，浏览器将以当前账号登录。") : L10n.text("确认后，发起请求的设备将登录此应用。授权可在通行证隐私设置中撤销。"), systemImage: "lock.shield")
                .font(.caption).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
        }
    }
    private var targetApplicationLogo: some View {
        AsyncImage(url: confirmation.preview.targetLogoUrl.flatMap(URL.init(string:))) { phase in
            if let image = phase.image { image.resizable().scaledToFit().padding(6) }
            else { Image(systemName: "app.fill").font(.title2).foregroundStyle(Brand.gold) }
        }
        .frame(width: 52, height: 52)
        .background(.white, in: RoundedRectangle(cornerRadius: 14))
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Color.primary.opacity(0.08)))
        .accessibilityHidden(true)
    }
    private var requestSourceCard: some View {
        AppCard {
            CardHeader(title: L10n.text("请求来源"), subtitle: L10n.text("检查设备和网络位置是否与你的操作一致"), icon: "desktopcomputer")
            InfoRow(title: L10n.text("客户端"), value: confirmation.preview.clientName ?? L10n.text("未知客户端"))
            let device = [confirmation.preview.system, confirmation.preview.browser].compactMap { $0 }.joined(separator: " · ")
            InfoRow(title: L10n.text("设备"), value: device.isEmpty ? L10n.text("未知") : device)
            InfoRow(title: "IP", value: confirmation.preview.ipAddress ?? L10n.text("未知"))
            InfoRow(title: L10n.text("位置"), value: confirmation.preview.ipLocation ?? L10n.text("未知"))
        }
    }
    @ViewBuilder private func confirmationActions(remaining: Int) -> some View {
        VStack(spacing: 8) {
            if !model.isAuthenticated && !confirmation.isTransfer {
                Text(L10n.text("请先登录，再确认授权。" )).font(.caption).foregroundStyle(.secondary)
                NavigationLink { AuthenticationView() } label: { Text(L10n.text("登录后继续")).frame(maxWidth: .infinity, minHeight: 50) }.buttonStyle(GoldButtonStyle())
            } else {
                AsyncActionButton(title: approveTitle, isBusy: model.isBusy, disabled: remaining <= 0) {
                    if confirmation.isTransfer && model.isAuthenticated { confirmTransfer = true }
                    else { await model.approveQRCode() }
                }
                .confirmationDialog(L10n.text("切换到二维码所属账号？"), isPresented: $confirmTransfer, titleVisibility: .visible) {
                    Button(L10n.text("确认切换")) { Task { await model.approveQRCode() } }
                } message: {
                    Text(L10n.text("完成验证后，这台设备会显示新账号的资料。"))
                }
                if confirmation.preview.isOfficialAuthorization {
                    Button(L10n.text("拒绝登录"), role: .cancel) { Task { await model.rejectOfficialQRCode() } }
                        .disabled(model.isBusy || remaining <= 0).frame(minHeight: 40)
                }
            }
        }
        .padding(.horizontal, 20).padding(.top, 12).padding(.bottom, 8)
        .background(.regularMaterial)
    }
    private var approveTitle: String {
        confirmation.isTransfer ? L10n.text("确认切换账号") : (confirmation.preview.isConsoleLogin ? L10n.text("确认登录管理中心") : (confirmation.preview.isOfficialAuthorization ? L10n.text("确认登录应用") : L10n.text("确认授权")))
    }
    private var title: String { confirmation.isTransfer ? L10n.text("确认账号切换") : (confirmation.preview.isConsoleLogin ? L10n.text("登录管理中心") : (confirmation.preview.isOfficialAuthorization ? L10n.text("官方应用登录") : L10n.text("确认扫码授权"))) }
    private func scopeLabel(_ scope: String) -> String {
        switch scope {
        case "openid": L10n.text("真实用户 UUID")
        case "profile": L10n.text("用户名和头像")
        case "email": L10n.text("邮箱")
        case "real_name": L10n.text("真实姓名")
        case "gender": L10n.text("性别")
        case "birth_date": L10n.text("出生日期")
        case "region": L10n.text("所在地区")
        case "bio": L10n.text("个人简介")
        default: scope
        }
    }
    private var typeLabel: String {
        switch confirmation.preview.codeType {
        case "approve_console_login": L10n.text("管理中心登录")
        case "approve_official": L10n.text("官方应用登录")
        case "login", "LOGIN", "approve_login": L10n.text("登录授权")
        case "mfa", "MFA", "approve_mfa": L10n.text("双重验证")
        case "sensitive", "SENSITIVE", "approve_sensitive": L10n.text("敏感操作")
        case "recovery", "RECOVERY", "approve_recovery": L10n.text("账号恢复授权")
        case "transfer", "session-transfer", "SESSION_TRANSFER": L10n.text("会话转移")
        default: confirmation.preview.codeType
        }
    }
}

struct BridgeConfirmationView: View {
    @Environment(\.locale) private var localizationLocale
    @Environment(AppModel.self) private var model
    let confirmation: BridgeConfirmation
    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            let remaining = max(0, Int(confirmation.expiresAt.timeIntervalSince(context.date)))
            PageScroll {
                AppCard {
                    VStack(spacing: 14) {
                        ZStack {
                            Circle().fill(Brand.subtleGold).frame(width: 76, height: 76)
                            Image(systemName: "globe.americas.fill").font(.system(size: 34, weight: .medium)).foregroundStyle(Brand.gold)
                        }
                        Text(L10n.text("允许浏览器登录？")).font(.title2.bold()).multilineTextAlignment(.center)
                        Text(L10n.text("你正在把当前账号的登录授权交给这个站点。"))
                            .font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true)
                        StatusPill(title: remaining > 0 ? L10n.format("{0} 秒", [String(describing: remaining)]) : L10n.text("已过期"), positive: remaining > 0)
                    }.frame(maxWidth: .infinity).padding(.vertical, 8)
                }
                AppCard {
                    CardHeader(title: L10n.text("目标网站"), subtitle: L10n.text("请确认这是你正在使用的网站"), icon: "network")
                    Label(confirmation.status.returnOrigin ?? L10n.text("未知站点"), systemImage: "link")
                        .font(.headline).foregroundStyle(.primary).textSelection(.enabled).fixedSize(horizontal: false, vertical: true)
                }
                AppCard {
                    CardHeader(title: L10n.text("使用账号"), subtitle: L10n.text("授权后浏览器将以此账号登录"), icon: "person.crop.circle")
                    if let user = model.user {
                        HStack(spacing: 12) {
                            AvatarView(url: user.avatarUrl, name: user.displayName, size: 48)
                            VStack(alignment: .leading, spacing: 4) {
                                Text(user.displayName).font(.headline)
                                Text(user.email).font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            Image(systemName: "checkmark.circle.fill").foregroundStyle(Brand.success)
                        }
                    } else {
                        Text(L10n.text("尚未登录")).foregroundStyle(.secondary)
                    }
                }
                Label(L10n.text("仅批准你本人发起的请求。陌生二维码可能导致其他设备访问你的账号。"), systemImage: "exclamationmark.shield.fill")
                    .font(.caption).foregroundStyle(Brand.gold).fixedSize(horizontal: false, vertical: true)
                    .padding(14).frame(maxWidth: .infinity, alignment: .leading)
                    .background(Brand.subtleGold, in: RoundedRectangle(cornerRadius: 16))
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                VStack(spacing: 8) {
                    AsyncActionButton(title: L10n.text("确认并打开网页"), isBusy: model.isBusy, disabled: remaining <= 0 || !model.isAuthenticated) {
                        if let url = await model.approveBridge() { returnToBrowser(url) }
                    }
                    if !model.isAuthenticated {
                        Text(L10n.text("请先登录，再确认授权。" )).font(.caption).foregroundStyle(.secondary)
                    }
                }.padding(.horizontal, 20).padding(.top, 12).padding(.bottom, 8).background(.regularMaterial)
            }
        }.navigationTitle(L10n.text("网页登录授权")).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L10n.text("取消")) { Task { if let url = await model.cancelBridge() { returnToBrowser(url) } } } } }
    }

    private func returnToBrowser(_ url: URL) {
        let preferredURL = MobileAuthorizationLink.preferredReturnURL(url, browser: confirmation.returnBrowser)
        // Reopen the browser session that requested login, outside the app's embedded Safari.
        UIApplication.shared.open(preferredURL, options: [:]) { accepted in
            if !accepted && preferredURL != url {
                UIApplication.shared.open(url, options: [:])
            }
        }
    }
}

struct AccountRecoveryTicketView: View {
    @Environment(\.locale) private var localizationLocale
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            let remaining = max(0, Int((model.recoveryTicketExpiresAt ?? context.date).timeIntervalSince(context.date)))
            PageScroll {
                if let ticket = model.recoveryTicket {
                    AppCard {
                        CardHeader(title: L10n.text("账号恢复授权"), subtitle: L10n.text("仅将这份授权提供给你自己的设备。任何获得它的人都可能继续恢复此账号。"), icon: "lifepreserver")
                        if let code = ticket.recoveryCode, remaining > 0 { LocalQRView(value: accountRecoveryURL(code: code, environment: model.environment).absoluteString).frame(maxWidth: .infinity) }
                        if let code = ticket.recoveryCode {
                            Text(code).font(.system(.title3, design: .monospaced)).textSelection(.enabled).frame(maxWidth: .infinity)
                            Button(L10n.text("复制恢复码"), systemImage: "doc.on.doc") { UIPasteboard.general.string = code }.frame(minHeight: 44).disabled(remaining <= 0)
                        }
                        InfoRow(title: L10n.text("账号"), value: ticket.username ?? model.user?.username ?? "")
                        InfoRow(title: L10n.text("邮箱"), value: ticket.maskedEmail ?? L10n.text("未绑定"))
                        InfoRow(title: L10n.text("背书设备"), value: ticket.sponsorClientName ?? L10n.text("当前设备"))
                        InfoRow(title: L10n.text("背书环境"), value: [ticket.sponsorSystem, ticket.sponsorBrowser].compactMap { $0 }.joined(separator: " · "))
                        InfoRow(title: L10n.text("背书位置"), value: ticket.sponsorIpLocation ?? L10n.text("未知"))
                        InfoRow(title: L10n.text("有效期"), value: remaining > 0 ? L10n.format("{0} 秒", [String(describing: remaining)]) : L10n.text("已过期"))
                        if let code = ticket.recoveryCode { Button(L10n.text("打开网页恢复"), systemImage: "safari") { openURL(accountRecoveryURL(code: code, environment: model.environment)) }.frame(minHeight: 44).disabled(remaining <= 0) }
                        AsyncActionButton(title: remaining > 0 ? L10n.text("重新生成") : L10n.text("生成新的授权"), isBusy: model.isBusy) { await model.requireSensitive(title: "生成恢复授权") { await model.generateRecoveryTicket() } }
                    }
                } else { ContentUnavailableView(L10n.text("暂无恢复授权"), systemImage: "qrcode") }
            }
        }.navigationTitle(L10n.text("恢复授权")).navigationBarTitleDisplayMode(.inline)
            .toolbar { Button(L10n.text("完成")) { model.recoveryTicket = nil; model.recoveryTicketExpiresAt = nil; dismiss() } }
            .sensitiveVerification()
            .onDisappear { model.recoveryTicket = nil; model.recoveryTicketExpiresAt = nil }
    }
}
