package cn.ksuser.api.service;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.dto.QrChallengeInitResponse;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.repository.UserRepository;
import cn.ksuser.api.result.ApiResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/** Independent browser sessions for Console. Passport bearer credentials are never accepted here. */
@Service
public class ConsoleSessionService {
    public static final String SESSION_COOKIE = "consoleSession";
    public static final String LOGIN_COOKIE = "consoleLogin";
    public static final String QR_LOGIN_COOKIE = "consoleQrLogin";
    private static final Duration FLOW_TTL = Duration.ofMinutes(5);
    private static final Duration SESSION_TTL = Duration.ofHours(8);
    private static final Duration FRESH_TTL = Duration.ofMinutes(15);
    private static final String PREFIX = "console:";
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final UserRepository users;
    private final OfficialAuthorizationService official;
    private final QrChallengeService qr;
    private final AppProperties properties;
    private final String consoleOrigin;
    private final String passportOrigin;
    private final SecureRandom random = new SecureRandom();

    public ConsoleSessionService(StringRedisTemplate redis, ObjectMapper json, UserRepository users,
                                 OfficialAuthorizationService official, QrChallengeService qr, AppProperties properties,
                                 @Value("${app.console.origin:}") String consoleOrigin,
                                 @Value("${app.console.passport-origin:}") String passportOrigin) {
        this.redis = redis;
        this.json = json;
        this.users = users;
        this.official = official;
        this.qr = qr;
        this.properties = properties;
        this.consoleOrigin = trimOrigin(consoleOrigin.isBlank()
            ? (properties.isDebug() ? "http://localhost:5174" : "https://console.ksuser.cn") : consoleOrigin);
        this.passportOrigin = trimOrigin(passportOrigin.isBlank()
            ? (properties.isDebug() ? "http://localhost:5173" : "https://auth.ksuser.cn") : passportOrigin);
    }

    public Map<String, String> startLogin(HttpServletResponse response) {
        String browser = randomToken();
        String state = randomToken();
        String nonce = randomToken();
        String verifier = randomToken();
        LoginFlow flow = new LoginFlow(state, nonce, verifier, callbackUri(), now() + FLOW_TTL.toMillis());
        store("login:" + hash(browser), flow, FLOW_TTL);
        cookie(response, LOGIN_COOKIE, browser, FLOW_TTL);
        String authorizationUrl = UriComponentsBuilder.fromUriString(passportOrigin + "/official/authorize")
            .queryParam("client_id", OfficialAuthorizationService.CONSOLE_CLIENT_ID)
            .queryParam("redirect_uri", flow.redirectUri()).queryParam("response_type", "code")
            .queryParam("scope", "openid profile email").queryParam("state", state).queryParam("nonce", nonce)
            .queryParam("code_challenge", PkceUtil.toS256CodeChallenge(verifier))
            .queryParam("code_challenge_method", "S256").build().encode().toUriString();
        return Map.of("authorizationUrl", authorizationUrl);
    }

    public SessionView exchangeLogin(String code, String state, HttpServletRequest request, HttpServletResponse response) {
        String browser = readCookie(request, LOGIN_COOKIE);
        if (browser == null || code == null || code.isBlank()) throw flowFailure();
        String key = "login:" + hash(browser);
        LoginFlow flow = read(key, LoginFlow.class);
        if (flow == null || flow.expiresAt() <= now() || !equal(flow.state(), state)) throw flowFailure();
        flow = consume(key, LoginFlow.class);
        if (flow == null || !equal(flow.state(), state)) throw flowFailure();
        cookie(response, LOGIN_COOKIE, "", Duration.ZERO);
        OfficialAuthorizationService.ConsoleExchange exchange = official.exchangeForConsole(
            code, flow.redirectUri(), flow.verifier(), flow.nonce());
        return createSession(exchange.user(), exchange.authorizationId(), request, response);
    }

