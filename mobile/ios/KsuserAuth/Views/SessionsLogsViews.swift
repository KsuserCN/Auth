import SwiftUI

struct SessionsView: View {
    @Environment(\.locale) private var localizationLocale
    @Environment(AppModel.self) private var model
    @State private var logoutAll = false
    var body: some View {
        PageScroll {
            AppCard {
                CardHeader(title: L10n.text("已连接设备"), subtitle: L10n.format("共 {0} 台设备。轻点设备查看登录详情。", [String(describing: model.sessions.count)]), icon: "laptopcomputer.and.iphone")
            }
            if model.sessions.isEmpty {
                if model.isBusy { ProgressView(L10n.text("正在读取设备…")).frame(maxWidth: .infinity).padding() }
                else { ContentUnavailableView(L10n.text("暂无设备记录"), systemImage: "laptopcomputer.and.iphone", description: Text(L10n.text("下拉可以重新加载设备列表。"))) }
            }
            ForEach(model.sessions) { session in
                NavigationLink {
                    SessionDetailView(session: session)
                } label: {
                    AppCard {
                        HStack(alignment: .top, spacing: 12) {
                            Image(systemName: deviceIcon(for: session)).font(.title2).foregroundStyle(Brand.gold).frame(width: 34).accessibilityHidden(true)
                            VStack(alignment: .leading, spacing: 6) {
                                Text(session.browser ?? session.deviceType ?? L10n.text("未知设备")).font(.headline).foregroundStyle(.primary)
                                Text(session.ipLocation ?? L10n.text("未知位置")).font(.subheadline).foregroundStyle(.secondary).lineLimit(1)
                            }
                            Spacer(minLength: 0)
                            Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(.tertiary).accessibilityHidden(true)
                        }
                        SessionStatusPills(session: session)
                    }
                    .fixedSize(horizontal: false, vertical: true)
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("sessionRow-\(session.id)")
            }
            AppCard {
                CardHeader(title: L10n.text("退出所有设备"), subtitle: L10n.text("撤销所有会话，包括当前设备。操作完成后需要重新登录。"), icon: "power")
                Button(L10n.text("退出所有设备"), role: .destructive) { logoutAll = true }
                    .frame(minHeight: 44)
                    .disabled(model.isBusy)
                    .confirmationDialog(L10n.text("退出所有设备？"), isPresented: $logoutAll, titleVisibility: .visible) {
                        Button("退出所有设备", role: .destructive) { Task { await model.requireSensitive(title: "退出所有设备") { await model.logout(allDevices: true) } } }
                    }
            }
        }.refreshable { await model.refreshSessions() }.task { await model.refreshSessions() }
            .sensitiveVerification()
    }
}

private struct SessionDetailView: View {
    @Environment(\.locale) private var localizationLocale
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var showRevokeConfirmation = false
    let session: SessionItem
    private var currentSession: SessionItem { model.sessions.first(where: { $0.id == session.id }) ?? session }

