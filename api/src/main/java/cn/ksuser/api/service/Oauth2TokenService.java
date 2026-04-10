package cn.ksuser.api.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class Oauth2TokenService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${app.oauth2.access-token-expiration-seconds:7200}")
    private long accessTokenExpirationSeconds;

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

    public record ParsedOauth2AccessToken(String clientId,
                                          Long ownerUserId,
                                          Long userId,
                                          String userUuid,
                                          String scope,
                                          String openid,
                                          String unionid,
                                          Date expiresAt) {
    }
}
