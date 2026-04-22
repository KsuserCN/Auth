package cn.ksuser.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class AdaptiveRiskAlertService {

    private static final String ALERT_PREFIX = "adaptive:risk:alert:";
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public AdaptiveRiskAlertService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
    }

    public void publishStepUpAlert(Long userId, int riskScore, List<String> reasons) {
        if (userId == null) {
            return;
        }
        long now = Instant.now().getEpochSecond();
        AlertPayload payload = new AlertPayload(
            "medium",
            "检测到会话风险波动",
            buildMessage("建议立即完成一次 step-up 验证", reasons),
            "STEP_UP",
            riskScore,
            now,
            now + Duration.ofMinutes(15).getSeconds()
        );
        save(userId, payload, Duration.ofMinutes(15));
    }

    public void publishFreezeAlert(Long userId, int riskScore, List<String> reasons) {
        if (userId == null) {
            return;
        }
        long now = Instant.now().getEpochSecond();
        AlertPayload payload = new AlertPayload(
            "high",
            "高风险会话已冻结",
            buildMessage("当前会话已被冻结，请重新登录并完成安全验证", reasons),
            "FREEZE",
            riskScore,
            now,
            now + Duration.ofMinutes(30).getSeconds()
        );
        save(userId, payload, Duration.ofMinutes(30));
    }

    public AlertPayload getActiveAlert(Long userId) {
        if (userId == null) {
            return null;
        }
        String raw = redisTemplate.opsForValue().get(alertKey(userId));
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, AlertPayload.class);
        } catch (Exception e) {
            return null;
        }
    }

    public long getRemainingSeconds(Long userId) {
        if (userId == null) {
            return 0L;
        }
        Long ttl = redisTemplate.getExpire(alertKey(userId), java.util.concurrent.TimeUnit.SECONDS);
        return ttl == null || ttl < 0 ? 0L : ttl;
    }

    private void save(Long userId, AlertPayload payload, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(
                alertKey(userId),
                objectMapper.writeValueAsString(payload),
                ttl
            );
        } catch (JsonProcessingException ignored) {
            // 仅影响告警投递，不影响主流程
        }
    }

    private String buildMessage(String prefix, List<String> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return prefix;
        }
        String first = reasons.get(0) == null ? "" : reasons.get(0).trim();
        if (first.isEmpty()) {
            return prefix;
        }
        return prefix + "（风险信号：" + first + "）";
    }

    private String alertKey(Long userId) {
        return ALERT_PREFIX + userId;
    }

    public static class AlertPayload {
        private String level;
        private String title;
        private String message;
        private String decision;
        private int riskScore;
        private long createdAtEpochSeconds;
        private long expiresAtEpochSeconds;

        public AlertPayload() {
        }

        public AlertPayload(String level, String title, String message, String decision,
                            int riskScore, long createdAtEpochSeconds, long expiresAtEpochSeconds) {
            this.level = level;
            this.title = title;
            this.message = message;
            this.decision = decision;
            this.riskScore = riskScore;
            this.createdAtEpochSeconds = createdAtEpochSeconds;
            this.expiresAtEpochSeconds = expiresAtEpochSeconds;
        }

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getDecision() {
            return decision;
        }

        public void setDecision(String decision) {
            this.decision = decision;
        }

        public int getRiskScore() {
            return riskScore;
        }

        public void setRiskScore(int riskScore) {
            this.riskScore = riskScore;
        }

        public long getCreatedAtEpochSeconds() {
            return createdAtEpochSeconds;
        }

        public void setCreatedAtEpochSeconds(long createdAtEpochSeconds) {
            this.createdAtEpochSeconds = createdAtEpochSeconds;
        }

        public long getExpiresAtEpochSeconds() {
            return expiresAtEpochSeconds;
        }

        public void setExpiresAtEpochSeconds(long expiresAtEpochSeconds) {
            this.expiresAtEpochSeconds = expiresAtEpochSeconds;
        }
    }
}
