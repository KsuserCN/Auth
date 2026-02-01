package cn.ksuser.api.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class SensitiveOperationService {

    private static final String SENSITIVE_VERIFICATION_PREFIX = "sensitive:verified:";
    private static final String SENSITIVE_IP_PREFIX = "sensitive:ip:";
    private static final int VERIFICATION_EXPIRATION_MINUTES = 15;

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
     * 检查用户是否已完成敏感操作验证，并验证 IP 是否匹配
     * @param userUuid 用户 UUID
     * @param clientIp 当前客户端 IP
     * @return true 表示已验证且 IP 匹配，false 表示未验证或 IP 不匹配
     */
    public boolean isVerified(String userUuid, String clientIp) {
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
        
        redisTemplate.delete(verificationKey);
        redisTemplate.delete(ipKey);
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
