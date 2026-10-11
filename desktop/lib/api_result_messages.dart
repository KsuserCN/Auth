import 'generated/api_result_codes.dart';

/// Resolves API outcomes using the shared catalog, never server-provided text.
String resolveApiResultMessage({
  String? errorCode,
  Map<String, dynamic> params = const <String, dynamic>{},
  int? statusCode,
}) {
  final String fallback = apiResultMessages[apiFallbackResultCode(statusCode)]!;
  final String template = apiResultMessages[errorCode] ?? fallback;
  final RegExp placeholders = RegExp(r'\{([A-Za-z0-9_]+)\}');
  for (final Match match in placeholders.allMatches(template)) {
    final dynamic value = params[match.group(1)];
    if (value is! String && value is! num && value is! bool) return fallback;
  }
  return template.replaceAllMapped(placeholders, (Match match) {
    final dynamic value = params[match.group(1)];
    return value.toString();
  });
}

String apiFallbackResultCode(int? statusCode) {
  if (statusCode != null && statusCode >= 200 && statusCode < 300) {
    return 'REQUEST_SUCCESS';
  }
  if (statusCode == 401) return 'UNAUTHENTICATED';
  if (statusCode == 403) return 'FORBIDDEN';
  if (statusCode == 404) return 'NOT_FOUND';
  if (statusCode == 405) return 'METHOD_NOT_ALLOWED';
  if (statusCode == 408) return 'REQUEST_TIMEOUT';
  if (statusCode == 409) return 'REQUEST_CONFLICT';
  if (statusCode == 413) return 'REQUEST_UPLOAD_TOO_LARGE';
  if (statusCode == 415) return 'UNSUPPORTED_MEDIA_TYPE';
  if (statusCode == 429) return 'RATE_LIMITED';
  if (statusCode != null && statusCode >= 500) return 'SERVER_ERROR';
  if (statusCode != null && statusCode >= 400) return 'BAD_REQUEST';
  return 'REQUEST_FAILED';
}

Map<String, dynamic> apiResultParams(dynamic value) {
  if (value is! Map) return const <String, dynamic>{};
  return <String, dynamic>{
    for (final MapEntry<dynamic, dynamic> entry in value.entries)
      if (entry.key is String &&
          (entry.value is String || entry.value is num || entry.value is bool))
        entry.key as String: entry.value,
  };
}

String? apiResultCode(dynamic value) {
  if (value is! Map) return null;
  final dynamic code = value['errorCode'];
  return code is String && code.isNotEmpty ? code : null;
}

String resolveApiResultPayload(dynamic value, {int? statusCode}) {
  return resolveApiResultMessage(
    errorCode: apiResultCode(value),
    params: apiResultParams(value is Map ? value['params'] : null),
    statusCode: statusCode,
  );
}

/// New audit entries may store a complete result identifier; older prose stays
/// intact so existing audit records remain readable.
String? resolveAuditFailureReason(String? reason) {
  if (reason == null || !apiResultMessages.containsKey(reason)) return reason;
  return resolveApiResultMessage(errorCode: reason);
}
