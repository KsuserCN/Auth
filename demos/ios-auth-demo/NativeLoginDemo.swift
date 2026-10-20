import SwiftUI
import AuthenticationServices
import KsuserAuthSDK

@main
struct NativeLoginDemo: App {
    @StateObject private var login = DemoLoginModel()

    var body: some Scene {
        WindowGroup {
            NavigationStack {
                Form {
                    Section("应用配置") {
                        Picker("授权类型", selection: Binding(get: { login.mode }, set: { mode in
                            Task { await login.selectMode(mode) }
                        })) {
                            ForEach(DemoMode.allCases) { Text($0.title).tag($0) }
                        }
                        .disabled(login.waiting || login.cancelling)

                        LabeledContent("Client ID") {
                            Text(DemoConfiguration.clientID(for: login.mode)).font(.caption.monospaced()).textSelection(.enabled)
                        }
                        LabeledContent("回调") {
                            Text(DemoConfiguration.redirectURI.absoluteString).font(.caption).textSelection(.enabled)
                        }
                        LabeledContent("权限", value: DemoConfiguration.scopes(for: login.mode).joined(separator: " "))
                        if !login.configured {
                            Text("先在 DemoConfiguration.swift 中填写移动应用 AppID（或官方客户端 ID）、已验证的 iOS HTTPS 回调及域名关联配置。")
                                .font(.footnote).foregroundStyle(.secondary)
                        }
                    }

                    Section("授权操作") {
                        Button("使用 Ksuser 账号授权") { Task { await login.authorize() } }
                            .disabled(!login.configured || login.waiting || login.cancelling)
                        Button("恢复待完成授权") { Task { await login.resume() } }
                            .disabled(!login.configured || login.waiting || login.cancelling)
                        Button("改用网页继续") { Task { await login.resume(web: true) } }
                            .disabled(!login.configured || login.cancelling)
                        Button("取消当前请求", role: .cancel) { Task { await login.cancel() } }
                            .disabled(!login.configured || login.cancelling)
                    }

                    Section("授权结果") {
                        if login.waiting || login.cancelling {
                            HStack {
                                ProgressView()
                                Text(login.cancelling ? "正在取消…" : "等待授权结果…")
                            }
                        }
                        Text(login.message).textSelection(.enabled)
                        if let identity = login.identity { LabeledContent("用户标识", value: identity).textSelection(.enabled) }
                        if login.mode == .oauth, login.identity != nil {
                            if let unionID = login.unionID { LabeledContent("UnionID", value: unionID).textSelection(.enabled) }
                            else { Text("此应用尚未开通 UnionID，使用应用内 OpenID 识别用户。").font(.footnote).foregroundStyle(.secondary) }
                        }
                        if let expiresIn = login.expiresIn { LabeledContent("令牌有效期", value: "\(expiresIn) 秒") }
                    }
                }
                .navigationTitle("Ksuser 授权 Demo")
            }
            .onOpenURL { login.callback($0) }
            .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                if let url = activity.webpageURL { login.callback(url) }
            }
        }
    }
}

@MainActor
final class DemoLoginModel: NSObject, ObservableObject, ASWebAuthenticationPresentationContextProviding {
    @Published private(set) var mode: DemoMode
    @Published private(set) var message = "选择授权类型，完成配置后点击授权。"
    @Published private(set) var waiting = false
    @Published private(set) var cancelling = false
    @Published private(set) var identity: String?
    @Published private(set) var unionID: String?
    @Published private(set) var expiresIn: Int?
    var configured: Bool { DemoConfiguration.isConfigured(for: mode) && client != nil }

    private var client: KsuserAuthClient?
    private static let modePreference = "ksuser.demo.authorization-mode"

    override init() {
        mode = UserDefaults.standard.string(forKey: Self.modePreference).flatMap(DemoMode.init(rawValue:)) ?? .oauth
        super.init()
        configureClient()
    }

    private func configureClient() {
        guard DemoConfiguration.isConfigured(for: mode) else {
            client = nil
            message = "请先配置移动应用 AppID（或官方客户端 ID）和自己的 HTTPS 回调。"
            return
        }
        do { client = try KsuserAuthClient(configuration: DemoConfiguration.sdkConfiguration(for: mode)) }
        catch { client = nil; message = describe(error) }
    }

    func selectMode(_ selected: DemoMode) async {
        guard selected != mode, !waiting, !cancelling else { return }
        cancelling = true
        defer { cancelling = false }
        // End the previous mode's pending transaction before replacing its SDK instance.
        await client?.cancel()
        client = nil; identity = nil; unionID = nil; expiresIn = nil
        mode = selected
        UserDefaults.standard.set(selected.rawValue, forKey: Self.modePreference)
        message = "已切换为\(selected.title)，可以发起授权。"
        configureClient()
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            .filter { $0.activationState == .foregroundActive }.flatMap(\.windows).first { $0.isKeyWindow }
            ?? ASPresentationAnchor()
    }

    func authorize() async {
        guard let client, !waiting, !cancelling else { return }
        waiting = true; identity = nil; unionID = nil; expiresIn = nil
        message = "请在 Ksuser App 或系统网页中确认授权。"
        defer { waiting = false }
        do { show(try await client.authorize(scopes: DemoConfiguration.scopes(for: mode), presentationContextProvider: self)) }
        catch { message = describe(error) }
    }

    func resume(web: Bool = false) async {
        guard let client, !cancelling else { return }
        if waiting {
            guard web else { return }
            do { _ = try await client.resumePendingAuthorization(presentationContextProvider: self, useWebFallback: true) }
            catch { message = describe(error) }
            return // The original authorize/resume operation still receives this request's result.
        }
        waiting = true
        defer { waiting = false }
        do {
            if let result = try await client.resumePendingAuthorization(presentationContextProvider: self, useWebFallback: web) {
                show(result)
            } else { message = "没有待恢复的授权请求。" }
        } catch { message = describe(error) }
    }

    func callback(_ url: URL) {
        guard client?.handleCallback(url) == true else { return }
        if !waiting { Task { await resume() } }
    }

    func cancel() async {
        guard !cancelling else { return }
        cancelling = true
        defer { cancelling = false }
        await client?.cancel()
        identity = nil; unionID = nil; expiresIn = nil
        message = "已取消当前授权请求。"
    }

    private func show(_ result: KsuserAuthorizationResult) {
        switch result {
        case .oauth(let tokens):
            message = "移动应用 OAuth 授权成功。"
            identity = tokens.openID
            unionID = tokens.unionID
            expiresIn = tokens.expiresIn
        case .official(let tokens, let subject):
            message = "官方 OIDC 授权成功，ID Token 已验证。"
            identity = subject
            unionID = nil
            expiresIn = tokens.expiresIn
        }
        // A demo only shows identity and expiry. Never display or log access tokens.
    }

    private func describe(_ error: Error) -> String {
        switch error {
        case KsuserAuthError.cancelled: return "已取消授权。"
        case KsuserAuthError.expired: return "授权请求已过期，请重新发起。"
        case KsuserAuthError.authorizationInProgress: return "已有待完成请求，请先恢复或取消；修改过配置的旧请求会在五分钟后过期。"
        case KsuserAuthError.invalidConfiguration: return "配置不正确，请检查 Client ID、HTTPS 回调和服务地址。"
        case KsuserAuthError.server("access_denied"): return "用户已拒绝授权。"
        case KsuserAuthError.httpStatus(let status, let details): return "授权服务返回 HTTP \(status)\n\(details)"
        case is URLError: return "网络请求失败，可以尝试恢复待完成授权。"
        default: return error.localizedDescription
        }
    }
}
