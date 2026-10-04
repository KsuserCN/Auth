import SwiftUI

struct AuthorizedAppsView: View {
    @Environment(AppModel.self) private var model
    @State private var ksuserApps: [KsuserAuthorizedApp] = []
    @State private var thirdPartyApps: [ThirdPartyAuthorizedApp] = []
    @State private var isLoading = false
    @State private var hasLoaded = false
    @State private var loadError: String?
    @State private var authorizationRevision = 0

    var body: some View {
        PageScroll {
            AppCard {
                CardHeader(title: "访问授权", subtitle: "查看哪些应用可以使用你的 Ksuser 账号信息", icon: "person.crop.circle.badge.checkmark")
                if hasLoaded {
                    HStack(spacing: 12) {
                        countLabel("Ksuser 应用", count: ksuserApps.count)
                        countLabel("第三方应用", count: thirdPartyApps.count)
                    }
                    .accessibilityElement(children: .combine)
                }
            }

            if let loadError {
                AppCard {
                    Label(loadError, systemImage: "exclamationmark.circle")
                        .foregroundStyle(Brand.danger)
                        .fixedSize(horizontal: false, vertical: true)
                    Button("重试") { Task { await loadAuthorizations() } }
                        .disabled(isLoading)
                }
            }

            if !hasLoaded && isLoading {
                ProgressView("正在加载授权应用…")
                    .frame(maxWidth: .infinity)
                    .padding(36)
            } else if hasLoaded {
                AppCard {
                    CardHeader(title: "Ksuser 应用", subtitle: "已授权的 Ksuser 内部应用", icon: "square.stack.3d.up")
                    if ksuserApps.isEmpty {
                        emptyState("暂无已授权的 Ksuser 应用")
                    } else {
                        ForEach(ksuserApps) { app in
                            NavigationLink {
                                AuthorizedAppDetailView(record: .ksuser(app)) {
                                    let accountID = model.user?.uuid
                                    let revoked = await model.revokeKsuserAuthorization(app.clientId)
                                    if revoked && accountID == model.user?.uuid {
                                        authorizationRevision += 1
                                        ksuserApps.removeAll { $0.clientId == app.clientId }
                                    }
                                    return revoked
                                }
                            } label: {
                                AuthorizedAppRow(name: app.clientName, logoURL: app.logoUrl, lastAuthorizedAt: app.lastAuthorizedAt, grantMode: app.grantMode)
                            }
                            .buttonStyle(.plain)
                            if app.id != ksuserApps.last?.id { Divider() }
                        }
                    }
                }

                AppCard {
                    CardHeader(title: "第三方应用", subtitle: "已通过 Ksuser 登录并获得授权的外部应用", icon: "square.on.square")
                    if thirdPartyApps.isEmpty {
                        emptyState("暂无已授权的第三方应用")
                    } else {
                        ForEach(thirdPartyApps) { app in
                            NavigationLink {
                                AuthorizedAppDetailView(record: .thirdParty(app)) {
                                    let accountID = model.user?.uuid
                                    let revoked = await model.revokeThirdPartyAuthorization(app.appId)
                                    if revoked && accountID == model.user?.uuid {
                                        authorizationRevision += 1
                                        thirdPartyApps.removeAll { $0.appId == app.appId }
                                    }
                                    return revoked
                                }
                            } label: {
                                AuthorizedAppRow(name: app.appName, logoURL: app.logoUrl, lastAuthorizedAt: app.lastAuthorizedAt, grantMode: app.grantMode)
                            }
                            .buttonStyle(.plain)
                            if app.id != thirdPartyApps.last?.id { Divider() }
                        }
                    }
                }
            }
        }
        .navigationTitle("授权应用")
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await loadAuthorizations() }
        .task(id: model.user?.uuid) {
            ksuserApps = []
            thirdPartyApps = []
            hasLoaded = false
            loadError = nil
            await loadAuthorizations()
        }
    }

    private func countLabel(_ title: String, count: Int) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(title).font(.caption).foregroundStyle(.secondary)
            Text("\(count)").font(.title2.weight(.semibold)).monospacedDigit()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(Brand.background, in: RoundedRectangle(cornerRadius: 12))
    }

    private func emptyState(_ message: String) -> some View {
        Text(message)
            .font(.subheadline)
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, minHeight: 72)
    }

    private func loadAuthorizations() async {
        guard model.isAuthenticated, !isLoading else { return }
        let accountID = model.user?.uuid
        let revision = authorizationRevision
        isLoading = true
        defer { isLoading = false }
        do {
            let snapshot = try await model.authorizedApps()
            guard accountID == model.user?.uuid, revision == authorizationRevision else { return }
            ksuserApps = snapshot.ksuserApps
            thirdPartyApps = snapshot.thirdPartyApps
            hasLoaded = true
            loadError = nil
        } catch {
            guard accountID == model.user?.uuid, revision == authorizationRevision else { return }
            loadError = error.localizedDescription
        }
    }
}

