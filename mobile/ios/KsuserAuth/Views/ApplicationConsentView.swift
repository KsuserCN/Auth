import SwiftUI
import UIKit

struct ApplicationConsentView: View {
    @Environment(\.locale) private var localizationLocale
    @Environment(AppModel.self) private var model
    let context: MobileAuthorizationContext
    @State private var mode = "PERSISTENT"
    @State private var ttl = 3600
    @State private var submitting = false
    @State private var returnURL: URL?
    @State private var returnError = false
    @State private var loginWeb = false

    var body: some View {
        PageScroll {
            VStack(spacing: 18) {
                HStack(spacing: 18) {
                    Image("AppLogo").resizable().scaledToFit().frame(width: 64, height: 64).clipShape(RoundedRectangle(cornerRadius: 16))
                    Image(systemName: "arrow.right").font(.title3).foregroundStyle(Brand.gold)
                    applicationLogo
                }.padding(.top, 10).accessibilityHidden(true)
                applicationTrustBadge
                Text(context.appName).font(.title.bold()).multilineTextAlignment(.center)
                Text(L10n.text("申请使用你的 Ksuser 账号继续"))
                    .font(.subheadline).foregroundStyle(.secondary)
            }.frame(maxWidth: .infinity).padding(.vertical, 20)

            AppCard {
                CardHeader(title: L10n.text("应用信息"), subtitle: L10n.text("确认应用身份后再授权"), icon: "square.stack.3d.up")
                consentDetail(title: L10n.text("应用名称"), value: context.appName)
                if context.officialApplication != true {
                    consentDetail(title: L10n.text("联系方式"), value: context.contactInfo?.isEmpty == false ? context.contactInfo! : L10n.text("应用未提供联系方式"))
                }
                consentDetail(title: L10n.text("返回网站"), value: redirectOrigin)
                if context.consoleApplication == true {
                    Text(L10n.text("仅超级管理员可登录管理中心。确认后，浏览器将以当前账号登录。"))
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            AppCard {
                CardHeader(title: L10n.text("授权范围"), subtitle: L10n.text("该应用将获得以下权限"), icon: "hand.raised.fill")
                ForEach(context.requestedScopes, id: \.self) { scope in
                    HStack(alignment: .top, spacing: 12) {
                        Image(systemName: scopeIcon(scope)).foregroundStyle(Brand.gold).frame(width: 24)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(scopeTitle(scope)).font(.subheadline.weight(.semibold))
                            Text(scope).font(.caption.monospaced()).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Image(systemName: "checkmark.circle.fill").foregroundStyle(Brand.success)
                    }.padding(.vertical, 6)
                }
                Label(L10n.text("你的密码不会提供给该应用"), systemImage: "lock.fill")
                    .font(.caption).foregroundStyle(.secondary).padding(.top, 8)
            }
            AppCard {
                CardHeader(title: L10n.text("使用账号"), subtitle: L10n.text("授权仅适用于当前账号"), icon: "person.crop.circle")
                HStack(spacing: 12) {
                    AvatarView(url: model.user?.avatarUrl, name: model.user?.username ?? "", size: 44)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(model.user?.username ?? "").font(.headline)
                        Text(model.user?.email ?? "").font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
            if context.qr == true {
                AppCard {
                    Toggle(L10n.text("同时登录通行证网页端"), isOn: $loginWeb)
                        .tint(Brand.gold).frame(minHeight: 50)
                        .accessibilityIdentifier("applicationConsentLoginWeb")
                    if let origin = context.webOrigin { consentDetail(title: L10n.text("网页登录网站"), value: origin) }
                    Text(L10n.text("默认仅授权此应用。开启后，发起请求的浏览器也会登录当前 Ksuser 账号。"))
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            if context.officialApplication == true {
                AppCard {
                    CardHeader(title: L10n.text("官方应用授权"), subtitle: L10n.text("授权可在通行证隐私设置中撤销"), icon: "hand.raised.fill")
                    Text(L10n.text("仅共享本次确认的资料范围，新增资料范围需要再次确认。"))
                        .font(.caption).foregroundStyle(.secondary)
                }
            } else {
                AppCard {
                    CardHeader(title: L10n.text("授权有效期"), subtitle: L10n.text("你可以随时在安全中心撤销授权"), icon: "clock")
                    Picker(L10n.text("授权方式"), selection: $mode) {
                        Text(L10n.text("长期")).tag("PERSISTENT")
                        Text(L10n.text("一次性")).tag("ONE_TIME")
                        Text(L10n.text("限时")).tag("TIME_LIMITED")
                    }.pickerStyle(.segmented).tint(Brand.gold)
                    Text(mode == "ONE_TIME" ? L10n.text("仅本次有效，下次需要再次确认。") : mode == "TIME_LIMITED" ? L10n.text("有效期内可继续使用，过期后需要再次确认。") : L10n.text("后续相同权限的请求可以继续使用，直到你撤销授权。"))
                        .font(.caption).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
                    if mode == "TIME_LIMITED" {
                        Picker(L10n.text("有效期"), selection: $ttl) {
                            Text(L10n.text("5 分钟")).tag(300); Text(L10n.text("1 小时")).tag(3600)
                            Text(L10n.text("24 小时")).tag(86400); Text(L10n.text("7 天")).tag(604800)
                        }.tint(Brand.gold).accessibilityIdentifier("applicationConsentDuration")
                    }
                }
            }
            if let error = model.errorMessage {
                Label(error, systemImage: "exclamationmark.circle").font(.subheadline).foregroundStyle(Brand.danger)
            }
            if returnError { Text(L10n.text("无法自动打开浏览器，请点击下方按钮继续。")).font(.subheadline).foregroundStyle(.secondary) }
        }
        .navigationTitle(L10n.text("应用授权")).navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L10n.text("关闭")) { model.dismissMobileAuthorization() } } }
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: 10) {
                if let returnURL {
                    Button(L10n.text("返回网页继续"), systemImage: "arrow.up.forward.app") { returnToBrowser(returnURL) }.buttonStyle(GoldButtonStyle())
                } else {
                    Button { submit(approve: true) } label: {
                        HStack { if submitting { ProgressView() }; Text(submitting ? L10n.text("正在提交…") : L10n.text("同意并继续")) }
                    }.buttonStyle(GoldButtonStyle()).accessibilityIdentifier("applicationConsentApprove")
                    Button(L10n.text("拒绝授权")) { submit(approve: false) }.font(.subheadline).foregroundStyle(.secondary)
                        .frame(minHeight: 44).accessibilityIdentifier("applicationConsentDeny")
                }
                Text(context.qr == true ? L10n.text("确认后请返回发起扫码请求的设备") : L10n.text("完成后将自动返回网页")).font(.caption2).foregroundStyle(.secondary)
            }.padding(.horizontal, 20).padding(.vertical, 12).background(.regularMaterial)
        }
        .disabled(submitting)
    }
    private func consentDetail(title: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(title).font(.caption).foregroundStyle(.secondary)
            Text(value).font(.subheadline).fixedSize(horizontal: false, vertical: true).textSelection(.enabled)
        }
    }
    private var applicationLogo: some View {
        AsyncImage(url: context.logoUrl.flatMap(URL.init(string:))) { phase in
            if let image = phase.image {
                image.resizable().scaledToFit().padding(7)
            } else {
                Text(String(context.appName.prefix(1))).font(.title.bold()).foregroundStyle(Brand.gold)
                    .frame(maxWidth: .infinity, maxHeight: .infinity).background(Brand.subtleGold)
            }
        }
        .frame(width: 64, height: 64)
        .background(.white, in: RoundedRectangle(cornerRadius: 20))
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(Color.primary.opacity(0.08)))
    }
    private var applicationTrustBadge: some View {
        Group {
            if context.officialApplication == true {
                Label(L10n.text("官方应用授权"), systemImage: "checkmark.seal.fill")
            } else if let subjectName = context.subjectName, !subjectName.isEmpty {
                Label {
                    Text(subjectVerificationLabel.map { "\(subjectName) · \($0)" } ?? subjectName)
                } icon: {
                    Image(systemName: "person.crop.circle.badge.checkmark")
                }
            } else {
                Label(L10n.text("开发者信息未提供"), systemImage: "questionmark.circle")
            }
        }
        .font(.caption.weight(.semibold))
        .foregroundStyle(Brand.gold)
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(Brand.subtleGold, in: Capsule())
    }
    private var redirectOrigin: String {
        guard let url = URL(string: context.redirectUri), let scheme = url.scheme,
              let host = url.host else { return context.redirectUri }
        let port = url.port.map { ":\($0)" } ?? ""
        return "\(scheme)://\(host)\(port)"
    }
    private var subjectVerificationLabel: String? {
        switch context.subjectVerificationType {
        case "personal": L10n.text("个人认证")
        case "enterprise": L10n.text("企业认证")
        case "admin": L10n.text("管理员")
        default: nil
        }
    }
    private func submit(approve: Bool) {
        guard !submitting else { return }
        submitting = true
        Task {
            if let url = await model.decideMobileAuthorization(approve: approve, mode: mode, ttl: ttl, loginWeb: loginWeb) {
                returnURL = url
                returnToBrowser(url)
            }
            submitting = false
        }
    }
    private func returnToBrowser(_ url: URL) {
        // Bypass AppNavigationStack's HTTP(S) openURL handler, which presents SFSafariViewController.
        // For Chrome-originated requests, try Chrome's URL scheme first, then use the system browser.
        let preferredURL = MobileAuthorizationLink.preferredReturnURL(url, browser: model.pendingMobileAuthorizationReturnBrowser)
        UIApplication.shared.open(preferredURL, options: [:]) { accepted in
            if accepted { model.dismissMobileAuthorization() }
            else if preferredURL != url {
                UIApplication.shared.open(url, options: [:]) { fallbackAccepted in
                    if fallbackAccepted { model.dismissMobileAuthorization() }
                    else { returnError = true }
                }
            } else { returnError = true }
        }
    }
    private func scopeTitle(_ scope: String) -> String {
        switch scope {
        case "openid": context.officialApplication == true ? L10n.text("真实用户 UUID") : L10n.text("确认你的账号身份")
        case "profile": L10n.text("读取你的昵称与头像")
        case "email": L10n.text("读取你的邮箱地址")
        case "real_name": L10n.text("真实姓名")
        case "gender": L10n.text("性别")
        case "birth_date": L10n.text("出生日期")
        case "region": L10n.text("所在地区")
        case "bio": L10n.text("个人简介")
        default: L10n.format("访问权限：{0}", [String(describing: scope)])
        }
    }
    private func scopeIcon(_ scope: String) -> String {
        switch scope { case "openid": "checkmark.shield"; case "profile": "person.crop.rectangle"; case "email": "envelope"; default: "key" }
    }
}
