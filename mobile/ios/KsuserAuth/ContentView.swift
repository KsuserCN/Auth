import SwiftUI
import AuthenticationServices

enum MainDestination: String, CaseIterable, Identifiable {
    case home, profile, security, sessions, logs
    var id: String { rawValue }
    var title: String { switch self { case .home: "概览"; case .profile: "资料"; case .security: "安全"; case .sessions: "会话"; case .logs: "日志" } }
    var icon: String { switch self { case .home: "square.grid.2x2"; case .profile: "person.crop.circle"; case .security: "shield.lefthalf.filled"; case .sessions: "laptopcomputer.and.iphone"; case .logs: "clock.arrow.circlepath" } }
}

private enum ChallengeSheet: Identifiable {
    case mfa(MFAChallenge), oauth(PendingOAuth), sensitive(SensitiveRequest), qr(QRConfirmation), bridge(BridgeConfirmation), application(MobileAuthorizationContext)
    var id: String { switch self { case .mfa(let x): "mfa-" + x.id; case .oauth(let x): "oauth-" + x.id; case .sensitive(let x): "sensitive-" + x.id.uuidString; case .qr(let x): "qr-" + x.id; case .bridge(let x): "bridge-" + x.id; case .application(let x): "application-" + x.id } }
}

struct ContentView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.horizontalSizeClass) private var sizeClass
    @Environment(\.scenePhase) private var scenePhase
    @AppStorage("appearance") private var appearance = AppTheme.system.rawValue
    @State private var destination: MainDestination = .home
    @State private var showScanner = false
    @State private var showAbout = false
    @State private var presentedChallengeID: String?
    @State private var pendingBrowserPage: BrowserPage?
    @State private var browserPage: BrowserPage?
    @State private var pendingPushLogID: Int64?

    private var challenge: Binding<ChallengeSheet?> {
        Binding(get: {
            if model.serviceAvailability.isUnavailable { return nil }
            if let x = model.mfaChallenge { return .mfa(x) }
            if let x = model.qrConfirmation { return .qr(x) }
            if let x = model.bridgeConfirmation, model.isAuthenticated { return .bridge(x) }
            if let x = model.mobileAuthorization, model.isAuthenticated { return .application(x) }
            if let x = model.pendingOAuth { return .oauth(x) }
            return nil
        }, set: { if $0 == nil { cancelChallenge(id: presentedChallengeID) } })
    }

    var body: some View {
        Group {
            if model.serviceAvailability.isUnavailable { ServiceUnavailableOverlay() }
            else if model.isAuthenticated { shell }
            else { AppNavigationStack { AuthenticationView() } }
        }
        .tint(Brand.gold)
        .preferredColorScheme(AppTheme(rawValue: appearance)?.colorScheme)
        .sheet(item: challenge, onDismiss: {
            browserPage = pendingBrowserPage; pendingBrowserPage = nil
        }) { sheet in
            AppNavigationStack {
                switch sheet {
                case .application(let x): ApplicationConsentView(context: x)
                case .mfa(let x): MFAVerificationView(challenge: x)
                case .oauth(let x): PendingOAuthView(pending: x)
                case .sensitive(let x): SensitiveVerificationView(request: x)
                case .qr(let x): QRConfirmationView(confirmation: x)
                case .bridge(let x): BridgeConfirmationView(confirmation: x, onReturn: { pendingBrowserPage = BrowserPage(url: $0) })
                }
            }.presentationDragIndicator(.visible).interactiveDismissDisabled(model.mobileAuthorization != nil).onAppear { presentedChallengeID = sheet.id }
        }
        .background { SafariPresenter(page: $browserPage).frame(width: 0, height: 0) }
        .sheet(isPresented: $showScanner) {
            ScannerSheet { value in
                showScanner = false
                Task { await model.previewQRCode(value) }
            }
        }
        .sheet(isPresented: $showAbout) { AppNavigationStack { AboutView() } }
        .onOpenURL(perform: handleIncomingURL)
        .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in if let url = activity.webpageURL { handleIncomingURL(url) } }
        .onReceive(NotificationCenter.default.publisher(for: AppDelegate.shortcutNotification)) { notification in
            let type = AppDelegate.takePendingShortcutType() ?? (notification.object as? String)
            if let type { performShortcut(type) }
        }
        .onReceive(NotificationCenter.default.publisher(for: AppDelegate.pushTokenNotification)) { _ in
            Task { await model.synchronizePushNotifications(requestPermission: false) }
        }
        .onReceive(NotificationCenter.default.publisher(for: AppDelegate.pushFailedNotification)) { _ in
            model.pushNotifications.registrationFailed()
        }
        .onReceive(NotificationCenter.default.publisher(for: AppDelegate.pushOpenedNotification)) { _ in openPendingPush() }
        .onChange(of: model.user?.uuid) { _, _ in openPendingPush() }
        .task(id: model.user?.uuid) { await model.loadMobileAuthorization() }
        .onChange(of: model.serviceAvailability.isUnavailable) { _, unavailable in
            guard unavailable else { return }
            showScanner = false
            showAbout = false
            browserPage = nil
            pendingBrowserPage = nil
        }
        .onChange(of: scenePhase) { _, phase in
            guard phase == .active else { return }
            if let type = AppDelegate.takePendingShortcutType() { performShortcut(type) }
            openPendingPush()
            Task { await model.validateAppleCredential(); await model.synchronizePushNotifications(requestPermission: false) }
        }
        .onReceive(NotificationCenter.default.publisher(for: ASAuthorizationAppleIDProvider.credentialRevokedNotification)) { _ in Task { await model.handleAppleCredentialRevocation() } }
        .task {
            await model.connectServiceAvailabilityMonitor()
            await model.restoreSession()
            openPendingPush()
            if let type = AppDelegate.takePendingShortcutType() { performShortcut(type) }
        }
        .disabled(model.serviceAvailability.isUnavailable)
        .task(id: model.serviceAvailability.isUnavailable) {
            guard model.serviceAvailability.isUnavailable else { return }
            while !Task.isCancelled && model.serviceAvailability.isUnavailable {
                do { try await Task.sleep(for: .seconds(15)) }
                catch { return }
                guard !Task.isCancelled else { return }
                await model.probeServiceAvailability()
            }
        }
    }

    private func openPendingPush() {
        guard model.isAuthenticated, let accountID = AppDelegate.pendingPushAccountID else { return }
        AppDelegate.pendingPushAccountID = nil
        guard accountID == model.user?.uuid else {
            AppDelegate.pendingPushEventID = nil
            return
        }
        pendingPushLogID = AppDelegate.pendingPushEventID
        AppDelegate.pendingPushEventID = nil
        showAbout = false; destination = .logs
    }

    private var shell: some View {
        Group {
            if sizeClass == .regular {
                NavigationSplitView {
                    List {
                        ForEach(MainDestination.allCases) { entry in
                            Button { destination = entry } label: {
                                Label(entry.title, systemImage: entry.icon).foregroundStyle(destination == entry ? Brand.gold : Color.primary).frame(minHeight: 44)
                            }.listRowBackground(destination == entry ? Brand.subtleGold : nil)
                        }
                    }.navigationTitle("Ksuser 安全").listStyle(.sidebar)
                    .safeAreaInset(edge: .bottom) {
                        HStack { Button("扫码", systemImage: "qrcode.viewfinder") { showScanner = true }; Spacer(); Button("关于", systemImage: "info.circle") { showAbout = true } }.padding()
                    }
                } detail: { AppNavigationStack { destinationView(destination).navigationTitle(destination.title).toolbar { shellToolbar } } }
            } else {
                TabView(selection: $destination) {
                    ForEach(MainDestination.allCases) { entry in
                        AppNavigationStack { destinationView(entry).navigationTitle(entry.title).toolbar { shellToolbar } }
                            .tabItem { Label(entry.title, systemImage: entry.icon) }.tag(entry)
                    }
                }
            }
        }
    }

    @ViewBuilder private func destinationView(_ item: MainDestination) -> some View {
        switch item {
        case .home: OverviewView(onNavigate: { destination = $0 }, onScan: { showScanner = true })
        case .profile: ProfileView()
        case .security: SecurityView()
        case .sessions: SessionsView()
        case .logs: LogsView(requestedLogID: $pendingPushLogID)
        }
    }

    @ToolbarContentBuilder private var shellToolbar: some ToolbarContent {
        ToolbarItemGroup(placement: .topBarTrailing) {
            Button("扫码", systemImage: "qrcode.viewfinder") { showScanner = true }.accessibilityIdentifier("scanButton")
            Button("关于与设置", systemImage: "gearshape") { showAbout = true }.accessibilityIdentifier("aboutButton")
        }
    }

    private func cancelChallenge(id: String?) {
        guard let id else { return }
        if let item = model.mfaChallenge, id == "mfa-" + item.id { model.cancelMFA() }
        else if let item = model.qrConfirmation, id == "qr-" + item.id { model.dismissQR() }
        else if let item = model.bridgeConfirmation, id == "bridge-" + item.id { Task { await model.cancelBridge() } }
        else if let item = model.mobileAuthorization, id == "application-" + item.id { model.dismissMobileAuthorization() }
        else if let item = model.pendingOAuth, id == "oauth-" + item.id { model.cancelPendingOAuth() }
    }

    private func handleIncomingURL(_ url: URL) {
        guard !NativeAuthenticationProvider.handleCallback(url) else { return }
        Task { await model.handleURL(url) }
    }

    private func performShortcut(_ type: String) {
        switch type {
        case AppDelegate.scanShortcut:
            showScanner = true
        case AppDelegate.securityShortcut:
            destination = .security
        case AppDelegate.sessionsShortcut:
            destination = .sessions
        case AppDelegate.profileShortcut:
            destination = .profile
        default:
            break
        }
    }
}
