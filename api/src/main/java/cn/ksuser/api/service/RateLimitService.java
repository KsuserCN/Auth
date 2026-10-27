package cn.ksuser.api.service;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);
    private final StringRedisTemplate redisTemplate;
    private final AppProperties appProperties;
    private static final String MINUTE_LIMIT_PREFIX = "ratelimit:minute:";
    private static final String HOUR_LIMIT_PREFIX = "ratelimit:hour:";
    private static final Duration MINUTE_WINDOW = Duration.ofMinutes(1);
    private static final Duration HOUR_WINDOW = Duration.ofHours(1);

    // Check every quota before changing any counter. Redis runs the check and reservation atomically.
    private static final DefaultRedisScript<Long> ACQUIRE_SEND_CODE_PERMIT = new DefaultRedisScript<>("""
        local limited = 0
        for i = 1, 4 do
            local stored = redis.call('GET', KEYS[i])
            local count = tonumber(stored or '0')
            -- Older INCR/EXPIRE calls could leave counters without expiry after an interruption.
            if stored and redis.call('TTL', KEYS[i]) == -1 then
                local ttl = (i == 1 or i == 3) and ARGV[5] or ARGV[6]
                redis.call('EXPIRE', KEYS[i], ttl)
            end
            if count >= tonumber(ARGV[i]) and limited == 0 then
                limited = i
            end
        end
        if limited ~= 0 then
            return limited
        end
        for i = 1, 4 do
            local count = redis.call('INCR', KEYS[i])
            if count == 1 then
                local ttl = (i == 1 or i == 3) and ARGV[5] or ARGV[6]
                redis.call('EXPIRE', KEYS[i], ttl)
            end
        end
        return 0
        """, Long.class);

    public enum SendCodeLimit {
        ALLOWED, EMAIL_MINUTE, EMAIL_HOUR, IP_MINUTE, IP_HOUR, UNAVAILABLE
    }

    private static final String PASSWORD_LIMIT_PREFIX = "ratelimit:password:v1:";
    private static final DefaultRedisScript<Long> ACQUIRE_PASSWORD_ATTEMPT = new DefaultRedisScript<>("""
        -- Reserve all shared budgets before any expensive credential verification.
        -- A token counter is created with the pending identity and must keep that original expiry.
        for i = 1, 4 do
            local stored = redis.call('GET', KEYS[i])
            local count = tonumber(stored or '0')
            if not count or count < 0 or count % 1 ~= 0 then return -1 end
            if stored and redis.call('TTL', KEYS[i]) < 0 then return -1 end
            if count >= tonumber(ARGV[i]) then return 1 end
        end
        if #KEYS == 5 then
            local stored = redis.call('GET', KEYS[5])
            local count = tonumber(stored or '')
            if not count or count < 0 or count % 1 ~= 0 or redis.call('TTL', KEYS[5]) <= 0 then return 1 end
            if count >= 5 then return 1 end
        end
        for i = 1, 4 do
            local count = redis.call('INCR', KEYS[i])
            if count == 1 then
                redis.call('EXPIRE', KEYS[i], (i == 1 or i == 3) and ARGV[5] or ARGV[6])
            end
        end
        if #KEYS == 5 then redis.call('INCR', KEYS[5]) end
        return 0
        """, Long.class);

    public enum PasswordAttemptLimit { ALLOWED, LIMITED, UNAVAILABLE }
    
    // 操作类型常量
    public static final String TYPE_VERIFICATION_CODE = "verify";
    public static final String TYPE_LOGIN = "login";

    public RateLimitService(StringRedisTemplate redisTemplate, AppProperties appProperties) {
        this.redisTemplate = redisTemplate;
        this.appProperties = appProperties;
    }

    /**
     * Reserve email and shared-network quotas before generating or sending a code.
     * Rejected requests do not consume another recipient's network quota or extend any window.
     * Failed deliveries retain their reservation to bound repeated delivery attempts.
     */
    public SendCodeLimit acquireSendCodePermit(String email, String ip) {
        String emailIdentifier = TYPE_VERIFICATION_CODE + ":" + email;
        String ipIdentifier = TYPE_VERIFICATION_CODE + ":" + ip;
        AppProperties.RateLimit limits = appProperties.getRateLimit();
        Long result;
        try {
            result = redisTemplate.execute(ACQUIRE_SEND_CODE_PERMIT, List.of(
                MINUTE_LIMIT_PREFIX + emailIdentifier, HOUR_LIMIT_PREFIX + emailIdentifier,
                MINUTE_LIMIT_PREFIX + ipIdentifier, HOUR_LIMIT_PREFIX + ipIdentifier),
                Integer.toString(limits.getSendCodeEmailPerMinute()),
                Integer.toString(limits.getSendCodeEmailPerHour()),
                Integer.toString(limits.getSendCodeIpPerMinute()),
                Integer.toString(limits.getSendCodeIpPerHour()),
                Long.toString(MINUTE_WINDOW.toSeconds()), Long.toString(HOUR_WINDOW.toSeconds()));
        } catch (DataAccessException ex) {
            log.warn("Unable to reserve verification-code quota", ex);
            return SendCodeLimit.UNAVAILABLE;
        }
        if (result == null) return SendCodeLimit.UNAVAILABLE;
        if (result == 0) return SendCodeLimit.ALLOWED;
        if (result == 1) return SendCodeLimit.EMAIL_MINUTE;
        if (result == 2) return SendCodeLimit.EMAIL_HOUR;
        if (result == 3) return SendCodeLimit.IP_MINUTE;
        if (result == 4) return SendCodeLimit.IP_HOUR;
        return SendCodeLimit.UNAVAILABLE;
    }

    /**
     * A single atomic reservation shared by regular password login and all OAuth bind-existing routes.
     * The optional token key must already exist with a fixed positive TTL; it is never recreated here.
     */
    public PasswordAttemptLimit acquirePasswordAttempt(String accountKey, String ip, String pendingTokenQuotaKey) {
        if (accountKey == null || accountKey.isBlank() || ip == null || ip.isBlank()
            || (pendingTokenQuotaKey != null && pendingTokenQuotaKey.isBlank())) return PasswordAttemptLimit.UNAVAILABLE;
        String account = PASSWORD_LIMIT_PREFIX + "account:" + digest(accountKey);
        String network = PASSWORD_LIMIT_PREFIX + "ip:" + digest(ip);
        List<String> keys = pendingTokenQuotaKey == null
            ? List.of(account + ":minute", account + ":hour", network + ":minute", network + ":hour")
            : List.of(account + ":minute", account + ":hour", network + ":minute", network + ":hour", pendingTokenQuotaKey);
        AppProperties.RateLimit limits = appProperties.getRateLimit();
        try {
            Long result = redisTemplate.execute(ACQUIRE_PASSWORD_ATTEMPT, keys,
                Integer.toString(limits.getLoginEmailPerMinute()), Integer.toString(limits.getLoginEmailPerHour()),
                Integer.toString(limits.getLoginIpPerMinute()), Integer.toString(limits.getLoginIpPerHour()),
                Long.toString(MINUTE_WINDOW.toSeconds()), Long.toString(HOUR_WINDOW.toSeconds()));
            if (Long.valueOf(0).equals(result)) return PasswordAttemptLimit.ALLOWED;
            if (Long.valueOf(1).equals(result)) return PasswordAttemptLimit.LIMITED;
        } catch (DataAccessException ex) {
            log.warn("Unable to reserve password authentication quota", ex);
        }
        return PasswordAttemptLimit.UNAVAILABLE;
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
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
        String identifier = TYPE_VERIFICATION_CODE + ":" + email;
        return getRemainingMinuteRequests(identifier, appProperties.getRateLimit().getSendCodeEmailPerMinute());
    }

    /**
     * 获取IP剩余分钟限制次数
     * @param ip IP
     * @return 剩余次数
     */
    public int getRemainingMinuteRequestsForIp(String ip) {
        String identifier = TYPE_VERIFICATION_CODE + ":" + ip;
        return getRemainingMinuteRequests(identifier, appProperties.getRateLimit().getSendCodeIpPerMinute());
    }

    /**
     * 获取客户端 User-Agent
     * @param request HttpServletRequest
     * @return User-Agent
     */
    public String getClientUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null || userAgent.trim().isEmpty()) {
            return "unknown";
        }
        return userAgent.trim();
    }

    private int getRemainingMinuteRequests(String identifier, int limit) {
        String key = MINUTE_LIMIT_PREFIX + identifier;
        String count = redisTemplate.opsForValue().get(key);
        int used = count == null ? 0 : Integer.parseInt(count);
        return Math.max(0, limit - used);
    }

    /**
     * Compatibility hook. Successful authentication must not reset shared attempt budgets.
     * @param email 邮箱
     * @param ip IP地址
     */
    @Deprecated
    public void clearAllLimits(String email, String ip) { }
}
