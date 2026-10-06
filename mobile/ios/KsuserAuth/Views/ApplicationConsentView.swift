import SwiftUI
import UIKit

struct ApplicationConsentView: View {
    @Environment(AppModel.self) private var model
    let context: MobileAuthorizationContext
    @State private var mode = "PERSISTENT"
    @State private var ttl = 3600
    @State private var submitting = false
    @State private var returnURL: URL?
    @State private var returnError = false

    var body: some View {
        PageScroll {
            VStack(spacing: 18) {
                Label("KSUSER · 安全授权", systemImage: "lock.shield.fill")
                    .font(.caption.weight(.semibold)).tracking(2).foregroundStyle(Brand.gold)
                HStack(spacing: 18) {
                    Image("AppLogo").resizable().scaledToFit().frame(width: 56, height: 56).clipShape(RoundedRectangle(cornerRadius: 16))
                    Image(systemName: "arrow.right").font(.title3).foregroundStyle(Brand.gold)
                    applicationLogo
                }.padding(.top, 10).accessibilityHidden(true)
                Text(context.appName).font(.title.bold()).multilineTextAlignment(.center)
                Text("申请使用你的 Ksuser 账号继续")
                    .font(.subheadline).foregroundStyle(.secondary)
            }.frame(maxWidth: .infinity).padding(.vertical, 20)

            AppCard {
                CardHeader(title: "应用信息", subtitle: "确认应用身份后再授权", icon: "square.stack.3d.up")
                consentDetail(title: "应用名称", value: context.appName)
                consentDetail(title: "联系方式", value: context.contactInfo?.isEmpty == false ? context.contactInfo! : "应用未提供联系方式")
                consentDetail(title: "返回网站", value: URL(string: context.redirectUri)?.host ?? context.redirectUri)
            }
            AppCard {
                CardHeader(title: "授权范围", subtitle: "该应用将获得以下权限", icon: "hand.raised.fill")
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
                Label("你的密码不会提供给该应用", systemImage: "lock.fill")
                    .font(.caption).foregroundStyle(.secondary).padding(.top, 8)
            }
            AppCard {
                CardHeader(title: "使用账号", subtitle: "授权仅适用于当前账号", icon: "person.crop.circle")
                HStack(spacing: 12) {
                    AvatarView(url: model.user?.avatarUrl, name: model.user?.username ?? "", size: 44)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(model.user?.username ?? "").font(.headline)
                        Text(model.user?.email ?? "").font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
            AppCard {
                CardHeader(title: "授权有效期", subtitle: "你可以随时在安全中心撤销授权", icon: "clock")
                Picker("授权方式", selection: $mode) {
                    Text("长期").tag("PERSISTENT")
                    Text("一次性").tag("ONE_TIME")
                    Text("限时").tag("TIME_LIMITED")
                }.pickerStyle(.segmented)
                Text(mode == "ONE_TIME" ? "仅本次有效，下次需要再次确认。" : mode == "TIME_LIMITED" ? "有效期内可继续使用，过期后需要再次确认。" : "后续相同权限的请求可以继续使用，直到你撤销授权。")
                    .font(.caption).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
                if mode == "TIME_LIMITED" {
                    Picker("有效期", selection: $ttl) {
                        Text("5 分钟").tag(300); Text("1 小时").tag(3600)
                        Text("24 小时").tag(86400); Text("7 天").tag(604800)
                    }.tint(Brand.gold).accessibilityIdentifier("applicationConsentDuration")
                }
            }
            if let error = model.errorMessage {
                Label(error, systemImage: "exclamationmark.circle").font(.subheadline).foregroundStyle(Brand.danger)
            }
            if returnError { Text("无法自动打开浏览器，请点击下方按钮继续。").font(.subheadline).foregroundStyle(.secondary) }
        }
        .navigationTitle("应用授权").navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .cancellationAction) { Button("关闭") { model.dismissMobileAuthorization() } } }
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: 10) {
                if let returnURL {
                    Button("返回网页继续", systemImage: "arrow.up.forward.app") { returnToBrowser(returnURL) }.buttonStyle(GoldButtonStyle())
                } else {
                    Button { submit(approve: true) } label: {
                        HStack { if submitting { ProgressView() }; Text(submitting ? "正在提交…" : "同意并继续") }
                    }.buttonStyle(GoldButtonStyle()).accessibilityIdentifier("applicationConsentApprove")
                    Button("拒绝授权") { submit(approve: false) }.font(.subheadline).foregroundStyle(.secondary)
                        .frame(minHeight: 36).accessibilityIdentifier("applicationConsentDeny")
                }
                Text("完成后将自动返回网页").font(.caption2).foregroundStyle(.secondary)
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
            if let image = phase.image { image.resizable().scaledToFill() }
            else { Text(String(context.appName.prefix(1))).font(.title.bold()).foregroundStyle(Brand.gold).frame(maxWidth: .infinity, maxHeight: .infinity).background(Brand.subtleGold) }
        }.frame(width: 72, height: 72).clipShape(RoundedRectangle(cornerRadius: 20))
    }
    private func submit(approve: Bool) {
        guard !submitting else { return }
        submitting = true
        Task {
            if let url = await model.decideMobileAuthorization(approve: approve, mode: mode, ttl: ttl) {
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
        switch scope { case "openid": "确认你的账号身份"; case "profile": "读取你的昵称与头像"; case "email": "读取你的邮箱地址"; default: "访问权限：\(scope)" }
    }
    private func scopeIcon(_ scope: String) -> String {
        switch scope { case "openid": "checkmark.shield"; case "profile": "person.crop.rectangle"; case "email": "envelope"; default: "key" }
    }
}
