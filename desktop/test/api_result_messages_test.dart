import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:ksuser_auth_desktop/api_result_messages.dart';
import 'package:ksuser_auth_desktop/generated/api_result_codes.dart';
import 'package:ksuser_auth_desktop/main.dart';
import 'package:ksuser_auth_desktop/i18n.dart';

void main() {
  setUp(() {
    desktopLocaleController = DesktopLocaleController(
      preference: DesktopLanguage.simplifiedChinese,
    );
  });
  test('HTTP fallback categories follow the shared client contract', () {
    const Map<int, String> expectedCodes = <int, String>{
      100: 'REQUEST_FAILED',
      200: 'REQUEST_SUCCESS',
      201: 'REQUEST_SUCCESS',
      202: 'REQUEST_SUCCESS',
      299: 'REQUEST_SUCCESS',
      300: 'REQUEST_FAILED',
      400: 'BAD_REQUEST',
      401: 'UNAUTHENTICATED',
      403: 'FORBIDDEN',
      404: 'NOT_FOUND',
      405: 'METHOD_NOT_ALLOWED',
      408: 'REQUEST_TIMEOUT',
      409: 'REQUEST_CONFLICT',
      413: 'REQUEST_UPLOAD_TOO_LARGE',
      415: 'UNSUPPORTED_MEDIA_TYPE',
      429: 'RATE_LIMITED',
      499: 'BAD_REQUEST',
      500: 'SERVER_ERROR',
      503: 'SERVER_ERROR',
    };
    for (final MapEntry<int, String> entry in expectedCodes.entries) {
      expect(apiFallbackResultCode(entry.key), entry.value);
    }
    expect(apiFallbackResultCode(null), 'REQUEST_FAILED');
  });

  test('detailed code selects the message independently of HTTP status', () {
    final ApiException error = ApiException.fromResponse(<String, dynamic>{
      'code': 400,
      'errorCode': 'SENSITIVE_VERIFICATION_REQUIRED',
      'msg': 'untrusted backend message',
      'error_description': 'untrusted OAuth message',
    }, 400);
    expect(error.message, apiResultMessages['SENSITIVE_VERIFICATION_REQUIRED']);
    expect(error.errorCode, 'SENSITIVE_VERIFICATION_REQUIRED');
    expect(error.statusCode, 400);
  });

  test(
    'missing and unknown codes fall back without displaying server text',
    () {
      for (final int status in <int>[
        200,
        201,
        202,
        400,
        401,
        403,
        404,
        405,
        408,
        409,
        413,
        415,
        429,
        500,
      ]) {
        for (final String? code in <String?>[null, 'FUTURE_UNKNOWN_CODE']) {
          final ApiException error =
              ApiException.fromResponse(<String, dynamic>{
                'errorCode': ?code,
                'msg': 'untrusted backend message',
                'message': 'untrusted alternate message',
                'error_description': 'untrusted OAuth message',
              }, status);
          expect(
            error.message,
            apiResultMessages[apiFallbackResultCode(status)],
          );
        }
      }
    },
  );

  test('catalog templates only interpolate primitive parameters', () {
    final MapEntry<String, String> entry = apiResultMessages.entries.firstWhere(
      (MapEntry<String, String> entry) => entry.value.contains('{'),
    );
    final List<RegExpMatch> placeholders = RegExp(
      r'\{([A-Za-z0-9_]+)\}',
    ).allMatches(entry.value).toList();
    final Map<String, dynamic> params = <String, dynamic>{
      for (final RegExpMatch match in placeholders) match.group(1)!: 12,
    };
    expect(
      resolveApiResultMessage(errorCode: entry.key, params: params),
      entry.value.replaceAll(RegExp(r'\{([A-Za-z0-9_]+)\}'), '12'),
    );
    params[placeholders.first.group(1)!] = <String, String>{
      'unsafe': 'object value',
    };
    expect(
      resolveApiResultMessage(errorCode: entry.key, params: params),
      apiResultMessages['REQUEST_FAILED'],
    );
    for (final dynamic value in <dynamic>[
      null,
      <String, String>{'unsafe': 'object value'},
      <String>['array value'],
    ]) {
      params[placeholders.first.group(1)!] = value;
      expect(
        resolveApiResultMessage(
          errorCode: entry.key,
          params: params,
          statusCode: 400,
        ),
        apiResultMessages['BAD_REQUEST'],
      );
    }
    params.remove(placeholders.first.group(1));
    expect(
      resolveApiResultMessage(
        errorCode: entry.key,
        params: params,
        statusCode: 500,
      ),
      apiResultMessages['SERVER_ERROR'],
    );
  });

  test(
    'Passkey bridge ignores message and resolves a detailed result code',
    () {
      final BrowserPasskeyBridgeResponse response =
          BrowserPasskeyBridgeResponse.fromJson(<String, dynamic>{
            'status': 'error',
            'errorCode': 'BAD_REQUEST',
            'message': 'untrusted bridge text',
          });
      expect(response.success, isFalse);
      expect(response.message, apiResultMessages['BAD_REQUEST']);
      expect(response.errorCode, 'BAD_REQUEST');
      expect(response.toException('fallback').errorCode, 'BAD_REQUEST');
    },
  );

  test('adaptive status resolves reason codes and ignores old text fields', () {
    final AdaptiveAuthStatus status = AdaptiveAuthStatus.fromJson(
      <String, dynamic>{
        'reasonCodes': <Map<String, dynamic>>[
          <String, dynamic>{'errorCode': 'FORBIDDEN'},
        ],
        'recommendedActionCode': 'SENSITIVE_VERIFICATION_REQUIRED',
        'alertTitleCode': 'FORBIDDEN',
        'alertMessageCode': 'UNAUTHENTICATED',
        'reasons': <String>['untrusted risk text'],
        'recommendedAction': 'untrusted action text',
        'alertTitle': 'untrusted title',
        'alertMessage': 'untrusted alert',
      },
    );
    expect(status.reasons, <String>[apiResultMessages['FORBIDDEN']!]);
    expect(
      status.recommendedAction,
      apiResultMessages['SENSITIVE_VERIFICATION_REQUIRED'],
    );
    expect(status.alertTitle, apiResultMessages['FORBIDDEN']);
    expect(status.alertMessage, apiResultMessages['UNAUTHENTICATED']);
  });

  test(
    'audit entries resolve full known codes and preserve historical prose',
    () {
      expect(
        resolveAuditFailureReason('FORBIDDEN'),
        apiResultMessages['FORBIDDEN'],
      );
      expect(resolveAuditFailureReason('历史审计原因'), '历史审计原因');
      expect(
        resolveAuditFailureReason('FUTURE_UNKNOWN_CODE'),
        'FUTURE_UNKNOWN_CODE',
      );
      expect(
        resolveAuditFailureReason('FORBIDDEN: existing detail'),
        'FORBIDDEN: existing detail',
      );
      expect(resolveAuditFailureReason(null), isNull);
      final String parameterizedCode = apiResultMessages.entries
          .firstWhere(
            (MapEntry<String, String> entry) => entry.value.contains('{'),
          )
          .key;
      expect(
        resolveAuditFailureReason(parameterizedCode),
        apiResultMessages['REQUEST_FAILED'],
      );
      final SensitiveLogItem log = SensitiveLogItem.fromJson(<String, dynamic>{
        'failureReason': 'FORBIDDEN',
      });
      expect(log.failureReason, apiResultMessages['FORBIDDEN']);
    },
  );

  test(
    'retries CSRF using a code and does not retry ordinary forbidden',
    () async {
      final HttpServer server = await HttpServer.bind(
        InternetAddress.loopbackIPv4,
        0,
      );
      addTearDown(server.close);
      int healthCount = 0;
      int protectedRequests = 0;
      int forbiddenRequests = 0;
      server.listen((HttpRequest request) async {
        request.response.headers.contentType = ContentType.json;
        if (request.uri.path == '/auth/health') {
          healthCount += 1;
          request.response.headers.add(
            HttpHeaders.setCookieHeader,
            'XSRF-TOKEN=xsrf-$healthCount; Path=/',
          );
          request.response.write(
            jsonEncode(<String, dynamic>{
              'code': 200,
              'errorCode': 'REQUEST_SUCCESS',
            }),
          );
        } else if (request.uri.path == '/protected') {
          protectedRequests += 1;
          request.response.statusCode = protectedRequests == 1 ? 403 : 200;
          request.response.write(
            jsonEncode(<String, dynamic>{
              'code': request.response.statusCode,
              'errorCode': protectedRequests == 1
                  ? 'SECURITY_CSRF_INVALID'
                  : 'REQUEST_SUCCESS',
              if (protectedRequests > 1) 'data': <String, dynamic>{'ok': true},
            }),
          );
        } else {
          forbiddenRequests += 1;
          request.response.statusCode = 403;
          request.response.write(
            jsonEncode(<String, dynamic>{
              'code': 403,
              'errorCode': 'FORBIDDEN',
              'msg': '无权限',
            }),
          );
        }
        await request.response.close();
      });
      final KsuserApiClient client = KsuserApiClient(
        baseUrl: 'http://127.0.0.1:${server.port}',
      );
      expect(asMap(await client.post('/protected'))['ok'], isTrue);
      expect(healthCount, 2);
      expect(protectedRequests, 2);
      await expectLater(
        client.post('/forbidden'),
        throwsA(
          isA<ApiException>().having(
            (ApiException error) => error.errorCode,
            'errorCode',
            'FORBIDDEN',
          ),
        ),
      );
      expect(forbiddenRequests, 1);
      expect(healthCount, 2);
    },
  );

  test('202 sensitive verification requests retain the retry signal', () async {
    final HttpServer server = await HttpServer.bind(
      InternetAddress.loopbackIPv4,
      0,
    );
    addTearDown(server.close);
    server.listen((HttpRequest request) async {
      request.response.headers.contentType = ContentType.json;
      request.response.statusCode = 202;
      request.response.write(
        jsonEncode(<String, dynamic>{
          'code': 202,
          'errorCode': 'SENSITIVE_VERIFICATION_REQUIRED',
          'data': <String, dynamic>{'needVerification': true},
        }),
      );
      await request.response.close();
    });
    final KsuserApiClient client = KsuserApiClient(
      baseUrl: 'http://127.0.0.1:${server.port}',
    );
    await expectLater(
      client.get('/requires-verification'),
      throwsA(
        isA<ApiException>()
            .having(
              (ApiException error) => error.errorCode,
              'errorCode',
              'SENSITIVE_VERIFICATION_REQUIRED',
            )
            .having(
              (ApiException error) => error.statusCode,
              'statusCode',
              202,
            ),
      ),
    );
  });

  test('MFA and binding results still return their flow data', () async {
    final HttpServer server = await HttpServer.bind(
      InternetAddress.loopbackIPv4,
      0,
    );
    addTearDown(server.close);
    server.listen((HttpRequest request) async {
      final bool mfa = request.uri.path == '/mfa';
      request.response.headers.contentType = ContentType.json;
      request.response.statusCode = mfa ? 201 : 202;
      request.response.write(
        jsonEncode(<String, dynamic>{
          'code': mfa ? 201 : 202,
          'errorCode': mfa ? 'AUTH_MFA_REQUIRED' : 'OAUTH_BIND_REQUIRED',
          'data': <String, dynamic>{
            if (mfa) 'challengeId': 'challenge-1',
            if (!mfa) 'needBind': true,
          },
        }),
      );
      await request.response.close();
    });
    final KsuserApiClient client = KsuserApiClient(
      baseUrl: 'http://127.0.0.1:${server.port}',
    );
    expect(asMap(await client.get('/mfa'))['challengeId'], 'challenge-1');
    expect(asMap(await client.get('/bind'))['needBind'], isTrue);
  });

  test(
    'multipart errors use codes and HTTP 200 business failures still fail',
    () async {
      final HttpServer server = await HttpServer.bind(
        InternetAddress.loopbackIPv4,
        0,
      );
      addTearDown(server.close);
      server.listen((HttpRequest request) async {
        request.response.headers.contentType = ContentType.json;
        if (request.uri.path == '/auth/health') {
          request.response.headers.add(
            HttpHeaders.setCookieHeader,
            'XSRF-TOKEN=xsrf; Path=/',
          );
          request.response.write(
            jsonEncode(<String, dynamic>{
              'code': 200,
              'errorCode': 'REQUEST_SUCCESS',
            }),
          );
        } else {
          await request.drain<void>();
          request.response.statusCode = request.uri.path == '/upload'
              ? 400
              : 200;
          request.response.write(
            jsonEncode(<String, dynamic>{
              'code': 400,
              'errorCode': 'BAD_REQUEST',
              'msg': 'untrusted backend message',
            }),
          );
        }
        await request.response.close();
      });
      final KsuserApiClient client = KsuserApiClient(
        baseUrl: 'http://127.0.0.1:${server.port}',
      );
      final Matcher expectedError = throwsA(
        isA<ApiException>()
            .having(
              (ApiException error) => error.errorCode,
              'errorCode',
              'BAD_REQUEST',
            )
            .having(
              (ApiException error) => error.message,
              'message',
              apiResultMessages['BAD_REQUEST'],
            ),
      );
      await expectLater(
        client.postMultipartFile(
          '/upload',
          fieldName: 'avatar',
          fileName: 'test.png',
          contentType: 'image/png',
          bytes: Uint8List.fromList(<int>[1, 2, 3]),
        ),
        expectedError,
      );
      await expectLater(client.get('/business-error'), expectedError);
    },
  );
}
