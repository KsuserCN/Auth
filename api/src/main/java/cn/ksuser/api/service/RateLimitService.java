package cn.ksuser.api.service;

import cn.ksuser.api.util.IpUtil;
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
    private static final int MAX_REQUESTS_PER_MINUTE_EMAIL = 1;
    private static final int MAX_REQUESTS_PER_MINUTE_IP = 3;
    private static final int MAX_REQUESTS_PER_HOUR = 14;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 检查是否允许发送（同时检查分钟和小时限制）
     * @param identifier 标识符（IP或邮箱）
     * @return 是否允许
     */
    public boolean isAllowed(String identifier) {
        return isAllowedPerMinute(identifier, MAX_REQUESTS_PER_MINUTE_EMAIL) && isAllowedPerHour(identifier);
    }

    /**
     * 检查邮箱是否允许发送（分钟限制=1，小时限制=14）
     * @param email 邮箱
     * @return 是否允许
     */
    public boolean isEmailAllowed(String email) {
        return isAllowedPerMinute(email, MAX_REQUESTS_PER_MINUTE_EMAIL) && isAllowedPerHour(email);
    }

    /**
     * 检查IP是否允许发送（分钟限制=3，小时限制=14）
     * @param ip IP
     * @return 是否允许
     */
    public boolean isIpAllowed(String ip) {
        return isAllowedPerMinute(ip, MAX_REQUESTS_PER_MINUTE_IP) && isAllowedPerHour(ip);
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
     * 记录邮箱发送
     * @param email 邮箱
     */
    public void recordEmailRequest(String email) {
        recordMinuteRequest(email);
        recordHourRequest(email);
    }

    /**
     * 记录IP发送
     * @param ip IP
     */
    public void recordIpRequest(String ip) {
        recordMinuteRequest(ip);
        recordHourRequest(ip);
    }

    /**
     * 检查分钟限制
     * @param identifier 标识符
     * @param limit 最大次数
     * @return 是否允许
     */
    private boolean isAllowedPerMinute(String identifier, int limit) {
        String key = MINUTE_LIMIT_PREFIX + identifier;
        String count = redisTemplate.opsForValue().get(key);
        return count == null || Integer.parseInt(count) < limit;
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
        String remoteAddr = request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }

        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }

        return remoteAddr;
    }

    /**
     * 判断是否是可信的代理IP
     * @param ip IP地址
     * @return 是否是可信代理
     */
    private boolean isTrustedProxy(String ip) {
        return IpUtil.isTrustedProxyIp(ip);
    }

    /**
     * 获取剩余分钟限制次数
     * @param identifier 标识符
     * @return 剩余次数
     */
    public int getRemainingMinuteRequests(String identifier) {
        return getRemainingMinuteRequests(identifier, MAX_REQUESTS_PER_MINUTE_EMAIL);
    }

    /**
     * 获取邮箱剩余分钟限制次数（1/分钟）
     * @param email 邮箱
     * @return 剩余次数
     */
    public int getRemainingMinuteRequestsForEmail(String email) {
        return getRemainingMinuteRequests(email, MAX_REQUESTS_PER_MINUTE_EMAIL);
    }

    /**
     * 获取IP剩余分钟限制次数（3/分钟）
     * @param ip IP
     * @return 剩余次数
     */
    public int getRemainingMinuteRequestsForIp(String ip) {
        return getRemainingMinuteRequests(ip, MAX_REQUESTS_PER_MINUTE_IP);
    }

    private int getRemainingMinuteRequests(String identifier, int limit) {
        String key = MINUTE_LIMIT_PREFIX + identifier;
        String count = redisTemplate.opsForValue().get(key);
        int used = count == null ? 0 : Integer.parseInt(count);
        return Math.max(0, limit - used);
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
