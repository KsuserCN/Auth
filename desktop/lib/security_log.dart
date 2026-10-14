import 'generated/security_log_catalog.dart';
import 'i18n.dart';
import 'api_result_messages.dart';

String securityLogText(String key) =>
    securityLogCatalog[desktopLocaleController.languageTag]?[key] ?? key;
String _normalize(String value) => value
    .trim()
    .replaceAll(RegExp('["\']'), '')
    .toUpperCase()
    .replaceAll(RegExp('[- ]'), '_');

List<String> parseLogMethods(
  String? raw, [
  List<String> methods = const <String>[],
]) {
  final values = methods.isNotEmpty ? methods : <String>[raw ?? ''];
  final tokens = <String>[];
  for (final value in values) {
    for (final segment
        in value.replaceAll(RegExp(r'^\s*\[|\]\s*$'), '').split(',')) {
      final key = _normalize(segment);
      if (key.isEmpty || key == 'NULL') continue;
      for (final token
          in key.endsWith('_MFA')
              ? <String>[key.substring(0, key.length - 4), 'MFA']
              : <String>[key]) {
        final normalized = token == 'EMAIL'
            ? 'EMAIL_CODE'
            : token == 'WEIXIN'
            ? 'WECHAT'
            : token;
        if (!tokens.contains(normalized)) tokens.add(normalized);
      }
    }
  }
  return tokens;
}

String logOperationLabel(String operation) =>
    securityLogCatalog[desktopLocaleController
        .languageTag]?['operation.${_normalize(operation)}'] ??
    securityLogText('ui.unknownOperation');
String logActionLabel(String? action) =>
    securityLogCatalog[desktopLocaleController
        .languageTag]?['action.${_normalize(action ?? '')}'] ??
    securityLogText('ui.unknownAction');
List<String> logMethodLabels(
  String operation,
  String? raw,
  List<String> methods,
) {
  if (!<String>[
    'LOGIN',
    'SENSITIVE_VERIFY',
    'BIND_OAUTH',
    'UNBIND_OAUTH',
  ].contains(_normalize(operation))) {
    return <String>[];
  }
  return parseLogMethods(raw, methods)
      .map(
        (token) =>
            securityLogCatalog[desktopLocaleController
                .languageTag]?['method.$token'] ??
            securityLogText('ui.unknownMethod'),
      )
      .toSet()
      .toList();
}

String? logReason(String? raw) {
  if (raw == null || raw.isEmpty) return null;
  final resolved = resolveAuditFailureReason(raw);
  if (resolved != raw) return resolved;
  final historical = raw
      .replaceFirst(RegExp(r'^risk=\d+;?\s*', caseSensitive: false), '')
      .trim();
  if (historical.isEmpty) return null;
  return securityLogCatalog[desktopLocaleController
          .languageTag]?['reason.${_normalize(historical).toLowerCase()}'] ??
      historical;
}
