package cn.ksuser.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Service
public class SessionTransferService {

    public static final String TARGET_WEB = "web";
    public static final String TARGET_DESKTOP = "desktop";
    public static final String PURPOSE_BRIDGE_LOGIN = "bridge_login";
    public static final String PURPOSE_SESSION_SYNC = "session_sync";
    public static final String PURPOSE_AUTH_BRIDGE_INTERNAL = "auth_bridge_internal";
    public static final long DEFAULT_TTL_SECONDS = 90L;
    private static final String REDIS_PREFIX = "auth:session-transfer:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom secureRandom = new SecureRandom();

    public SessionTransferService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String normalizeTarget(String rawTarget) {
        if (rawTarget == null) {
            return null;
        }

        String target = rawTarget.trim().toLowerCase();
        if (TARGET_WEB.equals(target) || TARGET_DESKTOP.equals(target)) {
            return target;
        }
        return null;
    }

    public SessionTransferPayload createTransfer(Long userId, String target) {
        return createTransfer(userId, target, null);
    }

    public String normalizePurpose(String rawPurpose) {
        if (rawPurpose == null) {
            return PURPOSE_BRIDGE_LOGIN;
        }

        String purpose = rawPurpose.trim().toLowerCase();
        if (purpose.isEmpty()) {
            return PURPOSE_BRIDGE_LOGIN;
        }

        if (PURPOSE_BRIDGE_LOGIN.equals(purpose)
            || PURPOSE_SESSION_SYNC.equals(purpose)
            || PURPOSE_AUTH_BRIDGE_INTERNAL.equals(purpose)) {
            return purpose;
        }

        return PURPOSE_BRIDGE_LOGIN;
    }

    public SessionTransferPayload createTransfer(Long userId, String target, String purpose) {
        String normalizedTarget = normalizeTarget(target);
        if (normalizedTarget == null) {
            throw new IllegalArgumentException("target 只能是 web 或 desktop");
        }
        String normalizedPurpose = normalizePurpose(purpose);

        String transferCode = generateTransferCode();
        SessionTransferPayload payload = new SessionTransferPayload(userId, normalizedTarget, normalizedPurpose);

        try {
            String value = objectMapper.writeValueAsString(payload);
            redisTemplate.opsForValue().set(redisKey(transferCode), value, Duration.ofSeconds(DEFAULT_TTL_SECONDS));
            return payload.withTransferCode(transferCode);
        } catch (Exception e) {
            throw new IllegalStateException("创建跨端票据失败", e);
        }
    }

    public SessionTransferPayload consumeTransfer(String transferCode, String target) {
        String normalizedTarget = normalizeTarget(target);
        if (normalizedTarget == null) {
            throw new IllegalArgumentException("target 只能是 web 或 desktop");
        }
        if (transferCode == null || transferCode.trim().isEmpty()) {
            throw new IllegalArgumentException("transferCode 不能为空");
        }

        String payloadJson;
        try {
            payloadJson = redisTemplate.opsForValue().getAndDelete(redisKey(transferCode.trim()));
        } catch (Exception e) {
            throw new IllegalStateException("读取跨端票据失败", e);
        }

        if (payloadJson == null || payloadJson.isBlank()) {
            throw new IllegalArgumentException("跨端票据不存在或已过期");
        }

        try {
            SessionTransferPayload payload = objectMapper.readValue(payloadJson, SessionTransferPayload.class);
            if (!normalizedTarget.equals(payload.getTarget())) {
                throw new IllegalArgumentException("跨端票据目标端不匹配");
            }
            if (payload.getUserId() == null) {
                throw new IllegalArgumentException("跨端票据数据无效");
            }
            payload.setPurpose(normalizePurpose(payload.getPurpose()));
            return payload;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("解析跨端票据失败", e);
        }
    }

    private String redisKey(String transferCode) {
        return REDIS_PREFIX + transferCode;
    }

    private String generateTransferCode() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static class SessionTransferPayload {
        private Long userId;
        private String target;
        private String purpose;
        private String transferCode;

        public SessionTransferPayload() {
        }

        public SessionTransferPayload(Long userId, String target) {
            this.userId = userId;
            this.target = target;
        }

        public SessionTransferPayload(Long userId, String target, String purpose) {
            this.userId = userId;
            this.target = target;
            this.purpose = purpose;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }

        public String getTransferCode() {
            return transferCode;
        }

        public void setTransferCode(String transferCode) {
            this.transferCode = transferCode;
        }

        public String getPurpose() {
            return purpose;
        }

        public void setPurpose(String purpose) {
            this.purpose = purpose;
        }

        public boolean shouldAuditAsBridgeLogin() {
            return PURPOSE_BRIDGE_LOGIN.equals(purpose);
        }

        public SessionTransferPayload withTransferCode(String code) {
            this.transferCode = code;
            return this;
        }
    }
}
