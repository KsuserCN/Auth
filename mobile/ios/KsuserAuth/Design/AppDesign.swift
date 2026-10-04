import SwiftUI
import UIKit

enum AppTheme: String, CaseIterable, Identifiable {
    case system, light, dark
    var id: String { rawValue }
    var title: String { switch self { case .system: "跟随系统"; case .light: "浅色"; case .dark: "深色" } }
    var colorScheme: ColorScheme? { switch self { case .system: nil; case .light: .light; case .dark: .dark } }
}

enum Brand {
    static let gold = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark ? UIColor(red: 0.90, green: 0.73, blue: 0.33, alpha: 1) : UIColor(red: 0.59, green: 0.39, blue: 0.08, alpha: 1)
    })
    static let success = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark ? UIColor(red: 0.36, green: 0.82, blue: 0.55, alpha: 1) : UIColor(red: 0.09, green: 0.46, blue: 0.22, alpha: 1)
    })
    static let danger = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark ? UIColor(red: 1, green: 0.43, blue: 0.40, alpha: 1) : UIColor(red: 0.76, green: 0.13, blue: 0.13, alpha: 1)
    })
    static let buttonGold = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark ? UIColor(red: 0.36, green: 0.27, blue: 0.10, alpha: 1) : UIColor(red: 0.91, green: 0.73, blue: 0.33, alpha: 1)
    })
    static let background = Color(uiColor: .systemGroupedBackground)
    static let card = Color(uiColor: .secondarySystemGroupedBackground)
    static let subtleGold = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark ? UIColor(red: 0.18, green: 0.16, blue: 0.11, alpha: 1) : UIColor(red: 1.0, green: 0.96, blue: 0.86, alpha: 1)
    })
}

struct GoldButtonStyle: ButtonStyle {
    @Environment(\.colorScheme) private var colorScheme

    func makeBody(configuration: Configuration) -> some View {
        configuration.label.font(.headline).foregroundStyle(colorScheme == .dark ? Color(red: 0.99, green: 0.95, blue: 0.82) : Color(red: 0.15, green: 0.12, blue: 0.07))
            .frame(maxWidth: .infinity, minHeight: 50)
            .background(Brand.buttonGold, in: RoundedRectangle(cornerRadius: 15))
            .opacity(configuration.isPressed ? 0.75 : 1)
    }
}

struct LoginProviderButtonStyle: ButtonStyle {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.isEnabled) private var isEnabled
    var outlined = false

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.weight(.semibold))
            .padding(.horizontal, 16).padding(.vertical, 12)
            .frame(maxWidth: .infinity, minHeight: 50)
            .foregroundStyle(outlined ? Color.primary : Color.white)
            .background(outlined ? Brand.card : colorScheme == .dark ? Color(uiColor: .tertiarySystemGroupedBackground) : Color.black, in: RoundedRectangle(cornerRadius: 14))
            .overlay { RoundedRectangle(cornerRadius: 14).strokeBorder(outlined ? Color.primary.opacity(0.10) : Color.white.opacity(colorScheme == .dark ? 0.16 : 0), lineWidth: 1) }
            .contentShape(RoundedRectangle(cornerRadius: 14))
            .opacity(!isEnabled ? 0.5 : configuration.isPressed ? 0.75 : 1)
    }
}

struct LoginProviderLabel: View {
    let title: String
    var icon: String? = nil
    var imageAsset: String? = nil
    var isBusy = false
    var outlined = false

    var body: some View {
        HStack(spacing: 10) {
            Group {
                if isBusy {
                    ProgressView().tint(outlined ? Brand.gold : .white)
                } else if let imageAsset {
                    Image(imageAsset).renderingMode(.original).resizable().scaledToFit()
                } else if let icon {
                    Image(systemName: icon).font(.system(size: 20, weight: .medium))
                }
            }.frame(width: 24, height: 24).accessibilityHidden(true)
            Text(title)
        }
    }
}

struct AsyncLoginProviderButton: View {
    let title: String
    var icon: String? = nil
    var imageAsset: String? = nil
    var outlined = false
    var disabled = false
    let action: () async -> Void
    @State private var isPerforming = false

    var body: some View {
        Button {
            guard !isPerforming, !disabled else { return }
            isPerforming = true
            Task {
                defer { isPerforming = false }
                await action()
            }
        } label: {
            LoginProviderLabel(title: title, icon: icon, imageAsset: imageAsset, isBusy: isPerforming, outlined: outlined)
        }.buttonStyle(LoginProviderButtonStyle(outlined: outlined)).disabled(disabled || isPerforming)
    }
}

