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
public class Oauth2TokenService {

    private static final Set<String> PROFILE_CLAIMS = Set.of("profile");
    private static final Set<String> EMAIL_CLAIMS = Set.of("email");

    @Value("${jwt.secret}")
    private String secret;

    @Value("${app.oauth2.access-token-expiration-seconds:7200}")
    private long accessTokenExpirationSeconds;

    @Value("${app.oidc.issuer:http://localhost:8000}")
    private String issuer;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(String clientId, Long ownerUserId, Long userId, String userUuid,
                                      String scope, String openid, String unionid) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
            .subject(userUuid)
            .claim("type", "oauth2_access")
            .claim("client_id", clientId)
            .claim("owner_user_id", ownerUserId)
            .claim("user_id", userId)
            .claim("scope", scope == null ? "" : scope)
            .claim("openid", openid)
            .claim("unionid", unionid)
            .issuedAt(new Date(now))
            .expiration(new Date(now + accessTokenExpirationSeconds * 1000))
            .signWith(getSigningKey())
            .compact();
    }

    public String generateIdToken(String clientId,
                                  String subject,
                                  String nonce,
                                  String scope,
                                  String username,
                                  String avatarUrl,
                                  String email) {
        long now = System.currentTimeMillis();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("type", "oidc_id_token");
        claims.put("iss", issuer);
        claims.put("aud", clientId);

        Set<String> scopes = Set.copyOf(Oauth2ScopeUtil.parseScopeSet(scope));
        if (nonce != null && !nonce.isBlank()) {
            claims.put("nonce", nonce);
        }
        if (scopes.stream().anyMatch(PROFILE_CLAIMS::contains) && username != null && !username.isBlank()) {
            claims.put("nickname", username);
            claims.put("preferred_username", username);
        }
        if (scopes.stream().anyMatch(PROFILE_CLAIMS::contains) && avatarUrl != null && !avatarUrl.isBlank()) {
            claims.put("picture", avatarUrl);
        }
        if (scopes.stream().anyMatch(EMAIL_CLAIMS::contains) && email != null && !email.isBlank()) {
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

    public ParsedOauth2AccessToken parse(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        try {
            Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

            if (!"oauth2_access".equals(claims.get("type", String.class))) {
                return null;
            }

            Date expiration = claims.getExpiration();
            if (expiration == null || expiration.before(new Date())) {
                return null;
            }

            return new ParsedOauth2AccessToken(
                claims.get("client_id", String.class),
                toLong(claims.get("owner_user_id")),
                toLong(claims.get("user_id")),
                claims.getSubject(),
                claims.get("scope", String.class),
                claims.get("openid", String.class),
                claims.get("unionid", String.class),
                expiration
            );
        } catch (Exception e) {
            return null;
        }
    }

    public ParsedOidcIdToken parseIdToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        try {
            Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

            if (!"oidc_id_token".equals(claims.get("type", String.class))) {
                return null;
            }

            Date expiration = claims.getExpiration();
            if (expiration == null || expiration.before(new Date())) {
                return null;
            }

            return new ParsedOidcIdToken(
                firstStringValue(claims.get("iss")),
                firstStringValue(claims.get("aud")),
                claims.getSubject(),
                claims.get("nonce", String.class),
                claims.get("nickname", String.class),
                claims.get("preferred_username", String.class),
                claims.get("picture", String.class),
                claims.get("email", String.class),
                Boolean.TRUE.equals(claims.get("email_verified", Boolean.class)),
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

    public record ParsedOauth2AccessToken(String clientId,
                                          Long ownerUserId,
                                          Long userId,
                                          String userUuid,
                                          String scope,
                                          String openid,
                                          String unionid,
                                          Date expiresAt) {
    }

    public record ParsedOidcIdToken(String issuer,
                                    String audience,
                                    String subject,
                                    String nonce,
                                    String nickname,
                                    String preferredUsername,
                                    String picture,
                                    String email,
                                    boolean emailVerified,
                                    Date expiresAt) {
    }
}
