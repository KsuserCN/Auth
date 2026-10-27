package cn.ksuser.api.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class SensitiveOperationService {

    private static final String SENSITIVE_VERIFICATION_PREFIX = "sensitive:verified:";
    private static final String SENSITIVE_IP_PREFIX = "sensitive:ip:";
    private static final String SENSITIVE_ONCE_IP_PREFIX = "sensitive:once:ip:";
    private static final String QR_GRANT_PREFIX = "sensitive:qr:v2:";
    private static final String QR_EPOCH_PREFIX = "sensitive:qr:epoch:v2:";
    public static final Set<String> QR_PURPOSES = Set.of(
        "change-password", "change-email", "delete-account", "login-options", "session-transfer");
    private static final int VERIFICATION_EXPIRATION_MINUTES = 15;
    private static final DefaultRedisScript<Long> CONSUME_ONCE = new DefaultRedisScript<>(
        "local ip = redis.call('GET', KEYS[1]); " +
        "if not ip then return -1 end; " +
        "if ip ~= ARGV[1] then return 0 end; " +
        "redis.call('DEL', KEYS[1]); return 1", Long.class);

    private final StringRedisTemplate redisTemplate;

    public SensitiveOperationService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 标记用户已完成敏感操作验证
     * @param userUuid 用户 UUID
     * @param clientIp 客户端 IP
     */
    public void markVerified(String userUuid, String clientIp) {
        String verificationKey = SENSITIVE_VERIFICATION_PREFIX + userUuid;
        String ipKey = SENSITIVE_IP_PREFIX + userUuid;
        
        // 标记已验证，15分钟有效期
        redisTemplate.opsForValue().set(verificationKey, "verified", VERIFICATION_EXPIRATION_MINUTES, TimeUnit.MINUTES);
        
        // 记录验证时的 IP，15分钟有效期
        redisTemplate.opsForValue().set(ipKey, clientIp, VERIFICATION_EXPIRATION_MINUTES, TimeUnit.MINUTES);
    }

    /** Legacy user/IP QR grants are deliberately disabled; use atomic protected QR approval. */
    @Deprecated
    public void markVerifiedOnce(String userUuid, String webIp) {
        throw new IllegalStateException("QR verification requires a session and operation purpose");
    }

    /**
     * 检查直接凭据验证状态及 IP；旧的用户/IP扫码资格不再生效。
     * @param userUuid 用户 UUID
     * @param clientIp 当前客户端 IP
     * @return true 表示已验证且 IP 匹配，false 表示未验证或 IP 不匹配
     */
    public boolean isVerified(String userUuid, String clientIp) {
        if (isInvalidContext(userUuid, clientIp)) return false;
        return isRegularVerified(userUuid, clientIp);
    }

    /** Direct credential verification retains its existing reusable lifetime. */
    public boolean consumeVerification(String userUuid, String clientIp) {
        if (isInvalidContext(userUuid, clientIp)) return false;
        return isRegularVerified(userUuid, clientIp);
    }

    /** The QR service writes this grant only in its atomic proof-consumption transaction. */
    public String qrGrantKey(String uuid, Long sessionId, Integer version, String purpose) {
        if (uuid == null || uuid.isBlank() || sessionId == null || version == null
            || purpose == null || !QR_PURPOSES.contains(purpose)) throw new IllegalArgumentException("Invalid QR grant context");
        String epoch = redisTemplate.opsForValue().get(QR_EPOCH_PREFIX + uuid);
        return QR_GRANT_PREFIX + uuid + ":" + sessionId + ":" + version + ":"
            + (epoch == null ? "0" : epoch) + ":" + purpose;
    }

    public boolean isVerified(String uuid, String ip, Long sessionId, Integer version, String purpose) {
        if (isInvalidContext(uuid, ip)) return false;
        if (sessionId != null && version != null && purpose != null && QR_PURPOSES.contains(purpose)) {
            String qrIp = redisTemplate.opsForValue().get(qrGrantKey(uuid, sessionId, version, purpose));
            if (qrIp != null) return ip.equals(qrIp);
        }
        return isRegularVerified(uuid, ip);
    }

    public boolean consumeVerification(String uuid, String ip, Long sessionId, Integer version, String purpose) {
        if (isInvalidContext(uuid, ip)) return false;
        if (sessionId != null && version != null && purpose != null && QR_PURPOSES.contains(purpose)) {
            Long result = redisTemplate.execute(CONSUME_ONCE,
                List.of(qrGrantKey(uuid, sessionId, version, purpose)), ip);
            if (!Long.valueOf(-1).equals(result)) return Long.valueOf(1).equals(result);
        }
        return isRegularVerified(uuid, ip);
    }

    public long getRemainingTime(String uuid, Long sessionId, Integer version, String purpose) {
        if (uuid == null || uuid.isBlank()) return -1;
        long remaining = -1;
        if (sessionId != null && version != null) {
            for (String candidate : QR_PURPOSES) {
                if (purpose != null && !candidate.equals(purpose)) continue;
                Long ttl = redisTemplate.getExpire(qrGrantKey(uuid, sessionId, version, candidate), TimeUnit.SECONDS);
                if (ttl != null && ttl > remaining) remaining = ttl;
            }
        }
        return Math.max(remaining, getRemainingTime(uuid));
    }

    public boolean isVerifiedForAnyPurpose(String uuid, String ip, Long sessionId, Integer version) {
        for (String purpose : QR_PURPOSES) {
            if (isVerified(uuid, ip, sessionId, version, purpose)) return true;
        }
        return false;
    }

    private static boolean isInvalidContext(String userUuid, String clientIp) {
        return userUuid == null || userUuid.isBlank() || clientIp == null || clientIp.isBlank();
    }

    private boolean isRegularVerified(String userUuid, String clientIp) {
        String verificationKey = SENSITIVE_VERIFICATION_PREFIX + userUuid;
        String ipKey = SENSITIVE_IP_PREFIX + userUuid;
        
        // 检查是否已验证
        String verified = redisTemplate.opsForValue().get(verificationKey);
        if (!"verified".equals(verified)) {
            return false;
        }
        
        // 检查 IP 是否匹配
        String storedIp = redisTemplate.opsForValue().get(ipKey);
        return clientIp.equals(storedIp);
    }

    /**
     * 清除用户的敏感操作验证标记（可选，Redis 会自动过期）
     * @param userUuid 用户 UUID
     */
    public void clearVerification(String userUuid) {
        String verificationKey = SENSITIVE_VERIFICATION_PREFIX + userUuid;
        String ipKey = SENSITIVE_IP_PREFIX + userUuid;
        String onceIpKey = SENSITIVE_ONCE_IP_PREFIX + userUuid;
        
        redisTemplate.delete(verificationKey);
        redisTemplate.delete(ipKey);
        redisTemplate.delete(onceIpKey);
        // Revokes every session-scoped QR grant without scanning Redis or revoking login sessions.
        redisTemplate.opsForValue().increment(QR_EPOCH_PREFIX + userUuid);
    }

    /**
     * 获取验证剩余时间（秒）
     * @param userUuid 用户 UUID
     * @return 剩余秒数，-1 表示未验证或已过期
     */
    public long getRemainingTime(String userUuid) {
        String verificationKey = SENSITIVE_VERIFICATION_PREFIX + userUuid;
        Long expire = redisTemplate.getExpire(verificationKey, TimeUnit.SECONDS);
        return expire != null && expire > 0 ? expire : -1;
    }
}
