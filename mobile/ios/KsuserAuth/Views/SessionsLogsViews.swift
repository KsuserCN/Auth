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
    @State private var filterExpanded = false
    @State private var detail: SensitiveLogItem?
    private let types = ["", "LOGIN", "REGISTER", "SENSITIVE_VERIFY", "ADAPTIVE_POLICY", "CHANGE_PASSWORD", "CHANGE_EMAIL", "ADD_PASSKEY", "DELETE_PASSKEY", "ENABLE_TOTP", "DISABLE_TOTP"]
    var body: some View {
        PageScroll {
            AppCard {
                CardHeader(title: "安全活动记录", subtitle: "了解登录、账号修改与验证操作的结果。", icon: "clock.arrow.circlepath")
                DisclosureGroup("筛选日志", isExpanded: $filterExpanded) {
                    VStack(spacing: 12) {
                        Picker("操作类型", selection: $operation) { ForEach(types, id: \.self) { Text(operationTitle($0)).tag($0) } }
                        Picker("操作结果", selection: $result) { Text("全部结果").tag(""); Text("成功").tag("SUCCESS"); Text("失败").tag("FAILURE") }
                        AsyncActionButton(title: "应用筛选", isBusy: model.isBusy) { await reload(); filterExpanded = false }
                    }.padding(.top, 12)
                }
            }
            if model.logs.isEmpty {
                if model.isBusy { ProgressView("正在读取日志…").frame(maxWidth: .infinity).padding() }
                else { ContentUnavailableView("暂无符合条件的记录", systemImage: "doc.text.magnifyingglass", description: Text("调整筛选条件，或下拉重新加载。")) }
            }
            ForEach(model.logs) { log in
                Button { detail = log } label: {
                    AppCard {
                        HStack(alignment: .top) {
                            Image(systemName: log.result.uppercased() == "SUCCESS" ? "checkmark.circle.fill" : "exclamationmark.circle.fill").foregroundStyle(log.result.uppercased() == "SUCCESS" ? Brand.success : Brand.danger)
                            VStack(alignment: .leading, spacing: 7) {
                                Text(operationTitle(log.operationType)).font(.headline).foregroundStyle(.primary)
                                ScrollableDateText(value: log.createdAt).font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            Image(systemName: "chevron.right").font(.caption).foregroundStyle(.tertiary)
                        }
                        Text([log.ipLocation, log.browser, log.deviceType].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")).font(.caption).foregroundStyle(.secondary)
                        if let failure = log.failureReason, !failure.isEmpty { Text(failure).font(.caption).foregroundStyle(Brand.danger).lineLimit(2) }
                    }
                }.buttonStyle(.plain).accessibilityLabel("\(operationTitle(log.operationType))，\(log.result.uppercased() == "SUCCESS" ? "成功" : "失败")，\(displayDate(log.createdAt))")
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
    }
    private func reload() async { await load(page: 1) }
    private func load(page: Int) async { await model.loadLogs(page: page, operationType: operation.isEmpty ? nil : operation, result: result.isEmpty ? nil : result) }
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
                if let failure = log.failureReason { Text(failure).font(.subheadline).foregroundStyle(Brand.danger).textSelection(.enabled) }
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