    var body: some View {
        PageScroll {
            AppCard {
                CardHeader(title: currentSession.browser ?? currentSession.deviceType ?? L10n.text("未知设备"), subtitle: currentSession.deviceType, icon: deviceIcon(for: currentSession))
                SessionStatusPills(session: currentSession)
            }
            AppCard {
                CardHeader(title: L10n.text("登录详情"), icon: "info.circle")
                InfoRow(title: L10n.text("登录位置"), value: currentSession.ipLocation ?? L10n.text("未知"))
                InfoRow(title: "IP", value: currentSession.ipAddress)
                if let platform = mobilePlatform(for: currentSession) {
                    InfoRow(title: L10n.text("设备类型"), value: platform)
                    if platform == "iOS" {
                        InfoRow(title: L10n.text("iOS 版本"), value: iosVersion(for: currentSession) ?? L10n.text("未知"))
                    }
                } else if let userAgent = currentSession.userAgent, !userAgent.isEmpty {
                    InfoRow(title: L10n.text("设备信息"), value: userAgent)
                }
                DateInfoRow(title: L10n.text("登录时间"), value: currentSession.createdAt)
                DateInfoRow(title: L10n.text("最近活动"), value: currentSession.lastSeenAt)
                DateInfoRow(title: L10n.text("会话到期"), value: currentSession.expiresAt)
            }
            if !currentSession.current {
                AppCard {
                    CardHeader(title: L10n.text("撤销设备登录"), subtitle: L10n.text("这台设备将退出当前账号。"), icon: "rectangle.portrait.and.arrow.right")
                    Button(L10n.text("撤销设备"), role: .destructive) { showRevokeConfirmation = true }
                        .frame(minHeight: 44)
                        .disabled(model.isBusy)
                        .confirmationDialog(L10n.text("撤销这台设备的登录？"), isPresented: $showRevokeConfirmation, titleVisibility: .visible) {
                            Button(L10n.text("撤销登录"), role: .destructive) { Task { await model.revokeSession(id: session.id) } }
                        }
                }
            }
        }
        .navigationTitle(L10n.text("设备详情"))
        .navigationBarTitleDisplayMode(.inline)
        .onChange(of: model.sessions.contains(where: { $0.id == session.id })) { _, isPresent in
            if !isPresent { dismiss() }
        }
    }
}

private struct SessionStatusPills: View {
    @Environment(\.locale) private var localizationLocale
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    let session: SessionItem

    var body: some View {
        if dynamicTypeSize.isAccessibilitySize {
            VStack(alignment: .leading, spacing: 8) { pills }
        } else {
            HStack(spacing: 8) { pills }
        }
    }

    @ViewBuilder private var pills: some View {
        if session.current { StatusPill(title: L10n.text("当前设备")) }
        StatusPill(title: session.online ? L10n.text("在线") : L10n.text("离线"), positive: session.online)
    }
}

private func deviceIcon(for session: SessionItem) -> String {
    let value = "\(session.deviceType ?? "") \(session.userAgent ?? "")".lowercased()
    if value.contains("ipad") || value.contains("tablet") { return "ipad" }
    if mobilePlatform(for: session) != nil || value.contains("mobile") { return "iphone" }
    return "laptopcomputer"
}

private func mobilePlatform(for session: SessionItem) -> String? {
    let value = "\(session.deviceType ?? "") \(session.userAgent ?? "")".lowercased()
    if value.contains("ios") || value.contains("iphone") || value.contains("ipad") { return "iOS" }
    if value.contains("android") { return "Android" }
    if value.contains("mobile") || value.contains("tablet") { return L10n.text("移动设备") }
    return nil
}

private func iosVersion(for session: SessionItem) -> String? {
    guard let userAgent = session.userAgent else { return nil }
    let pattern = #"(?i)(?:iOS|iPhone\s+OS|CPU\s+OS)\s+([0-9]+(?:[._][0-9]+)*)"#
    guard let regex = try? NSRegularExpression(pattern: pattern),
          let match = regex.firstMatch(in: userAgent, range: NSRange(userAgent.startIndex..., in: userAgent)),
          let range = Range(match.range(at: 1), in: userAgent) else { return nil }
    return userAgent[range].replacingOccurrences(of: "_", with: ".")
}

struct LogsView: View {
    @Environment(\.locale) private var localizationLocale
    @Environment(AppModel.self) private var model
    @Binding var requestedLogID: Int64?
    @State private var operation = ""
    @State private var result = ""
    @State private var showingFilters = false
    @State private var detail: SensitiveLogItem?

    private var dayGroups: [LogDayGroup] {
        var groups: [LogDayGroup] = []
        for log in model.logs {
            let date = DatePresentation(log.createdAt)
            if let index = groups.firstIndex(where: { $0.id == date.dayKey }) {
                groups[index].logs.append(log)
            } else {
                groups.append(LogDayGroup(id: date.dayKey, title: date.dateText, logs: [log]))
            }
        }
        return groups
    }

