import AuthenticationServices
import Cocoa
import FlutterMacOS
import LocalAuthentication

class MainFlutterWindow: NSWindow {
  private var passkeyBridge: PasskeyBridge?
  private var appMenuBridge: AppMenuBridge?
  private var localAuthBridge: LocalAuthBridge?

  override func awakeFromNib() {
    let flutterViewController = FlutterViewController()
    self.contentViewController = flutterViewController
    self.setContentSize(NSSize(width: 1440, height: 920))
    self.minSize = NSSize(width: 1240, height: 820)
    self.center()
    self.title = "Ksuser Auth 统一认证中心"
    if #available(macOS 11.0, *) {
      self.toolbarStyle = .unified
    }

    RegisterGeneratedPlugins(registry: flutterViewController)
    passkeyBridge = PasskeyBridge(
      messenger: flutterViewController.engine.binaryMessenger,
      windowProvider: { [weak self] in self }
    )
    appMenuBridge = AppMenuBridge(messenger: flutterViewController.engine.binaryMessenger)
    localAuthBridge = LocalAuthBridge(messenger: flutterViewController.engine.binaryMessenger)

    super.awakeFromNib()
  }

  func dispatchMenuCommand(_ command: String) {
    appMenuBridge?.send(command: command)
  }
}

private final class AppMenuBridge {
  private let channel: FlutterMethodChannel

  init(messenger: FlutterBinaryMessenger) {
    channel = FlutterMethodChannel(name: "ksuser/app_menu", binaryMessenger: messenger)
  }

  func send(command: String) {
    channel.invokeMethod(command, arguments: nil)
  }
}

private final class LocalAuthBridge {
  private let channel: FlutterMethodChannel

  init(messenger: FlutterBinaryMessenger) {
    channel = FlutterMethodChannel(name: "ksuser/local_auth", binaryMessenger: messenger)
    channel.setMethodCallHandler { [weak self] call, result in
      self?.handle(call, result: result)
    }
  }

  private func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    switch call.method {
    case "isAvailable":
      result(isLocalAuthAvailable())
    case "authenticate":
      let arguments = call.arguments as? [String: Any]
      let normalizedReason = (arguments?["reason"] as? String)?
        .trimmingCharacters(in: .whitespacesAndNewlines)
      let reason = (normalizedReason?.isEmpty == false)
        ? (normalizedReason ?? "请先验证身份")
        : "请先验证身份"
      authenticate(reason: reason, result: result)
    default:
      result(FlutterMethodNotImplemented)
    }
  }

  private func isLocalAuthAvailable() -> Bool {
    let context = LAContext()
    var error: NSError?
    return context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error)
  }

  private func authenticate(reason: String, result: @escaping FlutterResult) {
    let context = LAContext()
    var error: NSError?
    guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else {
      let message = error?.localizedDescription ?? "当前设备不支持本地认证"
      result(FlutterError(code: "not_available", message: message, details: nil))
      return
    }

    context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: reason) { success, authError in
      DispatchQueue.main.async {
        if success {
          result(true)
          return
        }

        if let nsError = authError as NSError? {
          if nsError.code == LAError.userCancel.rawValue
            || nsError.code == LAError.systemCancel.rawValue
            || nsError.code == LAError.appCancel.rawValue {
            result(FlutterError(code: "canceled", message: "用户取消了本地认证", details: nil))
            return
          }
          result(FlutterError(code: "failed", message: nsError.localizedDescription, details: nil))
          return
        }

        result(FlutterError(code: "failed", message: "本地认证失败", details: nil))
      }
    }
  }
}

private final class PasskeyBridge: NSObject {
  private let channel: FlutterMethodChannel
  private let windowProvider: () -> NSWindow?
  private var coordinator: AnyObject?

  init(messenger: FlutterBinaryMessenger, windowProvider: @escaping () -> NSWindow?) {
    self.channel = FlutterMethodChannel(name: "ksuser/passkey", binaryMessenger: messenger)
    self.windowProvider = windowProvider
    super.init()

    channel.setMethodCallHandler { [weak self] call, result in
      self?.handle(call, result: result)
    }
  }

