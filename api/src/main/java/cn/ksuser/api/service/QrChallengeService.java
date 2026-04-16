package cn.ksuser.api.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class QrChallengeService {

    public static final long DEFAULT_TTL_SECONDS = 120L;
    private static final String CHALLENGE_PREFIX = "auth:qr:challenge:";
    private static final String APPROVE_PREFIX = "auth:qr:approve:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom;

    public QrChallengeService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.secureRandom = new SecureRandom();
    }

    public QrChallengePayload createChallenge(ChallengeType type, Long requestedUserId, String mfaChallengeId, String webIp) {
        long now = Instant.now().getEpochSecond();
        QrChallengePayload payload = new QrChallengePayload();
        payload.setChallengeId(UUID.randomUUID().toString());
        payload.setApproveCode(generateToken());
        payload.setPollToken(generateToken());
        payload.setType(type.value());
        payload.setStatus(ChallengeStatus.PENDING.value());
        payload.setRequestedUserId(requestedUserId);
        payload.setMfaChallengeId(mfaChallengeId);
        payload.setWebIp(webIp);
        payload.setCreatedAt(now);
        payload.setUpdatedAt(now);

        save(payload, DEFAULT_TTL_SECONDS);
        return payload;
    }

    public QrChallengePayload getByApproveCode(String approveCode) {
        if (approveCode == null || approveCode.isBlank()) {
            return null;
        }
        String challengeId = redisTemplate.opsForValue().get(approveKey(approveCode.trim()));
        if (challengeId == null || challengeId.isBlank()) {
            return null;
        }
        return getByChallengeId(challengeId);
    }

    public QrChallengePayload getByChallengeId(String challengeId) {
        if (challengeId == null || challengeId.isBlank()) {
            return null;
        }

        String raw = redisTemplate.opsForValue().get(challengeKey(challengeId.trim()));
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(raw, QrChallengePayload.class);
        } catch (Exception e) {
            return null;
        }
    }

    public void approveChallenge(String challengeId, Long approvedByUserId, Map<String, Object> result) {
        updateChallengeStatus(challengeId, ChallengeStatus.APPROVED, approvedByUserId, result);
    }

    public void rejectChallenge(String challengeId, Long approvedByUserId, Map<String, Object> result) {
        updateChallengeStatus(challengeId, ChallengeStatus.REJECTED, approvedByUserId, result);
    }

    private void updateChallengeStatus(String challengeId, ChallengeStatus status, Long approvedByUserId, Map<String, Object> result) {
        QrChallengePayload payload = getByChallengeId(challengeId);
        if (payload == null) {
            throw new IllegalArgumentException("二维码挑战不存在或已过期");
        }
        if (!ChallengeStatus.PENDING.value().equals(payload.getStatus())) {
            throw new IllegalArgumentException("二维码挑战状态不可用");
        }

        long now = Instant.now().getEpochSecond();
        payload.setStatus(status.value());
        payload.setApprovedByUserId(approvedByUserId);
        payload.setResult(result);
        payload.setUpdatedAt(now);
        payload.setApprovedAt(now);

        long remaining = getRemainingSeconds(payload.getChallengeId());
        if (remaining <= 0) {
            remaining = DEFAULT_TTL_SECONDS;
        }
        save(payload, remaining);
    }

    public long getRemainingSeconds(String challengeId) {
        if (challengeId == null || challengeId.isBlank()) {
            return -1;
        }
        Long expire = redisTemplate.getExpire(challengeKey(challengeId.trim()), TimeUnit.SECONDS);
        if (expire == null || expire < 0) {
            return -1;
        }
        return expire;
    }

    private void save(QrChallengePayload payload, long ttlSeconds) {
        long ttl = ttlSeconds > 0 ? ttlSeconds : DEFAULT_TTL_SECONDS;
        try {
            String value = objectMapper.writeValueAsString(payload);
            redisTemplate.opsForValue().set(challengeKey(payload.getChallengeId()), value, ttl, TimeUnit.SECONDS);
            redisTemplate.opsForValue().set(approveKey(payload.getApproveCode()), payload.getChallengeId(), ttl, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("保存二维码挑战失败", e);
        }
    }

    private String challengeKey(String challengeId) {
        return CHALLENGE_PREFIX + challengeId;
    }

    private String approveKey(String approveCode) {
        return APPROVE_PREFIX + approveCode;
    }

    private String generateToken() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public enum ChallengeType {
        LOGIN("login"),
        MFA("mfa"),
        SENSITIVE("sensitive");

        private final String value;

        ChallengeType(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        public static ChallengeType fromValue(String value) {
            if (value == null) {
                return null;
            }
            for (ChallengeType type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            return null;
        }
    }

    public enum ChallengeStatus {
        PENDING("pending"),
        APPROVED("approved"),
        REJECTED("rejected");

        private final String value;

        ChallengeStatus(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QrChallengePayload {
        private String challengeId;
        private String approveCode;
        private String pollToken;
        private String type;
        private String status;
        private Long requestedUserId;
        private String mfaChallengeId;
        private String webIp;
        private Map<String, Object> result;
        private Long approvedByUserId;
        private Long createdAt;
        private Long updatedAt;
        private Long approvedAt;

        public String getChallengeId() {
            return challengeId;
        }

        public void setChallengeId(String challengeId) {
            this.challengeId = challengeId;
        }

        public String getApproveCode() {
            return approveCode;
        }

        public void setApproveCode(String approveCode) {
            this.approveCode = approveCode;
        }

        public String getPollToken() {
            return pollToken;
        }

        public void setPollToken(String pollToken) {
            this.pollToken = pollToken;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Long getRequestedUserId() {
            return requestedUserId;
        }

        public void setRequestedUserId(Long requestedUserId) {
            this.requestedUserId = requestedUserId;
        }

        public String getMfaChallengeId() {
            return mfaChallengeId;
        }

        public void setMfaChallengeId(String mfaChallengeId) {
            this.mfaChallengeId = mfaChallengeId;
        }

        public String getWebIp() {
            return webIp;
        }

        public void setWebIp(String webIp) {
            this.webIp = webIp;
        }

        public Map<String, Object> getResult() {
            return result;
        }

        public void setResult(Map<String, Object> result) {
            this.result = result;
        }

        public Long getApprovedByUserId() {
            return approvedByUserId;
        }

        public void setApprovedByUserId(Long approvedByUserId) {
            this.approvedByUserId = approvedByUserId;
        }

        public Long getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Long createdAt) {
            this.createdAt = createdAt;
        }

        public Long getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(Long updatedAt) {
            this.updatedAt = updatedAt;
        }

        public Long getApprovedAt() {
            return approvedAt;
        }

        public void setApprovedAt(Long approvedAt) {
            this.approvedAt = approvedAt;
        }
    }
}