    var body: some View {
        PageScroll(spacing: 16) {
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(L10n.text("安全活动")).font(.title3.weight(.bold))
                    Text(L10n.format("{0} 条记录 · 最近的账号操作", [String(describing: model.logsTotal)]))
                        .font(.subheadline).foregroundStyle(.secondary)
                }
                Spacer(minLength: 12)
                Button { showingFilters = true } label: {
                    Label(L10n.text("筛选"), systemImage: "line.3.horizontal.decrease")
                        .font(.subheadline.weight(.semibold))
                        .padding(.horizontal, 14).frame(minHeight: 40)
                        .background(Brand.card, in: Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("logFilterButton")
            }

            HStack(spacing: 8) {
                resultButton(L10n.text("全部"), value: "")
                resultButton(L10n.text("成功"), value: "SUCCESS")
                resultButton(L10n.text("失败"), value: "FAILURE")
            }

            if !operation.isEmpty {
                HStack(spacing: 8) {
                    Text(L10n.format("操作类型：{0}", [String(describing: operationTitle(operation))]))
                        .font(.subheadline).foregroundStyle(.secondary)
                    Spacer(minLength: 0)
                    Button(L10n.text("清除"), systemImage: "xmark") {
                        operation = ""
                        Task { await reload() }
                    }
                    .labelStyle(.titleAndIcon)
                    .font(.subheadline)
                    .disabled(model.isBusy)
                }
                .padding(.horizontal, 4)
            }

            if model.logs.isEmpty {
                if model.isBusy { ProgressView(L10n.text("正在读取日志…")).frame(maxWidth: .infinity).padding() }
                else { ContentUnavailableView(L10n.text("暂无符合条件的记录"), systemImage: "doc.text.magnifyingglass", description: Text(L10n.text("调整筛选条件，或下拉重新加载。"))) }
            }
            ForEach(dayGroups) { group in
                VStack(alignment: .leading, spacing: 8) {
                    Text(group.title).font(.subheadline.weight(.semibold))
                        .foregroundStyle(.secondary).padding(.horizontal, 4)
                    VStack(spacing: 0) {
                        ForEach(group.logs.indices, id: \.self) { index in
                            let log = group.logs[index]
                            if index > 0 { Divider().padding(.leading, 64) }
                            Button { detail = log } label: { LogListRow(log: log) }
                                .buttonStyle(.plain)
                                .accessibilityIdentifier("logRow-\(log.id)")
                                .accessibilityLabel(L10n.format("log.accessibilitySummary", [String(describing: SecurityLogPresentation.title(log)), String(describing: log.result.uppercased() == "SUCCESS" ? (log.operationType.uppercased() == "ADAPTIVE_POLICY" ? SecurityLogPresentation.text("ui.executed") : L10n.text("成功")) : L10n.text("失败")), String(describing: displayDate(log.createdAt))]))
                        }
                    }
                    .background(Brand.card, in: RoundedRectangle(cornerRadius: 20))
                }
            }
            if model.totalPages > 1 {
                HStack {
                    Button(L10n.text("上一页")) { Task { await load(page: model.logsPage - 1) } }.disabled(model.logsPage <= 1 || model.isBusy).frame(minHeight: 44)
                    Spacer()
                    Text("\(model.logsPage) / \(model.totalPages)").font(.subheadline).foregroundStyle(.secondary)
                    Spacer()
                    Button(L10n.text("下一页")) { Task { await load(page: model.logsPage + 1) } }.disabled(model.logsPage >= model.totalPages || model.isBusy).frame(minHeight: 44)
                }.padding(.horizontal)
            }
        }.refreshable { await reload() }.task(id: model.user?.uuid) {
            // Notification details are fetched independently; their errors survive list loading.
            await load(page: 1, reportError: requestedLogID == nil)
        }.task(id: requestedLogID) {
            guard let requestedLogID else { return }
            await openLog(id: requestedLogID)
        }
            .onChange(of: model.user?.uuid) { _, _ in detail = nil; showingFilters = false }
            .sheet(item: $detail) { log in AppNavigationStack { LogDetailView(log: log) } }
            .sheet(isPresented: $showingFilters) {
                AppNavigationStack {
                    LogFilterView(operation: operation, result: result) { selectedOperation, selectedResult in
                        operation = selectedOperation
                        result = selectedResult
                        Task { await reload() }
                    }
                }
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
            }
    }
    private func resultButton(_ title: String, value: String) -> some View {
        Button {
            guard result != value else { return }
            result = value
            Task { await reload() }
        } label: {
            Text(title).font(.subheadline.weight(result == value ? .semibold : .regular))
                .foregroundStyle(result == value ? Brand.gold : Color.secondary)
                .frame(maxWidth: .infinity, minHeight: 40)
                .background(result == value ? Brand.subtleGold : Brand.card, in: Capsule())
        }
        .buttonStyle(.plain)
        .disabled(model.isBusy)
        .accessibilityAddTraits(result == value ? .isSelected : [])
    }
    private func reload() async { await load(page: 1) }
    private func load(page: Int, reportError: Bool = true) async {
        await model.loadLogs(page: page, operationType: operation.isEmpty ? nil : operation, result: result.isEmpty ? nil : result, reportError: reportError)
    }