  private func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    switch call.method {
    case "isAvailable":
      result(isPasskeyAvailable())
    case "performAssertion":
      performAssertion(call.arguments, result: result)
    default:
      result(FlutterMethodNotImplemented)
    }
  }

  private func isPasskeyAvailable() -> Bool {
    if #available(macOS 13.5, *) {
      return true
    }
    return false
  }

  private func performAssertion(_ arguments: Any?, result: @escaping FlutterResult) {
    guard #available(macOS 13.5, *) else {
      result(
        FlutterError(
          code: "unsupported_os",
          message: "Passkey 需要 macOS 13.5 或更高版本",
          details: nil
        )
      )
      return
    }

    guard coordinator == nil else {
      result(
        FlutterError(
          code: "busy",
          message: "已有进行中的 Passkey 请求",
          details: nil
        )
      )
      return
    }

    guard let args = arguments as? [String: Any] else {
      result(FlutterError(code: "bad_args", message: "Passkey 参数缺失", details: nil))
      return
    }

    guard let challenge = args["challenge"] as? String, !challenge.isEmpty else {
      result(FlutterError(code: "bad_args", message: "challenge 不能为空", details: nil))
      return
    }
    guard let rpId = args["rpId"] as? String, !rpId.isEmpty else {
      result(FlutterError(code: "bad_args", message: "rpId 不能为空", details: nil))
      return
    }
    guard let origin = args["origin"] as? String, !origin.isEmpty else {
      result(
        FlutterError(
          code: "bad_args",
          message: "Passkey origin 未配置，请检查 FLUTTER_PASSKEY_ORIGIN",
          details: nil
        )
      )
      return
    }
    guard let challengeData = Data.fromBase64OrBase64URL(challenge) else {
      result(FlutterError(code: "bad_args", message: "challenge 格式无效", details: nil))
      return
    }

    let allowCredentials = (args["allowCredentials"] as? [[String: Any]] ?? [])
      .compactMap(AllowedCredential.init)

    let request = PasskeyAssertionRequest(
      challengeData: challengeData,
      rpId: rpId,
      origin: origin,
      userVerification: args["userVerification"] as? String,
      allowCredentials: allowCredentials
    )

    let coordinator = PasskeyAssertionCoordinator(
      request: request,
      windowProvider: windowProvider
    ) { [weak self] response in
      self?.coordinator = nil
      result(response)
    }

    self.coordinator = coordinator
    coordinator.start()
  }
}

private struct AllowedCredential {
  let id: Data
  let transports: [String]

  init?(json: [String: Any]) {
    guard
      let rawId = json["id"] as? String,
      let decodedId = Data.fromBase64OrBase64URL(rawId)
    else {
      return nil
    }
    id = decodedId
    transports = json["transports"] as? [String] ?? []
  }
}

@available(macOS 13.5, *)
private struct PasskeyAssertionRequest {
  let challengeData: Data
  let rpId: String
  let origin: String
  let userVerification: String?
  let allowCredentials: [AllowedCredential]
}

