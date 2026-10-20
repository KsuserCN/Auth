#if canImport(UIKit)
import UIKit
import AuthenticationServices

/// Retain one client per application. Forward Universal Links before resuming a saved transaction.
@available(iOS 17.4, *)
@MainActor public final class KsuserAuthClient {
    private let configuration: KsuserAuthConfiguration
    private let storage: any SDKTransactionStoring
    private let http: SDKHTTP
    private var pending: AuthorizationTransaction?
    private var continuation: CheckedContinuation<KsuserAuthorizationResult, Error>?
    private var browser: ASWebAuthenticationSession?
    private var presentationProvider: (any ASWebAuthenticationPresentationContextProviding)?
    private var timeoutTask: Task<Void, Never>?
    private var processingTask: Task<Void, Never>?
    private var leaseID = UUID()
    private var starting = false
    private var generation = 0

    public convenience init(configuration: KsuserAuthConfiguration) throws {
        try self.init(configuration: configuration, storage: TransactionStore(configuration: configuration), transport: SDKURLSessionTransport())
    }
    init(configuration: KsuserAuthConfiguration, storage: any SDKTransactionStoring, transport: any SDKTransport) throws {
        try configuration.validate()
        self.configuration = configuration; self.storage = storage
        http = SDKHTTP(configuration: configuration, transport: transport)
        pending = try PendingAuthorizationRecovery.load(from: storage, configuration: configuration)
    }

    public func authorize(scopes: [String]? = nil,
                          presentationContextProvider: any ASWebAuthenticationPresentationContextProviding) async throws -> KsuserAuthorizationResult {
        guard !starting, continuation == nil, AuthorizationLease.claim(leaseID) else { throw KsuserAuthError.authorizationInProgress }
        generation += 1
        starting = true; let currentGeneration = generation; let operationLease = leaseID
        defer { if generation == currentGeneration { starting = false }; AuthorizationLease.release(operationLease) }
        pending = try PendingAuthorizationRecovery.load(from: storage, configuration: configuration)
        guard pending == nil else { throw KsuserAuthError.authorizationInProgress }
        let state = try PKCE.random(), verifier = try PKCE.random()
        let nonce = configuration.mode == .official ? try PKCE.random() : nil
        let created = try await http.create(scopes: scopes ?? configuration.defaultScopes, state: state, nonce: nonce, verifier: verifier)
        var transaction = AuthorizationTransaction(clientID: configuration.clientID, mode: configuration.mode, redirectURI: configuration.redirectURI,
                                                 verifier: verifier, state: state, nonce: nonce, created: created,
                                                 expiresAt: Date().addingTimeInterval(Double(created.expiresInSeconds)))
        transaction.configurationID = configuration.transactionIdentifier
        guard currentGeneration == generation, !Task.isCancelled else {
            try? await http.cancel(transaction); throw KsuserAuthError.cancelled
        }
        try storage.save(transaction); pending = transaction
        starting = false
        return try await waitForAuthorization(provider: presentationContextProvider, useWebFallback: false)
    }

    /// Set useWebFallback to continue the SAME request in a browser when an older Ksuser app cannot handle it.
    public func resumePendingAuthorization(presentationContextProvider: any ASWebAuthenticationPresentationContextProviding,
                                           useWebFallback: Bool = false) async throws -> KsuserAuthorizationResult? {
        guard !starting else { throw KsuserAuthError.authorizationInProgress }
        if continuation != nil {
            guard useWebFallback, let transaction = pending else { throw KsuserAuthError.authorizationInProgress }
            await launch(transaction, useWebFallback: true)
            return nil // The original authorize/resume call still receives the result.
        }
        guard AuthorizationLease.claim(leaseID) else { throw KsuserAuthError.authorizationInProgress }
        let operationLease = leaseID
        defer { AuthorizationLease.release(operationLease) }
        pending = try PendingAuthorizationRecovery.load(from: storage, configuration: configuration)
        guard let pending else { return nil }
        guard pending.configurationID == configuration.transactionIdentifier else { throw KsuserAuthError.authorizationInProgress }
        return try await waitForAuthorization(provider: presentationContextProvider, useWebFallback: useWebFallback)
    }

    @discardableResult public func handleCallback(_ url: URL) -> Bool {
        guard AuthorizationLease.claim(leaseID) else { return false }
        let callbackLease = leaseID
        defer { if continuation == nil { AuthorizationLease.release(callbackLease) } }
        do { pending = try PendingAuthorizationRecovery.load(from: storage, configuration: configuration) } catch { return false }
        guard var transaction = pending, transaction.configurationID == configuration.transactionIdentifier, transaction.expiresAt > Date(),
              let outcome = CallbackParser.parse(url, redirectURI: transaction.redirectURI, state: transaction.state) else { return false }
        guard transaction.code == nil, transaction.callbackError == nil, transaction.tokens == nil else { return true }
        switch outcome { case .code(let code): transaction.code = code; case .error(let error): transaction.callbackError = error }
        do { try storage.save(transaction); pending = transaction }
        catch { finish(.failure(error)); return true }
        if continuation != nil { processCallback() }
        return true
    }

