package cn.ksuser.api.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Token 黑名单服务 - 用于管理被吊销的 Token
 */
@Service
public class TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "token:blacklist:";
    private final StringRedisTemplate redisTemplate;

    public TokenBlacklistService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 将 Token 加入黑名单
     * @param token JWT Token
     * @param expirationMinutes Token 过期时间（分钟）
     */
    public void addToBlacklist(String token, long expirationMinutes) {
        if (token != null && !token.isEmpty()) {
            String key = BLACKLIST_PREFIX + hashToken(token);
            redisTemplate.opsForValue().set(key, "revoked", expirationMinutes, TimeUnit.MINUTES);
        }
    }

    /**
     * 将 Token 加入黑名单
     * @param token JWT Token
     * @param expirationDuration 过期时长
     */
    public void addToBlacklist(String token, Duration expirationDuration) {
        if (token != null && !token.isEmpty()) {
            String key = BLACKLIST_PREFIX + hashToken(token);
            redisTemplate.opsForValue().set(key, "revoked", expirationDuration);
        }
    }

    /**
     * 检查 Token 是否在黑名单中
     * @param token JWT Token
     * @return true 表示 Token 已被吊销，false 表示仍有效
     */
    public boolean isBlacklisted(String token) {
        if (token == null || token.isEmpty()) {
            return true; // 空 Token 视为无效
        }
        String key = BLACKLIST_PREFIX + hashToken(token);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 从黑名单中移除 Token（不常用）
     * @param token JWT Token
     */
    public void removeFromBlacklist(String token) {
        if (token != null && !token.isEmpty()) {
            String key = BLACKLIST_PREFIX + hashToken(token);
            redisTemplate.delete(key);
        }
    }

    /**
     * 将用户的所有 Token 加入黑名单
     * @param userUuid 用户 UUID
     * @param expirationMinutes 过期时间（分钟）
     */
    public void revokeAllUserTokens(String userUuid, long expirationMinutes) {
        // 此处为示例，实际应该存储用户的所有活跃 Token
        // 可以使用 Redis Set 存储用户的所有 Token ID
        String userTokenKey = "user:tokens:" + userUuid;
        redisTemplate.delete(userTokenKey);
    }

    /**
     * 对 Token 进行哈希处理 - 防止在 Redis 中存储完整的 Token
     * @param token JWT Token
     * @return Token 的哈希值
     */
    private String hashToken(String token) {
        // 简单的哈希处理：使用 SHA-256
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            // 降级处理：使用 Token 的后 32 字符
            return token.substring(Math.max(0, token.length() - 32));
        }
    }
}
