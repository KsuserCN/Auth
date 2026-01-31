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
     * @param tokenVersion 令牌版本
     * @return AccessToken
     */
    public String generateAccessToken(String uuid, int tokenVersion) {
        return Jwts.builder()
                .subject(uuid)
                .claim("type", "access")
                .claim("tv", tokenVersion)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 生成 AccessToken（默认 tokenVersion = 0）
     * @param uuid 用户UUID
     * @return AccessToken
     */
    public String generateAccessToken(String uuid) {
        return generateAccessToken(uuid, 0);
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
     * 获取 Token 版本
     * @param token JWT Token
     * @return tokenVersion
     */
    public Integer getTokenVersion(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        return claims.get("tv", Integer.class);
    }

    /**
     * 获取 RefreshToken 过期时间（毫秒）
     * @return 过期时间
     */
    public long getRefreshTokenExpirationTime() {
        return refreshTokenExpiration;
    }
}
