import Foundation
import Observation

/// This preference belongs to the device, not the signed-in account.
enum AppLanguage: String, CaseIterable, Identifiable, Sendable {
    case system, simplifiedChinese = "zh-Hans", english = "en"
    var id: String { rawValue }
    var title: String {
        switch self {
        case .system: L10n.text("跟随系统")
        case .simplifiedChinese: "简体中文"
        case .english: "English"
        }
    }

    static func resolve(preference: String?, preferredLanguages: [String]) -> String {
        if let preference, let selected = AppLanguage(rawValue: preference), selected != .system { return selected.rawValue }
        let tag = (preferredLanguages.first ?? "en").replacingOccurrences(of: "_", with: "-").lowercased()
        return tag == "zh" || tag.hasPrefix("zh-") ? simplifiedChinese.rawValue : english.rawValue

    }
}

@MainActor @Observable final class LanguageSettings {
    static let preferenceKey = "language"
    var preference: AppLanguage {
        didSet { defaults.set(preference.rawValue, forKey: Self.preferenceKey) }
    }
    private var preferredLanguages: [String]
    @ObservationIgnored private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard, preferredLanguages: [String] = Locale.preferredLanguages) {
        self.defaults = defaults
        self.preferredLanguages = preferredLanguages
        preference = AppLanguage(rawValue: defaults.string(forKey: Self.preferenceKey) ?? "system") ?? .system
    }
    var effectiveLanguage: String { AppLanguage.resolve(preference: preference.rawValue, preferredLanguages: preferredLanguages) }
    var locale: Locale { Locale(identifier: effectiveLanguage) }
    func refreshSystemLanguage() { preferredLanguages = Locale.preferredLanguages }
}

/// Explicit resource lookups also work in actors and UIKit, outside SwiftUI's Locale environment.
enum L10n {
    static var language: String { AppLanguage.resolve(preference: UserDefaults.standard.string(forKey: "language"), preferredLanguages: Locale.preferredLanguages) }
    static var locale: Locale { Locale(identifier: language) }
    static func text(_ key: String, language: String = L10n.language) -> String {
        guard let path = Bundle.main.path(forResource: language, ofType: "lproj"), let bundle = Bundle(path: path) else { return key }
        return bundle.localizedString(forKey: key, value: key, table: "Localizable")
    }
    static func format(_ key: String, _ arguments: [String], language: String = L10n.language) -> String {
        let template = text(key, language: language)
        let expression = try! NSRegularExpression(pattern: #"\{(\d+)\}"#)
        var rendered = template
        for match in expression.matches(in: template, range: NSRange(template.startIndex..., in: template)).reversed() {
            guard let numberRange = Range(match.range(at: 1), in: template), let index = Int(template[numberRange]), arguments.indices.contains(index), let range = Range(match.range, in: rendered) else { continue }
            rendered.replaceSubrange(range, with: arguments[index])
        }
        return rendered
    }
}

/// Retain keys and arguments until presentation so pending messages follow a live language change.
struct LocalizedMessage: ExpressibleByStringLiteral, ExpressibleByStringInterpolation, Equatable, @unchecked Sendable {
    private indirect enum Content {
        case key(String, [LocalizedMessage])
        case verbatim(String)
        case error(any Error)
        case api(String?, [String: JSONValue]?, Int)
    }
    private let content: Content
    init(stringLiteral value: String) { content = .key(value, []) }
    init(key: String) { content = .key(key, []) }
    init(verbatim value: String) { content = .verbatim(value) }
    init(error: any Error) { content = .error(error) }
    init(errorCode: String?, params: [String: JSONValue]?, statusCode: Int) { content = .api(errorCode, params, statusCode) }
    init(stringInterpolation: StringInterpolation) { content = .key(stringInterpolation.key, stringInterpolation.arguments) }
    var rendered: String {
        switch content {
        case .key(let key, let arguments): return L10n.format(key, arguments.map(\.rendered))
        case .verbatim(let value): return value
        case .error(let error):
            if let error = error as? URLError {
                return APIResultCodeResolver.message(errorCode: error.code == .timedOut ? "REQUEST_TIMEOUT" : "NETWORK_ERROR")
            }
            return error.localizedDescription
        case .api(let code, let params, let status): return APIResultCodeResolver.message(errorCode: code, params: params, statusCode: status)
        }
    }
    static func == (lhs: LocalizedMessage, rhs: LocalizedMessage) -> Bool {
        switch (lhs.content, rhs.content) {
        case (.key(let a, let aa), .key(let b, let ba)): a == b && aa == ba
        case (.verbatim(let a), .verbatim(let b)): a == b
        case (.api(let a, let ap, let ac), .api(let b, let bp, let bc)): a == b && ap == bp && ac == bc
        case (.error(let a), .error(let b)): (a as NSError).isEqual(b as NSError)
        default: false
        }
    }
    struct StringInterpolation: StringInterpolationProtocol {
        var key = ""
        var arguments: [LocalizedMessage] = []
        init(literalCapacity: Int, interpolationCount: Int) { arguments.reserveCapacity(interpolationCount) }
        mutating func appendLiteral(_ literal: String) { key += literal }
        mutating func appendInterpolation(_ value: LocalizedMessage) { key += "{\(arguments.count)}"; arguments.append(value) }
        mutating func appendInterpolation<T>(_ value: T) { appendInterpolation(LocalizedMessage(verbatim: String(describing: value))) }
    }
}
