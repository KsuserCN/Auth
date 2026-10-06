import Foundation

enum MobileBridgeLink {
    static func challengeID(from url: URL, environment: AppEnvironment) -> String? {
        let custom = url.scheme == "ksuserauth" && url.host == "bridge-login" && url.path.isEmpty && url.port == nil
        let universal = url.scheme == "https" && url.host?.lowercased() == environment.webURL.host?.lowercased()
            && url.port == environment.webURL.port && url.path == "/app/bridge-login"
        guard custom || universal, url.user == nil, url.password == nil, url.fragment == nil,
              let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems,
              items.filter({ $0.name == "challengeId" }).count == 1,
              let challenge = items.first(where: { $0.name == "challengeId" })?.value,
              challenge.range(of: #"^[A-Za-z0-9_-]{32}$"#, options: .regularExpression) != nil else { return nil }
        return challenge
    }

    static func returnBrowser(from url: URL) -> MobileAuthorizationReturnBrowser {
        guard url.scheme == "ksuserauth", url.host == "bridge-login",
              let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems else { return .system }
        let browsers = items.filter { $0.name == "returnBrowser" }
        return browsers.count == 1 && browsers[0].value == "chrome" ? .chrome : .system
    }
}
