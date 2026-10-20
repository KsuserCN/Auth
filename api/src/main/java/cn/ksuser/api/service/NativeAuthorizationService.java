package cn.ksuser.api.service;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.dto.MobileAuthorizeRequest;
import cn.ksuser.api.dto.Oauth2AuthorizeRequest;
import cn.ksuser.api.dto.OfficialAuthorizeRequest;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.result.ApiResultCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Native codes are delivered exclusively to a registered, app-claimed HTTPS callback. */
@Service
public class NativeAuthorizationService {
    private static final Logger log = LoggerFactory.getLogger(NativeAuthorizationService.class);
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String PREFIX = "native:authorization:";
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final AppProperties properties;
    private final Oauth2PlatformService oauth;
    private final OfficialAuthorizationService official;
    private MobileAppPlatformService platforms;
    private final SecureRandom random = new SecureRandom();

    // Claims and cancellations compete in Redis, including across API instances. Neither can revive an expired request.
    static final DefaultRedisScript<Long> CLAIM = new DefaultRedisScript<>("""
        local ttl = redis.call('PTTL', KEYS[1])
        if ttl <= 0 then return -1 end
        local old = redis.call('GET', KEYS[2])
        if old then
            if old == ARGV[1] then return 0 end
            return -2
        end
        redis.call('SET', KEYS[2], ARGV[1], 'PX', ttl)
        return 1
        """, Long.class);
    static final DefaultRedisScript<Long> PUBLISH = new DefaultRedisScript<>("""
        local ttl = redis.call('PTTL', KEYS[1])
        if ttl <= 0 or redis.call('GET', KEYS[2]) ~= ARGV[1] then return 0 end
        if redis.call('EXISTS', KEYS[3]) == 1 then return 0 end
        redis.call('SET', KEYS[3], ARGV[2], 'PX', ttl)
        return 1
        """, Long.class);
    static final DefaultRedisScript<Long> CANCEL = new DefaultRedisScript<>("""
        local ttl = redis.call('PTTL', KEYS[1])
        if ttl <= 0 then return 0 end
        local old = redis.call('GET', KEYS[2])
        if old == 'cancelled' then return 1 end
        if old then return 2 end
        redis.call('SET', KEYS[2], 'cancelled', 'PX', ttl)
        return 1
        """, Long.class);
    static final DefaultRedisScript<Long> QUOTA = new DefaultRedisScript<>("""
        for i = 1, 2 do
            local count = tonumber(redis.call('GET', KEYS[i]) or '0')
            if count >= tonumber(ARGV[i]) then return 0 end
        end
        for i = 1, 2 do
            redis.call('INCR', KEYS[i])
            if redis.call('TTL', KEYS[i]) < 0 then redis.call('EXPIRE', KEYS[i], 60) end
        end
        return 1
        """, Long.class);

    public NativeAuthorizationService(StringRedisTemplate redis, ObjectMapper mapper, AppProperties properties,
                                      Oauth2PlatformService oauth, OfficialAuthorizationService official) {
        this.redis = redis; this.mapper = mapper; this.properties = properties; this.oauth = oauth; this.official = official;
    }

    @Autowired
    public void setMobileAppPlatformService(MobileAppPlatformService platforms) { this.platforms = platforms; }

    public record CreateRequest(String mode, String platform, MobileAuthorizeRequest authorization) { }
    public record Created(String ticket, String cancelSecret, String appLink, String webAuthorizationUrl, int expiresInSeconds) { }
    public record Decision(String ticket, boolean approve, String grantMode, Integer grantTtlSeconds) { }
    public record Return(String returnUrl, boolean completed) { }
    public record CancelRequest(String ticket, String cancelSecret) { }
    public record Cancelled(String status) { }
    public record Context(String ticket, String appName, String logoUrl, String contactInfo, String redirectUri,
                          List<String> requestedScopes, boolean alreadyAuthorized, String existingGrantMode,
                          int expiresInSeconds, boolean officialApplication, boolean consoleApplication,
                          String subjectName, String subjectVerificationType, boolean nativeAuthorization) { }
    record Stored(String mode, String platform, String platformRevision, String cancelSecretHash, MobileAuthorizeRequest authorization) { }
    record Claim(String userUuid, boolean approve, String grantMode, Integer grantTtlSeconds) { }
    record Outcome(String returnUrl, boolean failed) { }

