package cn.ksuser.api.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Service
public class VerificationCodeService {

    private final StringRedisTemplate redisTemplate;
    private static final String CODE_PREFIX = "verification:code:";
    private static final String SENT_PREFIX = "verification:sent:";
    private static final String ERROR_COUNT_PREFIX = "verification:error:";
    private static final String LOCK_PREFIX = "verification:lock:";
    private static final int CODE_LENGTH = 6;
    private static final Duration CODE_EXPIRATION = Duration.ofMinutes(10);
    private static final Duration LOCK_DURATION = Duration.ofHours(1);
    private static final int MAX_ERROR_COUNT = 5;

    private final SecureRandom random = new SecureRandom();

    public VerificationCodeService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 生成验证码
     * @return 6位数字验证码
     */
    public String generateCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    /**
     * 保存验证码
     * @param email 邮箱
     * @param code 验证码
     */
    public void saveCode(String email, String code) {
        String codeKey = CODE_PREFIX + email;
        String sentKey = SENT_PREFIX + email;
        redisTemplate.opsForValue().set(codeKey, code, CODE_EXPIRATION);
        // 同时保存"已发送"标记，用于判断验证码是否已过期
        redisTemplate.opsForValue().set(sentKey, "1", CODE_EXPIRATION);
    }

    /**
     * 验证验证码
     * @param email 邮箱
     * @param code 验证码
     * @return 验证结果，0-成功，1-验证码已过期，2-验证码错误，3-邮箱被锁定，4-未发送验证码
     */
    public int verifyCode(String email, String code) {
        // 检查是否被锁定
        if (isLocked(email)) {
            return 3;
        }

        String codeKey = CODE_PREFIX + email;
        String sentKey = SENT_PREFIX + email;
        String storedCode = redisTemplate.opsForValue().get(codeKey);
        boolean hasSent = Boolean.TRUE.equals(redisTemplate.hasKey(sentKey));

        // 如果从未发送过验证码
        if (!hasSent) {
            return 4; // 未发送验证码，不计入错误
        }

        // 如果验证码不存在但曾经发送过，说明验证码已过期
        if (storedCode == null) {
            // 清理已发送标记，保持一致性
            redisTemplate.delete(sentKey);
            // 计为一次错误尝试，防止滥用
            incrementErrorCount(email);
            return 1; // 验证码已过期
        }

        if (storedCode.equals(code)) {
            // 验证成功，删除验证码、已发送标记和错误计数
            redisTemplate.delete(codeKey);
            redisTemplate.delete(sentKey);
            redisTemplate.delete(ERROR_COUNT_PREFIX + email);
            return 0; // 成功
        } else {
            // 验证失败，增加错误计数
            incrementErrorCount(email);
            return 2; // 验证码错误
        }
    }

    /**
     * 增加错误计数
     * @param email 邮箱
     */
    private void incrementErrorCount(String email) {
        String key = ERROR_COUNT_PREFIX + email;
        Long count = redisTemplate.opsForValue().increment(key);

        if (count == 1) {
            // 第一次错误，设置过期时间为验证码过期时间
            redisTemplate.expire(key, CODE_EXPIRATION);
        }

        if (count >= MAX_ERROR_COUNT) {
            // 错误次数达到上限，锁定邮箱
            lockEmail(email);
        }
    }

    /**
     * 锁定邮箱
     * @param email 邮箱
     */
    private void lockEmail(String email) {
        String lockKey = LOCK_PREFIX + email;
        redisTemplate.opsForValue().set(lockKey, "locked", LOCK_DURATION);
        // 删除验证码、已发送标记和错误计数
        redisTemplate.delete(CODE_PREFIX + email);
        redisTemplate.delete(SENT_PREFIX + email);
        redisTemplate.delete(ERROR_COUNT_PREFIX + email);
    }

    /**
     * 检查邮箱是否被锁定
     * @param email 邮箱
     * @return 是否被锁定
     */
    public boolean isLocked(String email) {
        String key = LOCK_PREFIX + email;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 获取错误次数
     * @param email 邮箱
     * @return 错误次数
     */
    public int getErrorCount(String email) {
        String key = ERROR_COUNT_PREFIX + email;
        String count = redisTemplate.opsForValue().get(key);
        return count == null ? 0 : Integer.parseInt(count);
    }
}
