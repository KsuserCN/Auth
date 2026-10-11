import Foundation

/// Resolve API results from the shared catalogue. Server prose is never displayed.
enum APIResultCodeResolver {
    static func message(errorCode: String?, params: [String: JSONValue]? = nil, statusCode: Int = 500) -> String {
        guard let errorCode, let template = GeneratedAPIResultCodes.messages[errorCode] else {
            return fallback(statusCode: statusCode)
        }
        let expression = try! NSRegularExpression(pattern: #"\{([A-Za-z][A-Za-z0-9_]*)\}"#)
        let matches = expression.matches(in: template, range: NSRange(template.startIndex..., in: template))
        var resolved = template
        for match in matches.reversed() {
            guard let nameRange = Range(match.range(at: 1), in: template),
                  let value = params?[String(template[nameRange])]?.displayParameter,
                  let replacementRange = Range(match.range, in: resolved) else {
                return fallback(statusCode: statusCode)
            }
            resolved.replaceSubrange(replacementRange, with: value)
        }
        return resolved
    }

    private static func fallback(statusCode: Int) -> String {
        let code: String
        switch statusCode {
        case 200..<300: code = "REQUEST_SUCCESS"
        case 401: code = "UNAUTHENTICATED"
        case 403: code = "FORBIDDEN"
        case 404: code = "NOT_FOUND"
        case 405: code = "METHOD_NOT_ALLOWED"
        case 408: code = "REQUEST_TIMEOUT"
        case 409: code = "REQUEST_CONFLICT"
        case 413: code = "REQUEST_UPLOAD_TOO_LARGE"
        case 415: code = "UNSUPPORTED_MEDIA_TYPE"
        case 429: code = "RATE_LIMITED"
        case 400..<500: code = "BAD_REQUEST"
        case 500...: code = "SERVER_ERROR"
        default: code = "REQUEST_FAILED"
        }
        return GeneratedAPIResultCodes.messages[code] ?? "请求失败，请重试"
    }
}

private extension JSONValue {
    var displayParameter: String? {
        switch self {
        case .string(let value): return value
        case .number(let value):
            guard value.isFinite else { return nil }
            return value.rounded() == value ? String(format: "%.0f", value) : String(value)
        case .bool(let value): return value ? "true" : "false"
        default: return nil
        }
    }
}