struct LoadingStatusBanner: View {
    let activity: LoadingActivity
    @State private var isVisible = false

    var body: some View {
        VStack(spacing: 0) {
            if isVisible {
                TimelineView(.periodic(from: .now, by: 1)) { context in
                    HStack(spacing: 12) {
                        ProgressView().tint(Brand.gold).accessibilityHidden(true)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(activity.message ?? "正在加载…").font(.subheadline.weight(.medium))
                            if context.date.timeIntervalSince(activity.startedAt) >= 8 {
                                Text("仍在处理中，请稍候…").font(.caption).foregroundStyle(.secondary)
                            }
                        }.fixedSize(horizontal: false, vertical: true)
                        Spacer(minLength: 0)
                    }
                    .padding(16)
                    .background(Brand.subtleGold, in: RoundedRectangle(cornerRadius: 16))
                    .padding(.horizontal, 20).padding(.vertical, 8)
                    .frame(maxWidth: 760).frame(maxWidth: .infinity)
                    .background(Brand.background)
                    .accessibilityElement(children: .combine).accessibilityIdentifier("loadingBanner")
                }
            }
        }
        .task(id: activity.id) {
            isVisible = false
            // Keep fast requests from flashing a banner; retain elapsed time across page changes.
            let delay = max(0, 0.3 - Date().timeIntervalSince(activity.startedAt))
            do { try await Task.sleep(for: .seconds(delay)); isVisible = true }
            catch { }
        }
    }
}

struct StatusMessageBanner: View {
    let message: String
    var isError = false
    let dismiss: () -> Void
    private var accent: Color { isError ? Brand.danger : Brand.success }

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            Image(systemName: isError ? "exclamationmark.circle.fill" : "checkmark.circle.fill")
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(accent)
                .frame(width: 36, height: 36)
                .background(accent.opacity(0.1), in: RoundedRectangle(cornerRadius: 10))
                .accessibilityHidden(true)
            Text(message).font(.subheadline.weight(.medium))
                .foregroundStyle(.primary).fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
            Button(action: dismiss) {
                Image(systemName: "xmark").font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(.secondary)
                    .frame(width: 24, height: 24)
                    .background(Color.primary.opacity(0.06), in: Circle())
                    .frame(width: 44, height: 44).contentShape(Rectangle())
            }.buttonStyle(.plain).accessibilityLabel("关闭提示")
                .accessibilityIdentifier("dismissStatusBanner")
        }
        .padding(.leading, 14).padding(.trailing, 4).padding(.vertical, 8)
        .background(Brand.card, in: RoundedRectangle(cornerRadius: 16))
        .overlay { RoundedRectangle(cornerRadius: 16).strokeBorder(accent.opacity(0.15), lineWidth: 1) }
        .shadow(color: .black.opacity(0.06), radius: 8, y: 3)
        .accessibilityElement(children: .contain).accessibilityIdentifier("statusBanner")
    }
}

struct AppCard<Content: View>: View {
    private let content: Content
    init(@ViewBuilder content: () -> Content) { self.content = content() }
    var body: some View {
        VStack(alignment: .leading, spacing: 16) { content }
            .padding(20).frame(maxWidth: .infinity, alignment: .leading)
            .background(Brand.card, in: RoundedRectangle(cornerRadius: 22))
    }
}

struct CardHeader: View {
    let title: String
    var subtitle: String? = nil
    var icon: String? = nil
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            if let icon { Image(systemName: icon).font(.title3).foregroundStyle(Brand.gold).frame(width: 28).accessibilityHidden(true) }
            VStack(alignment: .leading, spacing: 5) {
                Text(title).font(.headline)
                if let subtitle { Text(subtitle).font(.subheadline).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true) }
            }
        }
    }
}

