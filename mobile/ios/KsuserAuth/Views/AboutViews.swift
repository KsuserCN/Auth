import SwiftUI

struct AboutView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @AppStorage("appearance") private var appearance = AppTheme.system.rawValue
    private var version: String { Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0" }
    private var build: String { Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "1" }
    private var currentYear: Int { Calendar.current.component(.year, from: .now) }
    private var filingNumber: String {
        guard let value = Bundle.main.object(forInfoDictionaryKey: "KSUSER_ICP_NUMBER") as? String,
              !value.isEmpty, !value.hasPrefix("$(") else { return "沪ICP备2025144703号-3A" }
        return value
    }
    private var themeLayout: AnyLayout {
        dynamicTypeSize.isAccessibilitySize ? AnyLayout(VStackLayout(spacing: 8)) : AnyLayout(HStackLayout(spacing: 8))
    }

    var body: some View {
        PageScroll(maxWidth: 600, spacing: 16) {
            VStack(spacing: 10) {
                Image("AppLogo").resizable().scaledToFit().frame(width: 84, height: 84)
                    .clipShape(RoundedRectangle(cornerRadius: 20))
                    .accessibilityLabel("Ksuser 安全软件图标").accessibilityIdentifier("appLogo")
                Text("Ksuser 安全").font(.title2.weight(.bold))
                Text("安全连接，安心使用。").font(.subheadline).foregroundStyle(.secondary)
                Text("版本 \(version) (\(build))")
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 12).padding(.vertical, 7)
                    .background(Brand.card, in: Capsule())
            }
            .frame(maxWidth: .infinity)
            .padding(.top, 20).padding(.bottom, 12)
            VStack(alignment: .leading, spacing: 10) {
                Text("外观").font(.headline).padding(.horizontal, 4)
                themeLayout {
                    ForEach(AppTheme.allCases) { theme in themeButton(theme) }
                }
                .padding(12)
                .background(Brand.card, in: RoundedRectangle(cornerRadius: 22))
            }
            if model.isAuthenticated { PushNotificationSettingsView() }
            VStack(alignment: .leading, spacing: 10) {
                Text("协议与隐私").font(.headline).padding(.horizontal, 4)
                VStack(spacing: 0) {
                    legalLink("服务协议", subtitle: "使用条款", icon: "doc.text", path: "user")
                    Divider().padding(.leading, 48)
                    legalLink("隐私政策", subtitle: "个人信息保护", icon: "hand.raised", path: "privacy")
                    Divider().padding(.leading, 48)
                    legalLink("第三方信息共享清单", subtitle: "第三方服务说明", icon: "square.stack.3d.up", path: "third-party-information-sharing")
                }
                .padding(.horizontal, 16).padding(.vertical, 4)
                .background(Brand.card, in: RoundedRectangle(cornerRadius: 22))
            }
            VStack(alignment: .leading, spacing: 10) {
                Text("帮助与反馈").font(.headline).padding(.horizontal, 4)
                VStack(spacing: 0) {
                    externalLink("问题反馈", subtitle: "提交问题或建议", icon: "bubble.left.and.bubble.right", url: "https://github.com/KsuserCN/Auth/issues")
                }
                .padding(.horizontal, 16).padding(.vertical, 4)
                .background(Brand.card, in: RoundedRectangle(cornerRadius: 22))
            }
        }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                VStack(spacing: 0) {
                    Text("\(currentYear.formatted(.number.grouping(.never))) Ksuser | KsuserKqy")
                        .font(.caption2)
                        .foregroundStyle(.tertiary)
                        .frame(maxWidth: .infinity, minHeight: 24)
                    Button { openURL(URL(string: "https://beian.miit.gov.cn/")!) } label: {
                        HStack(spacing: 4) {
                            Text(filingNumber)
                            Image(systemName: "arrow.up.right").font(.system(size: 9, weight: .semibold)).accessibilityHidden(true)
                        }
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, minHeight: 36)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(filingNumber)
                    .accessibilityHint("在应用内打开工信部备案管理系统")
                    .accessibilityIdentifier("icpFilingNumber")
                }
                .padding(.horizontal, 20)
                .background(Brand.background)
            }
            .navigationTitle("关于与设置").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("完成") { dismiss() } } }
            .preferredColorScheme(AppTheme(rawValue: appearance)?.colorScheme)
    }
    private func themeButton(_ theme: AppTheme) -> some View {
        let selected = appearance == theme.rawValue
        let icon: String = switch theme {
        case .system: "circle.lefthalf.filled"
        case .light: "sun.max.fill"
        case .dark: "moon.stars.fill"
        }
        return Button { appearance = theme.rawValue } label: {
            VStack(spacing: 8) {
                Image(systemName: icon).font(.system(size: 20, weight: .medium)).accessibilityHidden(true)
                Text(theme.title).font(.footnote.weight(.medium))
            }
            .foregroundStyle(selected ? Brand.gold : Color.primary)
            .frame(maxWidth: .infinity, minHeight: 72)
            .background(selected ? Brand.subtleGold : Brand.background, in: RoundedRectangle(cornerRadius: 14))
            .overlay { RoundedRectangle(cornerRadius: 14).strokeBorder(selected ? Brand.gold.opacity(0.55) : Color.primary.opacity(0.05), lineWidth: selected ? 1.5 : 1) }
            .contentShape(RoundedRectangle(cornerRadius: 14))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(theme.title)
        .accessibilityValue(selected ? "已选择" : "未选择")
        .accessibilityIdentifier("theme-" + theme.rawValue)
    }

    private func legalLink(_ title: String, subtitle: String, icon: String, path: String) -> some View {
        externalLink(title, subtitle: subtitle, icon: icon, url: "https://docs.ksuser.cn/agreement/" + path)
    }

    private func externalLink(_ title: String, subtitle: String, icon: String, url: String) -> some View {
        Button { openURL(URL(string: url)!) } label: {
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .font(.system(size: 17))
                    .foregroundStyle(Brand.gold)
                    .frame(width: 36, height: 36)
                    .background(Brand.subtleGold, in: RoundedRectangle(cornerRadius: 11))
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 3) {
                    Text(title).font(.subheadline.weight(.medium)).foregroundStyle(.primary)
                    Text(subtitle).font(.caption).foregroundStyle(.secondary)
                }
                Spacer(minLength: 8)
                Image(systemName: "arrow.up.right").font(.caption.weight(.semibold)).foregroundStyle(.tertiary).accessibilityHidden(true)
            }
            .frame(minHeight: 62)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(model.isBusy)
        .accessibilityLabel(title)
    }
}
