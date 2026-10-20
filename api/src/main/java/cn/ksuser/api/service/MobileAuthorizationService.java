package cn.ksuser.api.service;

import cn.ksuser.api.result.ApiResultCode;

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

/** Immutable mobile consent. Browser login is issued only after an explicit choice on a QR request. */
@Service
public class MobileAuthorizationService {
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String PREFIX = "mobile:authorization:";
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final AppProperties properties;
    private final Oauth2PlatformService oauth;
    private final OfficialAuthorizationService official;
    private final QrChallengeService qr;
    private final SessionTransferService transfers;
    private final SecureRandom random = new SecureRandom();

    public MobileAuthorizationService(StringRedisTemplate redis, ObjectMapper mapper, AppProperties properties,
                                      Oauth2PlatformService oauth,
                                      OfficialAuthorizationService official, QrChallengeService qr, SessionTransferService transfers) {
        this.redis = redis; this.mapper = mapper; this.properties = properties; this.oauth = oauth;
        this.official = official; this.qr = qr; this.transfers = transfers;
    }
    public record CreateRequest(String mode, String returnOrigin, MobileAuthorizeRequest authorization) {}
    public record Created(String ticket, String secret, String appLink, int expiresInSeconds) {}
    public record QrCreated(String ticket, String secret, String qrText, int expiresInSeconds, Context application) {}
    public record Stored(String mode, String returnOrigin, String secret, MobileAuthorizeRequest authorization,
                         boolean qr, String qrChallengeId, String requesterIp, String requesterUserAgent) {
        public Stored(String mode, String returnOrigin, String secret, MobileAuthorizeRequest authorization) {
            this(mode, returnOrigin, secret, authorization, false, null, null, null);
        }
    }
    public record Context(String ticket, String appName, String logoUrl, String contactInfo, String redirectUri,
                          List<String> requestedScopes, boolean alreadyAuthorized, String existingGrantMode,
                          int expiresInSeconds, boolean qr, boolean officialApplication, boolean consoleApplication,
                          String webOrigin, String subjectName, String subjectVerificationType) {
        public Context(String ticket, String appName, String logoUrl, String contactInfo, String redirectUri,
                       List<String> requestedScopes, boolean alreadyAuthorized, String existingGrantMode,
                       int expiresInSeconds, boolean qr, boolean officialApplication, boolean consoleApplication,
                       String webOrigin) {
            this(ticket, appName, logoUrl, contactInfo, redirectUri, requestedScopes, alreadyAuthorized,
                existingGrantMode, expiresInSeconds, qr, officialApplication, consoleApplication, webOrigin, null, null);
        }
    }
    public record Decision(String ticket, boolean approve, String grantMode, Integer grantTtlSeconds, Boolean loginWeb) {
        public Decision(String ticket, boolean approve, String grantMode, Integer grantTtlSeconds) {
            this(ticket, approve, grantMode, grantTtlSeconds, false);
        }
    }
    public record BrowserRequest(String ticket, String secret) {}
    public record Result(String status, String redirectUrl, String webLoginTransferCode) {
        public Result(String status, String redirectUrl) { this(status, redirectUrl, null); }
    }
    public record Return(String returnUrl, boolean completed) {
        public Return(String returnUrl) { this(returnUrl, true); }
    }
    public record QrStatus(String status, int expiresInSeconds) {}

