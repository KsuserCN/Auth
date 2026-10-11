import SwiftUI
import PhotosUI

struct OverviewView: View {
    @Environment(\.locale) private var localizationLocale
    @Environment(AppModel.self) private var model
    let onNavigate: (MainDestination) -> Void
    let onScan: () -> Void
    var body: some View {
        PageScroll {
            if let user = model.user {
                VStack(alignment: .leading, spacing: 22) {
                    HStack(alignment: .center, spacing: 16) {
                        AvatarView(url: user.avatarUrl, name: user.displayName, size: 72)
                        VStack(alignment: .leading, spacing: 7) {
                            Text(L10n.text("欢迎回来，")).font(.subheadline).foregroundStyle(.secondary)
                            Text(user.username).font(.title.weight(.bold)).textSelection(.enabled)
                                .accessibilityIdentifier("welcomeUsername")
                            Text(user.email.isEmpty ? L10n.text("当前账号未绑定邮箱") : user.email).font(.caption).foregroundStyle(.secondary).textSelection(.enabled)
                        }
                    }
                    HStack { StatusPill(title: user.settings?.mfaEnabled == true ? L10n.text("双重验证已开启") : L10n.text("建议开启双重验证"), positive: user.settings?.mfaEnabled == true); Spacer() }
                    Button { onNavigate(.profile) } label: { Text(L10n.text("查看我的资料")) }.buttonStyle(GoldButtonStyle())
                }.padding(24).frame(maxWidth: .infinity, alignment: .leading)
                    .background(LinearGradient(colors: [Brand.subtleGold, Brand.card], startPoint: .topLeading, endPoint: .bottomTrailing), in: RoundedRectangle(cornerRadius: 26))

                AppCard {
                    CardHeader(title: L10n.text("保护你的每一次登录"), subtitle: L10n.text("及时检查验证方式和已登录设备"), icon: "shield.lefthalf.filled")
                    securityLine(L10n.text("双重验证"), enabled: user.settings?.mfaEnabled == true)
                    securityLine(L10n.text("异地登录检测"), enabled: user.settings?.detectUnusualLogin == true)
                    securityLine(L10n.text("敏感操作提醒"), enabled: user.settings?.notifySensitiveActionEmail == true)
                    ActionRow(title: L10n.text("管理账号安全"), icon: "lock.shield") { onNavigate(.security) }
                }

                AppCard {
                    CardHeader(title: L10n.text("快捷入口"), icon: "sparkles")
                    ActionRow(title: L10n.text("扫码授权"), icon: "qrcode.viewfinder", subtitle: L10n.text("安全批准其他设备的登录和验证")) { onScan() }
                    Divider()
                    ActionRow(title: L10n.text("已登录设备"), icon: "laptopcomputer.and.iphone") { onNavigate(.sessions) }
                    Divider()
                    NavigationLink {
                        AuthorizedAppsView()
                    } label: {
                        HStack(spacing: 13) {
                            Image(systemName: "person.crop.circle.badge.checkmark").frame(width: 24).foregroundStyle(Brand.gold)
                            Text(L10n.text("授权应用")).foregroundStyle(.primary)
                            Spacer(minLength: 8)
                            Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(.tertiary)
                        }
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("authorizedAppsRow")
                    Divider()
                    ActionRow(title: L10n.text("安全日志"), icon: "clock.arrow.circlepath") { onNavigate(.logs) }
                }

                AppCard {
                    HStack { CardHeader(title: L10n.text("让大家更好地认识你"), icon: "person.text.rectangle"); Spacer(); Text("\(user.profileCompleteness)%").font(.title2.weight(.bold)).foregroundStyle(Brand.gold) }
                    ProgressView(value: Double(user.profileCompleteness), total: 100).tint(Brand.gold)
                    Text(L10n.text("补充头像、姓名、地区和个人简介，让资料更完整。")).font(.subheadline).foregroundStyle(.secondary)
                    ActionRow(title: L10n.text("完善个人资料"), icon: "pencil") { onNavigate(.profile) }
                }
            } else { ProgressView(L10n.text("正在读取账号…")).frame(maxWidth: .infinity).padding(40) }
        }.refreshable { await model.loadDashboard() }.task { await model.loadDashboard() }
    }
    private func securityLine(_ title: String, enabled: Bool) -> some View {
        HStack { Text(title).font(.subheadline); Spacer(); Label(enabled ? L10n.text("已开启") : L10n.text("未开启"), systemImage: enabled ? "checkmark.circle.fill" : "circle").font(.caption).foregroundStyle(enabled ? Brand.success : Color.secondary) }.accessibilityElement(children: .combine)
    }
}

struct ProfileView: View {
    @Environment(\.locale) private var localizationLocale
    @Environment(AppModel.self) private var model
    @State private var editor: ProfileField?
    @State private var selectedPhoto: PhotosPickerItem?
    @State private var avatarSelection: AvatarSelection?
    @State private var photoBusy = false
    #if DEBUG
    @State private var didSeedCropFixture = false
    #endif
    var body: some View {
        PageScroll {
            if let user = model.user {
                AppCard {
                    HStack(spacing: 20) {
                        AvatarView(url: user.avatarUrl, name: user.displayName, size: 88)
                        VStack(alignment: .leading, spacing: 8) {
                            Text(user.displayName).font(.title2.weight(.bold))
                            PhotosPicker(selection: $selectedPhoto, matching: .images) {
                                Label(photoBusy ? L10n.text("上传中…") : L10n.text("更换头像"), systemImage: "photo").frame(minHeight: 44)
                            }.disabled(photoBusy || model.isBusy).accessibilityIdentifier("avatarPicker")
                        }
                    }
                    Text(L10n.text("选择图片后可拖动、缩放裁剪，确认后再上传。")).font(.caption).foregroundStyle(.secondary)
                }
                AppCard {
                    CardHeader(title: L10n.text("个人资料"), subtitle: L10n.text("轻点项目即可编辑"), icon: "person.text.rectangle")
                    ForEach(ProfileField.allCases) { field in
                        Button { editor = field } label: {
                            HStack {
                                Text(field.title).foregroundStyle(.primary)
                                Spacer()
                                if field == .birthDate { ScrollableDateText(value: field.displayValue(from: user)).foregroundStyle(.secondary) }
                                else { Text(field.displayValue(from: user)).foregroundStyle(.secondary).multilineTextAlignment(.trailing) }
                                Image(systemName: "chevron.right").font(.caption).foregroundStyle(.tertiary)
                            }.frame(minHeight: 44).contentShape(Rectangle())
                        }.buttonStyle(.plain)
                        if field != ProfileField.allCases.last { Divider() }
                    }
                }
                AppCard {
                    CardHeader(title: L10n.text("账号信息"), icon: "person.crop.rectangle")
                    InfoRow(title: L10n.text("邮箱"), value: user.email.isEmpty ? L10n.text("未绑定") : user.email)
                    InfoRow(title: "UUID", value: user.uuid)
                    DateInfoRow(title: L10n.text("最近更新"), value: user.updatedAt)
                }
                AppCard {
                    CardHeader(title: L10n.text("账号管理"), icon: "person.crop.circle")
                    NavigationLink {
                        DeleteAccountView()
                    } label: {
                        HStack(spacing: 13) {
                            Image(systemName: "person.crop.circle.badge.xmark").frame(width: 24).foregroundStyle(Brand.danger)
                            VStack(alignment: .leading, spacing: 4) {
                                Text(L10n.text("删除账号")).foregroundStyle(Brand.danger)
                                Text(L10n.text("永久删除账号及相关数据")).font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer(minLength: 8)
                            Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(.tertiary)
                        }
                        .frame(minHeight: 48)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("deleteAccountRow")
                }
            }
        }.refreshable { await model.refreshProfile() }
            .task { await model.refreshProfile() }
            .onAppear {
                #if DEBUG
                if ProcessInfo.processInfo.arguments.contains("--ui-testing"),
                   ProcessInfo.processInfo.arguments.contains("--ui-test-avatar-crop"), !didSeedCropFixture {
                    didSeedCropFixture = true
                    if let image = UIImage(named: "AppLogo") { avatarSelection = AvatarSelection(image: image) }
                }
                #endif
            }
            .sheet(item: $editor) { field in AppNavigationStack { ProfileEditorView(field: field) } }
            .sheet(item: $avatarSelection) { selection in
                AvatarCropView(image: selection.image, onCancel: { avatarSelection = nil }) { image in
                    avatarSelection = nil
                    guard let jpeg = image.jpegData(compressionQuality: 0.85) else {
                        model.reportError(AvatarImage.ReadError.invalidImage); return
                    }
                    Task {
                        photoBusy = true
                        defer { photoBusy = false }
                        await model.uploadAvatar(data: jpeg, mimeType: "image/jpeg")
                    }
                }.presentationDragIndicator(.visible)
            }
            .onChange(of: selectedPhoto) { _, photo in
                guard let photo else { return }
                Task {
                    photoBusy = true
                    defer { photoBusy = false; selectedPhoto = nil }
                    do {
                        guard let data = try await photo.loadTransferable(type: Data.self) else { throw AvatarImage.ReadError.invalidImage }
                        avatarSelection = AvatarSelection(image: try AvatarImage.prepare(data))
                    } catch { model.reportError(error) }
                }
            }
    }
}

enum ProfileField: String, CaseIterable, Identifiable {
    case username, realName, gender, birthDate, region, bio
    var id: String { rawValue }
    var title: String { switch self { case .username: L10n.text("用户名"); case .realName: L10n.text("姓名"); case .gender: L10n.text("性别"); case .birthDate: L10n.text("生日"); case .region: L10n.text("地区"); case .bio: L10n.text("个人简介") } }
    func value(from user: UserProfile) -> String { switch self { case .username: user.username; case .realName: user.meaningfulRealName ?? ""; case .gender: user.gender ?? ""; case .birthDate: user.birthDate ?? ""; case .region: user.region ?? ""; case .bio: user.bio ?? "" } }
    func displayValue(from user: UserProfile) -> String {
        let value = value(from: user)
        if value.isEmpty { return L10n.text("未填写") }
        if self == .gender { switch value { case "male": return L10n.text("男"); case "female": return L10n.text("女"); case "secret": return L10n.text("保密"); default: break } }
        return value
    }
}

struct ProfileEditorView: View {
    @Environment(\.locale) private var localizationLocale
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let field: ProfileField
    @State private var value = ""
    @State private var birthday = Date()
    private var valid: Bool { field == .birthDate || (field == .username ? validUsername(value) : !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) }
    var body: some View {
        PageScroll {
            AppCard {
                if field == .gender {
                    Picker(L10n.text("性别"), selection: $value) { Text(L10n.text("保密")).tag("secret"); Text(L10n.text("男")).tag("male"); Text(L10n.text("女")).tag("female") }.pickerStyle(.inline)
                } else if field == .birthDate {
                    DatePicker(L10n.text("生日"), selection: $birthday, in: ...Date(), displayedComponents: .date).datePickerStyle(.graphical)
                } else if field == .bio {
                    TextEditor(text: $value).frame(minHeight: 170).padding(8).background(Brand.background, in: RoundedRectangle(cornerRadius: 12)).accessibilityLabel(L10n.text("个人简介"))
                } else { FormField(title: field.title, text: $value) }
                AsyncActionButton(title: L10n.text("保存"), isBusy: model.isBusy, disabled: !valid) {
                    if field == .birthDate {
                        let formatter = DateFormatter(); formatter.dateFormat = "yyyy-MM-dd"; value = formatter.string(from: birthday)
                    }
                    if field == .username, value != model.user?.username, !(await model.checkUsername(value)) { return }
                    await save()
                }
            }
        }.navigationTitle(L10n.format("编辑{0}", [String(describing: field.title)])).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L10n.text("取消")) { dismiss() } } }
            .onAppear {
                if let user = model.user { value = field.value(from: user) }
                if field == .gender, value.isEmpty { value = "secret" }
                let formatter = DateFormatter(); formatter.dateFormat = "yyyy-MM-dd"; birthday = formatter.date(from: value) ?? Date()
            }
    }
    private func save() async {
        await model.updateProfile(key: field.rawValue, value: value)
        if model.errorMessage == nil { dismiss() }
    }
}
