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
    public static final String TARGET_MOBILE = "mobile";
    public static final String PURPOSE_BRIDGE_LOGIN = "bridge_login";
    public static final String PURPOSE_SESSION_SYNC = "session_sync";
    public static final String PURPOSE_AUTH_BRIDGE_INTERNAL = "auth_bridge_internal";
    public static final long DEFAULT_TTL_SECONDS = 90L;
    private static final String REDIS_PREFIX = "auth:session-transfer:";

    private final StringRedisTemplate redisTemplate;
    private final IpLocationService ipLocationService;
    private final UserAgentParserService userAgentParserService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom secureRandom = new SecureRandom();

    public SessionTransferService(
            StringRedisTemplate redisTemplate,
            IpLocationService ipLocationService,
            UserAgentParserService userAgentParserService) {
        this.redisTemplate = redisTemplate;
        this.ipLocationService = ipLocationService;
        this.userAgentParserService = userAgentParserService;
    }

    public String normalizeTarget(String rawTarget) {
        if (rawTarget == null) {
            return null;
        }

        String target = rawTarget.trim().toLowerCase();
        if (TARGET_WEB.equals(target)
            || TARGET_DESKTOP.equals(target)
            || TARGET_MOBILE.equals(target)) {
            return target;
        }
        return null;
    }

    public SessionTransferPayload createTransfer(Long userId, String target) {
        return createTransfer(userId, target, null, null, null);
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
        return createTransfer(userId, target, purpose, null, null);
    }

    public SessionTransferPayload createTransfer(
            Long userId,
            String target,
            String purpose,
            String requesterIp,
            String requesterUserAgent) {
        String normalizedTarget = normalizeTarget(target);
        if (normalizedTarget == null) {
            throw new IllegalArgumentException("target 只能是 web、desktop 或 mobile");
        }
        String normalizedPurpose = normalizePurpose(purpose);

        String transferCode = generateTransferCode();
        SessionTransferPayload payload = new SessionTransferPayload(userId, normalizedTarget, normalizedPurpose);
        payload.setRequesterIp(requesterIp);
        payload.setRequesterIpLocation(ipLocationService.getIpLocation(requesterIp));
        payload.setRequesterUserAgent(requesterUserAgent);
        UserAgentParserService.UserAgentInfo uaInfo = userAgentParserService.parse(requesterUserAgent);
        payload.setRequesterBrowser(uaInfo.getBrowser());
        payload.setRequesterSystem(userAgentParserService.normalizeSystemLabel(uaInfo.getDeviceType()));
        payload.setRequesterClientName(userAgentParserService.describeClientSource(uaInfo));

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
            throw new IllegalArgumentException("target 只能是 web、desktop 或 mobile");
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

    public SessionTransferPayload getTransfer(String transferCode) {
        if (transferCode == null || transferCode.trim().isEmpty()) {
            return null;
        }
        try {
            String payloadJson = redisTemplate.opsForValue().get(redisKey(transferCode.trim()));
            if (payloadJson == null || payloadJson.isBlank()) {
                return null;
            }
            SessionTransferPayload payload = objectMapper.readValue(payloadJson, SessionTransferPayload.class);
            payload.setPurpose(normalizePurpose(payload.getPurpose()));
            return payload;
        } catch (Exception e) {
            return null;
        }
    }

    public long getRemainingSeconds(String transferCode) {
        if (transferCode == null || transferCode.isBlank()) {
            return -1;
        }
        Long expire = redisTemplate.getExpire(redisKey(transferCode.trim()), java.util.concurrent.TimeUnit.SECONDS);
        if (expire == null || expire < 0) {
            return -1;
        }
        return expire;
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
        private String requesterIp;
        private String requesterIpLocation;
        private String requesterUserAgent;
        private String requesterBrowser;
        private String requesterSystem;
        private String requesterClientName;

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

        public String getRequesterIp() {
            return requesterIp;
        }

        public void setRequesterIp(String requesterIp) {
            this.requesterIp = requesterIp;
        }

        public String getRequesterIpLocation() {
            return requesterIpLocation;
        }

        public void setRequesterIpLocation(String requesterIpLocation) {
            this.requesterIpLocation = requesterIpLocation;
        }

        public String getRequesterUserAgent() {
            return requesterUserAgent;
        }

        public void setRequesterUserAgent(String requesterUserAgent) {
            this.requesterUserAgent = requesterUserAgent;
        }

        public String getRequesterBrowser() {
            return requesterBrowser;
        }

        public void setRequesterBrowser(String requesterBrowser) {
            this.requesterBrowser = requesterBrowser;
        }

        public String getRequesterSystem() {
            return requesterSystem;
        }

        public void setRequesterSystem(String requesterSystem) {
            this.requesterSystem = requesterSystem;
        }

        public String getRequesterClientName() {
            return requesterClientName;
        }

        public void setRequesterClientName(String requesterClientName) {
            this.requesterClientName = requesterClientName;
        }
    }
}