struct PageScroll<Content: View>: View {
    @Environment(AppModel.self) private var model
    private let content: Content
    private let maxWidth: CGFloat
    private let spacing: CGFloat
    init(maxWidth: CGFloat = 760, spacing: CGFloat = 18, @ViewBuilder content: () -> Content) {
        self.maxWidth = maxWidth; self.spacing = spacing; self.content = content()
    }
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: spacing) {
                content
            }
                .padding(20).frame(maxWidth: maxWidth).frame(maxWidth: .infinity)
        }.background(Brand.background).scrollDismissesKeyboard(.interactively)
            .safeAreaInset(edge: .top, spacing: 0) {
                VStack(spacing: 0) {
                    if let activity = model.loadingActivity { LoadingStatusBanner(activity: activity) }
                    if let message = model.errorMessage ?? model.noticeMessage {
                        StatusMessageBanner(message: message, isError: model.errorMessage != nil) {
                            model.errorMessage = nil; model.noticeMessage = nil
                        }
                        .padding(.horizontal, 20).padding(.vertical, 8)
                        .frame(maxWidth: 760).frame(maxWidth: .infinity)
                        .background(Brand.background)
                    }
                }
            }
            .toolbar {
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("完成输入") { UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil) }
                        .accessibilityIdentifier("keyboardDone")
                }
            }
    }
}

private struct SensitiveVerificationModifier: ViewModifier {
    @Environment(AppModel.self) private var model
    @State private var presentedRequestID: UUID?
    var enabled: Bool
    var onPresent: (() -> Void)?
    var onDismiss: (() -> Void)?
    func body(content: Content) -> some View {
        content.sheet(item: Binding(get: { enabled ? model.sensitiveRequest : nil }, set: { if $0 == nil, model.sensitiveRequest?.id == presentedRequestID { model.cancelSensitive() } }), onDismiss: { onDismiss?() }) { request in
            AppNavigationStack { SensitiveVerificationView(request: request) }.presentationDragIndicator(.visible).onAppear { presentedRequestID = request.id; onPresent?() }
        }
    }
}

extension View {
    func sensitiveVerification(enabled: Bool = true, onPresent: (() -> Void)? = nil, onDismiss: (() -> Void)? = nil) -> some View { modifier(SensitiveVerificationModifier(enabled: enabled, onPresent: onPresent, onDismiss: onDismiss)) }
}

struct ActionRow: View {
    @Environment(AppModel.self) private var model
    let title: String
    let icon: String
    var subtitle: String? = nil
    var destructive = false
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 13) {
                Image(systemName: icon).frame(width: 24).foregroundStyle(destructive ? Brand.danger : Brand.gold)
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).foregroundStyle(destructive ? Brand.danger : Color.primary)
                    if let subtitle { Text(subtitle).font(.caption).foregroundStyle(.secondary) }
                }
                Spacer(minLength: 8)
                Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(.tertiary)
            }.frame(minHeight: 44).contentShape(Rectangle())
        }.buttonStyle(.plain).disabled(model.isBusy)
    }
}

struct AvatarView: View {
    var url: String?
    let name: String
    var size: CGFloat = 64
    var body: some View {
        AsyncImage(url: url.flatMap(URL.init(string:))) { image in
            image.resizable().scaledToFill()
        } placeholder: {
            ZStack {
                Brand.subtleGold
                Text(String(name.prefix(1)).uppercased()).font(.system(size: size * 0.4, weight: .semibold)).foregroundStyle(Brand.gold)
            }
        }.frame(width: size, height: size).clipShape(Circle()).accessibilityLabel("\(name) 的头像")
    }
}

struct InfoRow: View {
    let title: String
    let value: String
    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .firstTextBaseline) { Text(title).foregroundStyle(.secondary); Spacer(minLength: 12); Text(value).multilineTextAlignment(.trailing) }
            VStack(alignment: .leading, spacing: 5) { Text(title).foregroundStyle(.secondary); Text(value) }
        }.font(.subheadline).textSelection(.enabled)
    }
}

struct ScrollableDateText: View {
    let value: String?

    var body: some View {
        ScrollView(.horizontal) {
            Text(displayDate(value)).monospacedDigit().fixedSize(horizontal: true, vertical: false)
                .textSelection(.enabled).padding(.vertical, 2)
        }.scrollIndicators(.visible)
    }
}

struct DateInfoRow: View {
    let title: String
    let value: String?

    var body: some View {
        HStack(spacing: 12) {
            Text(title).foregroundStyle(.secondary).fixedSize()
            ScrollableDateText(value: value).accessibilityIdentifier("dateScroll-" + title)
        }.font(.subheadline)
    }
}

struct StatusPill: View {
    let title: String
    var positive = true
    var body: some View {
        Label(title, systemImage: positive ? "checkmark.circle.fill" : "exclamationmark.circle.fill")
            .font(.caption.weight(.medium)).foregroundStyle(positive ? Brand.success : Brand.gold)
            .padding(.horizontal, 10).padding(.vertical, 7)
            .background((positive ? Brand.success : Brand.gold).opacity(0.10), in: Capsule())
    }
}

