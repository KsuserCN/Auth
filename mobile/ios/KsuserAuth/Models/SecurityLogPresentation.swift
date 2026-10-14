import Foundation

enum SecurityLogPresentation {
    static func text(_ key: String) -> String { GeneratedSecurityLogCatalog.messages[L10n.language]?[key] ?? key }
    private static func normalize(_ value: String) -> String {
        value.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: "\"", with: "")
            .replacingOccurrences(of: "'", with: "").uppercased().replacingOccurrences(of: "-", with: "_").replacingOccurrences(of: " ", with: "_")
    }
    static func parseMethods(_ raw: String?, methods: [String]? = nil) -> [String] {
        let values = methods?.isEmpty == false ? methods! : [raw ?? ""]
        var tokens: [String] = []
        for value in values {
            let segments = value.trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "[]")).split(separator: ",")
            for segment in segments {
                let key = normalize(String(segment))
                guard !key.isEmpty && key != "NULL" else { continue }
                for token in key.hasSuffix("_MFA") ? [String(key.dropLast(4)), "MFA"] : [key] {
                    let normalized = token == "EMAIL" ? "EMAIL_CODE" : token == "WEIXIN" ? "WECHAT" : token
                    if !tokens.contains(normalized) { tokens.append(normalized) }
                }
            }
        }
        return tokens
    }
    static func operation(_ value: String) -> String { GeneratedSecurityLogCatalog.messages[L10n.language]?["operation.\(normalize(value))"] ?? text("ui.unknownOperation") }
    static func action(_ value: String?) -> String { GeneratedSecurityLogCatalog.messages[L10n.language]?["action.\(normalize(value ?? ""))"] ?? text("ui.unknownAction") }
    static func title(_ log: SensitiveLogItem) -> String { normalize(log.operationType) == "ADAPTIVE_POLICY" ? action(log.actionTaken) : operation(log.operationType) }
    static func methods(_ log: SensitiveLogItem) -> [String] {
        guard ["LOGIN", "SENSITIVE_VERIFY", "BIND_OAUTH", "UNBIND_OAUTH"].contains(normalize(log.operationType)) else { return [] }
        var labels: [String] = []
        for token in parseMethods(log.loginMethod, methods: log.loginMethods) {
            let label = GeneratedSecurityLogCatalog.messages[L10n.language]?["method.\(token)"] ?? text("ui.unknownMethod")
            if !labels.contains(label) { labels.append(label) }
        }
        return labels
    }
    static func reason(_ raw: String?) -> String? {
        guard let raw, !raw.isEmpty else { return nil }
        if GeneratedAPIResultCodes.messages[raw] != nil { return APIResultCodeResolver.message(errorCode: raw, statusCode: 400) }
        let historical = raw.replacingOccurrences(of: #"^risk=\d+;?\s*"#, with: "", options: [.regularExpression, .caseInsensitive]).trimmingCharacters(in: .whitespacesAndNewlines)
        guard !historical.isEmpty else { return nil }
        return GeneratedSecurityLogCatalog.messages[L10n.language]?["reason.\(normalize(historical).lowercased())"] ?? historical
    }
}
