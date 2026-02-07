package cn.ksuser.api.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MfaService {

    private static class Challenge {
        public final Long userId;
        public final String clientIp;
        public final String userAgent;
        public final long expiresAt;

        public Challenge(Long userId, String clientIp, String userAgent, long expiresAt) {
            this.userId = userId;
            this.clientIp = clientIp;
            this.userAgent = userAgent;
            this.expiresAt = expiresAt;
        }
    }

    private final Map<String, Challenge> store = new ConcurrentHashMap<>();

    // challenge 有效期（秒）
    private static final long DEFAULT_TTL_SECONDS = 300; // 5 分钟

    public String createChallenge(Long userId, String clientIp, String userAgent) {
        String id = UUID.randomUUID().toString();
        long expiresAt = Instant.now().getEpochSecond() + DEFAULT_TTL_SECONDS;
        store.put(id, new Challenge(userId, clientIp, userAgent, expiresAt));
        return id;
    }

    public Long consumeChallenge(String challengeId, String clientIp, String userAgent) {
        if (challengeId == null) return null;
        Challenge chal = store.remove(challengeId);
        if (chal == null) return null;
        long now = Instant.now().getEpochSecond();
        if (chal.expiresAt < now) return null;
        // 可选：校验 clientIp / userAgent 是否一致（不强制）
        return chal.userId;
    }
}