private struct AuthorizedAppRow: View {
    let name: String
    let logoURL: String?
    let lastAuthorizedAt: String?
    let grantMode: String

    var body: some View {
        HStack(spacing: 13) {
            AvatarView(url: logoURL, name: name, size: 44)
            VStack(alignment: .leading, spacing: 4) {
                Text(name).font(.body.weight(.medium)).foregroundStyle(.primary)
                Text("最近授权：\(displayDate(lastAuthorizedAt))")
                    .font(.caption).foregroundStyle(.secondary)
            }
            Spacer(minLength: 6)
            Text(authorizationGrantModeLabel(grantMode))
                .font(.caption).foregroundStyle(Brand.gold)
                .padding(.horizontal, 8).padding(.vertical, 5)
                .background(Brand.subtleGold, in: Capsule())
            Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(.tertiary)
        }
        .frame(minHeight: 56)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }
}

private enum AuthorizedAppRecord {
    case ksuser(KsuserAuthorizedApp)
    case thirdParty(ThirdPartyAuthorizedApp)

    var name: String { switch self { case .ksuser(let app): app.clientName; case .thirdParty(let app): app.appName } }
    var logoURL: String? { switch self { case .ksuser(let app): app.logoUrl; case .thirdParty(let app): app.logoUrl } }
    var redirectURI: String? { switch self { case .ksuser(let app): app.redirectUri; case .thirdParty(let app): app.redirectUri } }
    var scopes: [String] { switch self { case .ksuser(let app): app.scopes; case .thirdParty(let app): app.scopes } }
    var authorizedAt: String? { switch self { case .ksuser(let app): app.authorizedAt; case .thirdParty(let app): app.authorizedAt } }
    var lastAuthorizedAt: String? { switch self { case .ksuser(let app): app.lastAuthorizedAt; case .thirdParty(let app): app.lastAuthorizedAt } }
    var grantMode: String { switch self { case .ksuser(let app): app.grantMode; case .thirdParty(let app): app.grantMode } }
    var expiresAt: String? { switch self { case .ksuser(let app): app.expiresAt; case .thirdParty(let app): app.expiresAt } }
    var creatorName: String? { if case .thirdParty(let app) = self { return app.creatorName }; return nil }
    var creatorVerificationType: String? { if case .thirdParty(let app) = self { return app.creatorVerificationType }; return nil }
    var contactInfo: String? { if case .thirdParty(let app) = self { return app.contactInfo }; return nil }
    var isKsuser: Bool { if case .ksuser = self { return true }; return false }
}