struct AsyncActionButton: View {
    @Environment(\.colorScheme) private var colorScheme
    @State private var isPerforming = false
    let title: String
    var isBusy = false
    var disabled = false
    let action: () async -> Void
    var body: some View {
        Button {
            guard !isPerforming, !isBusy, !disabled else { return }
            isPerforming = true
            Task {
                defer { isPerforming = false }
                await action()
            }
        } label: {
            HStack(spacing: 10) {
                if isPerforming { ProgressView().tint(colorScheme == .dark ? .white : .black) }
                Text(title)
            }
        }.buttonStyle(GoldButtonStyle()).disabled(isPerforming || isBusy || disabled).opacity(disabled ? 0.5 : 1)
    }
}

struct FormField: View {
    let title: String
    @Binding var text: String
    var secure = false
    var keyboard: UIKeyboardType = .default
    var contentType: UITextContentType? = nil
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.subheadline.weight(.medium))
            Group {
                if secure { SecureField(title, text: $text) }
                else { TextField(title, text: $text).keyboardType(keyboard) }
            }.textContentType(contentType).textInputAutocapitalization(.never).autocorrectionDisabled()
                .padding(14).frame(minHeight: 50).background(Brand.background, in: RoundedRectangle(cornerRadius: 12))
                .accessibilityIdentifier(title)
        }
    }
}

struct AdaptivePicker<Selection: Hashable, Content: View>: View {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    let title: String
    @Binding var selection: Selection
    private let content: Content
    init(_ title: String, selection: Binding<Selection>, @ViewBuilder content: () -> Content) {
        self.title = title; _selection = selection; self.content = content()
    }
    var body: some View {
        Group {
            if dynamicTypeSize.isAccessibilitySize { Picker(title, selection: $selection) { content }.pickerStyle(.menu) }
            else { Picker(title, selection: $selection) { content }.pickerStyle(.segmented) }
        }.frame(minHeight: 44)
    }
}

struct LocalQRView: View {
    let value: String
    var body: some View {
        if let image = LocalQRCode.image(from: value) {
            Image(uiImage: image).interpolation(.none).resizable().scaledToFit()
                .padding(14).background(.white, in: RoundedRectangle(cornerRadius: 16))
                .frame(maxWidth: 260).accessibilityLabel("授权二维码")
        }
    }
}

func displayDate(_ value: String?) -> String {
    guard let value = value?.trimmingCharacters(in: .whitespacesAndNewlines), !value.isEmpty else { return "暂无记录" }
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    let date = formatter.date(from: value) ?? ISO8601DateFormatter().date(from: value)
    if let date {
        let output = DateFormatter()
        output.locale = Locale(identifier: "en_US_POSIX")
        output.dateFormat = "yyyy-MM-dd HH:mm:ss"
        return output.string(from: date)
    }
    // LocalDateTime responses have no zone; keep their original wall-clock time.
    return value.replacingOccurrences(of: #"^(\d{4}-\d{2}-\d{2})[Tt](\d{2}:)"#, with: "$1 $2", options: .regularExpression)
}

func methodLabel(_ value: String) -> String {
    switch value { case "password": "密码"; case "email-code", "email_code": "邮箱验证码"; case "passkey": "Passkey"; case "totp": "验证器 / 恢复码"; case "apple": "Apple"; case "qq": "QQ"; default: value }
}

func validEmail(_ value: String) -> Bool {
    value.trimmingCharacters(in: .whitespacesAndNewlines).range(of: #"^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$"#, options: .regularExpression) != nil
}

func validUsername(_ value: String) -> Bool {
    let value = value.trimmingCharacters(in: .whitespacesAndNewlines)
    return (3...20).contains(value.count) && value.unicodeScalars.allSatisfy {
        (65...90).contains($0.value) || (97...122).contains($0.value) || (48...57).contains($0.value) || (0x4E00...0x9FFF).contains($0.value) || $0 == "_" || $0 == "-"
    }
}

func accountRecoveryURL(code: String? = nil, environment: AppEnvironment) -> URL {
    var components = URLComponents(url: environment.webURL.appendingPathComponent("forgot-password"), resolvingAgainstBaseURL: false)!
    components.queryItems = [URLQueryItem(name: "apiBaseUrl", value: environment.apiBaseURL.absoluteString), URLQueryItem(name: "source", value: "mobile")]
    if let code, !code.isEmpty { components.queryItems?.append(URLQueryItem(name: "recoveryCode", value: code)) }
    return components.url!
}