    public QrChallengeInitResponse startQrLogin(HttpServletRequest request, HttpServletResponse response,
                                               String ip, String userAgent) {
        official.validateConsoleLogin(callbackUri());
        invalidateQrLogin(request);
        QrChallengeService.QrChallengePayload challenge = qr.createChallenge(
            QrChallengeService.ChallengeType.CONSOLE_LOGIN, null, null, "console", ip, userAgent);
        Duration ttl = Duration.ofSeconds(QrChallengeService.DEFAULT_TTL_SECONDS);
        String browser = randomToken();
        String browserHash = hash(browser);
        QrLoginFlow flow = new QrLoginFlow(challenge.getChallengeId(), challenge.getPollToken(), now() + ttl.toMillis());
        store("qr-login:" + browserHash, flow, ttl);
        store("qr-approval:" + flow.challengeId(), new QrLoginApproval(browserHash, flow.expiresAt()), ttl);
        cookie(response, QR_LOGIN_COOKIE, browser, ttl);
        return new QrChallengeInitResponse(flow.challengeId(), flow.pollToken(),
            QrChallengeService.QR_TEXT_PREFIX + challenge.getApproveCode(), ttl.toSeconds());
    }

    /** Called only by the authenticated mobile QR approval endpoint. */
    public void approveQrLogin(QrChallengeService.QrChallengePayload challenge, User user) {
        requireSuperAdmin(user);
        if (!QrChallengeService.ChallengeType.CONSOLE_LOGIN.value().equals(challenge.getType())
            || !QrChallengeService.ChallengeStatus.PENDING.value().equals(challenge.getStatus())) throw flowFailure();
        String key = "qr-approval:" + challenge.getChallengeId();
        QrLoginApproval approval = read(key, QrLoginApproval.class);
        if (approval == null || approval.expiresAt() <= now()) throw flowFailure();
        QrLoginFlow flow = read("qr-login:" + approval.browserHash(), QrLoginFlow.class);
        if (flow == null || !equal(flow.challengeId(), challenge.getChallengeId()) || flow.expiresAt() <= now()) throw flowFailure();
        // Claim approval once, so simultaneous scans cannot replace the confirmed account.
        approval = consume(key, QrLoginApproval.class);
        if (approval == null || approval.expiresAt() <= now()) throw flowFailure();
        OfficialAuthorizationService.ConsoleExchange grant = official.approveConsoleLogin(user, callbackUri());
        store("qr-grant:" + challenge.getChallengeId(), new QrLoginGrant(grant.user().getUuid(), grant.authorizationId()),
            Duration.ofMillis(Math.max(1, approval.expiresAt() - now())));
    }

    public QrLoginStatus qrLoginStatus(String challengeId, String pollToken, HttpServletRequest request) {
        QrLoginFlow flow = qrLoginFlow(challengeId, pollToken, request);
        if (flow == null) return new QrLoginStatus("expired", 0);
        QrChallengeService.QrChallengePayload challenge = qr.getByChallengeId(flow.challengeId());
        if (challenge == null) return new QrLoginStatus("expired", 0);
        return new QrLoginStatus(challenge.getStatus(), Math.max(0, (flow.expiresAt() - now() + 999) / 1000));
    }

    public SessionView exchangeQrLogin(String challengeId, String pollToken, HttpServletRequest request,
                                       HttpServletResponse response) {
        QrLoginFlow flow = qrLoginFlow(challengeId, pollToken, request);
        if (flow == null) throw flowFailure();
        QrChallengeService.QrChallengePayload challenge = qr.getByChallengeId(flow.challengeId());
        if (challenge == null || !QrChallengeService.ChallengeStatus.APPROVED.value().equals(challenge.getStatus())) throw flowFailure();
        String browser = readCookie(request, QR_LOGIN_COOKIE);
        flow = consume("qr-login:" + hash(browser), QrLoginFlow.class);
        if (flow == null || flow.expiresAt() <= now() || !equal(flow.challengeId(), challengeId)
            || !equal(flow.pollToken(), pollToken)) throw flowFailure();
        QrLoginGrant grant = consume("qr-grant:" + flow.challengeId(), QrLoginGrant.class);
        cookie(response, QR_LOGIN_COOKIE, "", Duration.ZERO);
        if (grant == null) throw flowFailure();
        User user = users.findByUuid(grant.userUuid()).orElseThrow(ConsoleSessionService::sessionFailure);
        return createSession(user, grant.authorizationId(), request, response);
    }

    public void cancelQrLogin(HttpServletRequest request, HttpServletResponse response) {
        cancelQrLogin(null, request, response);
    }

