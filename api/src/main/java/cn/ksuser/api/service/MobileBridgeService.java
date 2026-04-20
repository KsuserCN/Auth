package cn.ksuser.api.service;

import cn.ksuser.api.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Service
public class MobileBridgeService {
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String STATUS_EXPIRED = "expired";
    public static final long DEFAULT_TTL_SECONDS = 120L;
    private static final String REDIS_PREFIX = "auth:mobile-bridge:";

    private final StringRedisTemplate redisTemplate;
    private final AppProperties appProperties;
    private final SessionTransferService sessionTransferService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom secureRandom = new SecureRandom();

    public MobileBridgeService(
            StringRedisTemplate redisTemplate,
            AppProperties appProperties,
            SessionTransferService sessionTransferService) {
        this.redisTemplate = redisTemplate;
        this.appProperties = appProperties;
        this.sessionTransferService = sessionTransferService;
    }

    public MobileBridgePayload createChallenge(String returnUrl, String clientNonce) {
        String challengeId = generateChallengeId();
        ValidatedReturnUrl validated = validateReturnUrl(rewriteReturnUrlChallengeId(returnUrl, challengeId));
        MobileBridgePayload payload = new MobileBridgePayload();
        payload.setChallengeId(challengeId);
        payload.setClientNonce(normalizeBlank(clientNonce));
        payload.setReturnUrl(validated.returnUrl());
        payload.setReturnOrigin(validated.returnOrigin());
        payload.setStatus(STATUS_PENDING);

        try {
            redisTemplate.opsForValue().set(
                redisKey(challengeId),
                objectMapper.writeValueAsString(payload),
                Duration.ofSeconds(DEFAULT_TTL_SECONDS)
            );
            return payload.withAppLink(buildAppLink(challengeId, validated.returnUrl()));
        } catch (Exception e) {
            throw new IllegalStateException("创建移动桥接挑战失败", e);
        }
    }

    public MobileBridgePayload getChallenge(String challengeId) {
        String normalizedChallengeId = normalizeChallengeId(challengeId);
        if (normalizedChallengeId == null) {
            return null;
        }

        try {
            String raw = redisTemplate.opsForValue().get(redisKey(normalizedChallengeId));
            if (raw == null || raw.isBlank()) {
                return null;
            }
            MobileBridgePayload payload = objectMapper.readValue(raw, MobileBridgePayload.class);
            payload.setChallengeId(normalizedChallengeId);
            payload.setStatus(normalizeStatus(payload.getStatus()));
            return payload;
        } catch (Exception e) {
            throw new IllegalStateException("读取移动桥接挑战失败", e);
        }
    }

    public MobileBridgePayload approveChallenge(
            String challengeId,
            Long userId,
            String requesterIp,
            String requesterUserAgent) {
        MobileBridgePayload payload = getChallenge(challengeId);
        if (payload == null) {
            throw new IllegalArgumentException("移动桥接挑战不存在或已过期");
        }
        if (!STATUS_PENDING.equals(payload.getStatus())) {
            throw new IllegalArgumentException("移动桥接挑战已处理");
        }
        if (userId == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        SessionTransferService.SessionTransferPayload transferPayload = sessionTransferService.createTransfer(
            userId,
            SessionTransferService.TARGET_WEB,
            SessionTransferService.PURPOSE_BRIDGE_LOGIN,
            requesterIp,
            requesterUserAgent
        );
        payload.setStatus(STATUS_APPROVED);
        payload.setTransferCode(transferPayload.getTransferCode());
        persistChallenge(payload);
        return payload;
    }

    public MobileBridgePayload cancelChallenge(String challengeId) {
        MobileBridgePayload payload = getChallenge(challengeId);
        if (payload == null) {
            return null;
        }
        if (!STATUS_PENDING.equals(payload.getStatus())) {
            return payload;
        }
        payload.setStatus(STATUS_CANCELLED);
        persistChallenge(payload);
        return payload;
    }

    public long getRemainingSeconds(String challengeId) {
        String normalizedChallengeId = normalizeChallengeId(challengeId);
        if (normalizedChallengeId == null) {
            return 0L;
        }
        Long expire = redisTemplate.getExpire(redisKey(normalizedChallengeId), java.util.concurrent.TimeUnit.SECONDS);
        if (expire == null || expire < 0) {
            return 0L;
        }
        return expire;
    }

    private void persistChallenge(MobileBridgePayload payload) {
        long ttlSeconds = getRemainingSeconds(payload.getChallengeId());
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("移动桥接挑战不存在或已过期");
        }
        try {
            redisTemplate.opsForValue().set(
                redisKey(payload.getChallengeId()),
                objectMapper.writeValueAsString(payload),
                Duration.ofSeconds(ttlSeconds)
            );
        } catch (Exception e) {
            throw new IllegalStateException("更新移动桥接挑战失败", e);
        }
    }

