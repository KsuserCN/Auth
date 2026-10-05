package cn.ksuser.api.service;

import cn.ksuser.api.config.ApnsProperties;
import cn.ksuser.api.entity.UserPushDevice;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.EnumMap;
import java.util.Map;

@Component
public class ApnsClient {
    public record Delivery(boolean accepted, boolean invalidToken, String reason) {}
    private record ProviderKey(UserPushDevice.Environment environment, String keyId, PrivateKey key) {}
    private record CachedToken(String value, Instant issuedAt) {}
    private final ApnsProperties properties;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<UserPushDevice.Environment, ProviderKey> providerKeys;
    private final Map<UserPushDevice.Environment, CachedToken> cachedTokens = new EnumMap<>(UserPushDevice.Environment.class);

    @org.springframework.beans.factory.annotation.Autowired
    public ApnsClient(ApnsProperties properties) {
        this(properties, HttpClient.newBuilder().version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(5)).build(), loadKeys(properties));
    }

    ApnsClient(ApnsProperties properties, HttpClient http, PrivateKey key) {
        this(properties, http, testKeys(properties, key));
    }

    private ApnsClient(ApnsProperties properties, HttpClient http, Map<UserPushDevice.Environment, ProviderKey> providerKeys) {
        this.properties = properties; this.http = http; this.providerKeys = providerKeys;
    }

    private static Map<UserPushDevice.Environment, ProviderKey> testKeys(ApnsProperties properties, PrivateKey key) {
        if (key == null) return Map.of();
        Map<UserPushDevice.Environment, ProviderKey> result = new EnumMap<>(UserPushDevice.Environment.class);
        for (var environment : UserPushDevice.Environment.values()) {
            String keyId = keyId(properties, environment);
            result.put(environment, new ProviderKey(environment, keyId, key));
        }
        return result;
    }

    private static Map<UserPushDevice.Environment, ProviderKey> loadKeys(ApnsProperties properties) {
        if (!properties.isEnabled()) return Map.of();
        if (properties.getTeamId().isBlank() || properties.getTopic().isBlank()) {
            throw new IllegalStateException("APNs enabled but team ID or topic is missing");
        }
        Map<UserPushDevice.Environment, ProviderKey> result = new EnumMap<>(UserPushDevice.Environment.class);
        for (var environment : UserPushDevice.Environment.values()) {
            String keyId = keyId(properties, environment);
            String path = keyPath(properties, environment);
            if (keyId.isBlank() || path.isBlank()) {
                throw new IllegalStateException("APNs enabled but " + environment + " key ID or private key path is missing");
            }
            result.put(environment, new ProviderKey(environment, keyId, loadKey(path)));
        }
        return result;
    }

    private static String keyId(ApnsProperties p, UserPushDevice.Environment environment) {
        String scoped = environment == UserPushDevice.Environment.SANDBOX ? p.getSandboxKeyId() : p.getProductionKeyId();
        return scoped == null || scoped.isBlank() ? p.getKeyId() : scoped;
    }

    private static String keyPath(ApnsProperties p, UserPushDevice.Environment environment) {
        String scoped = environment == UserPushDevice.Environment.SANDBOX ? p.getSandboxPrivateKeyPath() : p.getProductionPrivateKeyPath();
        return scoped == null || scoped.isBlank() ? p.getPrivateKeyPath() : scoped;
    }

    private static PrivateKey loadKey(String path) {
        try {
            String pem = Files.readString(Path.of(path))
                .replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
            PrivateKey key = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
            if (!(key instanceof java.security.interfaces.ECPrivateKey ec) || ec.getParams().getOrder().bitLength() != 256) {
                throw new IllegalArgumentException("APNs requires a P-256 signing key");
            }
            return key;
        } catch (Exception e) { throw new IllegalStateException("Unable to load APNs signing key", e); }
    }

    private synchronized String providerToken(ProviderKey providerKey) {
        Instant now = Instant.now();
        CachedToken cached = cachedTokens.get(providerKey.environment());
        if (cached == null || cached.issuedAt().plusSeconds(3000).isBefore(now)) {
            cached = new CachedToken(Jwts.builder().header().keyId(providerKey.keyId()).and()
                .issuer(properties.getTeamId()).issuedAt(Date.from(now)).signWith(providerKey.key(), Jwts.SIG.ES256).compact(), now);
            cachedTokens.put(providerKey.environment(), cached);
        }
        return cached.value();
    }

    public Delivery send(UserPushDevice device, String title, String body, String eventId) throws Exception {
        if (!properties.isEnabled()) return new Delivery(false, false, "Disabled");
        ProviderKey providerKey = providerKeys.get(device.getEnvironment());
        if (providerKey == null) throw new IllegalStateException("APNs signing key is not configured for device environment");
        String host = device.getEnvironment() == UserPushDevice.Environment.SANDBOX
            ? "https://api.sandbox.push.apple.com" : "https://api.push.apple.com";
        // Payload deliberately excludes IP addresses, credentials and failure details from the lock screen.
        String payload = json.writeValueAsString(Map.of("aps", Map.of("alert", Map.of("title", title, "body", body),
            "sound", "default", "thread-id", "account-security"), "kind", "security", "eventId", eventId,
            "accountId", device.getSession().getUser().getUuid()));
        HttpRequest request = HttpRequest.newBuilder(URI.create(host + "/3/device/" + device.getDeviceToken()))
            .version(HttpClient.Version.HTTP_2).timeout(Duration.ofSeconds(10))
            .header("authorization", "bearer " + providerToken(providerKey)).header("apns-topic", properties.getTopic())
            .header("apns-push-type", "alert").header("apns-priority", "10")
            // Do not queue security notifications for a device that may subsequently log out.
            .header("apns-expiration", "0").header("apns-collapse-id", "security-" + eventId)
            .header("content-type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) return new Delivery(true, false, "Success");
        String reason = json.readTree(response.body()).path("reason").asText("Unknown");
        boolean invalid = "Unregistered".equals(reason) || "BadDeviceToken".equals(reason) || "DeviceTokenNotForTopic".equals(reason);
        return new Delivery(false, invalid, reason);
    }
}