    public void cancelQrLogin(String challengeId, HttpServletRequest request, HttpServletResponse response) {
        String browser = readCookie(request, QR_LOGIN_COOKIE);
        if (challengeId != null && browser != null) {
            QrLoginFlow flow = read("qr-login:" + hash(browser), QrLoginFlow.class);
            // A late cancellation for an old tab must not cancel a newer login request.
            if (flow != null && !equal(flow.challengeId(), challengeId)) return;
        }
        invalidateQrLogin(request);
        // Clearing a cookie in a delayed response could overwrite a newer tab's cookie.
        if (challengeId == null) cookie(response, QR_LOGIN_COOKIE, "", Duration.ZERO);
    }

    private void invalidateQrLogin(HttpServletRequest request) {
        String browser = readCookie(request, QR_LOGIN_COOKIE);
        if (browser == null) return;
        QrLoginFlow flow = consume("qr-login:" + hash(browser), QrLoginFlow.class);
        if (flow != null) redis.delete(java.util.List.of(PREFIX + "qr-approval:" + flow.challengeId(),
            PREFIX + "qr-grant:" + flow.challengeId()));
    }

    private QrLoginFlow qrLoginFlow(String challengeId, String pollToken, HttpServletRequest request) {
        String browser = readCookie(request, QR_LOGIN_COOKIE);
        if (browser == null || challengeId == null || pollToken == null) throw flowFailure();
        QrLoginFlow flow = read("qr-login:" + hash(browser), QrLoginFlow.class);
        if (flow == null || flow.expiresAt() <= now()) return null;
        if (!equal(flow.challengeId(), challengeId) || !equal(flow.pollToken(), pollToken)) throw flowFailure();
        return flow;
    }

    private SessionView createSession(User user, Long authorizationId, HttpServletRequest request, HttpServletResponse response) {
        requireSuperAdmin(user);
        if (!official.isConsoleAuthorizationActive(user.getUuid(), authorizationId)) throw accessFailure();
        String previous = readCookie(request, SESSION_COOKIE);
        if (previous != null) deleteSession(hash(previous));
        String token = randomToken();
        String sessionId = hash(token);
        SessionData data = new SessionData(user.getUuid(), authorizationId, now() + SESSION_TTL.toMillis());
        store("session:" + sessionId, data, SESSION_TTL);
        cookie(response, SESSION_COOKIE, token, SESSION_TTL);
        return sessionView(new ConsolePrincipal(sessionId, user.getUuid(), data.authorizationId(), data.expiresAt(), 0));
    }

    public ConsolePrincipal authenticate(HttpServletRequest request) {
        String token = readCookie(request, SESSION_COOKIE);
        if (token == null) throw sessionFailure();
        return authenticateSession(hash(token));
    }

    private ConsolePrincipal authenticateSession(String sessionId) {
        SessionData session = read("session:" + sessionId, SessionData.class);
        if (session == null || session.expiresAt() <= now()) throw sessionFailure();
        User user = users.findByUuid(session.userUuid()).orElseThrow(ConsoleSessionService::sessionFailure);
        requireSuperAdmin(user);
        if (!official.isConsoleAuthorizationActive(user.getUuid(), session.authorizationId())) {
            deleteSession(sessionId);
            throw accessFailure();
        }
        String fresh = redis.opsForValue().get(PREFIX + "fresh:" + sessionId);
        long freshUntil = fresh == null ? 0 : Long.parseLong(fresh);
        return new ConsolePrincipal(sessionId, user.getUuid(), session.authorizationId(), session.expiresAt(), freshUntil);
    }

    public SessionView sessionView(ConsolePrincipal principal) {
        User user = users.findByUuid(principal.userUuid()).orElseThrow(ConsoleSessionService::sessionFailure);
        requireSuperAdmin(user);
        return new SessionView(new SessionUser(user.getUuid(), user.getUsername(), user.getEmail(), user.getAvatarUrl(),
            user.getSystemRole()), principal.expiresAt(), principal.freshUntil());
    }

    public void logout(ConsolePrincipal principal, HttpServletResponse response) {
        deleteSession(principal.sessionId());
        cookie(response, SESSION_COOKIE, "", Duration.ZERO);
    }

    public void requireFresh(ConsolePrincipal principal) {
        ConsolePrincipal current = authenticateSession(principal.sessionId());
        if (current.freshUntil() <= now()) {
            throw new Oauth2Exception(HttpStatus.FORBIDDEN, "access_denied", ApiResultCode.SENSITIVE_VERIFICATION_REQUIRED);
        }
    }

