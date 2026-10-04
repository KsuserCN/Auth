import SwiftUI

struct SessionsView: View {
    @Environment(AppModel.self) private var model
    @State private var revokeTarget: SessionItem?
    @State private var logoutAll = false
    var body: some View {
        PageScroll {
            AppCard { CardHeader(title: "设备与登录", subtitle: "查看账号在哪里登录，并及时撤销不熟悉的设备。", icon: "laptopcomputer.and.iphone") }
            if model.sessions.isEmpty {
                if model.isBusy { ProgressView("正在读取设备…").frame(maxWidth: .infinity).padding() }
                else { ContentUnavailableView("暂无设备记录", systemImage: "laptopcomputer.and.iphone", description: Text("下拉可以重新加载设备列表。")) }
            }
            ForEach(model.sessions) { session in
                AppCard {
                    HStack(alignment: .top, spacing: 12) {
                        Image(systemName: deviceIcon(session.deviceType)).font(.title2).foregroundStyle(Brand.gold).frame(width: 34).accessibilityHidden(true)
                        VStack(alignment: .leading, spacing: 6) {
                            Text(session.browser ?? session.deviceType ?? "未知设备").font(.headline)
                            Text(session.userAgent ?? session.deviceType ?? "").font(.caption).foregroundStyle(.secondary).lineLimit(3)
                        }
                        Spacer(minLength: 0)
                        if !session.current { Button("撤销设备", systemImage: "trash", role: .destructive) { revokeTarget = session }.labelStyle(.iconOnly).frame(minWidth: 44, minHeight: 44).disabled(model.isBusy) }
                    }
                    ViewThatFits {
                        HStack { if session.current { StatusPill(title: "当前设备") }; StatusPill(title: session.online ? "在线" : "离线", positive: session.online) }
                        VStack(alignment: .leading) { if session.current { StatusPill(title: "当前设备") }; StatusPill(title: session.online ? "在线" : "离线", positive: session.online) }
                    }
                    InfoRow(title: "登录位置", value: session.ipLocation ?? "未知")
                    InfoRow(title: "IP", value: session.ipAddress)
                    DateInfoRow(title: "登录时间", value: session.createdAt)
                    DateInfoRow(title: "最近活动", value: session.lastSeenAt)
                    DateInfoRow(title: "会话到期", value: session.expiresAt)
                }
            }
            AppCard {
                CardHeader(title: "退出所有设备", subtitle: "撤销所有会话，包括当前设备。操作完成后需要重新登录。", icon: "power")
                Button("退出所有设备", role: .destructive) { logoutAll = true }.frame(minHeight: 44).disabled(model.isBusy)
            }
        }.refreshable { await model.refreshSessions() }.task { await model.refreshSessions() }
            .sensitiveVerification()
            .confirmationDialog("撤销这台设备的登录？", isPresented: Binding(get: { revokeTarget != nil }, set: { if !$0 { revokeTarget = nil } }), titleVisibility: .visible) {
                Button("撤销登录", role: .destructive) {
                    if let session = revokeTarget { Task { await model.revokeSession(id: session.id) } }
                    revokeTarget = nil
                }
            }
            .confirmationDialog("退出所有设备？", isPresented: $logoutAll, titleVisibility: .visible) {
                Button("退出所有设备", role: .destructive) { Task { await model.requireSensitive(title: "退出所有设备") { await model.logout(allDevices: true) } } }
            }
    }
    private func deviceIcon(_ value: String?) -> String {
        let value = (value ?? "").lowercased()
        if value.contains("ipad") || value.contains("tablet") { return "ipad" }
        if value.contains("iphone") || value.contains("android") || value.contains("mobile") { return "iphone" }
        return "laptopcomputer"
    }
}

struct LogsView: View {
    @Environment(AppModel.self) private var model
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
                            if index > 0 { Divider().padding(.leading, 54) }
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
        }.refreshable { await reload() }.task { await reload() }
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
}

private struct LogDayGroup: Identifiable {
    let id: String
    var logs: [SensitiveLogItem]
}

private struct LogListRow: View {
    let log: SensitiveLogItem
    private var succeeded: Bool { log.result.uppercased() == "SUCCESS" }
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
            Image(systemName: succeeded ? "checkmark.circle.fill" : "exclamationmark.circle.fill")
                .font(.system(size: 19)).foregroundStyle(succeeded ? Brand.success : Brand.danger)
                .frame(width: 26).padding(.top, 1).accessibilityHidden(true)
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
                Picker("操作结果", selection: $selectedResult) {
                    Text("全部").tag("")
                    Text("成功").tag("SUCCESS")
                    Text("失败").tag("FAILURE")
                }
                .pickerStyle(.segmented)
                .labelsHidden()
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
}

struct LogDetailView: View {
    @Environment(\.dismiss) private var dismiss
    let log: SensitiveLogItem
    var body: some View {
        PageScroll {
            AppCard {
                CardHeader(title: operationTitle(log.operationType), icon: "doc.text")
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