    public Created create(CreateRequest input, String ip) {
        if (input == null || input.authorization() == null || !"ios".equals(input.platform()) || !List.of("oauth", "official").contains(input.mode() == null ? "" : input.mode())) throw invalid();
        var a = input.authorization();
        checkEnabled(a.getClientId());
        String origin = appLinkOrigin();
        validateAuthorization(a, origin);
        String serialized = encode(a);
        if (serialized.length() > 8192) throw invalid();
        // Take a deep copy so no caller-controlled object can mutate a frozen request.
        String revision = "oauth".equals(input.mode()) ? platforms.nativeRevision(a.getClientId(), input.platform(), a.getRedirectUri()) : null;
        Stored frozen = new Stored(input.mode(), input.platform(), revision, "", decode(serialized, MobileAuthorizeRequest.class));
        context("", frozen, null, 300);
        acquireQuota(ip, a.getClientId());
        String ticket = opaque(), cancelSecret = opaque();
        redis.opsForValue().set(key(ticket), encode(new Stored(input.mode(), input.platform(), revision, hash(cancelSecret), frozen.authorization())), TTL);
        return new Created(ticket, cancelSecret, origin + "/app/sdk-authorize?ticket=" + ticket,
            origin + "/app/sdk-authorize/web?ticket=" + ticket, 300);
    }

    public Context preview(String ticket, User user) {
        requireUser(user);
        Stored stored = stored(ticket);
        checkEnabled(stored.authorization().getClientId());
        if ("cancelled".equals(redis.opsForValue().get(key(ticket) + ":decision"))) throw expired();
        return context(ticket, stored, user, remaining(ticket));
    }

    public Return decide(Decision decision, User user) {
        requireUser(user);
        if (decision == null) throw invalid();
        Stored stored = stored(decision.ticket());
        checkEnabled(stored.authorization().getClientId());
        String mode = "PERSISTENT";
        Integer ttl = null;
        if (decision.approve() && "oauth".equals(stored.mode())) {
            mode = AuthorizationGrantPolicy.normalizeGrantMode(decision.grantMode());
            ttl = AuthorizationGrantPolicy.resolveTtlSeconds(mode, decision.grantTtlSeconds());
        }
        context(decision.ticket(), stored, user, remaining(decision.ticket()));
        String claim = encode(new Claim(user.getUuid(), decision.approve(), mode, ttl));
        List<String> keys = List.of(key(decision.ticket()), key(decision.ticket()) + ":decision", key(decision.ticket()) + ":result");
        Long acquired = redis.execute(CLAIM, keys.subList(0, 2), claim);
        if (acquired == null) throw unavailable();
        if (acquired == -1) throw expired();
        if (acquired == -2) throw conflict();
        if (acquired == 0) {
            String result = redis.opsForValue().get(keys.get(2));
            if (result == null) throw conflict();
            Outcome outcome = decode(result, Outcome.class);
            if (outcome.failed()) throw failed();
            return new Return(outcome.returnUrl(), true);
        }
        String redirect;
        try {
            var a = stored.authorization();
            if (!decision.approve()) {
                redirect = UriComponentsBuilder.fromUriString(a.getRedirectUri()).queryParam("error", "access_denied")
                    .queryParam("state", a.getState()).build(true).toUriString();
            } else if ("official".equals(stored.mode())) {
                redirect = official.approveAuthorization(user, officialRequest(a, true)).get("redirectUrl");
            } else {
                Oauth2AuthorizeRequest request = new Oauth2AuthorizeRequest();
                request.setClientId(a.getClientId()); request.setRedirectUri(a.getRedirectUri());
                request.setResponseType(a.getResponseType()); request.setScope(a.getScope()); request.setState(a.getState());
                request.setCodeChallenge(a.getCodeChallenge()); request.setCodeChallengeMethod(a.getCodeChallengeMethod());
                request.setGrantMode(mode); request.setGrantTtlSeconds(ttl);
                redirect = oauth.approveAuthorization(user, request).getRedirectUrl();
            }
            validateCallback(redirect, stored.authorization(), decision.approve());
            // Issuance crosses Redis/database boundaries; do not publish a callback if its platform
            // configuration changed while the decision was being processed.
            context(decision.ticket(), stored, user, remaining(decision.ticket()));
            Long published = redis.execute(PUBLISH, keys, claim, encode(new Outcome(redirect, false)));
            if (!Long.valueOf(1).equals(published)) throw expired();
        } catch (RuntimeException ex) {
            // A cross-store/transport failure can leave issuance uncertain. Never release the claim and issue a second code.
            try { redis.execute(PUBLISH, keys, claim, encode(new Outcome(null, true))); }
            catch (RuntimeException ignored) { /* The exclusive claim remains until the original deadline. */ }
            throw ex;
        }
        log.info("Native authorization decided mode={} outcome={}", stored.mode(), decision.approve() ? "approved" : "denied");
        return new Return(redirect, true);
    }