    public Map<String, String> startVerification(ConsolePrincipal principal) {
        ConsolePrincipal current = authenticateSession(principal.sessionId());
        String requestId = randomToken();
        VerificationRequest request = new VerificationRequest(current.sessionId(), current.userUuid(), randomToken(),
            now() + FLOW_TTL.toMillis());
        store("verification:" + hash(requestId), request, FLOW_TTL);
        return Map.of("requestId", requestId, "authorizationUrl", UriComponentsBuilder.fromUriString(consoleOrigin + "/verification")
            .queryParam("request_id", requestId).build().encode().toUriString());
    }

    public void validateVerificationRequest(String requestId, ConsolePrincipal principal) {
        VerificationRequest request = verificationRequest(requestId, principal.userUuid());
        if (!equal(request.sessionId(), principal.sessionId())) throw flowFailure();
    }

    public SessionView completeDirectVerification(String requestId, ConsolePrincipal principal) {
        validateVerificationRequest(requestId, principal);
        VerificationRequest request = consume("verification:" + hash(requestId), VerificationRequest.class);
        if (request == null || request.expiresAt() <= now()
            || !equal(request.sessionId(), principal.sessionId())
            || !equal(request.userUuid(), principal.userUuid())) throw flowFailure();

        ConsolePrincipal current = authenticateSession(principal.sessionId());
        long freshUntil = Math.min(now() + FRESH_TTL.toMillis(), current.expiresAt());
        redis.opsForValue().set(PREFIX + "fresh:" + principal.sessionId(), Long.toString(freshUntil),
            Duration.ofMillis(Math.max(1, freshUntil - now())));
        return sessionView(new ConsolePrincipal(current.sessionId(), current.userUuid(), current.authorizationId(),
            current.expiresAt(), freshUntil));
    }

    /** Called by native verification endpoints before checking the supplied credential. */
    public void validateVerificationRequest(String requestId, String userUuid) {
        verificationRequest(requestId, userUuid);
    }

    /** Called exclusively after a password, Passkey, TOTP or recovery-code verification succeeds. */
    public void recordVerificationProof(String requestId, String userUuid) {
        VerificationRequest request = verificationRequest(requestId, userUuid);
        redis.opsForValue().set(PREFIX + "proof:" + hash(requestId), userUuid,
            Duration.ofMillis(Math.max(1, request.expiresAt() - now())));
    }

    public Map<String, Object> verificationContext(String requestId, String userUuid) {
        verificationRequest(requestId, userUuid);
        User user = users.findByUuid(userUuid).orElseThrow(ConsoleSessionService::sessionFailure);
        return Map.of("requestId", requestId, "consoleName", "Ksuser Console", "hasPassword", user.getHasPassword());
    }

    public Map<String, String> completeVerification(String requestId, String userUuid) {
        VerificationRequest request = verificationRequest(requestId, userUuid);
        if (!equal(redis.opsForValue().getAndDelete(PREFIX + "proof:" + hash(requestId)), userUuid)) {
            throw new Oauth2Exception(HttpStatus.FORBIDDEN, "access_denied", ApiResultCode.SENSITIVE_VERIFICATION_REQUIRED);
        }
        request = consume("verification:" + hash(requestId), VerificationRequest.class);
        if (request == null || !equal(request.userUuid(), userUuid)) throw flowFailure();
        String ticket = randomToken();
        store("ticket:" + hash(ticket), request, Duration.ofMillis(Math.max(1, request.expiresAt() - now())));
        return Map.of("redirectUrl", UriComponentsBuilder.fromUriString(consoleOrigin + "/auth/verification-callback")
            .queryParam("ticket", ticket).queryParam("state", request.state()).build().encode().toUriString());
    }

    public SessionView exchangeVerification(ConsolePrincipal principal, String ticket, String state) {
        if (ticket == null || ticket.isBlank()) throw flowFailure();
        VerificationRequest request = read("ticket:" + hash(ticket), VerificationRequest.class);
        validateTicket(principal, request, state);
        request = consume("ticket:" + hash(ticket), VerificationRequest.class);
        validateTicket(principal, request, state);
        ConsolePrincipal current = authenticateSession(principal.sessionId());
        long freshUntil = Math.min(now() + FRESH_TTL.toMillis(), current.expiresAt());
        redis.opsForValue().set(PREFIX + "fresh:" + principal.sessionId(), Long.toString(freshUntil),
            Duration.ofMillis(Math.max(1, freshUntil - now())));
        return sessionView(new ConsolePrincipal(current.sessionId(), current.userUuid(), current.authorizationId(),
            current.expiresAt(), freshUntil));
    }

