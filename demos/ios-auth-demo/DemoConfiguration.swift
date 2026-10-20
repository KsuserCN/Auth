import Foundation
import KsuserAuthSDK

enum DemoMode: String, CaseIterable, Identifiable {
    case oauth, official

    var id: String { rawValue }
    var title: String { self == .oauth ? "移动应用 OAuth" : "官方 OIDC" }
    var sdkMode: KsuserAuthorizationMode { self == .oauth ? .oauth : .official }
}

/// Replace these public identifiers and callback domain before running a real authorization.
/// Register the OAuth AppID under Mobile Applications and verify its iOS platform first.
/// The callback host must also match the entitlements, AASA and server-side registration.
enum DemoConfiguration {
    static let oauthClientID = "ksapp_GGHAd6l3Tr-g7NYvaSgrPxUp"
    static let officialClientID = "REPLACE_OFFICIAL_CLIENT_ID"
    static let redirectURI = URL(string: "https://demos.ksuser.cn/ksuser/callback")!

    static let apiBaseURL = URL(string: "https://api.ksuser.cn")!
    static let webBaseURL = URL(string: "https://auth.ksuser.cn")!
    static let officialIssuer = "https://api.ksuser.cn/official"

    static func clientID(for mode: DemoMode) -> String {
        mode == .oauth ? oauthClientID : officialClientID
    }

    static func scopes(for mode: DemoMode) -> [String] {
        mode == .oauth ? ["profile"] : ["openid", "profile"]
    }

    static func isConfigured(for mode: DemoMode) -> Bool {
        let value = clientID(for: mode).trimmingCharacters(in: .whitespacesAndNewlines)
        return !value.isEmpty && !value.hasPrefix("REPLACE_")
    }

    static func sdkConfiguration(for mode: DemoMode) -> KsuserAuthConfiguration {
        .init(clientID: clientID(for: mode), mode: mode.sdkMode, redirectURI: redirectURI,
              apiBaseURL: apiBaseURL, webBaseURL: webBaseURL, officialIssuer: officialIssuer)
    }
}
