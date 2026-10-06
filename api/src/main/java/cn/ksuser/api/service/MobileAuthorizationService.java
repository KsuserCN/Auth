package cn.ksuser.api.service;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.dto.*;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.exception.Oauth2Exception;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Immutable authorization handoff; no browser login credentials are transferred. */
@Service
public class MobileAuthorizationService {
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String PREFIX = "mobile:authorization:";
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final AppProperties properties;
    private final Oauth2PlatformService oauth;
    private final SsoPlatformService sso;
    private final SecureRandom random = new SecureRandom();
    @Value("${app.sso.contact-info:}") private String ssoContactInfo = "";

    public MobileAuthorizationService(StringRedisTemplate redis, ObjectMapper mapper, AppProperties properties,
                                      Oauth2PlatformService oauth, SsoPlatformService sso) {
        this.redis = redis; this.mapper = mapper; this.properties = properties; this.oauth = oauth; this.sso = sso;
    }
    public record CreateRequest(String mode, String returnOrigin, SsoAuthorizeRequest authorization) {}
    public record Created(String ticket, String secret, String appLink, int expiresInSeconds) {}
    public record Stored(String mode, String returnOrigin, String secret, SsoAuthorizeRequest authorization) {}
    public record Context(String ticket, String appName, String logoUrl, String contactInfo, String redirectUri,
                          List<String> requestedScopes, boolean alreadyAuthorized, String existingGrantMode,
                          int expiresInSeconds) {}
    public record Decision(String ticket, boolean approve, String grantMode, Integer grantTtlSeconds) {}
    public record BrowserRequest(String ticket, String secret) {}
    public record Result(String status, String redirectUrl) {}
    public record Return(String returnUrl) {}