    private void validateTicket(ConsolePrincipal principal, VerificationRequest request, String state) {
        if (request == null || request.expiresAt() <= now() || !equal(request.state(), state)
            || !equal(request.sessionId(), principal.sessionId()) || !equal(request.userUuid(), principal.userUuid())) {
            throw flowFailure();
        }
    }

    private VerificationRequest verificationRequest(String requestId, String userUuid) {
        if (requestId == null || requestId.isBlank()) throw flowFailure();
        VerificationRequest request = read("verification:" + hash(requestId), VerificationRequest.class);
        if (request == null || request.expiresAt() <= now() || !equal(request.userUuid(), userUuid)) throw flowFailure();
        ConsolePrincipal session = authenticateSession(request.sessionId());
        if (!equal(session.userUuid(), userUuid)) throw flowFailure();
        return request;
    }

    public String callbackUri() { return consoleOrigin + "/auth/callback"; }
    public String consoleOrigin() { return consoleOrigin; }
    private static String trimOrigin(String origin) { return origin.replaceAll("/+$", ""); }
    private static long now() { return System.currentTimeMillis(); }
    private void deleteSession(String id) { redis.delete(java.util.List.of(PREFIX + "session:" + id, PREFIX + "fresh:" + id)); }
    private void cookie(HttpServletResponse response, String name, String value, Duration ttl) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, value).httpOnly(true)
            .secure(!properties.isDebug()).sameSite("Strict").path("/console").maxAge(ttl).build().toString());
    }
    private static String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && cookie.getValue().matches("[A-Za-z0-9_-]{43}")) return cookie.getValue();
        }
        return null;
    }
    private String randomToken() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static String hash(String value) {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private static boolean equal(String first, String second) {
        return first != null && second != null && MessageDigest.isEqual(first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8));
    }
    private <T> void store(String key, T value, Duration ttl) {
        try { redis.opsForValue().set(PREFIX + key, json.writeValueAsString(value), ttl); }
        catch (Exception ex) { throw new cn.ksuser.api.result.BusinessStateException(ApiResultCode.INTERNAL_ERROR, ex); }
    }
    private <T> T read(String key, Class<T> type) { return decode(redis.opsForValue().get(PREFIX + key), type); }
    private <T> T consume(String key, Class<T> type) { return decode(redis.opsForValue().getAndDelete(PREFIX + key), type); }
    private <T> T decode(String value, Class<T> type) {
        if (value == null) return null;
        try { return json.readValue(value, type); }
        catch (Exception ex) { throw new cn.ksuser.api.result.BusinessStateException(ApiResultCode.INTERNAL_ERROR, ex); }
    }
    private static void requireSuperAdmin(User user) { if (!"SUPER_ADMIN".equals(user.getSystemRole())) throw accessFailure(); }
    private static Oauth2Exception flowFailure() { return new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_request", ApiResultCode.CONSOLE_FLOW_INVALID); }
    private static Oauth2Exception sessionFailure() { return new Oauth2Exception(HttpStatus.UNAUTHORIZED, "invalid_token", ApiResultCode.CONSOLE_SESSION_INVALID); }
    private static Oauth2Exception accessFailure() { return new Oauth2Exception(HttpStatus.FORBIDDEN, "access_denied", ApiResultCode.CONSOLE_ACCESS_DENIED); }

    public record ConsolePrincipal(String sessionId, String userUuid, Long authorizationId, long expiresAt, long freshUntil) {}
    public record SessionUser(String uuid, String username, String email, String avatarUrl, String systemRole) {}
    public record SessionView(SessionUser user, long expiresAt, long freshUntil) {}
    public record LoginFlow(String state, String nonce, String verifier, String redirectUri, long expiresAt) {}
    public record QrLoginFlow(String challengeId, String pollToken, long expiresAt) {}
    public record QrLoginApproval(String browserHash, long expiresAt) {}
    public record QrLoginGrant(String userUuid, Long authorizationId) {}
    public record QrLoginStatus(String status, long expiresInSeconds) {}
    public record SessionData(String userUuid, Long authorizationId, long expiresAt) {}
    public record VerificationRequest(String sessionId, String userUuid, String state, long expiresAt) {}
}
