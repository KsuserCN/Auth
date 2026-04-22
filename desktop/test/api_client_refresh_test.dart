import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:ksuser_auth_desktop/main.dart';

void main() {
  test('refreshes expired access token and re-warms CSRF after server clears it', () async {
    final HttpServer server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    addTearDown(server.close);

    int healthCount = 0;
    int refreshCount = 0;
    String currentXsrfToken = 'xsrf-1';
    String currentRefreshToken = 'refresh-1';
    const String expiredAccessToken = 'access-expired';
    String currentAccessToken = expiredAccessToken;

    server.listen((HttpRequest request) async {
      final String path = request.uri.path;

      void jsonResponse(int statusCode, Map<String, dynamic> payload) {
        request.response.statusCode = statusCode;
        request.response.headers.contentType = ContentType.json;
        request.response.write(jsonEncode(payload));
      }

      if (path == '/auth/health') {
        healthCount += 1;
        currentXsrfToken = 'xsrf-$healthCount';
        request.response.headers.add(HttpHeaders.setCookieHeader, 'XSRF-TOKEN=$currentXsrfToken; Path=/');
        jsonResponse(200, <String, dynamic>{'code': 200, 'msg': '服务正常'});
        await request.response.close();
        return;
      }

      if (path == '/auth/login') {
        currentRefreshToken = 'refresh-1';
        currentAccessToken = expiredAccessToken;
        request.response.headers.add(
          HttpHeaders.setCookieHeader,
          'refreshToken=$currentRefreshToken; Path=/; HttpOnly; Max-Age=604800',
        );
        jsonResponse(200, <String, dynamic>{
          'code': 200,
          'msg': '登录成功',
          'data': <String, dynamic>{'accessToken': currentAccessToken},
        });
        await request.response.close();
        return;
      }

      if (path == '/auth/refresh') {
        refreshCount += 1;
        final String cookieHeader = request.headers.value(HttpHeaders.cookieHeader) ?? '';
        expect(cookieHeader, contains('refreshToken=$currentRefreshToken'));
        currentRefreshToken = 'refresh-${refreshCount + 1}';
        currentAccessToken = 'access-${refreshCount + 1}';
        request.response.headers.add(
          HttpHeaders.setCookieHeader,
          'refreshToken=$currentRefreshToken; Path=/; HttpOnly; Max-Age=604800',
        );
        jsonResponse(200, <String, dynamic>{
          'code': 200,
          'msg': '刷新成功',
          'data': <String, dynamic>{'accessToken': currentAccessToken},
        });
        await request.response.close();
        return;
      }

      if (path == '/auth/info') {
        final String authHeader = request.headers.value(HttpHeaders.authorizationHeader) ?? '';
        if (authHeader != 'Bearer $currentAccessToken') {
          jsonResponse(401, <String, dynamic>{'code': 401, 'msg': '未登录或Token已过期'});
        } else if (currentAccessToken == expiredAccessToken) {
          jsonResponse(401, <String, dynamic>{'code': 401, 'msg': '未登录或Token已过期'});
        } else {
          // Match the real backend behavior observed after refresh: it clears the XSRF cookie.
          request.response.headers.add(
            HttpHeaders.setCookieHeader,
            'XSRF-TOKEN=; Expires=Thu, 01 Jan 1970 00:00:10 GMT; Path=/',
          );
          jsonResponse(200, <String, dynamic>{
            'code': 200,
            'msg': '获取成功',
            'data': <String, dynamic>{'username': 'tester'},
          });
        }
        await request.response.close();
        return;
      }

      if (path == '/auth/update/profile') {
        final String authHeader = request.headers.value(HttpHeaders.authorizationHeader) ?? '';
        final String xsrfHeader = request.headers.value('X-XSRF-TOKEN') ?? '';
        if (authHeader != 'Bearer $currentAccessToken') {
          jsonResponse(401, <String, dynamic>{'code': 401, 'msg': '未登录或Token已过期'});
        } else if (xsrfHeader != currentXsrfToken) {
          jsonResponse(403, <String, dynamic>{'code': 403, 'msg': '无权限'});
        } else {
          jsonResponse(200, <String, dynamic>{
            'code': 200,
            'msg': '更新成功',
            'data': <String, dynamic>{'username': 'tester'},
          });
        }
        await request.response.close();
        return;
      }

      jsonResponse(404, <String, dynamic>{'code': 404, 'msg': 'not found'});
      await request.response.close();
    });

    final KsuserApiClient client = KsuserApiClient(baseUrl: 'http://127.0.0.1:${server.port}');

    final Map<String, dynamic> loginPayload = asMap(
      await client.post(
        '/auth/login',
        body: const <String, dynamic>{
          'email': 'integration@example.com',
          'password': 'secret',
        },
      ),
    );
    client.accessToken = asString(loginPayload['accessToken']);

    final Map<String, dynamic> infoPayload = asMap(
      await client.get('/auth/info', query: const <String, String>{'type': 'details'}, authorized: true),
    );
    expect(infoPayload['username'], 'tester');
    expect(refreshCount, 1);

    final Map<String, dynamic> updatedPayload = asMap(
      await client.post(
        '/auth/update/profile',
        authorized: true,
        body: const <String, dynamic>{'key': 'username', 'value': 'tester'},
      ),
    );
    expect(updatedPayload['username'], 'tester');
    expect(healthCount, 2);
    expect(refreshCount, 1);
  });
}
