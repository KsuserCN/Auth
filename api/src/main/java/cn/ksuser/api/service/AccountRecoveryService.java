package cn.ksuser.api.service;

import cn.ksuser.api.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class AccountRecoveryService {

    public static final long DEFAULT_TTL_SECONDS = 300L;
    private static final String REDIS_PREFIX = "auth:account-recovery:";

    private final StringRedisTemplate redisTemplate;
    private final IpLocationService ipLocationService;
    private final UserAgentParserService userAgentParserService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom secureRandom = new SecureRandom();

    public AccountRecoveryService(
            StringRedisTemplate redisTemplate,
            IpLocationService ipLocationService,
            UserAgentParserService userAgentParserService) {
        this.redisTemplate = redisTemplate;
        this.ipLocationService = ipLocationService;
        this.userAgentParserService = userAgentParserService;
    }

    public AccountRecoveryPayload createAuthorization(
            User user,
            Long sponsorSessionId,
            String sponsorIp,
            String sponsorUserAgent) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (sponsorSessionId == null) {
            throw new IllegalArgumentException("当前登录会话无效，请重新登录后再试");
        }

        String recoveryCode = generateRecoveryCode();
        AccountRecoveryPayload payload = new AccountRecoveryPayload();
        payload.setUserId(user.getId());
        payload.setSponsorSessionId(sponsorSessionId);
        payload.setUsername(user.getUsername());
        payload.setMaskedEmail(maskEmail(user.getEmail()));
        payload.setIssuedAt(Instant.now().toString());
        payload.setSponsorIp(sponsorIp);
        payload.setSponsorIpLocation(ipLocationService.getIpLocation(sponsorIp));
        payload.setSponsorUserAgent(sponsorUserAgent);

        UserAgentParserService.UserAgentInfo uaInfo = userAgentParserService.parse(sponsorUserAgent);
        payload.setSponsorBrowser(uaInfo.getBrowser());
        payload.setSponsorSystem(userAgentParserService.normalizeSystemLabel(uaInfo.getDeviceType()));
        payload.setSponsorClientName(userAgentParserService.describeClientSource(uaInfo));

        try {
            String value = objectMapper.writeValueAsString(payload);
            redisTemplate.opsForValue().set(
                redisKey(recoveryCode),
                value,
                Duration.ofSeconds(DEFAULT_TTL_SECONDS)
            );
            return payload.withRecoveryCode(recoveryCode);
        } catch (Exception e) {
            throw new IllegalStateException("创建恢复授权失败", e);
        }
    }

    public AccountRecoveryPayload getAuthorization(String recoveryCode) {
        String normalized = normalizeRecoveryCode(recoveryCode);
        if (normalized == null) {
            return null;
        }

        try {
            String payloadJson = redisTemplate.opsForValue().get(redisKey(normalized));
            if (payloadJson == null || payloadJson.isBlank()) {
                return null;
            }
            return objectMapper.readValue(payloadJson, AccountRecoveryPayload.class).withRecoveryCode(normalized);
        } catch (Exception e) {
            return null;
        }
    }

    public AccountRecoveryPayload consumeAuthorization(String recoveryCode) {
        String normalized = normalizeRecoveryCode(recoveryCode);
        if (normalized == null) {
            throw new IllegalArgumentException("recoveryCode 不能为空");
        }

        String payloadJson;
        try {
            payloadJson = redisTemplate.opsForValue().getAndDelete(redisKey(normalized));
        } catch (Exception e) {
            throw new IllegalStateException("读取恢复授权失败", e);
        }

        if (payloadJson == null || payloadJson.isBlank()) {
            throw new IllegalArgumentException("恢复授权不存在、已失效或已被使用");
        }

        try {
            return objectMapper.readValue(payloadJson, AccountRecoveryPayload.class).withRecoveryCode(normalized);
        } catch (Exception e) {
            throw new IllegalStateException("解析恢复授权失败", e);
        }
    }

    public long getRemainingSeconds(String recoveryCode) {
        String normalized = normalizeRecoveryCode(recoveryCode);
        if (normalized == null) {
            return -1;
        }

        Long expire = redisTemplate.getExpire(redisKey(normalized), java.util.concurrent.TimeUnit.SECONDS);
        if (expire == null || expire < 0) {
            return -1;
        }
        return expire;
    }

    private String normalizeRecoveryCode(String recoveryCode) {
        if (recoveryCode == null || recoveryCode.trim().isEmpty()) {
            return null;
        }
        return recoveryCode.trim();
    }

    private String redisKey(String recoveryCode) {
        return REDIS_PREFIX + recoveryCode;
    }

    private String generateRecoveryCode() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "未绑定邮箱";
        }

        int atIndex = email.indexOf('@');
        if (atIndex <= 0 || atIndex == email.length() - 1) {
            if (email.length() <= 2) {
                return email.charAt(0) + "*";
            }
            return email.substring(0, 1) + "***" + email.substring(email.length() - 1);
        }

        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (localPart.length() <= 2) {
            return localPart.substring(0, 1) + "***" + domain;
        }
        return localPart.substring(0, 2) + "***" + domain;
    }

    public static class AccountRecoveryPayload {
        private Long userId;
        private Long sponsorSessionId;
        private String recoveryCode;
        private String username;
        private String maskedEmail;
        private String issuedAt;
        private String sponsorIp;
        private String sponsorIpLocation;
        private String sponsorUserAgent;
        private String sponsorBrowser;
        private String sponsorSystem;
        private String sponsorClientName;

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public Long getSponsorSessionId() {
            return sponsorSessionId;
        }

        public void setSponsorSessionId(Long sponsorSessionId) {
            this.sponsorSessionId = sponsorSessionId;
        }

        public String getRecoveryCode() {
            return recoveryCode;
        }

        public void setRecoveryCode(String recoveryCode) {
            this.recoveryCode = recoveryCode;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getMaskedEmail() {
            return maskedEmail;
        }

        public void setMaskedEmail(String maskedEmail) {
            this.maskedEmail = maskedEmail;
        }

        public String getIssuedAt() {
            return issuedAt;
        }

        public void setIssuedAt(String issuedAt) {
            this.issuedAt = issuedAt;
        }

        public String getSponsorIp() {
            return sponsorIp;
        }

        public void setSponsorIp(String sponsorIp) {
            this.sponsorIp = sponsorIp;
        }

        public String getSponsorIpLocation() {
            return sponsorIpLocation;
        }

        public void setSponsorIpLocation(String sponsorIpLocation) {
            this.sponsorIpLocation = sponsorIpLocation;
        }

        public String getSponsorUserAgent() {
            return sponsorUserAgent;
        }

        public void setSponsorUserAgent(String sponsorUserAgent) {
            this.sponsorUserAgent = sponsorUserAgent;
        }

        public String getSponsorBrowser() {
            return sponsorBrowser;
        }

        public void setSponsorBrowser(String sponsorBrowser) {
            this.sponsorBrowser = sponsorBrowser;
        }

        public String getSponsorSystem() {
            return sponsorSystem;
        }

        public void setSponsorSystem(String sponsorSystem) {
            this.sponsorSystem = sponsorSystem;
        }

        public String getSponsorClientName() {
            return sponsorClientName;
        }

        public void setSponsorClientName(String sponsorClientName) {
            this.sponsorClientName = sponsorClientName;
        }

        public AccountRecoveryPayload withRecoveryCode(String code) {
            this.recoveryCode = code;
            return this;
        }
    }
}
