package cn.ksuser.api.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class SsoTokenService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${app.sso.access-token-expiration-seconds:7200}")
    private long accessTokenExpirationSeconds;

    @Value("${app.sso.issuer:http://localhost:8000}")
    private String issuer;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(String clientId, Long userId, String subject, String scope, String audience) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
            .subject(subject)
            .claim("type", "sso_access")
            .claim("client_id", clientId)
            .claim("user_id", userId)
            .claim("scope", scope == null ? "" : scope)
            .claim("aud", audience)
            .issuedAt(new Date(now))
            .expiration(new Date(now + accessTokenExpirationSeconds * 1000))
            .signWith(getSigningKey())
            .compact();
    }

    public String generateIdToken(String clientId, String subject, String nonce,
                                  String scope, String username, String avatarUrl, String email) {
        long now = System.currentTimeMillis();
        Set<String> scopes = Oauth2ScopeUtil.parseScopeSet(scope);
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("type", "sso_id_token");
        claims.put("iss", issuer);
        claims.put("aud", clientId);
        if (nonce != null && !nonce.isBlank()) {
            claims.put("nonce", nonce);
        }
        if (scopes.contains("profile")) {
            claims.put("nickname", username);
            claims.put("preferred_username", username);
            if (avatarUrl != null && !avatarUrl.isBlank()) {
                claims.put("picture", avatarUrl);
            }
        }
        if (scopes.contains("email") && email != null && !email.isBlank()) {
            claims.put("email", email);
            claims.put("email_verified", true);
        }
        return Jwts.builder()
            .claims(claims)
            .subject(subject)
            .issuedAt(new Date(now))
            .expiration(new Date(now + accessTokenExpirationSeconds * 1000))
            .signWith(getSigningKey())
            .compact();
    }

    public ParsedSsoAccessToken parseAccessToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        try {
            Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
            if (!"sso_access".equals(claims.get("type", String.class))) {
                return null;
            }
            Date expiration = claims.getExpiration();
            if (expiration == null || expiration.before(new Date())) {
                return null;
            }
            return new ParsedSsoAccessToken(
                claims.get("client_id", String.class),
                toLong(claims.get("user_id")),
                claims.getSubject(),
                claims.get("scope", String.class),
                firstStringValue(claims.get("aud")),
                expiration
            );
        } catch (Exception e) {
            return null;
        }
    }

    public int getAccessTokenExpiresInSeconds() {
        return (int) accessTokenExpirationSeconds;
    }

    private Long toLong(Object value) {
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Integer intValue) {
            return intValue.longValue();
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private String firstStringValue(Object value) {
        if (value instanceof String stringValue) {
            return stringValue;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null) {
                    return item.toString();
                }
            }
        }
        return value == null ? null : value.toString();
    }

    public record ParsedSsoAccessToken(String clientId,
                                       Long userId,
                                       String subject,
                                       String scope,
                                       String audience,
                                       Date expiresAt) {
    }
}