    public Cancelled cancel(CancelRequest input) {
        if (input == null) throw invalid();
        validateTicket(input.ticket());
        String raw = redis.opsForValue().get(key(input.ticket()));
        if (raw == null) return new Cancelled("expired");
        Stored stored = decode(raw, Stored.class);
        if (input.cancelSecret() == null || !input.cancelSecret().matches("[A-Za-z0-9_-]{32}")
            || !MessageDigest.isEqual(stored.cancelSecretHash().getBytes(StandardCharsets.US_ASCII), hash(input.cancelSecret()).getBytes(StandardCharsets.US_ASCII))) {
            throw failure(HttpStatus.FORBIDDEN, "access_denied", ApiResultCode.NATIVE_AUTHORIZATION_CANCEL_INVALID);
        }
        Long result = redis.execute(CANCEL, List.of(key(input.ticket()), key(input.ticket()) + ":decision"));
        if (result == null) throw unavailable();
        return new Cancelled(result == 0 ? "expired" : result == 1 ? "cancelled" : "decided");
    }

    private Context context(String ticket, Stored stored, User user, int seconds) {
        var a = stored.authorization();
        if (!"ios".equals(stored.platform())) throw invalid();
        if ("official".equals(stored.mode())) {
            official.validateNativeClient(a.getClientId());
            var c = official.buildAuthorizeContext(user, officialRequest(a, null));
            return new Context(ticket, c.clientName(), c.logoUrl(), null, a.getRedirectUri(), c.requestedScopes(),
                user != null && !c.consentRequired(), "PERSISTENT", seconds, true, c.consoleApplication(), null, null, true);
        }
        oauth.validateNativeClient(a.getClientId(), stored.platform(), a.getRedirectUri());
        if (stored.platformRevision() == null || !stored.platformRevision().equals(platforms.nativeRevision(a.getClientId(), stored.platform(), a.getRedirectUri()))) throw invalid();
        var c = oauth.buildAuthorizeContext(user, a.getClientId(), a.getRedirectUri(), a.getResponseType(), a.getScope(),
            a.getState(), a.getCodeChallenge(), a.getCodeChallengeMethod());
        return new Context(ticket, c.getAppName(), c.getLogoUrl(), c.getContactInfo(), c.getRedirectUri(), c.getRequestedScopes(),
            c.isAlreadyAuthorized(), c.getExistingGrantMode(), seconds, false, false, c.getSubjectName(), c.getSubjectVerificationType(), true);
    }

