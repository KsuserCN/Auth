package cn.ksuser.api.service;

import cn.ksuser.api.config.ApnsProperties;
import cn.ksuser.api.entity.*;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.net.http.*;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ApnsClientTest {
    private ApnsProperties properties;
    private HttpClient http;
    private ApnsClient client;
    private KeyPair key;
    private UserPushDevice device;
    @BeforeEach void setUp() throws Exception {
        properties = new ApnsProperties(); properties.setEnabled(true); properties.setTeamId("test-team"); properties.setKeyId("test-key");
        var generator = KeyPairGenerator.getInstance("EC"); generator.initialize(new ECGenParameterSpec("secp256r1")); key = generator.generateKeyPair();
        http = mock(HttpClient.class); client = new ApnsClient(properties, http, key.getPrivate());
        var user = new User(); user.setUuid("test-account"); var session = new UserSession(); session.setUser(user);
        device = new UserPushDevice(); device.setSession(session); device.setDeviceToken("a".repeat(64)); device.setEnvironment(UserPushDevice.Environment.SANDBOX);
    }
    @SuppressWarnings("unchecked") private void response(int status, String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class); when(response.statusCode()).thenReturn(status); when(response.body()).thenReturn(body);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }
    @Test void signedHttp2RequestUsesCorrectEnvironmentAndPrivatePayload() throws Exception {
        response(200, ""); assertTrue(client.send(device, "安全提醒", "请查看操作日志", "123").accepted());
        var capture = ArgumentCaptor.forClass(HttpRequest.class); verify(http).send(capture.capture(), any()); var request = capture.getValue();
        assertEquals(HttpClient.Version.HTTP_2, request.version().orElseThrow());
        assertEquals("api.sandbox.push.apple.com", request.uri().getHost()); assertEquals("POST", request.method());
        assertEquals("cn.ksuser.auth", request.headers().firstValue("apns-topic").orElseThrow());
        assertEquals("alert", request.headers().firstValue("apns-push-type").orElseThrow());
        assertEquals("0", request.headers().firstValue("apns-expiration").orElseThrow());
        String token = request.headers().firstValue("authorization").orElseThrow().substring(7);
        var jwt = Jwts.parser().verifyWith((ECPublicKey) key.getPublic()).build().parseSignedClaims(token);
        assertEquals("test-team", jwt.getPayload().getIssuer()); assertEquals("test-key", jwt.getHeader().getKeyId()); assertNotNull(jwt.getPayload().getIssuedAt());
        String payload = body(request);
        assertTrue(payload.contains("\"accountId\":\"test-account\"")); assertTrue(payload.contains("\"kind\":\"security\""));
        assertFalse(payload.contains(device.getDeviceToken())); assertFalse(payload.contains(token));
        device.setEnvironment(UserPushDevice.Environment.PRODUCTION); client.send(device, "安全提醒", "请查看操作日志", "124");
        verify(http, times(2)).send(capture.capture(), any()); assertEquals("api.push.apple.com", capture.getValue().uri().getHost());
    }
    @Test void onlyPermanentDeviceErrorsInvalidateToken() throws Exception {
        for (String reason : new String[]{"BadDeviceToken", "DeviceTokenNotForTopic", "Unregistered"}) {
            response(400, "{\"reason\":\"" + reason + "\"}"); assertTrue(client.send(device, "title", "body", "1").invalidToken());
        }
        for (String reason : new String[]{"ExpiredProviderToken", "TooManyRequests", "InternalServerError"}) {
            response(403, "{\"reason\":\"" + reason + "\"}"); assertFalse(client.send(device, "title", "body", "1").invalidToken());
        }
    }
    @Test void enabledConfigurationRequiresKeyButDisabledDoesNot() {
        assertDoesNotThrow(() -> new ApnsClient(new ApnsProperties()));
        assertThrows(IllegalStateException.class, () -> new ApnsClient(properties));
    }
    private String body(HttpRequest request) throws Exception {
        var done = new CompletableFuture<String>(); var bytes = new ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer value) { byte[] part = new byte[value.remaining()]; value.get(part); bytes.writeBytes(part); }
            public void onError(Throwable error) { done.completeExceptionally(error); }
            public void onComplete() { done.complete(bytes.toString(StandardCharsets.UTF_8)); }
        });
        return done.get();
    }
}
