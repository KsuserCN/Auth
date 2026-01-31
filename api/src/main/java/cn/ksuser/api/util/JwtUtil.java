package cn.ksuser.api.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 AccessToken
     * @param uuid 用户UUID
     * @param sessionId 会话ID
     * @param sessionVersion 会话令牌版本
     * @return AccessToken
     */
    public String generateAccessToken(String uuid, long sessionId, int sessionVersion) {
        return Jwts.builder()
                .subject(uuid)
                .claim("type", "access")
                .claim("sid", sessionId)
                .claim("sv", sessionVersion)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 生成 RefreshToken
     * @param uuid 用户UUID
     * @return RefreshToken
     */
    public String generateRefreshToken(String uuid) {
        return Jwts.builder()
                .subject(uuid)
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析 Token
     * @param token JWT Token
     * @return Claims
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 验证 Token 是否有效
     * @param token JWT Token
     * @return 是否有效
     */
    public boolean isTokenValid(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return false;
        }
        return claims.getExpiration().after(new Date());
    }

    /**
     * 从 Token 中获取用户 UUID
     * @param token JWT Token
     * @return 用户 UUID
     */
    public String getUuidFromToken(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        return claims.getSubject();
    }

    /**
     * 获取 Token 类型
     * @param token JWT Token
     * @return Token 类型 (access 或 refresh)
     */
    public String getTokenType(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        return claims.get("type", String.class);
    }

    /**
     * 获取会话ID
     * @param token JWT Token
     * @return sessionId
     */
    public Long getSessionId(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        return claims.get("sid", Long.class);
    }

    /**
     * 获取会话版本
     * @param token JWT Token
     * @return sessionVersion
     */
    public Integer getSessionVersion(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        return claims.get("sv", Integer.class);
    }

    /**
     * 获取 RefreshToken 过期时间（毫秒）
     * @return 过期时间
     */
    public long getRefreshTokenExpirationTime() {
        return refreshTokenExpiration;
    }
}