    private void validateAuthorization(MobileAuthorizeRequest a, String appOrigin) {
        if (a.getClientId() == null || a.getClientId().isBlank() || a.getClientId().length() > 128 || !"code".equals(a.getResponseType())
            || a.getState() == null || !a.getState().matches("[A-Za-z0-9_-]{22,128}")
            || a.getCodeChallenge() == null || !a.getCodeChallenge().matches("[A-Za-z0-9_-]{43}")
            || !"S256".equals(a.getCodeChallengeMethod())) throw invalid();
        try {
            URI uri = URI.create(a.getRedirectUri());
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                || uri.getRawPath() == null || !uri.getRawPath().startsWith("/") || !uri.normalize().equals(uri)
                || uri.getHost().equalsIgnoreCase(URI.create(appOrigin).getHost())) throw invalid();
        } catch (IllegalArgumentException | NullPointerException ex) { throw invalid(); }
    }

    private void validateCallback(String value, MobileAuthorizeRequest a, boolean approved) {
        try {
            URI callback = URI.create(value), expected = URI.create(a.getRedirectUri());
            if (!expected.getScheme().equals(callback.getScheme()) || !expected.getRawAuthority().equals(callback.getRawAuthority())
                || !expected.getRawPath().equals(callback.getRawPath()) || callback.getRawFragment() != null || callback.getRawQuery() == null) throw invalid();
            List<String> parts = Arrays.asList(callback.getRawQuery().split("&"));
            String field = approved ? "code" : "error";
            if (parts.size() != 2 || countField(parts, "state") != 1 || countField(parts, field) != 1) throw invalid();
            String state = fieldValue(parts, "state"), result = fieldValue(parts, field);
            if (!a.getState().equals(state) || result.isBlank() || (!approved && !"access_denied".equals(result))) throw invalid();
        } catch (IllegalArgumentException | NullPointerException ex) { throw invalid(); }
    }
    private static long countField(List<String> query, String name) { return query.stream().filter(part -> part.startsWith(name + "=")).count(); }
    private static String fieldValue(List<String> query, String name) {
        return URLDecoder.decode(query.stream().filter(part -> part.startsWith(name + "=")).findFirst().orElseThrow().substring(name.length() + 1), StandardCharsets.UTF_8);
    }

    private void checkEnabled(String clientId) {
        var config = properties.getNativeAuthorization();
        List<String> allowed = config.getAllowedClientIds().stream().filter(value -> !value.isBlank()).toList();
        if (!config.isEnabled() || (!allowed.isEmpty() && !allowed.contains(clientId))) throw unavailable();
    }
    private void acquireQuota(String ip, String clientId) {
        var config = properties.getNativeAuthorization();
        if (config.getCreateIpPerMinute() <= 0 || config.getCreateClientPerMinute() <= 0) throw unavailable();
        Long result = redis.execute(QUOTA, List.of(PREFIX + "quota:ip:" + hash(ip == null ? "unknown" : ip), PREFIX + "quota:client:" + hash(clientId)),
            Integer.toString(config.getCreateIpPerMinute()), Integer.toString(config.getCreateClientPerMinute()));
        if (result == null) throw unavailable();
        if (result != 1) throw failure(HttpStatus.TOO_MANY_REQUESTS, "slow_down", ApiResultCode.NATIVE_AUTHORIZATION_RATE_LIMITED);
    }
    private String appLinkOrigin() {
        try {
            String configured = properties.getMobileBridge().getAppLinkOrigin();
            URI uri = URI.create(configured);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null || !List.of("", "/").contains(uri.getPath()) || (uri.getPort() != -1 && uri.getPort() != 443)) throw unavailable();
            return configured.endsWith("/") ? configured.substring(0, configured.length() - 1) : configured;
        } catch (IllegalArgumentException | NullPointerException ex) { throw unavailable(); }
    }
    private Stored stored(String ticket) {
        validateTicket(ticket);
        String raw = redis.opsForValue().get(key(ticket));
        if (raw == null || "cancelled".equals(redis.opsForValue().get(key(ticket) + ":decision"))) throw expired();
        return decode(raw, Stored.class);
    }
    private int remaining(String ticket) {
        Long ttl = redis.getExpire(key(ticket), TimeUnit.SECONDS);
        if (ttl == null || ttl <= 0) throw expired();
        return ttl.intValue();
    }
    private static void validateTicket(String ticket) { if (ticket == null || !ticket.matches("[A-Za-z0-9_-]{32}")) throw invalid(); }
    private static void requireUser(User user) { if (user == null || user.getId() == null || user.getUuid() == null) throw failure(HttpStatus.UNAUTHORIZED, "login_required", ApiResultCode.UNAUTHENTICATED); }
    private static String key(String ticket) { return PREFIX + ticket; }
    private String opaque() { byte[] bytes = new byte[24]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static String hash(String value) { return OfficialAuthorizationService.hashOpaque(value); }
    private String encode(Object value) {
        try { return mapper.writeValueAsString(value); } catch (JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }
    private <T> T decode(String value, Class<T> type) {
        try { return mapper.readValue(value, type); } catch (JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }
    private static OfficialAuthorizeRequest officialRequest(MobileAuthorizeRequest a, Boolean approve) {
        return new OfficialAuthorizeRequest(a.getClientId(), a.getRedirectUri(), a.getResponseType(), a.getScope(), a.getState(),
            a.getNonce(), a.getCodeChallenge(), a.getCodeChallengeMethod(), approve);
    }
    private static Oauth2Exception failure(HttpStatus status, String error, ApiResultCode code) { return new Oauth2Exception(status, error, code); }
    private static Oauth2Exception invalid() { return failure(HttpStatus.BAD_REQUEST, "invalid_request", ApiResultCode.NATIVE_AUTHORIZATION_INVALID); }
    private static Oauth2Exception unavailable() { return failure(HttpStatus.SERVICE_UNAVAILABLE, "temporarily_unavailable", ApiResultCode.NATIVE_AUTHORIZATION_UNAVAILABLE); }
    private static Oauth2Exception expired() { return failure(HttpStatus.GONE, "invalid_request", ApiResultCode.NATIVE_AUTHORIZATION_EXPIRED); }
    private static Oauth2Exception conflict() { return failure(HttpStatus.CONFLICT, "invalid_request", ApiResultCode.NATIVE_AUTHORIZATION_CONFLICT); }
    private static Oauth2Exception failed() { return failure(HttpStatus.CONFLICT, "invalid_request", ApiResultCode.NATIVE_AUTHORIZATION_FAILED); }
}
