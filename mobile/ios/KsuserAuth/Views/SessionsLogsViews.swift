import SwiftUI

struct SessionsView: View {
    @Environment(AppModel.self) private var model
    @State private var logoutAll = false
    var body: some View {
        PageScroll {
            AppCard {
                CardHeader(title: "已连接设备", subtitle: "共 \(model.sessions.count) 台设备。轻点设备查看登录详情。", icon: "laptopcomputer.and.iphone")
            }
            if model.sessions.isEmpty {
                if model.isBusy { ProgressView("正在读取设备…").frame(maxWidth: .infinity).padding() }
                else { ContentUnavailableView("暂无设备记录", systemImage: "laptopcomputer.and.iphone", description: Text("下拉可以重新加载设备列表。")) }
            }
            ForEach(model.sessions) { session in
                NavigationLink {
                    SessionDetailView(session: session)
                } label: {
                    AppCard {
                        HStack(alignment: .top, spacing: 12) {
                            Image(systemName: deviceIcon(session.deviceType)).font(.title2).foregroundStyle(Brand.gold).frame(width: 34).accessibilityHidden(true)
                            VStack(alignment: .leading, spacing: 6) {
                                Text(session.browser ?? session.deviceType ?? "未知设备").font(.headline).foregroundStyle(.primary)
                                Text(session.ipLocation ?? "未知位置").font(.subheadline).foregroundStyle(.secondary).lineLimit(1)
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
                CardHeader(title: "退出所有设备", subtitle: "撤销所有会话，包括当前设备。操作完成后需要重新登录。", icon: "power")
                Button("退出所有设备", role: .destructive) { logoutAll = true }.frame(minHeight: 44).disabled(model.isBusy)
            }
        }.refreshable { await model.refreshSessions() }.task { await model.refreshSessions() }
            .sensitiveVerification()
            .confirmationDialog("退出所有设备？", isPresented: $logoutAll, titleVisibility: .visible) {
                Button("退出所有设备", role: .destructive) { Task { await model.requireSensitive(title: "退出所有设备") { await model.logout(allDevices: true) } } }
            }
    }
}

private struct SessionDetailView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var showRevokeConfirmation = false
    let session: SessionItem
    private var currentSession: SessionItem { model.sessions.first(where: { $0.id == session.id }) ?? session }

    var body: some View {
        PageScroll {
            AppCard {
                CardHeader(title: currentSession.browser ?? currentSession.deviceType ?? "未知设备", subtitle: currentSession.deviceType, icon: deviceIcon(currentSession.deviceType))
                SessionStatusPills(session: currentSession)
            }
            AppCard {
                CardHeader(title: "登录详情", icon: "info.circle")
                InfoRow(title: "登录位置", value: currentSession.ipLocation ?? "未知")
                InfoRow(title: "IP", value: currentSession.ipAddress)
                if let userAgent = currentSession.userAgent, !userAgent.isEmpty {
                    InfoRow(title: "设备信息", value: userAgent)
                }
                DateInfoRow(title: "登录时间", value: currentSession.createdAt)
                DateInfoRow(title: "最近活动", value: currentSession.lastSeenAt)
                DateInfoRow(title: "会话到期", value: currentSession.expiresAt)
            }
            if !currentSession.current {
                AppCard {
                    CardHeader(title: "撤销设备登录", subtitle: "这台设备将退出当前账号。", icon: "rectangle.portrait.and.arrow.right")
                    Button("撤销设备", role: .destructive) { showRevokeConfirmation = true }
                        .frame(minHeight: 44).disabled(model.isBusy)
                }
            }
        }
        .navigationTitle("设备详情")
        .navigationBarTitleDisplayMode(.inline)
        .confirmationDialog("撤销这台设备的登录？", isPresented: $showRevokeConfirmation, titleVisibility: .visible) {
            Button("撤销登录", role: .destructive) { Task { await model.revokeSession(id: session.id) } }
        }
        .onChange(of: model.sessions.contains(where: { $0.id == session.id })) { _, isPresent in
            if !isPresent { dismiss() }
        }
    }
}

private struct SessionStatusPills: View {
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
        if session.current { StatusPill(title: "当前设备") }
        StatusPill(title: session.online ? "在线" : "离线", positive: session.online)
    }
}

private func deviceIcon(_ value: String?) -> String {
    let value = (value ?? "").lowercased()
    if value.contains("ipad") || value.contains("tablet") { return "ipad" }
    if value.contains("iphone") || value.contains("android") || value.contains("mobile") { return "iphone" }
    return "laptopcomputer"
}

struct LogsView: View {
    @Environment(AppModel.self) private var model
    @Binding var requestedLogID: Int64?
    @State private var operation = ""
    @State private var result = ""
    @State private var showingFilters = false
    @State private var detail: SensitiveLogItem?

    private var dayGroups: [LogDayGroup] {
        var groups: [LogDayGroup] = []
        for log in model.logs {
            let day = String(displayDate(log.createdAt).prefix(10))
            if let index = groups.firstIndex(where: { $0.id == day }) {
                groups[index].logs.append(log)
            } else {
                groups.append(LogDayGroup(id: day, logs: [log]))
            }
        }
        return groups
    }

    var body: some View {
        PageScroll(spacing: 16) {
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("安全活动").font(.title3.weight(.bold))
                    Text("\(model.logsTotal) 条记录 · 最近的账号操作")
                        .font(.subheadline).foregroundStyle(.secondary)
                }
                Spacer(minLength: 12)
                Button { showingFilters = true } label: {
                    Label("筛选", systemImage: "line.3.horizontal.decrease")
                        .font(.subheadline.weight(.semibold))
                        .padding(.horizontal, 14).frame(minHeight: 40)
                        .background(Brand.card, in: Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("logFilterButton")
            }

            HStack(spacing: 8) {
                resultButton("全部", value: "")
                resultButton("成功", value: "SUCCESS")
                resultButton("失败", value: "FAILURE")
            }

            if !operation.isEmpty {
                HStack(spacing: 8) {
                    Text("操作类型：\(operationTitle(operation))")
                        .font(.subheadline).foregroundStyle(.secondary)
                    Spacer(minLength: 0)
                    Button("清除", systemImage: "xmark") {
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
                if model.isBusy { ProgressView("正在读取日志…").frame(maxWidth: .infinity).padding() }
                else { ContentUnavailableView("暂无符合条件的记录", systemImage: "doc.text.magnifyingglass", description: Text("调整筛选条件，或下拉重新加载。")) }
            }
            ForEach(dayGroups) { group in
                VStack(alignment: .leading, spacing: 8) {
                    Text(group.id).font(.subheadline.weight(.semibold))
                        .foregroundStyle(.secondary).padding(.horizontal, 4)
                    VStack(spacing: 0) {
                        ForEach(group.logs.indices, id: \.self) { index in
                            let log = group.logs[index]
                            if index > 0 { Divider().padding(.leading, 64) }
                            Button { detail = log } label: { LogListRow(log: log) }
                                .buttonStyle(.plain)
                                .accessibilityLabel("\(operationTitle(log.operationType))，\(log.result.uppercased() == "SUCCESS" ? "成功" : "失败")，\(displayDate(log.createdAt))")
                        }
                    }
                    .background(Brand.card, in: RoundedRectangle(cornerRadius: 20))
                }
            }
            if model.totalPages > 1 {
                HStack {
                    Button("上一页") { Task { await load(page: model.logsPage - 1) } }.disabled(model.logsPage <= 1 || model.isBusy).frame(minHeight: 44)
                    Spacer()
                    Text("\(model.logsPage) / \(model.totalPages)").font(.subheadline).foregroundStyle(.secondary)
                    Spacer()
                    Button("下一页") { Task { await load(page: model.logsPage + 1) } }.disabled(model.logsPage >= model.totalPages || model.isBusy).frame(minHeight: 44)
                }.padding(.horizontal)
            }
        }.refreshable { await reload() }.task(id: requestedLogID) {
            if let requestedLogID {
                await openLog(id: requestedLogID)
            } else {
                await reload()
            }
        }
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
    private func load(page: Int) async { await model.loadLogs(page: page, operationType: operation.isEmpty ? nil : operation, result: result.isEmpty ? nil : result) }

    private func openLog(id: Int64) async {
        await load(page: 1)
        guard !Task.isCancelled else { return }
        if let matchingLog = model.logs.first(where: { $0.id == id }) {
            detail = matchingLog
        } else {
            // The tapped alert may be stale or its log may have been removed; leave the user on the log list.
            model.noticeMessage = "未找到这条安全操作记录，请刷新日志列表"
        }
        requestedLogID = nil
    }
}

private struct LogDayGroup: Identifiable {
    let id: String
    var logs: [SensitiveLogItem]
}

private struct LogListRow: View {
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
    private var time: String {
        let date = displayDate(log.createdAt)
        return date.count >= 16 ? String(date.dropFirst(11).prefix(5)) : date
    }

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
                    Text(operationTitle(log.operationType)).font(.subheadline.weight(.semibold))
                        .foregroundStyle(.primary)
                    Spacer(minLength: 0)
                    Text(time).font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                }
                HStack(spacing: 6) {
                    Text(succeeded ? "成功" : "失败")
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(succeeded ? Brand.success : Brand.danger)
                    if !context.isEmpty {
                        Text("·").foregroundStyle(.tertiary)
                        Text(context).lineLimit(1).truncationMode(.tail)
                    }
                }
                .font(.caption).foregroundStyle(.secondary)
                if !succeeded, let failure = log.failureReason, !failure.isEmpty {
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
    @Environment(\.dismiss) private var dismiss
    @State private var selectedOperation: String
    @State private var selectedResult: String
    let onApply: (String, String) -> Void
    private let types = ["", "LOGIN", "REGISTER", "SENSITIVE_VERIFY", "ADAPTIVE_POLICY", "CHANGE_PASSWORD", "CHANGE_EMAIL", "ADD_PASSKEY", "DELETE_PASSKEY", "ENABLE_TOTP", "DISABLE_TOTP", "UPDATE_PROFILE", "DELETE_ACCOUNT"]

    init(operation: String, result: String, onApply: @escaping (String, String) -> Void) {
        _selectedOperation = State(initialValue: operation)
        _selectedResult = State(initialValue: result)
        self.onApply = onApply
    }

    var body: some View {
        Form {
            Section("操作结果") {
                HStack(spacing: 10) {
                    resultOption("全部", icon: "line.3.horizontal", value: "")
                    resultOption("成功", icon: "checkmark.circle", value: "SUCCESS")
                    resultOption("失败", icon: "xmark.circle", value: "FAILURE")
                }
                .listRowInsets(EdgeInsets(top: 4, leading: 0, bottom: 4, trailing: 0))
                .listRowBackground(Color.clear)
            }
            Section("操作类型") {
                Picker("操作类型", selection: $selectedOperation) {
                    ForEach(types, id: \.self) { Text(operationTitle($0)).tag($0) }
                }
                .pickerStyle(.navigationLink)
            }
        }
        .navigationTitle("筛选日志")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) { Button("取消") { dismiss() } }
            ToolbarItem(placement: .topBarTrailing) {
                Button("重置") { selectedOperation = ""; selectedResult = "" }
                    .disabled(selectedOperation.isEmpty && selectedResult.isEmpty)
            }
        }
        .safeAreaInset(edge: .bottom) {
            Button("查看记录") {
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
    @Environment(\.dismiss) private var dismiss
    let log: SensitiveLogItem
    var body: some View {
        PageScroll {
            AppCard {
                CardHeader(title: operationTitle(log.operationType), icon: logOperationIcon(log.operationType))
                InfoRow(title: "结果", value: log.result.uppercased() == "SUCCESS" ? "成功" : "失败")
                DateInfoRow(title: "时间", value: log.createdAt)
                InfoRow(title: "登录方式", value: (log.loginMethods ?? log.loginMethod.map { [$0] } ?? []).map(methodLabel).joined(separator: "、"))
                InfoRow(title: "IP", value: log.ipAddress)
                InfoRow(title: "位置", value: log.ipLocation ?? "未知")
                InfoRow(title: "浏览器", value: log.browser ?? "未知")
                InfoRow(title: "设备", value: log.deviceType ?? "未知")
                InfoRow(title: "风险评分", value: String(log.riskScore))
                InfoRow(title: "响应耗时", value: log.durationMs.map { "\($0) 毫秒" } ?? "暂无记录")
                if let action = log.actionTaken { InfoRow(title: "执行措施", value: action) }
                if let note = log.failureReason, !note.isEmpty {
                    InfoRow(title: log.result.uppercased() == "SUCCESS" ? "备注" : "失败原因", value: note)
                }
                if log.triggeredMultiErrorLock { Label("触发连续错误保护", systemImage: "lock.fill").foregroundStyle(Brand.gold) }
                if log.triggeredRateLimitLock { Label("触发请求频率保护", systemImage: "timer").foregroundStyle(Brand.gold) }
            }
        }.navigationTitle("日志详情").navigationBarTitleDisplayMode(.inline).toolbar { Button("完成") { dismiss() } }
    }
}

func operationTitle(_ value: String) -> String {
    switch value.uppercased() {
    case "": "全部操作"
    case "LOGIN": "登录"
    case "REGISTER": "注册"
    case "SENSITIVE_VERIFY": "敏感验证"
    case "ADAPTIVE_POLICY": "风控策略"
    case "CHANGE_PASSWORD": "修改密码"
    case "CHANGE_EMAIL": "修改邮箱"
    case "ADD_PASSKEY": "新增 Passkey"
    case "DELETE_PASSKEY": "删除 Passkey"
    case "ENABLE_TOTP": "启用验证器"
    case "DISABLE_TOTP": "停用验证器"
    case "UPDATE_PROFILE": "更新资料"
    case "DELETE_ACCOUNT": "注销账号"
    default: value
    }
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