    public Created create(CreateRequest input) {
        if (input == null || input.authorization() == null || (input.mode() == null || !List.of("oauth", "sso").contains(input.mode()))) {
            throw invalid("授权请求不完整");
        }
        String origin = validateOrigin(input.returnOrigin());
        String serialized = encode(input.authorization());
        if (serialized.length() > 8192) throw invalid("授权请求过长");
        context("", new Stored(input.mode(), origin, "", input.authorization()), null, 300);
        String ticket = opaque(), secret = opaque();
        redis.opsForValue().set(key(ticket), encode(new Stored(input.mode(), origin, secret, input.authorization())), TTL);
        // Only the ticket is handed to the app; the browser result secret is kept out of this link.
        return new Created(ticket, secret, "ksuserauth://authorize?ticket=" + ticket, 300);
    }
    public Context preview(String ticket, User user) {
        Stored stored = stored(ticket);
        if (redis.hasKey(key(ticket) + ":result")) throw expired();
        return context(ticket, stored, user, remaining(ticket));
    }
    private Context context(String ticket, Stored stored, User user, int seconds) {
        var a = stored.authorization();
        if (stored.mode().equals("sso")) {
            var c = sso.buildAuthorizeContext(user, a.getClientId(), a.getRedirectUri(), a.getResponseType(),
                a.getScope(), a.getNonce(), a.getCodeChallenge(), a.getCodeChallengeMethod());
            return new Context(ticket, c.getClientName(), c.getLogoUrl(), ssoContactInfo, c.getRedirectUri(),
                c.getRequestedScopes(), c.isAlreadyAuthorized(), c.getExistingGrantMode(), seconds);
        }
        var c = oauth.buildAuthorizeContext(user, a.getClientId(), a.getRedirectUri(), a.getResponseType(), a.getScope());
        return new Context(ticket, c.getAppName(), c.getLogoUrl(), c.getContactInfo(), c.getRedirectUri(),
            c.getRequestedScopes(), c.isAlreadyAuthorized(), c.getExistingGrantMode(), seconds);
    }
    public Return decide(Decision decision, User user) {
        if (decision == null || user == null) throw invalid("请先登录后授权");
        Stored stored = stored(decision.ticket());
        if (decision.approve()) {
            String mode = AuthorizationGrantPolicy.normalizeGrantMode(decision.grantMode());
            AuthorizationGrantPolicy.resolveTtlSeconds(mode, decision.grantTtlSeconds());
        }
        String lock = key(decision.ticket()) + ":decision";
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lock, user.getUuid(), TTL))) {
            if (user.getUuid().equals(redis.opsForValue().get(lock)) && redis.hasKey(key(decision.ticket()) + ":result")) return new Return(returnUrl(decision.ticket(), stored));
            throw new Oauth2Exception(HttpStatus.CONFLICT, "invalid_request", "请求正在处理中，请稍后重试");
        }
        Context context = context(decision.ticket(), stored, user, remaining(decision.ticket()));
        String redirect;
        var a = stored.authorization();
        if (decision.approve()) {
            a.setGrantMode(decision.grantMode()); a.setGrantTtlSeconds(decision.grantTtlSeconds());
            if (stored.mode().equals("sso")) redirect = sso.approveAuthorization(user, a).getRedirectUrl();
            else {
                var request = new Oauth2AuthorizeRequest();
                request.setClientId(a.getClientId()); request.setRedirectUri(a.getRedirectUri());
                request.setResponseType(a.getResponseType()); request.setScope(a.getScope());
                request.setState(a.getState()); request.setGrantMode(a.getGrantMode());
                request.setGrantTtlSeconds(a.getGrantTtlSeconds());
                redirect = oauth.approveAuthorization(user, request).getRedirectUrl();
            }
        } else {
            redirect = UriComponentsBuilder.fromUriString(context.redirectUri())
                .queryParam("error", "access_denied")
                .queryParamIfPresent("state", java.util.Optional.ofNullable(a.getState())
                    .map(value -> java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8)))
                .build(true).toUriString();
        }
        redis.opsForValue().set(key(decision.ticket()) + ":result", encode(new Result("completed", redirect)),
            Duration.ofSeconds(remaining(decision.ticket())));
        return new Return(returnUrl(decision.ticket(), stored));
    }
    public Result consume(BrowserRequest input) {
        if (input == null) throw invalid("授权请求不完整");
        Stored stored = stored(input.ticket());
        checkSecret(stored, input.secret());
        String result = redis.opsForValue().getAndDelete(key(input.ticket()) + ":result");
        if (result == null) return new Result("pending", null);
        redis.delete(key(input.ticket()));
        return decode(result, Result.class);
    }
    public void cancel(BrowserRequest input) {
        if (input == null) throw invalid("授权请求不完整");
        Stored stored = stored(input.ticket()); checkSecret(stored, input.secret());
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key(input.ticket()) + ":decision", "cancelled", TTL))) {
            throw new Oauth2Exception(HttpStatus.CONFLICT, "invalid_request", "授权已提交，请等待结果");
        }
        redis.delete(key(input.ticket()));
    }
    private String returnUrl(String ticket, Stored stored) {
        return stored.returnOrigin() + "/app/authorize-return#ticket=" + ticket + "&secret=" + stored.secret();
    }
    private void checkSecret(Stored stored, String secret) {
        if (secret == null || !MessageDigest.isEqual(stored.secret().getBytes(java.nio.charset.StandardCharsets.UTF_8),
            secret.getBytes(java.nio.charset.StandardCharsets.UTF_8))) throw invalid("授权请求校验失败");
    }
    private String validateOrigin(String origin) {
        try {
            URI uri = URI.create(origin);
            if (uri.getUserInfo() != null || uri.getHost() == null || uri.getQuery() != null || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty())
                || !List.of("https", "http").contains(uri.getScheme())
                || !properties.getMobileBridge().getAllowedReturnOrigins().contains(origin)) throw invalid("网页来源不受信任");
            return origin;
        } catch (IllegalArgumentException | NullPointerException e) { throw invalid("网页来源不受信任"); }
    }
    private Stored stored(String ticket) {
        if (ticket == null || !ticket.matches("[A-Za-z0-9_-]{32}")) throw invalid("授权请求无效");
        String raw = redis.opsForValue().get(key(ticket));
        if (raw == null) throw expired();
        return decode(raw, Stored.class);
    }
    private int remaining(String ticket) {
        Long ttl = redis.getExpire(key(ticket), TimeUnit.SECONDS);
        if (ttl == null || ttl <= 0) throw expired();
        return ttl.intValue();
    }
    private String key(String ticket) { return PREFIX + ticket; }
    private String opaque() { byte[] bytes = new byte[24]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private String encode(Object value) { try { return mapper.writeValueAsString(value); } catch (JsonProcessingException e) { throw new IllegalStateException(e); } }
    private <T> T decode(String value, Class<T> type) { try { return mapper.readValue(value, type); } catch (JsonProcessingException e) { throw new IllegalStateException(e); } }
    private Oauth2Exception invalid(String message) { return new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_request", message); }
    private Oauth2Exception expired() { return new Oauth2Exception(HttpStatus.GONE, "invalid_request", "授权请求已完成或过期，请返回网页重试"); }
}
