package cn.ksuser.api.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;
    private static final String MINUTE_LIMIT_PREFIX = "ratelimit:minute:";
    private static final String HOUR_LIMIT_PREFIX = "ratelimit:hour:";
    private static final Duration MINUTE_WINDOW = Duration.ofMinutes(1);
    private static final Duration HOUR_WINDOW = Duration.ofHours(1);
    private static final int MAX_REQUESTS_PER_MINUTE = 1;
    private static final int MAX_REQUESTS_PER_HOUR = 6;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 检查是否允许发送（同时检查分钟和小时限制）
     * @param identifier 标识符（IP或邮箱）
     * @return 是否允许
     */
    public boolean isAllowed(String identifier) {
        return isAllowedPerMinute(identifier) && isAllowedPerHour(identifier);
    }

    /**
     * 记录一次发送
     * @param identifier 标识符（IP或邮箱）
     */
    public void recordRequest(String identifier) {
        recordMinuteRequest(identifier);
        recordHourRequest(identifier);
    }

    /**
     * 检查分钟限制
     * @param identifier 标识符
     * @return 是否允许
     */
    private boolean isAllowedPerMinute(String identifier) {
        String key = MINUTE_LIMIT_PREFIX + identifier;
        String count = redisTemplate.opsForValue().get(key);
        return count == null || Integer.parseInt(count) < MAX_REQUESTS_PER_MINUTE;
    }

    /**
     * 检查小时限制
     * @param identifier 标识符
     * @return 是否允许
     */
    private boolean isAllowedPerHour(String identifier) {
        String key = HOUR_LIMIT_PREFIX + identifier;
        String count = redisTemplate.opsForValue().get(key);
        return count == null || Integer.parseInt(count) < MAX_REQUESTS_PER_HOUR;
    }

    /**
     * 记录分钟请求
     * @param identifier 标识符
     */
    private void recordMinuteRequest(String identifier) {
        String key = MINUTE_LIMIT_PREFIX + identifier;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == 1) {
            redisTemplate.expire(key, MINUTE_WINDOW);
        }
    }

    /**
     * 记录小时请求
     * @param identifier 标识符
     */
    private void recordHourRequest(String identifier) {
        String key = HOUR_LIMIT_PREFIX + identifier;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == 1) {
            redisTemplate.expire(key, HOUR_WINDOW);
        }
    }

    /**
     * 从请求中获取IP地址
     * @param request HttpServletRequest
     * @return IP地址
     */
    public String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 处理多级代理的情况
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 获取剩余分钟限制次数
     * @param identifier 标识符
     * @return 剩余次数
     */
    public int getRemainingMinuteRequests(String identifier) {
        String key = MINUTE_LIMIT_PREFIX + identifier;
        String count = redisTemplate.opsForValue().get(key);
        int used = count == null ? 0 : Integer.parseInt(count);
        return Math.max(0, MAX_REQUESTS_PER_MINUTE - used);
    }

    /**
     * 获取剩余小时限制次数
     * @param identifier 标识符
     * @return 剩余次数
     */
    public int getRemainingHourRequests(String identifier) {
        String key = HOUR_LIMIT_PREFIX + identifier;
        String count = redisTemplate.opsForValue().get(key);
        int used = count == null ? 0 : Integer.parseInt(count);
        return Math.max(0, MAX_REQUESTS_PER_HOUR - used);
    }
}
