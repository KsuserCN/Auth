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
                AppCard {
                    CardHeader(title: title, subtitle: confirmation.isTransfer ? L10n.text("即将把这台设备切换到二维码所属的账号。") : L10n.text("请核对请求来源，确认是你正在使用的设备。"), icon: confirmation.isTransfer ? "person.crop.circle.badge.checkmark" : "qrcode")
                    InfoRow(title: L10n.text("请求类型"), value: typeLabel)
                    InfoRow(title: L10n.text("客户端"), value: confirmation.preview.clientName ?? L10n.text("未知客户端"))
                    InfoRow(title: L10n.text("设备"), value: [confirmation.preview.system, confirmation.preview.browser].compactMap { $0 }.joined(separator: " · "))
                    InfoRow(title: "IP", value: confirmation.preview.ipAddress ?? L10n.text("未知"))
                    InfoRow(title: L10n.text("位置"), value: confirmation.preview.ipLocation ?? L10n.text("未知"))
                    InfoRow(title: L10n.text("有效期"), value: remaining > 0 ? L10n.format("{0} 秒", [String(describing: remaining)]) : L10n.text("已过期"))
                    if let user = model.user, !confirmation.isTransfer { InfoRow(title: L10n.text("授权账号"), value: user.displayName) }
                    if !model.isAuthenticated && !confirmation.isTransfer {
                        Text(L10n.text("请先登录，再确认授权。")).foregroundStyle(.secondary)
                        NavigationLink { AuthenticationView() } label: { Text(L10n.text("登录后继续")).frame(maxWidth: .infinity, minHeight: 50) }.buttonStyle(GoldButtonStyle())
                    } else {
                        AsyncActionButton(title: confirmation.isTransfer ? L10n.text("确认切换账号") : L10n.text("确认授权"), isBusy: model.isBusy, disabled: remaining <= 0) {
                            if confirmation.isTransfer && model.isAuthenticated { confirmTransfer = true }
                            else { await model.approveQRCode() }
                        }
                        .confirmationDialog(L10n.text("切换到二维码所属账号？"), isPresented: $confirmTransfer, titleVisibility: .visible) {
                            Button(L10n.text("确认切换")) { Task { await model.approveQRCode() } }
                        } message: {
                            Text(L10n.text("完成验证后，这台设备会显示新账号的资料。"))
                        }
                    }
                }
                Text(L10n.text("只批准你本人发起的请求。陌生二维码可能导致其他设备访问你的账号。")).font(.caption).foregroundStyle(.secondary).padding(.horizontal, 4)
            }
        }.navigationTitle(title).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L10n.text("取消")) { model.dismissQR() } } }
            .sensitiveVerification()
    }
    private var title: String { confirmation.isTransfer ? L10n.text("确认账号切换") : L10n.text("确认扫码授权") }
    private var typeLabel: String {
        switch confirmation.preview.codeType {
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
                    CardHeader(title: L10n.text("允许浏览器登录？"), subtitle: L10n.text("你正在把当前账号的登录授权交给这个站点。"), icon: "globe")
                    InfoRow(title: L10n.text("目标站点"), value: confirmation.status.returnOrigin ?? L10n.text("未知站点"))
                    InfoRow(title: L10n.text("当前账号"), value: model.user?.displayName ?? L10n.text("尚未登录"))
                    InfoRow(title: L10n.text("有效期"), value: remaining > 0 ? L10n.format("{0} 秒", [String(describing: remaining)]) : L10n.text("已过期"))
                    AsyncActionButton(title: L10n.text("确认并打开网页"), isBusy: model.isBusy, disabled: remaining <= 0 || !model.isAuthenticated) {
                        if let url = await model.approveBridge() { returnToBrowser(url) }
                    }
                }
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
