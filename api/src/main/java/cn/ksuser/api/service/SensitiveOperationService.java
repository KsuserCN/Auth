package cn.ksuser.api.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class SensitiveOperationService {

    private static final String SENSITIVE_VERIFICATION_PREFIX = "sensitive:verified:";
    private static final String SENSITIVE_IP_PREFIX = "sensitive:ip:";
    private static final String SENSITIVE_ONCE_IP_PREFIX = "sensitive:once:ip:";
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

    /**
     * 标记用户已通过一次性敏感操作扫码验证（仅允许成功一次）
     * @param userUuid 用户 UUID
     * @param webIp Web 端发起敏感操作时的 IP
     */
    public void markVerifiedOnce(String userUuid, String webIp) {
        if (userUuid == null || userUuid.isBlank() || webIp == null || webIp.isBlank()) {
            return;
        }
        String onceIpKey = SENSITIVE_ONCE_IP_PREFIX + userUuid;
        redisTemplate.opsForValue().set(onceIpKey, webIp, VERIFICATION_EXPIRATION_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * 只读检查验证状态及 IP，不消费扫码资格，也不延长有效期。
     * @param userUuid 用户 UUID
     * @param clientIp 当前客户端 IP
     * @return true 表示已验证且 IP 匹配，false 表示未验证或 IP 不匹配
     */
    public boolean isVerified(String userUuid, String clientIp) {
        if (isInvalidContext(userUuid, clientIp)) return false;
        String onceIpKey = SENSITIVE_ONCE_IP_PREFIX + userUuid;
        String onceIp = redisTemplate.opsForValue().get(onceIpKey);
        if (onceIp != null) {
            return clientIp.equals(onceIp);
        }

        return isRegularVerified(userUuid, clientIp);
    }

    /**
     * 在执行受保护操作前授权：原子消费匹配 IP 的一次性扫码资格。
     * 常规验证仍在原有有效期内复用；查询状态必须使用 isVerified。
     */
    public boolean consumeVerification(String userUuid, String clientIp) {
        if (isInvalidContext(userUuid, clientIp)) return false;
        Long result = redisTemplate.execute(CONSUME_ONCE,
            List.of(SENSITIVE_ONCE_IP_PREFIX + userUuid), clientIp);
        if (Long.valueOf(-1).equals(result)) {
            return isRegularVerified(userUuid, clientIp);
        }
        return Long.valueOf(1).equals(result);
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
    }

    /**
     * 获取验证剩余时间（秒）
     * @param userUuid 用户 UUID
     * @return 剩余秒数，-1 表示未验证或已过期
     */
    public long getRemainingTime(String userUuid) {
        Long onceExpire = redisTemplate.getExpire(SENSITIVE_ONCE_IP_PREFIX + userUuid, TimeUnit.SECONDS);
        if (onceExpire != null && onceExpire >= 0) return onceExpire;
        String verificationKey = SENSITIVE_VERIFICATION_PREFIX + userUuid;
        Long expire = redisTemplate.getExpire(verificationKey, TimeUnit.SECONDS);
        return expire != null && expire > 0 ? expire : -1;
    }
}