    private func openLog(id: Int64) async {
        let accountID = model.user?.uuid
        showingFilters = false
        detail = nil
        let matchingLog = await model.fetchLog(id: id)
        guard !Task.isCancelled, requestedLogID == id, accountID == model.user?.uuid else { return }
        if let matchingLog {
            detail = matchingLog
        } else if model.errorMessage == nil {
            // The tapped alert may be stale or its log may have been removed; leave the user on the log list.
            model.noticeMessage = "未找到这条安全操作记录，请刷新日志列表"
        }
        requestedLogID = nil
    }
}

private struct LogDayGroup: Identifiable {
    let id: String
    let title: String
    var logs: [SensitiveLogItem]
}

private struct LogListRow: View {
    @Environment(\.locale) private var localizationLocale
    let log: SensitiveLogItem
    private var succeeded: Bool { log.result.uppercased() == "SUCCESS" }
    private var iconColor: Color {
        switch log.operationType.uppercased() {
        case "LOGIN", "REGISTER": Brand.gold
        case "SENSITIVE_VERIFY", "ENABLE_TOTP", "DISABLE_TOTP": .purple
        case "ADAPTIVE_POLICY": .blue
        case "DELETE_PASSKEY", "DELETE_ACCOUNT": Brand.danger
        default: .orange
        }
    }
    private var context: String {
        [log.ipLocation, log.browser, log.deviceType]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .joined(separator: " · ")
    }
    private var time: String { DatePresentation(log.createdAt).timeText }

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: logOperationIcon(log.operationType))
                .font(.system(size: 18, weight: .medium))
                .foregroundStyle(iconColor)
                .frame(width: 36, height: 36)
                .background(iconColor.opacity(0.12), in: RoundedRectangle(cornerRadius: 11))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 6) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(SecurityLogPresentation.title(log)).font(.subheadline.weight(.semibold))
                        .foregroundStyle(.primary)
                    Spacer(minLength: 0)
                    Text(time).font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                }
                HStack(spacing: 6) {
                    Text(succeeded ? (log.operationType.uppercased() == "ADAPTIVE_POLICY" ? SecurityLogPresentation.text("ui.executed") : L10n.text("成功")) : L10n.text("失败"))
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(succeeded ? Brand.success : Brand.danger)
                    if !context.isEmpty {
                        Text("·").foregroundStyle(.tertiary)
                        Text(context).lineLimit(1).truncationMode(.tail)
                    }
                }
                .font(.caption).foregroundStyle(.secondary)
                if !SecurityLogPresentation.methods(log).isEmpty {
                    Text(SecurityLogPresentation.methods(log).joined(separator: " · "))
                        .font(.caption).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
                }
                if log.operationType.uppercased() == "ADAPTIVE_POLICY" {
                    Text(SecurityLogPresentation.text("ui.system")).font(.caption2).foregroundStyle(.secondary)
                }
                if !succeeded, let failure = log.displayFailureReason, !failure.isEmpty {
                    Text(failure).font(.caption).foregroundStyle(Brand.danger).lineLimit(2)
                }
            }
            Image(systemName: "chevron.right").font(.caption2).foregroundStyle(.tertiary)
                .padding(.top, 4).accessibilityHidden(true)
        }
        .padding(.horizontal, 16).padding(.vertical, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
    }
}

