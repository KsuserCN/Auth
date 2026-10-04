import Foundation

struct AppEnvironment: Sendable {
    let apiBaseURL: URL
    let passkeyRPID: String
    let qqAppID: String
    let webURL: URL
    static let current = AppEnvironment(
        apiBaseURL: URL(string: value("KSUSER_API_BASE_URL", fallback: "https://api.ksuser.cn"))!,
        passkeyRPID: value("KSUSER_PASSKEY_RP_ID", fallback: "auth.ksuser.cn"),
        qqAppID: value("KSUSER_QQ_APP_ID", fallback: "1903977704"),
        webURL: URL(string: "https://auth.ksuser.cn")!
    )
    private static func value(_ key: String, fallback: String) -> String {
        guard let value = Bundle.main.object(forInfoDictionaryKey: key) as? String, !value.isEmpty, !value.hasPrefix("$(") else { return fallback }
        return value
    }
    var userAgent: String {
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0"
        let system = ProcessInfo.processInfo.operatingSystemVersion
        return "KsuserAuthMobile/\(version) (iOS \(system.majorVersion).\(system.minorVersion).\(system.patchVersion))"
    }
}