    private ValidatedReturnUrl validateReturnUrl(String rawReturnUrl) {
        String returnUrl = normalizeBlank(rawReturnUrl);
        if (returnUrl == null) {
            throw new IllegalArgumentException("returnUrl 不能为空");
        }

        URI uri;
        try {
            uri = URI.create(returnUrl);
        } catch (Exception e) {
            throw new IllegalArgumentException("returnUrl 格式不正确");
        }

        String scheme = normalizeBlank(uri.getScheme());
        String host = normalizeBlank(uri.getHost());
        if (scheme == null || host == null || !uri.isAbsolute()) {
            throw new IllegalArgumentException("returnUrl 必须是完整地址");
        }
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("returnUrl 仅支持 http 或 https");
        }

        String origin = scheme.toLowerCase(Locale.ROOT) + "://" + host.toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        if (port > 0) {
            origin = origin + ":" + port;
        }

        List<String> allowedOrigins = appProperties.getMobileBridge().getAllowedReturnOrigins();
        boolean allowed = allowedOrigins.stream()
            .map(this::normalizeOrigin)
            .filter(item -> item != null && !item.isBlank())
            .anyMatch(origin::equals);
        if (!allowed) {
            throw new IllegalArgumentException("returnUrl 不在允许的域名白名单中");
        }

        return new ValidatedReturnUrl(uri.toString(), origin);
    }

    private String rewriteReturnUrlChallengeId(String rawReturnUrl, String challengeId) {
        String returnUrl = normalizeBlank(rawReturnUrl);
        if (returnUrl == null) {
            return null;
        }
        try {
            URI uri = URI.create(returnUrl);
            return UriComponentsBuilder.fromUri(uri)
                .replaceQueryParam("mobileBridgeChallengeId", challengeId)
                .replaceQueryParam("mobileBridgeFallback")
                .build(true)
                .toUriString();
        } catch (Exception e) {
            return rawReturnUrl;
        }
    }

    private String buildAppLink(String challengeId, String returnUrl) {
        String origin = normalizeOrigin(appProperties.getMobileBridge().getAppLinkOrigin());
        if (origin == null) {
            origin = "https://auth.ksuser.cn";
        }
        return UriComponentsBuilder.fromUriString(origin)
            .path("/app/bridge-login")
            .queryParam("challengeId", challengeId)
            .queryParam("returnUrl", returnUrl)
            .build()
            .encode()
            .toUriString();
    }

    private String normalizeChallengeId(String rawChallengeId) {
        String challengeId = normalizeBlank(rawChallengeId);
        return challengeId == null ? null : challengeId;
    }

    private String normalizeStatus(String rawStatus) {
        String status = normalizeBlank(rawStatus);
        if (STATUS_APPROVED.equals(status) || STATUS_CANCELLED.equals(status)) {
            return status;
        }
        return STATUS_PENDING;
    }

    private String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeOrigin(String rawOrigin) {
        String origin = normalizeBlank(rawOrigin);
        if (origin == null) {
            return null;
        }
        try {
            URI uri = URI.create(origin);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            String normalized = uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getHost().toLowerCase(Locale.ROOT);
            if (uri.getPort() > 0) {
                normalized = normalized + ":" + uri.getPort();
            }
            return normalized;
        } catch (Exception e) {
            return null;
        }
    }

    private String redisKey(String challengeId) {
        return REDIS_PREFIX + challengeId;
    }

    private String generateChallengeId() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record ValidatedReturnUrl(String returnUrl, String returnOrigin) {
    }

    public static class MobileBridgePayload {
        private String challengeId;
        private String clientNonce;
        private String returnUrl;
        private String returnOrigin;
        private String status;
        private String transferCode;
        private String appLink;

        public String getChallengeId() {
            return challengeId;
        }

        public void setChallengeId(String challengeId) {
            this.challengeId = challengeId;
        }

        public String getClientNonce() {
            return clientNonce;
        }

        public void setClientNonce(String clientNonce) {
            this.clientNonce = clientNonce;
        }

        public String getReturnUrl() {
            return returnUrl;
        }

        public void setReturnUrl(String returnUrl) {
            this.returnUrl = returnUrl;
        }

        public String getReturnOrigin() {
            return returnOrigin;
        }

        public void setReturnOrigin(String returnOrigin) {
            this.returnOrigin = returnOrigin;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getTransferCode() {
            return transferCode;
        }

        public void setTransferCode(String transferCode) {
            this.transferCode = transferCode;
        }

        public String getAppLink() {
            return appLink;
        }

        public void setAppLink(String appLink) {
            this.appLink = appLink;
        }

        public MobileBridgePayload withAppLink(String value) {
            this.appLink = value;
            return this;
        }
    }
}