@available(macOS 13.5, *)
private final class PasskeyAssertionCoordinator: NSObject,
  ASAuthorizationControllerDelegate,
  ASAuthorizationControllerPresentationContextProviding
{
  private let request: PasskeyAssertionRequest
  private let windowProvider: () -> NSWindow?
  private let completion: (Any?) -> Void
  private var authorizationController: ASAuthorizationController?

  init(
    request: PasskeyAssertionRequest,
    windowProvider: @escaping () -> NSWindow?,
    completion: @escaping (Any?) -> Void
  ) {
    self.request = request
    self.windowProvider = windowProvider
    self.completion = completion
    super.init()
  }

  func start() {
    let clientData = ASPublicKeyCredentialClientData(
      challenge: request.challengeData,
      origin: request.origin
    )

    let platformProvider = ASAuthorizationPlatformPublicKeyCredentialProvider(
      relyingPartyIdentifier: request.rpId
    )
    let platformRequest = platformProvider.createCredentialAssertionRequest(clientData: clientData)
    configure(platformRequest)

    var requests: [ASAuthorizationRequest] = [platformRequest]

    if #available(macOS 14.4, *) {
      let securityProvider = ASAuthorizationSecurityKeyPublicKeyCredentialProvider(
        relyingPartyIdentifier: request.rpId
      )
      let securityRequest = securityProvider.createCredentialAssertionRequest(clientData: clientData)
      configure(securityRequest)
      requests.append(securityRequest)
    }

    let controller = ASAuthorizationController(authorizationRequests: requests)
    controller.delegate = self
    controller.presentationContextProvider = self
    authorizationController = controller
    controller.performRequests()
  }

  private func configure(_ assertionRequest: some ASAuthorizationPublicKeyCredentialAssertionRequest) {
    assertionRequest.userVerificationPreference = userVerificationPreference(from: request.userVerification)
  }

  private func configure(_ assertionRequest: ASAuthorizationPlatformPublicKeyCredentialAssertionRequest) {
    assertionRequest.userVerificationPreference = userVerificationPreference(from: request.userVerification)
    assertionRequest.allowedCredentials = request.allowCredentials.map {
      ASAuthorizationPlatformPublicKeyCredentialDescriptor(credentialID: $0.id)
    }
  }

  @available(macOS 14.4, *)
  private func configure(_ assertionRequest: ASAuthorizationSecurityKeyPublicKeyCredentialAssertionRequest) {
    assertionRequest.userVerificationPreference = userVerificationPreference(from: request.userVerification)
    assertionRequest.allowedCredentials = request.allowCredentials.map {
      ASAuthorizationSecurityKeyPublicKeyCredentialDescriptor(
        credentialID: $0.id,
        transports: mapTransports($0.transports)
      )
    }
  }

  private func mapTransports(
    _ transports: [String]
  ) -> [ASAuthorizationSecurityKeyPublicKeyCredentialDescriptor.Transport] {
    let mapped = transports.compactMap {
      switch $0.lowercased() {
      case "usb":
        return ASAuthorizationSecurityKeyPublicKeyCredentialDescriptor.Transport.usb
      case "nfc":
        return ASAuthorizationSecurityKeyPublicKeyCredentialDescriptor.Transport.nfc
      case "ble", "bluetooth":
        return ASAuthorizationSecurityKeyPublicKeyCredentialDescriptor.Transport.bluetooth
      default:
        return nil
      }
    }
    return mapped.isEmpty ? ASAuthorizationSecurityKeyPublicKeyCredentialDescriptor.Transport.allSupported : mapped
  }

  private func userVerificationPreference(
    from value: String?
  ) -> ASAuthorizationPublicKeyCredentialUserVerificationPreference {
    switch value?.lowercased() {
    case "required":
      return .required
    case "discouraged":
      return .discouraged
    default:
      return .preferred
    }
  }

  func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
    if let window = windowProvider() ?? NSApplication.shared.keyWindow ?? NSApplication.shared.mainWindow {
      return window
    }
    return NSApplication.shared.windows.first ?? NSWindow()
  }

  func authorizationController(
    controller: ASAuthorizationController,
    didCompleteWithAuthorization authorization: ASAuthorization
  ) {
    guard let credential = authorization.credential as? ASAuthorizationPublicKeyCredentialAssertion else {
      completion(
        FlutterError(
          code: "invalid_credential",
          message: "未获取到有效的 Passkey 断言",
          details: nil
        )
      )
      return
    }

    completion(
      [
        "credentialRawId": credential.credentialID.base64URLEncodedString(),
        "clientDataJSON": credential.rawClientDataJSON.base64URLEncodedString(),
        "authenticatorData": credential.rawAuthenticatorData.base64URLEncodedString(),
        "signature": credential.signature.base64URLEncodedString(),
      ]
    )
  }

  func authorizationController(
    controller: ASAuthorizationController,
    didCompleteWithError error: Error
  ) {
    let nsError = error as NSError
    let message: String
    if nsError.domain == ASAuthorizationError.errorDomain,
      let code = ASAuthorizationError.Code(rawValue: nsError.code)
    {
      if code == .canceled {
        message = "用户取消了 Passkey 验证"
      } else if code == .invalidResponse {
        message = "Passkey 返回了无效响应"
      } else if code == .notHandled {
        message = "系统未处理当前 Passkey 请求"
      } else if code == .notInteractive {
        message = "系统当前无法展示 Passkey 验证界面"
      } else {
        message = nsError.localizedDescription.isEmpty ? "Passkey 验证失败" : nsError.localizedDescription
      }
    } else {
      message = nsError.localizedDescription.isEmpty ? "Passkey 验证失败" : nsError.localizedDescription
    }

    completion(FlutterError(code: "authorization_failed", message: message, details: nil))
  }
}

private extension Data {
  static func fromBase64OrBase64URL(_ value: String) -> Data? {
    let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !trimmed.isEmpty else {
      return nil
    }

    if let data = Data(base64Encoded: trimmed) {
      return data
    }

    var normalized = trimmed.replacingOccurrences(of: "-", with: "+")
      .replacingOccurrences(of: "_", with: "/")
    let remainder = normalized.count % 4
    if remainder != 0 {
      normalized += String(repeating: "=", count: 4 - remainder)
    }
    return Data(base64Encoded: normalized)
  }

  func base64URLEncodedString() -> String {
    base64EncodedString()
      .replacingOccurrences(of: "+", with: "-")
      .replacingOccurrences(of: "/", with: "_")
      .replacingOccurrences(of: "=", with: "")
  }
}
