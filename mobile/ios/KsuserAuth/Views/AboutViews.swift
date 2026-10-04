import SwiftUI

struct AboutView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @AppStorage("appearance") private var appearance = AppTheme.system.rawValue
    @State private var updateSheet = false
    private var version: String { Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0" }
    private var build: String { Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "1" }
    var body: some View {
        PageScroll {
            AppCard {
                HStack(spacing: 16) {
                    Image("AppLogo").resizable().scaledToFit().frame(width: 68, height: 68)
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                        .accessibilityLabel("Ksuser 安全软件图标").accessibilityIdentifier("appLogo")
                    VStack(alignment: .leading, spacing: 5) { Text("Ksuser 安全").font(.title2.weight(.bold)); Text("原生 iOS 客户端").font(.subheadline).foregroundStyle(.secondary) }
                }
                InfoRow(title: "版本", value: "\(version) (\(build))")
                if let filing = Bundle.main.object(forInfoDictionaryKey: "KSUSER_ICP_NUMBER") as? String, !filing.isEmpty, !filing.hasPrefix("$(") { InfoRow(title: "备案号", value: filing) }
            }
            AppCard {
                CardHeader(title: "外观", subtitle: "选择更适合你的界面。", icon: "circle.lefthalf.filled")
                AdaptivePicker("主题", selection: $appearance) { ForEach(AppTheme.allCases) { Text($0.title).tag($0.rawValue) } }.accessibilityIdentifier("themePicker")
            }
            AppCard {
                CardHeader(title: "应用更新", icon: "arrow.down.circle")
                if let update = model.updateInfo {
                    Text(update.available ? "新版本 \(update.versionName) 已发布" : "当前已是最新版本").font(.subheadline).foregroundStyle(.secondary)
                    if update.available {
                        if let notes = update.releaseNotes, !notes.isEmpty { Text(notes).font(.subheadline).foregroundStyle(.secondary) }
                        Button("查看更新") { updateSheet = true }.frame(minHeight: 44)
                    }
                } else { Text("检查是否有可用的新版本。").font(.subheadline).foregroundStyle(.secondary) }
                AsyncActionButton(title: "检查更新", isBusy: model.isBusy) { await model.manualCheckUpdate(); if model.updateInfo?.available == true { updateSheet = true } }
            }
            AppCard {
                CardHeader(title: "协议与隐私", icon: "doc.text")
                linkRow("服务协议", path: "user.html")
                Divider()
                linkRow("隐私政策", path: "privacy.html")
                Divider()
                linkRow("第三方信息共享清单", path: "third-party-information-sharing.html")
            }
            Text("安全连接，安心使用。").font(.caption).foregroundStyle(.secondary).frame(maxWidth: .infinity).padding(.vertical, 8)
        }.navigationTitle("关于与设置").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("完成") { dismiss() } } }
            .sheet(isPresented: $updateSheet) { if let info = model.updateInfo { AppNavigationStack { UpdateView(info: info) } } }
            .preferredColorScheme(AppTheme(rawValue: appearance)?.colorScheme)
    }
    private func linkRow(_ title: String, path: String) -> some View {
        ActionRow(title: title, icon: "arrow.up.right.square") { openURL(URL(string: "https://www.ksuser.cn/agreement/" + path)!) }
    }
}

struct UpdateView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    let info: AppUpdateInfo
    var body: some View {
        PageScroll {
            AppCard {
                CardHeader(title: "更新至 \(info.versionName)", subtitle: info.mandatory ? "当前版本需要更新后继续使用。" : "新的版本已经准备好。", icon: "arrow.down.circle.fill")
                if let notes = info.releaseNotes, !notes.isEmpty { Text(notes).font(.body).textSelection(.enabled) }
                if let value = info.appStoreUrl, let url = URL(string: value), ["https", "itms-apps"].contains(url.scheme?.lowercased() ?? "") {
                    Button("前往 App Store") { openURL(url) }.buttonStyle(GoldButtonStyle())
                }
                if !info.mandatory {
                    Button("稍后再说") { dismiss() }.frame(maxWidth: .infinity, minHeight: 44)
                    Button("忽略这个版本") { UserDefaults.standard.set(info.latestBuild, forKey: "ignoredUpdateBuild"); dismiss() }.font(.subheadline).frame(maxWidth: .infinity, minHeight: 44)
                }
            }
        }.navigationTitle("应用更新").navigationBarTitleDisplayMode(.inline).interactiveDismissDisabled(info.mandatory)
            .toolbar { if !info.mandatory { Button("完成") { dismiss() } } }
    }
}
