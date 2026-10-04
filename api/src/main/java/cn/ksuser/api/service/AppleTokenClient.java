package cn.ksuser.api.service;

import cn.ksuser.api.config.AppleAuthProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.time.Instant;
import java.util.*;

/** Fixed Apple HTTPS endpoints; no caller-controlled URL, key, issuer, or audience. */
@Service
public class AppleTokenClient {
    private static final String ISSUER = "https://appleid.apple.com";
    private final AppleAuthProperties properties;
    private final ObjectMapper json = new ObjectMapper();
    private final RestTemplate http;
    private Map<String, PublicKey> keys = Map.of();
    private long keysExpireAt;
    private long lastKeysFetchAt;
    public AppleTokenClient(AppleAuthProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.http = new RestTemplate(factory);
    }
    public record Identity(String sub, String email) {}
    public record Tokens(String refreshToken, Identity identity) {}

    public Identity verifyIdentity(String token, String clientId, String nonce) {
        Claims claims = verifyClaims(token, clientId);
        String claimedNonce = claims.get("nonce", String.class);
        if (nonce == null || claimedNonce == null || !MessageDigest.isEqual(
                nonce.getBytes(StandardCharsets.UTF_8), claimedNonce.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Apple nonce 校验失败，请重新授权");
        }
        if (claims.getSubject() == null || claims.getSubject().isBlank() || claims.getSubject().length() > 128) {
            throw new IllegalArgumentException("Apple 身份无效");
        }
        String email = claims.get("email", String.class);
        Object verified = claims.get("email_verified");
        if (!Boolean.TRUE.equals(verified) && !"true".equals(verified)) email = null;
        if (email != null && (email.length() > 255 || !email.contains("@"))) email = null;
        return new Identity(claims.getSubject(), email);
    }

    Claims verifyClaims(String token, String clientId) {
        if (token == null || token.length() > 16384) throw new IllegalArgumentException("Apple 凭据无效");
        if (!properties.getClientIds().contains(clientId)) throw new IllegalArgumentException("Apple audience 不受信任");
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) throw new IllegalArgumentException("Apple JWT 格式无效");
            JsonNode header = json.readTree(Base64.getUrlDecoder().decode(parts[0]));
            if (!"RS256".equals(header.path("alg").asText())) throw new IllegalArgumentException("Apple JWT 算法无效");
            PublicKey key = signingKey(header.path("kid").asText());
            Claims claims = Jwts.parser().verifyWith(key).requireIssuer(ISSUER).clockSkewSeconds(30)
                .build().parseSignedClaims(token).getPayload();
            if (claims.getAudience() == null || !claims.getAudience().contains(clientId)
                || claims.getExpiration() == null || claims.getIssuedAt() == null
                || claims.getIssuedAt().toInstant().isAfter(Instant.now().plusSeconds(30))) {
                throw new IllegalArgumentException("Apple JWT 声明无效");
            }
            return claims;
        } catch (IllegalArgumentException ex) { throw ex; }
        catch (Exception ex) { throw new IllegalArgumentException("Apple 凭据校验失败，请重新授权"); }
    }

    private synchronized PublicKey signingKey(String kid) throws Exception {
        if (kid == null || kid.isBlank() || kid.length() > 128) throw new IllegalArgumentException("Apple key id 无效");
        long now = Instant.now().getEpochSecond();
        if ((now >= keysExpireAt || !keys.containsKey(kid)) && now - lastKeysFetchAt >= 30) {
            // Unknown kid cannot force an unbounded number of network calls.
            lastKeysFetchAt = now;
            JsonNode document = json.readTree(http.getForObject(ISSUER + "/auth/keys", String.class));
            Map<String, PublicKey> next = new HashMap<>();
            for (JsonNode item : document.path("keys")) {
                if (!"RSA".equals(item.path("kty").asText()) || !"sig".equals(item.path("use").asText()) || !"RS256".equals(item.path("alg").asText())) continue;
                RSAPublicKeySpec spec = new RSAPublicKeySpec(
                    new BigInteger(1, Base64.getUrlDecoder().decode(item.path("n").asText())),
                    new BigInteger(1, Base64.getUrlDecoder().decode(item.path("e").asText())));
                next.put(item.path("kid").asText(), KeyFactory.getInstance("RSA").generatePublic(spec));
            }
            keys = Map.copyOf(next);
            keysExpireAt = now + 3600;
        }
        PublicKey key = keys.get(kid);
        if (key == null) throw new IllegalArgumentException("Apple 签名密钥未知");
        return key;
    }

    public Tokens exchangeCode(String code, String clientId, String nonce) {
        if (code == null || code.isBlank() || code.length() > 4096) throw new IllegalArgumentException("Apple 授权码缺失");
        MultiValueMap<String, String> form = form(clientId);
        form.add("grant_type", "authorization_code"); form.add("code", code);
        JsonNode result = post("/auth/token", form);
        Identity identity = verifyIdentity(result.path("id_token").asText(), clientId, nonce);
        String refresh = result.path("refresh_token").asText();
        if (refresh.isBlank()) throw new IllegalArgumentException("Apple 未返回可撤销凭据，请重新授权");
        return new Tokens(refresh, identity);
    }

    public void revoke(String token, String clientId) {
        MultiValueMap<String, String> form = form(clientId);
        form.add("token", token); form.add("token_type_hint", "refresh_token");
        post("/auth/revoke", form);
    }

    public Claims verifyNotification(String payload) {
        // Notifications are scoped to one configured primary App ID, verified exactly as identity tokens.
        for (String clientId : properties.getClientIds()) {
            try { return verifyClaims(payload, clientId); } catch (IllegalArgumentException ignored) { }
        }
        throw new IllegalArgumentException("Apple 通知签名或 audience 无效");
    }

    private MultiValueMap<String, String> form(String clientId) {
        properties.requireConfigured(clientId);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId); form.add("client_secret", clientSecret(clientId));
        return form;
    }
    private String clientSecret(String clientId) {
        try {
            String pem = properties.getPrivateKeyPem().replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
            PrivateKey key = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
            Instant now = Instant.now();
            return Jwts.builder().header().keyId(properties.getKeyId()).and().issuer(properties.getTeamId())
                .subject(clientId).audience().add(ISSUER).and().issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300))).signWith(key, Jwts.SIG.ES256).compact();
        } catch (Exception ex) { throw new IllegalStateException("Apple 服务端私钥配置无效"); }
    }
    private JsonNode post(String path, MultiValueMap<String, String> form) {
        HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        try {
            String body = http.postForObject(ISSUER + path, new HttpEntity<>(form, headers), String.class);
            return body == null || body.isBlank() ? json.createObjectNode() : json.readTree(body);
        } catch (Exception ex) {
            // Do not include provider response body, codes, client secrets or tokens in logs/errors.
            throw new IllegalStateException("Apple 服务暂时不可用，请稍后重试");
        }
    }
}