    /// Local cancellation is immediate. Server cancellation is best effort; already redeemed grants require revocation.
    public func cancel() async {
        guard AuthorizationLease.claim(leaseID) else { return }
        let cancellationLease = leaseID
        defer { AuthorizationLease.release(cancellationLease) }
        do { pending = try PendingAuthorizationRecovery.load(from: storage, configuration: configuration) }
        catch { return }
        generation += 1
        let transaction = pending
        finish(.failure(KsuserAuthError.cancelled))
        if let transaction { try? await http.cancel(transaction) }
    }

    private func waitForAuthorization(provider: any ASWebAuthenticationPresentationContextProviding, useWebFallback: Bool) async throws -> KsuserAuthorizationResult {
        guard let transaction = pending else { throw KsuserAuthError.invalidCallback }
        guard transaction.expiresAt > Date() else { try storage.clear(); pending = nil; throw KsuserAuthError.expired }
        let waitingLease = leaseID
        presentationProvider = provider
        return try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                self.continuation = continuation
                timeoutTask = Task { [weak self] in
                    do { try await Task.sleep(for: .seconds(max(0, transaction.expiresAt.timeIntervalSinceNow))) } catch { return }
                    guard self?.leaseID == waitingLease, self?.pending?.created.ticket == transaction.created.ticket else { return }
                    self?.finish(.failure(KsuserAuthError.expired))
                }
                if transaction.code != nil || transaction.callbackError != nil || transaction.tokens != nil { processCallback() }
                else { Task { await self.launch(transaction, useWebFallback: useWebFallback) } }
            }
        } onCancel: { Task { @MainActor [weak self] in await self?.cancel() } }
    }

    private func launch(_ transaction: AuthorizationTransaction, useWebFallback: Bool) async {
        guard let active = pending, active.created.ticket == transaction.created.ticket, continuation != nil,
              active.code == nil, active.callbackError == nil, active.tokens == nil, processingTask == nil else { return }
        if !useWebFallback {
            let accepted = await UIApplication.shared.open(transaction.created.appLink, options: [.universalLinksOnly: true])
            guard pending?.created.ticket == transaction.created.ticket, continuation != nil else { return }
            if accepted { return }
            guard pending?.code == nil, pending?.callbackError == nil, pending?.tokens == nil, processingTask == nil else { return }
        }
        guard browser == nil else { return }
        guard let host = configuration.redirectURI.host else { finish(.failure(KsuserAuthError.invalidConfiguration)); return }
        let browserLease = leaseID
        let session = ASWebAuthenticationSession(url: transaction.created.webAuthorizationUrl,
                                                callback: .https(host: host, path: configuration.redirectURI.path)) { [weak self] url, error in
            Task { @MainActor in
                guard let self, self.leaseID == browserLease, self.pending?.created.ticket == transaction.created.ticket, self.continuation != nil else { return }
                if let url { if !self.handleCallback(url) { self.finish(.failure(KsuserAuthError.invalidCallback)) } }
                else if error != nil { await self.cancel() }
            }
        }
        session.presentationContextProvider = presentationProvider
        browser = session
        guard session.start() else { finish(.failure(KsuserAuthError.invalidResponse), preserveTransaction: true); return }
    }

    private func processCallback() {
        guard processingTask == nil, let transaction = pending else { return }
        processingTask = Task { [weak self] in
            guard let self else { return }
            do {
                let result = try await SDKAuthorizationResolver.resolve(transaction, http: http) { updated in
                    guard self.pending?.created.ticket == transaction.created.ticket else { throw KsuserAuthError.cancelled }
                    try self.storage.save(updated); self.pending = updated
                }
                guard pending?.created.ticket == transaction.created.ticket else { return }
                finish(.success(result))
            } catch {
                guard pending?.created.ticket == transaction.created.ticket else { return }
                finish(.failure(error), preserveTransaction: KsuserAuthError.preservesPendingTransaction(error))
            }
        }
    }

    private func finish(_ result: Result<KsuserAuthorizationResult, Error>, preserveTransaction: Bool = false) {
        let callback = continuation; continuation = nil
        timeoutTask?.cancel(); timeoutTask = nil
        processingTask?.cancel(); processingTask = nil
        let session = browser; browser = nil
        presentationProvider = nil
        var outcome = result
        if !preserveTransaction {
            do { try storage.clear(); pending = nil }
            catch { outcome = .failure(error) }
        }
        session?.cancel()
        AuthorizationLease.release(leaseID); leaseID = UUID()
        callback?.resume(with: outcome)
    }
}
#endif