private struct AuthorizedAppDetailView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var showRevokeConfirmation = false
    @State private var isRevoking = false
    let record: AuthorizedAppRecord
    let onRevoke: @MainActor () async -> Bool

    var body: some View {
        PageScroll {
            AppCard {
                HStack(spacing: 15) {
                    AvatarView(url: record.logoURL, name: record.name, size: 56)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(record.name).font(.title3.weight(.semibold))
                        Text(record.isKsuser ? "Ksuser 应用" : "第三方应用")
                            .font(.subheadline).foregroundStyle(.secondary)
                    }
                    Spacer(minLength: 0)
                }
                InfoRow(title: "网站", value: website(from: record.redirectURI))
                if let creator = record.creatorName, !creator.isEmpty {
                    InfoRow(title: "开发者", value: creator)
                }
                if let verification = record.creatorVerificationType, !verification.isEmpty {
                    InfoRow(title: "开发者认证", value: verificationLabel(verification))
                }
                if let contact = record.contactInfo, !contact.isEmpty {
                    InfoRow(title: "联系信息", value: contact)
                }
            }

            AppCard {
                CardHeader(title: "授权信息", icon: "checkmark.shield")
                DateInfoRow(title: "首次授权", value: record.authorizedAt)
                DateInfoRow(title: "最近授权", value: record.lastAuthorizedAt)
                InfoRow(title: "授权方式", value: authorizationGrantModeLabel(record.grantMode))
                if record.grantMode == "TIME_LIMITED", record.expiresAt != nil {
                    DateInfoRow(title: "有效期至", value: record.expiresAt)
                }
            }

            AppCard {
                CardHeader(title: "权限范围", subtitle: "此应用获准读取以下账号信息", icon: "person.text.rectangle")
                if record.scopes.isEmpty {
                    Text("未提供权限范围").font(.subheadline).foregroundStyle(.secondary)
                } else {
                    ForEach(record.scopes, id: \.self) { scope in
                        Label(scopeLabel(scope), systemImage: "checkmark.circle.fill")
                            .font(.subheadline)
                            .foregroundStyle(.primary)
                    }
                }
            }

            AppCard {
                CardHeader(title: "撤销授权", subtitle: "撤销后，该应用需要重新请求授权才能访问你的账号信息。", icon: "hand.raised")
                Button("撤销授权", role: .destructive) { showRevokeConfirmation = true }
                    .buttonStyle(.borderedProminent)
                    .tint(Brand.danger)
                    .frame(minHeight: 44)
                    .disabled(isRevoking)
                    .accessibilityIdentifier("revokeAuthorizationButton")
            }
        }
        .navigationTitle("应用详情")
        .navigationBarTitleDisplayMode(.inline)
        .confirmationDialog("撤销「\(record.name)」的授权？", isPresented: $showRevokeConfirmation, titleVisibility: .visible) {
            Button("撤销授权", role: .destructive) {
                Task {
                    guard !isRevoking else { return }
                    isRevoking = true
                    defer { isRevoking = false }
                    if await onRevoke() { dismiss() }
                }
            }
            .accessibilityIdentifier("confirmRevokeAuthorization")
        } message: {
            Text("撤销后，该应用需要重新请求授权才能访问你的账号信息。")
        }
    }

    private func website(from redirectURI: String?) -> String {
        guard let redirectURI,
              let components = URLComponents(string: redirectURI),
              ["http", "https"].contains(components.scheme?.lowercased() ?? ""),
              let host = components.host, !host.isEmpty else { return "未提供" }
        return host
    }

    private func scopeLabel(_ scope: String) -> String {
        switch scope {
        case "openid": "基础身份标识"
        case "profile": "昵称与头像"
        case "email": "邮箱地址"
        default: scope
        }
    }

    private func verificationLabel(_ verification: String) -> String {
        switch verification {
        case "admin": "管理员"
        case "enterprise": "企业认证"
        case "personal": "个人认证"
        default: "未认证"
        }
    }
}

private func authorizationGrantModeLabel(_ mode: String) -> String {
    switch mode {
    case "ONE_TIME": "一次性"
    case "TIME_LIMITED": "限时"
    default: "长期"
    }
}