private struct LogFilterView: View {
    @Environment(\.locale) private var localizationLocale
    @Environment(\.dismiss) private var dismiss
    @State private var selectedOperation: String
    @State private var selectedResult: String
    let onApply: (String, String) -> Void
    private let types = ["", "LOGIN", "REGISTER", "SENSITIVE_VERIFY", "ADAPTIVE_POLICY", "CHANGE_PASSWORD", "CHANGE_EMAIL", "ADD_PASSKEY", "DELETE_PASSKEY", "ENABLE_TOTP", "DISABLE_TOTP", "BIND_OAUTH", "UNBIND_OAUTH", "UPDATE_PROFILE", "DELETE_ACCOUNT"]

    init(operation: String, result: String, onApply: @escaping (String, String) -> Void) {
        _selectedOperation = State(initialValue: operation)
        _selectedResult = State(initialValue: result)
        self.onApply = onApply
    }

    var body: some View {
        Form {
            Section(L10n.text("操作结果")) {
                HStack(spacing: 10) {
                    resultOption(L10n.text("全部"), icon: "line.3.horizontal", value: "")
                    resultOption(L10n.text("成功"), icon: "checkmark.circle", value: "SUCCESS")
                    resultOption(L10n.text("失败"), icon: "xmark.circle", value: "FAILURE")
                }
                .listRowInsets(EdgeInsets(top: 4, leading: 0, bottom: 4, trailing: 0))
                .listRowBackground(Color.clear)
            }
            Section(L10n.text("操作类型")) {
                Picker(L10n.text("操作类型"), selection: $selectedOperation) {
                    ForEach(types, id: \.self) { Text(operationTitle($0)).tag($0) }
                }
                .pickerStyle(.navigationLink)
            }
        }
        .navigationTitle(L10n.text("筛选日志"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) { Button(L10n.text("取消")) { dismiss() } }
            ToolbarItem(placement: .topBarTrailing) {
                Button(L10n.text("重置")) { selectedOperation = ""; selectedResult = "" }
                    .disabled(selectedOperation.isEmpty && selectedResult.isEmpty)
            }
        }
        .safeAreaInset(edge: .bottom) {
            Button(L10n.text("查看记录")) {
                onApply(selectedOperation, selectedResult)
                dismiss()
            }
            .buttonStyle(GoldButtonStyle())
            .padding(20)
            .background(Brand.background)
        }
    }