    public Created create(CreateRequest input) {
        validateInput(input);
        String origin = validateOrigin(input.returnOrigin());
        String serialized = encode(input.authorization());
        if (serialized.length() > 8192) throw invalid(ApiResultCode.MOBILE_AUTHORIZATION_002);
        context("", new Stored(input.mode(), origin, "", input.authorization()), null, 300);
        String ticket = opaque(), secret = opaque();
        redis.opsForValue().set(key(ticket), encode(new Stored(input.mode(), origin, secret, input.authorization())), TTL);
        // Only the ticket is handed to the app; the browser result secret is kept out of this link.
        return new Created(ticket, secret, "ksuserauth://authorize?ticket=" + ticket, 300);
    }
    public QrCreated createQr(CreateRequest input, String ip, String userAgent) {
        validateInput(input);
        String origin = validateOrigin(input.returnOrigin());
        String serialized = encode(input.authorization());
        if (serialized.length() > 8192) throw invalid(ApiResultCode.MOBILE_AUTHORIZATION_002);
        // Freeze the original request before any phone-controlled policy is added.
        MobileAuthorizeRequest authorization = decode(serialized, MobileAuthorizeRequest.class);
        context("", new Stored(input.mode(), origin, "", authorization), null, 120);
        String ticket = opaque(), secret = opaque();
        var challenge = qr.createApplicationAuthorizationChallenge(ticket, ip, userAgent);
        Stored stored = new Stored(input.mode(), origin, secret, authorization, true, challenge.getChallengeId(), ip, userAgent);
        redis.opsForValue().set(key(ticket), encode(stored), Duration.ofSeconds(120));
        return new QrCreated(ticket, secret, QrChallengeService.QR_TEXT_PREFIX + challenge.getApproveCode(), 120,
            context(ticket, stored, null, 120));
    }
    private void validateInput(CreateRequest input) {
        if (input == null || input.authorization() == null || input.mode() == null
            || !List.of("oauth", "official").contains(input.mode())) throw invalid(ApiResultCode.MOBILE_AUTHORIZATION_001);
    }
    public Context preview(String ticket, User user) {
        Stored stored = stored(ticket);
        if (redis.hasKey(key(ticket) + ":result")) throw expired();
        return context(ticket, stored, user, remaining(ticket));
    }
    private Context context(String ticket, Stored stored, User user, int seconds) {
        var a = stored.authorization();
        if (stored.mode().equals("official")) {
            var c = official.buildAuthorizeContext(user, officialRequest(a, null));
            return new Context(ticket, c.clientName(), c.logoUrl(), null, a.getRedirectUri(), c.requestedScopes(),
                user != null && !c.consentRequired(), "PERSISTENT", seconds, stored.qr(), true, c.consoleApplication(),
                stored.returnOrigin(), null, null);
        }
        var c = oauth.buildAuthorizeContext(user, a.getClientId(), a.getRedirectUri(), a.getResponseType(), a.getScope(),
            a.getState(), a.getCodeChallenge(), a.getCodeChallengeMethod());
        return new Context(ticket, c.getAppName(), c.getLogoUrl(), c.getContactInfo(), c.getRedirectUri(),
            c.getRequestedScopes(), c.isAlreadyAuthorized(), c.getExistingGrantMode(), seconds, stored.qr(), false,
            false, stored.returnOrigin(), c.getSubjectName(), c.getSubjectVerificationType());
    }
    public Return decide(Decision decision, User user) {
        if (decision == null || user == null) throw invalid(ApiResultCode.MOBILE_AUTHORIZATION_003);
        Stored stored = stored(decision.ticket());
        if (decision.approve() && !stored.mode().equals("official")) {
            String mode = AuthorizationGrantPolicy.normalizeGrantMode(decision.grantMode());
            AuthorizationGrantPolicy.resolveTtlSeconds(mode, decision.grantTtlSeconds());
        }
        Context context = context(decision.ticket(), stored, user, remaining(decision.ticket()));
        String lock = key(decision.ticket()) + ":decision";
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lock, user.getUuid(), TTL))) {
            if (user.getUuid().equals(redis.opsForValue().get(lock)) && redis.hasKey(key(decision.ticket()) + ":result")) return completed(decision.ticket(), stored);
            throw new Oauth2Exception(HttpStatus.CONFLICT, "invalid_request", ApiResultCode.MOBILE_AUTHORIZATION_007);
        }
        String redirect;
        var a = stored.authorization();
        if (decision.approve()) {
            a.setGrantMode(decision.grantMode()); a.setGrantTtlSeconds(decision.grantTtlSeconds());
            if (stored.mode().equals("official")) redirect = official.approveAuthorization(user, officialRequest(a, true)).get("redirectUrl");
            else {
                var request = new Oauth2AuthorizeRequest();
                request.setClientId(a.getClientId()); request.setRedirectUri(a.getRedirectUri());
                request.setResponseType(a.getResponseType()); request.setScope(a.getScope());
                request.setState(a.getState()); request.setGrantMode(a.getGrantMode());
                request.setCodeChallenge(a.getCodeChallenge()); request.setCodeChallengeMethod(a.getCodeChallengeMethod());
                request.setGrantTtlSeconds(a.getGrantTtlSeconds());
                redirect = oauth.approveAuthorization(user, request).getRedirectUrl();
            }
        } else {
            redirect = UriComponentsBuilder.fromUriString(context.redirectUri())
                .queryParam("error", "access_denied")
                .queryParam("errorCode", ApiResultCode.FORBIDDEN.name())
                .queryParamIfPresent("state", java.util.Optional.ofNullable(a.getState())
                    .map(value -> java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8)))
                .build(true).toUriString();
        }
        String webLoginCode = null;
        if (decision.approve() && stored.qr() && Boolean.TRUE.equals(decision.loginWeb())) {
            webLoginCode = transfers.createTransfer(user.getId(), SessionTransferService.TARGET_WEB,
                SessionTransferService.PURPOSE_BRIDGE_LOGIN, stored.requesterIp(), stored.requesterUserAgent()).getTransferCode();
        }
        redis.opsForValue().set(key(decision.ticket()) + ":result", encode(new Result("completed", redirect, webLoginCode)),
            Duration.ofSeconds(remaining(decision.ticket())));
        if (stored.qr()) {
            if (decision.approve()) qr.approveChallenge(stored.qrChallengeId(), user.getId(), java.util.Map.of("verified", true));
            else qr.rejectChallenge(stored.qrChallengeId(), user.getId(), java.util.Map.of("verified", false));
        }
        return completed(decision.ticket(), stored);
    }
    public QrStatus qrStatus(BrowserRequest input) {
        if (input == null) throw invalid(ApiResultCode.MOBILE_AUTHORIZATION_001);
        Stored stored = readStored(input.ticket());
        if (stored == null) return new QrStatus("expired", 0);
        checkSecret(stored, input.secret());
        if (!stored.qr()) throw invalid(ApiResultCode.MOBILE_AUTHORIZATION_001);
        var challenge = qr.getByChallengeId(stored.qrChallengeId());
        if (challenge == null) return new QrStatus("expired", 0);
        return new QrStatus(challenge.getStatus(), remaining(input.ticket()));
    }
    private Return completed(String ticket, Stored stored) {
        // A QR confirmation stays on the phone; its browser receipt and login code never go into a return link.
        return new Return(stored.qr() ? null : returnUrl(ticket, stored));
    }
    private OfficialAuthorizeRequest officialRequest(MobileAuthorizeRequest a, Boolean approved) {
        return new OfficialAuthorizeRequest(a.getClientId(), a.getRedirectUri(), a.getResponseType(), a.getScope(),
            a.getState(), a.getNonce(), a.getCodeChallenge(), a.getCodeChallengeMethod(), approved);
    }
    public Result consume(BrowserRequest input) {
        if (input == null) throw invalid(ApiResultCode.MOBILE_AUTHORIZATION_001);
        Stored stored = stored(input.ticket());
        checkSecret(stored, input.secret());
        String result = redis.opsForValue().getAndDelete(key(input.ticket()) + ":result");
        if (result == null) return new Result("pending", null);
        redis.delete(key(input.ticket()));
        return decode(result, Result.class);
    }
    public void cancel(BrowserRequest input) {
        if (input == null) throw invalid(ApiResultCode.MOBILE_AUTHORIZATION_001);
        Stored stored = readStored(input.ticket());
        if (stored == null) return;
        checkSecret(stored, input.secret());
        if (stored.qr()) {
            redis.delete(List.of(key(input.ticket()), key(input.ticket()) + ":result", key(input.ticket()) + ":decision"));
            return;
        }
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key(input.ticket()) + ":decision", "cancelled", TTL))) {
            throw new Oauth2Exception(HttpStatus.CONFLICT, "invalid_request", ApiResultCode.MOBILE_AUTHORIZATION_008);
        }
        redis.delete(key(input.ticket()));
    }
    private String returnUrl(String ticket, Stored stored) {
        return stored.returnOrigin() + "/app/authorize-return#ticket=" + ticket + "&secret=" + stored.secret();
    }
    private void checkSecret(Stored stored, String secret) {
        if (secret == null || !MessageDigest.isEqual(stored.secret().getBytes(java.nio.charset.StandardCharsets.UTF_8),
            secret.getBytes(java.nio.charset.StandardCharsets.UTF_8))) throw invalid(ApiResultCode.MOBILE_AUTHORIZATION_004);
    }
    private String validateOrigin(String origin) {
        try {
            URI uri = URI.create(origin);
            if (uri.getUserInfo() != null || uri.getHost() == null || uri.getQuery() != null || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty())
                || !List.of("https", "http").contains(uri.getScheme())
                || !properties.getMobileBridge().getAllowedReturnOrigins().contains(origin)) throw invalid(ApiResultCode.MOBILE_AUTHORIZATION_005);
            return origin;
        } catch (IllegalArgumentException | NullPointerException e) { throw invalid(ApiResultCode.MOBILE_AUTHORIZATION_005); }
    }
    private Stored stored(String ticket) {
        Stored value = readStored(ticket);
        if (value == null) throw expired();
        return value;
    }
    private Stored readStored(String ticket) {
        if (ticket == null || !ticket.matches("[A-Za-z0-9_-]{32}")) throw invalid(ApiResultCode.MOBILE_AUTHORIZATION_006);
        String raw = redis.opsForValue().get(key(ticket));
        if (raw == null) return null;
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
    private Oauth2Exception invalid(ApiResultCode message) { return new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_request", message); }
    private Oauth2Exception expired() { return new Oauth2Exception(HttpStatus.GONE, "invalid_request", ApiResultCode.MOBILE_AUTHORIZATION_009); }
}
