package cn.ksuser.api.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MfaService {

    private static class Challenge {
        public final Long userId;
        public final String clientIp;
        public final String userAgent;
        public final String loginMethod;
        public final Set<String> allowedMethods;
        public final long expiresAt;
        public int failedAttempts; // ✅ 添加失败次数记录

        public Challenge(Long userId, String clientIp, String userAgent, String loginMethod,
                         Set<String> allowedMethods, long expiresAt) {
            this.userId = userId;
            this.clientIp = clientIp;
            this.userAgent = userAgent;
            this.loginMethod = loginMethod;
            this.allowedMethods = allowedMethods;
            this.expiresAt = expiresAt;
            this.failedAttempts = 0;
        }
    }

    private final Map<String, Challenge> store = new ConcurrentHashMap<>();

    // challenge 有效期（秒）
    private static final long DEFAULT_TTL_SECONDS = 300; // 5 分钟
    private static final int MAX_FAILED_ATTEMPTS = 5; // ✅ 最大失败次数

    public String createChallenge(Long userId, String clientIp, String userAgent) {
        return createChallenge(userId, clientIp, userAgent, "password", Set.of("totp"));
    }

    public String createChallenge(Long userId, String clientIp, String userAgent,
                                  String loginMethod, Set<String> allowedMethods) {
        String id = UUID.randomUUID().toString();
        long expiresAt = Instant.now().getEpochSecond() + DEFAULT_TTL_SECONDS;
        Set<String> normalized = normalizeMethods(allowedMethods);
        store.put(id, new Challenge(userId, clientIp, userAgent, loginMethod, normalized, expiresAt));
        return id;
    }

    private Set<String> normalizeMethods(Set<String> allowedMethods) {
        if (allowedMethods == null || allowedMethods.isEmpty()) {
            return Set.of("totp");
        }
        Set<String> normalized = new HashSet<>();
        for (String method : allowedMethods) {
            if (method == null || method.isBlank()) {
                continue;
            }
            String value = method.trim().toLowerCase();
            if ("totp".equals(value) || "passkey".equals(value) || "qr".equals(value)) {
                normalized.add(value);
            }
        }
        if (normalized.isEmpty()) {
            normalized.add("totp");
        }
        return Collections.unmodifiableSet(normalized);
    }

    /**
     * 验证并获取challenge（不删除）
     * @return userId 如果有效，否则返回 null
     */
    public Long verifyChallenge(String challengeId, String clientIp, String userAgent) {
        if (challengeId == null) return null;
        Challenge chal = store.get(challengeId);
        if (chal == null) return null;
        long now = Instant.now().getEpochSecond();
        if (chal.expiresAt < now) {
            store.remove(challengeId); // 过期则删除
            return null;
        }
        // ✅ 检查是否超过最大失败次数
        if (chal.failedAttempts >= MAX_FAILED_ATTEMPTS) {
            store.remove(challengeId); // 超过失败次数则删除
            return null;
        }
        return chal.userId;
    }

    public boolean isMethodAllowed(String challengeId, String method) {
        if (challengeId == null || method == null || method.isBlank()) {
            return false;
        }
        Challenge chal = store.get(challengeId);
        if (chal == null) {
            return false;
        }
        return chal.allowedMethods.contains(method.trim().toLowerCase());
    }

    public Set<String> getAllowedMethods(String challengeId) {
        Challenge chal = store.get(challengeId);
        if (chal == null) {
            return Set.of();
        }
        return chal.allowedMethods;
    }

    public String getLoginMethod(String challengeId) {
        Challenge chal = store.get(challengeId);
        if (chal == null || chal.loginMethod == null || chal.loginMethod.isBlank()) {
            return "password";
        }
        return chal.loginMethod;
    }

    /**
     * 记录一次失败尝试
     */
    public void recordFailedAttempt(String challengeId) {
        if (challengeId == null) return;
        Challenge chal = store.get(challengeId);
        if (chal != null) {
            chal.failedAttempts++;
        }
    }

    /**
     * 获取剩余尝试次数
     */
    public int getRemainingAttempts(String challengeId) {
        if (challengeId == null) return 0;
        Challenge chal = store.get(challengeId);
        if (chal == null) return 0;
        return Math.max(0, MAX_FAILED_ATTEMPTS - chal.failedAttempts);
    }

    /**
     * MFA验证成功后消费challenge（删除）
     */
    public void consumeChallenge(String challengeId) {
        if (challengeId != null) {
            store.remove(challengeId);
        }
    }
}