    private func resultOption(_ title: String, icon: String, value: String) -> some View {
        let isSelected = selectedResult == value
        return Button {
            selectedResult = value
        } label: {
            VStack(spacing: 8) {
                Image(systemName: icon).font(.system(size: 20, weight: .medium))
                Text(title).font(.subheadline.weight(isSelected ? .semibold : .medium))
            }
            .foregroundStyle(isSelected ? Brand.gold : Color.primary)
            .frame(maxWidth: .infinity, minHeight: 76)
            .background(isSelected ? Brand.subtleGold : Brand.card, in: RoundedRectangle(cornerRadius: 16))
            .overlay {
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(isSelected ? Brand.gold.opacity(0.55) : Color.primary.opacity(0.06), lineWidth: 1)
            }
            .contentShape(RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(value.isEmpty ? "logResultFilter-all" : "logResultFilter-\(value)")
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

struct LogDetailView: View {
    @Environment(\.locale) private var localizationLocale
    @Environment(\.dismiss) private var dismiss
    let log: SensitiveLogItem
    var body: some View {
        PageScroll {
            AppCard {
                CardHeader(title: SecurityLogPresentation.title(log), icon: logOperationIcon(log.operationType))
                InfoRow(title: L10n.text("结果"), value: log.result.uppercased() == "SUCCESS" ? (log.operationType.uppercased() == "ADAPTIVE_POLICY" ? SecurityLogPresentation.text("ui.executed") : L10n.text("成功")) : L10n.text("失败"))
                DateInfoRow(title: L10n.text("时间"), value: log.createdAt)
                if !SecurityLogPresentation.methods(log).isEmpty {
                    InfoRow(title: SecurityLogPresentation.text(log.operationType.uppercased().contains("OAUTH") ? "ui.provider" : "ui.method"), value: SecurityLogPresentation.methods(log).joined(separator: " · "))
                }
                InfoRow(title: "IP", value: log.ipAddress)
                InfoRow(title: L10n.text("位置"), value: log.ipLocation ?? L10n.text("未知"))
                InfoRow(title: L10n.text("浏览器"), value: log.browser ?? L10n.text("未知"))
                InfoRow(title: L10n.text("设备"), value: log.deviceType ?? L10n.text("未知"))
                InfoRow(title: L10n.text("风险评分"), value: "\(SecurityLogPresentation.text(log.riskScore >= 70 ? "ui.high" : log.riskScore >= 40 ? "ui.medium" : "ui.low")) · \(log.riskScore)")
                InfoRow(title: L10n.text("响应耗时"), value: log.durationMs.map { L10n.format("{0} 毫秒", [String(describing: $0)]) } ?? L10n.text("暂无记录"))
                if let action = log.actionTaken { InfoRow(title: SecurityLogPresentation.text("ui.action"), value: SecurityLogPresentation.action(action)) }
                if let note = log.displayFailureReason, !note.isEmpty {
                    InfoRow(title: log.result.uppercased() == "SUCCESS" ? L10n.text("备注") : L10n.text("失败原因"), value: note)
                }
                if log.triggeredMultiErrorLock { Label(L10n.text("触发连续错误保护"), systemImage: "lock.fill").foregroundStyle(Brand.gold) }
                if log.triggeredRateLimitLock { Label(L10n.text("触发请求频率保护"), systemImage: "timer").foregroundStyle(Brand.gold) }
            }
        }.navigationTitle(L10n.text("日志详情")).navigationBarTitleDisplayMode(.inline).toolbar { Button(L10n.text("完成")) { dismiss() } }
    }
}

func operationTitle(_ value: String) -> String {
    value.isEmpty ? L10n.text("全部操作") : SecurityLogPresentation.operation(value)
}

private func logOperationIcon(_ value: String) -> String {
    switch value.uppercased() {
    case "LOGIN": "rectangle.portrait.and.arrow.right"
    case "REGISTER": "person.crop.circle.badge.plus"
    case "SENSITIVE_VERIFY": "lock.shield"
    case "ADAPTIVE_POLICY": "shield.lefthalf.filled"
    case "CHANGE_PASSWORD": "key.horizontal"
    case "CHANGE_EMAIL": "envelope"
    case "ADD_PASSKEY": "key.fill"
    case "DELETE_PASSKEY": "minus.circle"
    case "ENABLE_TOTP": "lock.fill"
    case "DISABLE_TOTP": "lock.open"
    case "UPDATE_PROFILE": "person.crop.circle"
    case "DELETE_ACCOUNT": "trash"
    default: "doc.text"
    }
}
