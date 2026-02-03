package cn.ksuser.api.service;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;
    private final AppProperties appProperties;
    private static final String MINUTE_LIMIT_PREFIX = "ratelimit:minute:";
    private static final String HOUR_LIMIT_PREFIX = "ratelimit:hour:";
    private static final Duration MINUTE_WINDOW = Duration.ofMinutes(1);
    private static final Duration HOUR_WINDOW = Duration.ofHours(1);
    
    // 操作类型常量
    public static final String TYPE_VERIFICATION_CODE = "verify";
    public static final String TYPE_LOGIN = "login";

    public RateLimitService(StringRedisTemplate redisTemplate, AppProperties appProperties) {
        this.redisTemplate = redisTemplate;
        this.appProperties = appProperties;
    }

    /**
     * 检查是否允许发送（同时检查分钟和小时限制）
     * @param identifier 标识符（IP或邮箱）
     * @return 是否允许
     */
    public boolean isAllowed(String identifier) {
        return isAllowedPerMinute(identifier, appProperties.getRateLimit().getSendCodeEmailPerMinute())
            && isAllowedPerHour(identifier, appProperties.getRateLimit().getSendCodeEmailPerHour());
    }

    /**
     * 检查邮箱是否允许发送（带操作类型）
     * @param email 邮箱
     * @param type 操作类型（verify/login）
     * @return 是否允许
     */
    public boolean isEmailAllowed(String email, String type) {
        String identifier = type + ":" + email;
        if (TYPE_LOGIN.equals(type)) {
            return isAllowedPerMinute(identifier, appProperties.getRateLimit().getLoginEmailPerMinute())
                && isAllowedPerHour(identifier, appProperties.getRateLimit().getLoginEmailPerHour());
        }
        return isAllowedPerMinute(identifier, appProperties.getRateLimit().getSendCodeEmailPerMinute())
            && isAllowedPerHour(identifier, appProperties.getRateLimit().getSendCodeEmailPerHour());
    }

    /**
     * 检查邮箱是否允许发送（兼容旧接口，默认验证码类型）
     * @param email 邮箱
     * @return 是否允许
     */
    public boolean isEmailAllowed(String email) {
        return isEmailAllowed(email, TYPE_VERIFICATION_CODE);
    }

    /**
     * 检查IP是否允许发送（带操作类型）
     * @param ip IP
     * @param type 操作类型（verify/login）
     * @return 是否允许
     */
    public boolean isIpAllowed(String ip, String type) {
        String identifier = type + ":" + ip;
        if (TYPE_LOGIN.equals(type)) {
            return isAllowedPerMinute(identifier, appProperties.getRateLimit().getLoginIpPerMinute())
                && isAllowedPerHour(identifier, appProperties.getRateLimit().getLoginIpPerHour());
        }
        return isAllowedPerMinute(identifier, appProperties.getRateLimit().getSendCodeIpPerMinute())
            && isAllowedPerHour(identifier, appProperties.getRateLimit().getSendCodeIpPerHour());
    }

    /**
     * 检查IP是否允许发送（兼容旧接口，默认验证码类型）
     * @param ip IP
     * @return 是否允许
     */
    public boolean isIpAllowed(String ip) {
        return isIpAllowed(ip, TYPE_VERIFICATION_CODE);
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
     * 记录邮箱发送（带操作类型）
     * @param email 邮箱
     * @param type 操作类型（verify/login）
     */
    public void recordEmailRequest(String email, String type) {
        String identifier = type + ":" + email;
        recordMinuteRequest(identifier);
        recordHourRequest(identifier);
    }

    /**
     * 记录邮箱发送（兼容旧接口，默认验证码类型）
     * @param email 邮箱
     */
    public void recordEmailRequest(String email) {
        recordEmailRequest(email, TYPE_VERIFICATION_CODE);
    }

    /**
     * 记录IP发送（带操作类型）
     * @param ip IP
     * @param type 操作类型（verify/login）
     */
    public void recordIpRequest(String ip, String type) {
        String identifier = type + ":" + ip;
        recordMinuteRequest(identifier);
        recordHourRequest(identifier);
    }

    /**
     * 记录IP发送（兼容旧接口，默认验证码类型）
     * @param ip IP
     */
    public void recordIpRequest(String ip) {
        recordIpRequest(ip, TYPE_VERIFICATION_CODE);
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
    private boolean isAllowedPerHour(String identifier, int limit) {
        String key = HOUR_LIMIT_PREFIX + identifier;
        String count = redisTemplate.opsForValue().get(key);
        return count == null || Integer.parseInt(count) < limit;
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
     * 获取邮箱剩余分钟限制次数
     * @param email 邮箱
     * @return 剩余次数
     */
    public int getRemainingMinuteRequestsForEmail(String email) {
        return getRemainingMinuteRequests(email, appProperties.getRateLimit().getSendCodeEmailPerMinute());
    }

    /**
     * 获取IP剩余分钟限制次数
     * @param ip IP
     * @return 剩余次数
     */
    public int getRemainingMinuteRequestsForIp(String ip) {
        return getRemainingMinuteRequests(ip, appProperties.getRateLimit().getSendCodeIpPerMinute());
    }

    private int getRemainingMinuteRequests(String identifier, int limit) {
        String key = MINUTE_LIMIT_PREFIX + identifier;
        String count = redisTemplate.opsForValue().get(key);
        int used = count == null ? 0 : Integer.parseInt(count);
        return Math.max(0, limit - used);
    }

    /**
     * 清除指定标识符的所有速率限制（成功登录后调用）
     * @param email 邮箱
     * @param ip IP地址
     */
    public void clearAllLimits(String email, String ip) {
        // 清除登录相关的邮箱限制
        redisTemplate.delete(MINUTE_LIMIT_PREFIX + TYPE_LOGIN + ":" + email);
        redisTemplate.delete(HOUR_LIMIT_PREFIX + TYPE_LOGIN + ":" + email);
        
        // 清除登录相关的IP限制
        redisTemplate.delete(MINUTE_LIMIT_PREFIX + TYPE_LOGIN + ":" + ip);
        redisTemplate.delete(HOUR_LIMIT_PREFIX + TYPE_LOGIN + ":" + ip);
        
        // 清除验证码相关的邮箱限制
        redisTemplate.delete(MINUTE_LIMIT_PREFIX + TYPE_VERIFICATION_CODE + ":" + email);
        redisTemplate.delete(HOUR_LIMIT_PREFIX + TYPE_VERIFICATION_CODE + ":" + email);
        
        // 清除验证码相关的IP限制
        redisTemplate.delete(MINUTE_LIMIT_PREFIX + TYPE_VERIFICATION_CODE + ":" + ip);
        redisTemplate.delete(HOUR_LIMIT_PREFIX + TYPE_VERIFICATION_CODE + ":" + ip);
    }
}
