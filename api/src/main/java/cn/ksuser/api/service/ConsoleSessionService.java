package cn.ksuser.api.service;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.entity.User;
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
    private static final Duration FLOW_TTL = Duration.ofMinutes(5);
    private static final Duration SESSION_TTL = Duration.ofHours(8);
    private static final Duration FRESH_TTL = Duration.ofMinutes(15);
    private static final String PREFIX = "console:";
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final UserRepository users;
    private final OfficialAuthorizationService official;
    private final AppProperties properties;
    private final String consoleOrigin;
    private final String passportOrigin;
    private final SecureRandom random = new SecureRandom();

    public ConsoleSessionService(StringRedisTemplate redis, ObjectMapper json, UserRepository users,
                                 OfficialAuthorizationService official, AppProperties properties,
                                 @Value("${app.console.origin:}") String consoleOrigin,
                                 @Value("${app.console.passport-origin:}") String passportOrigin) {
        this.redis = redis;
        this.json = json;
        this.users = users;
        this.official = official;
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
        User user = exchange.user();
        requireSuperAdmin(user);
        String previous = readCookie(request, SESSION_COOKIE);
        if (previous != null) deleteSession(hash(previous));
        String token = randomToken();
        String sessionId = hash(token);
        SessionData data = new SessionData(user.getUuid(), exchange.authorizationId(), now() + SESSION_TTL.toMillis());
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
        return Map.of("authorizationUrl", UriComponentsBuilder.fromUriString(consoleOrigin + "/verification")
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
    public record SessionData(String userUuid, Long authorizationId, long expiresAt) {}
    public record VerificationRequest(String sessionId, String userUuid, String state, long expiresAt) {}
}
