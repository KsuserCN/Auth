package cn.ksuser.api.service;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
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
        public final OauthPendingBindingService.Claim oauthBindingClaim;
        public volatile int failedAttempts;

        public Challenge(Long userId, String clientIp, String userAgent, String loginMethod,
                         Set<String> allowedMethods, long expiresAt, OauthPendingBindingService.Claim oauthBindingClaim) {
            this.userId = userId;
            this.clientIp = clientIp;
            this.userAgent = userAgent;
            this.loginMethod = loginMethod;
            this.allowedMethods = allowedMethods;
            this.expiresAt = expiresAt;
            this.failedAttempts = 0;
            this.oauthBindingClaim = oauthBindingClaim;
        }
    }

    private final Map<String, Challenge> store = new ConcurrentHashMap<>();
    private final Clock clock;

    public MfaService() { this(Clock.systemUTC()); }
    MfaService(Clock clock) { this.clock = clock; }

    public record Completion(Long userId, String loginMethod, OauthPendingBindingService.Claim oauthBindingClaim) {}

    // challenge 有效期（秒）
    private static final long DEFAULT_TTL_SECONDS = 300; // 5 分钟
    private static final int MAX_FAILED_ATTEMPTS = 5; // ✅ 最大失败次数

    public String createChallenge(Long userId, String clientIp, String userAgent) {
        return createChallenge(userId, clientIp, userAgent, "password", Set.of("totp"));
    }

    public String createChallenge(Long userId, String clientIp, String userAgent,
                                  String loginMethod, Set<String> allowedMethods) {
        String id = UUID.randomUUID().toString();
        long expiresAt = clock.instant().getEpochSecond() + DEFAULT_TTL_SECONDS;
        Set<String> normalized = normalizeMethods(allowedMethods);
        store.put(id, new Challenge(userId, clientIp, userAgent, loginMethod, normalized, expiresAt, null));
        return id;
    }

    /** Only a complete independent factor can authorize writing a new OAuth login credential. */
    public String createOauthBindingChallenge(Long userId, String clientIp, String userAgent,
            String loginMethod, Set<String> allowedMethods, OauthPendingBindingService.Claim claim) {
        if (claim == null || !Objects.equals(userId, claim.userId())) {
            throw new IllegalArgumentException("OAuth binding target does not match MFA user");
        }
        Set<String> normalized = new HashSet<>();
        if (allowedMethods != null) {
            for (String method : allowedMethods) {
                if (method == null) continue;
                String value = method.trim().toLowerCase(Locale.ROOT);
                if ("totp".equals(value) || "passkey".equals(value)) normalized.add(value);
            }
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("OAuth binding requires an existing MFA credential");
        }
        String id = UUID.randomUUID().toString();
        store.put(id, new Challenge(userId, clientIp, normalizeUserAgent(userAgent), loginMethod,
            Collections.unmodifiableSet(normalized), clock.instant().getEpochSecond() + DEFAULT_TTL_SECONDS, claim));
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
            String value = method.trim().toLowerCase(Locale.ROOT);
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
        if (isExpiredOrExhausted(chal)) {
            store.remove(challengeId); // 过期则删除
            return null;
        }
        if (!matchesBindingContext(chal, clientIp, userAgent)) return null;
        return chal.userId;
    }

    public boolean isMethodAllowed(String challengeId, String method) {
        if (challengeId == null || method == null || method.isBlank()) {
            return false;
        }
        Challenge chal = store.get(challengeId);
        if (chal == null || isExpiredOrExhausted(chal)) {
            return false;
        }
        return chal.allowedMethods.contains(method.trim().toLowerCase(Locale.ROOT));
    }

    public Set<String> getAllowedMethods(String challengeId) {
        Challenge chal = store.get(challengeId);
        if (chal == null || isExpiredOrExhausted(chal)) {
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
        store.computeIfPresent(challengeId, (id, chal) -> {
            chal.failedAttempts++;
            return chal;
        });
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

    /** Claims MFA completion once, retaining its trusted binding metadata after removal. */
    public Completion claimSuccessfulChallenge(String challengeId, String clientIp, String userAgent) {
        if (challengeId == null) return null;
        Completion[] completed = new Completion[1];
        store.computeIfPresent(challengeId, (id, chal) -> {
            if (isExpiredOrExhausted(chal)) return null;
            if (!matchesBindingContext(chal, clientIp, userAgent)) return chal;
            completed[0] = new Completion(chal.userId, chal.loginMethod, chal.oauthBindingClaim);
            return null;
        });
        return completed[0];
    }

    private boolean isExpiredOrExhausted(Challenge challenge) {
        return challenge.expiresAt <= clock.instant().getEpochSecond()
            || challenge.failedAttempts >= MAX_FAILED_ATTEMPTS;
    }

    private boolean matchesBindingContext(Challenge challenge, String clientIp, String userAgent) {
        // Existing QR MFA moves between devices. New OAuth credential creation stays at its initiator.
        return challenge.oauthBindingClaim == null || (Objects.equals(challenge.clientIp, clientIp)
            && Objects.equals(challenge.userAgent, normalizeUserAgent(userAgent)));
    }

    private static String normalizeUserAgent(String userAgent) {
        return userAgent == null || userAgent.isBlank() ? "unknown" : userAgent.trim();
    }
}
