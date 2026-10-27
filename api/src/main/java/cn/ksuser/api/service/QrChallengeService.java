package cn.ksuser.api.service;

import cn.ksuser.api.result.ApiResultCode;
import cn.ksuser.api.result.BusinessArgumentException;
import cn.ksuser.api.result.BusinessStateException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class QrChallengeService {

    public static final long DEFAULT_TTL_SECONDS = 120L;
    public static final String QR_TEXT_PREFIX = "KSUSER-AUTH-QR:";
    private static final int SCHEMA_VERSION = 2;
    private static final String CHALLENGE_PREFIX = "auth:qr:v2:challenge:";
    private static final String APPROVE_PREFIX = "auth:qr:v2:approve:";
    private static final String PROOF_PREFIX = "auth:qr:v2:proof:";
    private static final Set<String> PROOF_METHODS = Set.of("password", "email-code", "totp", "recovery_code", "passkey");

    static final DefaultRedisScript<Long> UPDATE_STATUS = new DefaultRedisScript<>("""
        local raw = redis.call('GET', KEYS[1])
        if not raw or raw ~= ARGV[1] then return 0 end
        local challenge = cjson.decode(raw)
        local now = tonumber(redis.call('TIME')[1])
        local ttl = redis.call('PTTL', KEYS[1])
        if challenge.status ~= 'pending' or challenge.schemaVersion ~= 2
            or not challenge.expiresAt or challenge.expiresAt <= now or ttl <= 0 then return 0 end
        local updated = cjson.decode(ARGV[2])
        if updated.status == 'approved' and (challenge.type == 'sensitive' or challenge.type == 'recovery') then return 0 end
        ttl = math.min(ttl, (challenge.expiresAt - now) * 1000)
        redis.call('SET', KEYS[1], ARGV[2], 'PX', ttl)
        return 1
        """, Long.class);

    // All checks, consumption and publication share one Redis transaction. An expired
    // challenge can never be recreated and competing proofs cannot both approve it.
    static final DefaultRedisScript<Long> APPROVE_PROTECTED = new DefaultRedisScript<>("""
        local raw = redis.call('GET', KEYS[1])
        local proofRaw = redis.call('GET', KEYS[2])
        if not raw or raw ~= ARGV[1] or not proofRaw then return 0 end
        local challenge = cjson.decode(raw)
        local proof = cjson.decode(proofRaw)
        local now = tonumber(redis.call('TIME')[1])
        local ttl = redis.call('PTTL', KEYS[1])
        if challenge.schemaVersion ~= 2 or challenge.status ~= 'pending'
            or (challenge.type ~= 'sensitive' and challenge.type ~= 'recovery')
            or not challenge.expiresAt or challenge.expiresAt <= now or ttl <= 0 then return 0 end
        if proof.schemaVersion ~= 2 or proof.purpose ~= 'QR_APPROVE' or proof.source ~= 'direct-factor'
            or proof.challengeId ~= challenge.challengeId or tostring(proof.userId) ~= ARGV[2]
            or tostring(proof.sessionId) ~= ARGV[3] or tostring(proof.sessionVersion) ~= ARGV[4]
            or not proof.expiresAt or proof.expiresAt <= now
            or proof.operationPurpose ~= challenge.operationPurpose then return 0 end
        if challenge.requestedUserId and challenge.requestedUserId ~= cjson.null
            and tostring(challenge.requestedUserId) ~= ARGV[2] then return 0 end
        if #KEYS == 3 and challenge.type == 'sensitive' and (not challenge.initiatorSessionId or challenge.initiatorSessionId == cjson.null
            or not challenge.initiatorSessionVersion or challenge.initiatorSessionVersion == cjson.null) then return 0 end
        ttl = math.min(ttl, (challenge.expiresAt - now) * 1000)
        redis.call('DEL', KEYS[2])
        redis.call('SET', KEYS[1], ARGV[5], 'PX', ttl)
        if #KEYS == 3 then
            redis.call('SET', KEYS[3], ARGV[6], 'EX', math.min(tonumber(ARGV[7]), 900))
        end
        return 1
        """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final IpLocationService ipLocationService;
    private final UserAgentParserService userAgentParserService;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom;
    private final Clock clock;

    @Autowired
    public QrChallengeService(
            StringRedisTemplate redisTemplate,
            IpLocationService ipLocationService,
            UserAgentParserService userAgentParserService) {
        this(redisTemplate, ipLocationService, userAgentParserService, Clock.systemUTC());
    }

    QrChallengeService(StringRedisTemplate redisTemplate, IpLocationService ipLocationService,
                       UserAgentParserService userAgentParserService, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.ipLocationService = ipLocationService;
        this.userAgentParserService = userAgentParserService;
        this.objectMapper = new ObjectMapper();
        this.secureRandom = new SecureRandom();
        this.clock = clock;
    }

    public QrChallengePayload createChallenge(
            ChallengeType type,
            Long requestedUserId,
            String mfaChallengeId,
            String target,
            String webIp,
            String userAgent) {
        return createChallenge(type, requestedUserId, mfaChallengeId, target, webIp, userAgent, null);
    }

    public QrChallengePayload createConsoleSensitiveChallenge(
            Long requestedUserId,
            String verificationRequestId,
            String target,
            String webIp,
            String userAgent) {
        return createChallenge(ChallengeType.SENSITIVE, requestedUserId, null, target, webIp, userAgent,
            verificationRequestId);
    }

    public QrChallengePayload createSensitiveChallenge(Long requestedUserId, Long initiatorSessionId,
            Integer initiatorSessionVersion, String operationPurpose, String target, String webIp, String userAgent) {
        if (requestedUserId == null || initiatorSessionId == null || initiatorSessionVersion == null
            || operationPurpose == null || operationPurpose.isBlank()) {
            throw new BusinessArgumentException(ApiResultCode.AUTH_090);
        }
        QrChallengePayload payload = createChallenge(ChallengeType.SENSITIVE, requestedUserId, null,
            target, webIp, userAgent, null);
        payload.setInitiatorSessionId(initiatorSessionId);
        payload.setInitiatorSessionVersion(initiatorSessionVersion);
        payload.setOperationPurpose(operationPurpose);
        save(payload, DEFAULT_TTL_SECONDS);
        return payload;
    }

    public QrChallengePayload createApplicationAuthorizationChallenge(String ticket, String webIp, String userAgent) {
        QrChallengePayload payload = createChallenge(ChallengeType.APPLICATION_AUTHORIZATION, null, null,
            SessionTransferService.TARGET_WEB, webIp, userAgent, null);
        payload.setAuthorizationTicket(ticket);
        save(payload, DEFAULT_TTL_SECONDS);
        return payload;
    }

    private QrChallengePayload createChallenge(
            ChallengeType type,
            Long requestedUserId,
            String mfaChallengeId,
            String target,
            String webIp,
            String userAgent,
            String consoleVerificationRequestId) {
        long now = clock.instant().getEpochSecond();
        QrChallengePayload payload = new QrChallengePayload();
        payload.setChallengeId(UUID.randomUUID().toString());
        payload.setApproveCode(generateToken());
        payload.setPollToken(generateToken());
        payload.setType(type.value());
        payload.setStatus(ChallengeStatus.PENDING.value());
        payload.setSchemaVersion(SCHEMA_VERSION);
        payload.setExpiresAt(now + DEFAULT_TTL_SECONDS);
        payload.setOperationPurpose(type == ChallengeType.RECOVERY ? "account-recovery"
            : consoleVerificationRequestId != null ? "console-verification" : type.value());
        payload.setRequestedUserId(requestedUserId);
        payload.setMfaChallengeId(mfaChallengeId);
        payload.setConsoleVerificationRequestId(consoleVerificationRequestId);
        payload.setTarget(target);
        payload.setWebIp(webIp);
        payload.setUserAgent(userAgent);
        payload.setIpLocation(ipLocationService.getIpLocation(webIp));
        UserAgentParserService.UserAgentInfo uaInfo = userAgentParserService.parse(userAgent);
        payload.setBrowser(uaInfo.getBrowser());
        payload.setSystem(userAgentParserService.normalizeSystemLabel(uaInfo.getDeviceType()));
        payload.setClientName(userAgentParserService.describeClientSource(uaInfo));
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
        QrChallengePayload payload = getByChallengeId(challengeId);
        if (requiresIndependentProof(payload)) {
            throw new BusinessArgumentException(ApiResultCode.SENSITIVE_VERIFICATION_REQUIRED);
        }
        updateChallengeStatus(challengeId, ChallengeStatus.APPROVED, approvedByUserId, result);
    }

    public boolean requiresIndependentProof(QrChallengePayload payload) {
        return payload != null && (ChallengeType.SENSITIVE.value().equals(payload.getType())
            || ChallengeType.RECOVERY.value().equals(payload.getType()));
    }

    public boolean isProtectedChallengeValid(QrChallengePayload payload) {
        return requiresIndependentProof(payload) && Integer.valueOf(SCHEMA_VERSION).equals(payload.getSchemaVersion())
            && ChallengeStatus.PENDING.value().equals(payload.getStatus()) && payload.getExpiresAt() != null
            && payload.getExpiresAt() > clock.instant().getEpochSecond()
            && payload.getOperationPurpose() != null && !payload.getOperationPurpose().isBlank();
    }

    /** Called only after the server has verified an existing independent credential. */
    public ApprovalProof issueApprovalProof(String challengeId, Long userId, Long sessionId,
                                            Integer sessionVersion, String method) {
        QrChallengePayload payload = getByChallengeId(challengeId);
        if (!isProtectedChallengeValid(payload) || userId == null || sessionId == null || sessionVersion == null
            || !PROOF_METHODS.contains(method == null ? "" : method)
            || (payload.getRequestedUserId() != null && !payload.getRequestedUserId().equals(userId))) {
            throw new BusinessArgumentException(ApiResultCode.SENSITIVE_VERIFICATION_REQUIRED);
        }
        long now = clock.instant().getEpochSecond();
        long expiresAt = Math.min(now + DEFAULT_TTL_SECONDS, payload.getExpiresAt());
        String proofId = generateToken();
        ApprovalProofPayload proof = new ApprovalProofPayload(SCHEMA_VERSION, "QR_APPROVE", "direct-factor",
            challengeId, userId, sessionId, sessionVersion, payload.getOperationPurpose(), method, now, expiresAt);
        try {
            redisTemplate.opsForValue().set(PROOF_PREFIX + proofId, objectMapper.writeValueAsString(proof),
                expiresAt - now, TimeUnit.SECONDS);
            return new ApprovalProof(proofId, expiresAt - now);
        } catch (Exception e) {
            throw new BusinessStateException(ApiResultCode.QR_001, e);
        }
    }

    public void approveProtectedChallenge(QrChallengePayload expected, String proofId, Long userId,
            Long sessionId, Integer sessionVersion, Map<String, Object> result,
            String grantKey, String grantValue, long grantTtlSeconds) {
        if (expected == null || proofId == null || !proofId.matches("[A-Za-z0-9_-]{32}")
            || userId == null || sessionId == null || sessionVersion == null
            || (grantKey != null && (grantValue == null || grantTtlSeconds <= 0))) {
            throw new BusinessArgumentException(ApiResultCode.SENSITIVE_VERIFICATION_REQUIRED);
        }
        String raw = redisTemplate.opsForValue().get(challengeKey(expected.getChallengeId()));
        QrChallengePayload payload = decode(raw);
        if (!isProtectedChallengeValid(payload)
            || !java.util.Objects.equals(payload.getInitiatorSessionId(), expected.getInitiatorSessionId())
            || !java.util.Objects.equals(payload.getInitiatorSessionVersion(), expected.getInitiatorSessionVersion())
            || !java.util.Objects.equals(payload.getConsoleVerificationRequestId(), expected.getConsoleVerificationRequestId())
            || !java.util.Objects.equals(payload.getOperationPurpose(), expected.getOperationPurpose())
            || (payload.getRequestedUserId() != null && !payload.getRequestedUserId().equals(userId))) {
            throw new BusinessArgumentException(ApiResultCode.SENSITIVE_VERIFICATION_REQUIRED);
        }
        long now = clock.instant().getEpochSecond();
        payload.setStatus(ChallengeStatus.APPROVED.value());
        payload.setApprovedByUserId(userId);
        payload.setResult(result);
        payload.setUpdatedAt(now);
        payload.setApprovedAt(now);
        List<String> keys = grantKey == null
            ? List.of(challengeKey(payload.getChallengeId()), PROOF_PREFIX + proofId)
            : List.of(challengeKey(payload.getChallengeId()), PROOF_PREFIX + proofId, grantKey);
        try {
            Long changed = redisTemplate.execute(APPROVE_PROTECTED, keys, raw, userId.toString(), sessionId.toString(),
                sessionVersion.toString(), objectMapper.writeValueAsString(payload), grantValue == null ? "" : grantValue,
                Long.toString(grantTtlSeconds));
            if (!Long.valueOf(1).equals(changed)) {
                throw new BusinessArgumentException(ApiResultCode.SENSITIVE_VERIFICATION_REQUIRED);
            }
        } catch (BusinessArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessStateException(ApiResultCode.QR_001, e);
        }
    }

    public void rejectChallenge(String challengeId, Long approvedByUserId, Map<String, Object> result) {
        updateChallengeStatus(challengeId, ChallengeStatus.REJECTED, approvedByUserId, result);
    }

    private void updateChallengeStatus(String challengeId, ChallengeStatus status, Long approvedByUserId, Map<String, Object> result) {
        String raw = redisTemplate.opsForValue().get(challengeKey(challengeId));
        QrChallengePayload payload = decode(raw);
        if (payload == null) {
            throw new BusinessArgumentException(ApiResultCode.AUTH_086);
        }
        if (!ChallengeStatus.PENDING.value().equals(payload.getStatus())) {
            throw new BusinessArgumentException(ApiResultCode.AUTH_089);
        }

        long now = clock.instant().getEpochSecond();
        payload.setStatus(status.value());
        payload.setApprovedByUserId(approvedByUserId);
        payload.setResult(result);
        payload.setUpdatedAt(now);
        payload.setApprovedAt(now);

        try {
            Long changed = redisTemplate.execute(UPDATE_STATUS, List.of(challengeKey(challengeId)),
                raw, objectMapper.writeValueAsString(payload));
            if (!Long.valueOf(1).equals(changed)) throw new BusinessArgumentException(ApiResultCode.AUTH_089);
        } catch (BusinessArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessStateException(ApiResultCode.QR_001, e);
        }
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
        long remaining = payload.getExpiresAt() == null ? 0 : payload.getExpiresAt() - clock.instant().getEpochSecond();
        long ttl = Math.min(ttlSeconds, remaining);
        if (ttl <= 0) throw new BusinessArgumentException(ApiResultCode.AUTH_086);
        try {
            String value = objectMapper.writeValueAsString(payload);
            redisTemplate.opsForValue().set(challengeKey(payload.getChallengeId()), value, ttl, TimeUnit.SECONDS);
            redisTemplate.opsForValue().set(approveKey(payload.getApproveCode()), payload.getChallengeId(), ttl, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new BusinessStateException(ApiResultCode.QR_001, e);
        }
    }

    private QrChallengePayload decode(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return objectMapper.readValue(raw, QrChallengePayload.class); }
        catch (Exception e) { return null; }
    }

    public record ApprovalProof(String proofId, long expiresInSeconds) {}
    private record ApprovalProofPayload(int schemaVersion, String purpose, String source, String challengeId,
        Long userId, Long sessionId, Integer sessionVersion, String operationPurpose, String method,
        long verifiedAt, long expiresAt) {}

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
        CONSOLE_LOGIN("console_login"),
        OFFICIAL_AUTHORIZATION("official"),
        APPLICATION_AUTHORIZATION("application"),
        MFA("mfa"),
        SENSITIVE("sensitive"),
        RECOVERY("recovery");

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
        private String consoleVerificationRequestId;
        private String authorizationTicket;
        private Integer schemaVersion;
        private Long expiresAt;
        private Long initiatorSessionId;
        private Integer initiatorSessionVersion;
        private String operationPurpose;

        public Integer getSchemaVersion() { return schemaVersion; }
        public void setSchemaVersion(Integer value) { schemaVersion = value; }
        public Long getExpiresAt() { return expiresAt; }
        public void setExpiresAt(Long value) { expiresAt = value; }
        public Long getInitiatorSessionId() { return initiatorSessionId; }
        public void setInitiatorSessionId(Long value) { initiatorSessionId = value; }
        public Integer getInitiatorSessionVersion() { return initiatorSessionVersion; }
        public void setInitiatorSessionVersion(Integer value) { initiatorSessionVersion = value; }
        public String getOperationPurpose() { return operationPurpose; }
        public void setOperationPurpose(String value) { operationPurpose = value; }

        public String getAuthorizationTicket() { return authorizationTicket; }
        public void setAuthorizationTicket(String value) { authorizationTicket = value; }
        private String target;
        private String webIp;
        private String ipLocation;
        private String userAgent;
        private String browser;
        private String system;
        private String clientName;
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

        public String getConsoleVerificationRequestId() {
            return consoleVerificationRequestId;
        }

        public void setConsoleVerificationRequestId(String consoleVerificationRequestId) {
            this.consoleVerificationRequestId = consoleVerificationRequestId;
        }

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }

        public String getWebIp() {
            return webIp;
        }

        public void setWebIp(String webIp) {
            this.webIp = webIp;
        }

        public String getIpLocation() {
            return ipLocation;
        }

        public void setIpLocation(String ipLocation) {
            this.ipLocation = ipLocation;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }

        public String getBrowser() {
            return browser;
        }

        public void setBrowser(String browser) {
            this.browser = browser;
        }

        public String getSystem() {
            return system;
        }

        public void setSystem(String system) {
            this.system = system;
        }

        public String getClientName() {
            return clientName;
        }

        public void setClientName(String clientName) {
            this.clientName = clientName;
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
