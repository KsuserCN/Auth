package cn.ksuser.api.service;

import cn.ksuser.api.dto.OfficialAuthorizeRequest;
import cn.ksuser.api.entity.OfficialApplication;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserOfficialAuthorization;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.repository.OfficialApplicationRepository;
import cn.ksuser.api.repository.UserOfficialAuthorizationRepository;
import cn.ksuser.api.repository.UserRepository;
import cn.ksuser.api.result.ApiResultCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/** Official applications receive the user's real identity only through this registry. */
@Service
public class OfficialAuthorizationService {
    public static final String CONSOLE_CLIENT_ID = "ksuser_console";
    public static final List<String> ALL_SCOPES = List.of("openid", "profile", "email", "real_name", "gender", "birth_date", "region", "bio");
    private static final String CODE_PREFIX = "official:code:";
    private static final String TOKEN_PREFIX = "official:access:";
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration TOKEN_TTL = Duration.ofHours(2);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final OfficialApplicationRepository apps;
    private final UserOfficialAuthorizationRepository grants;
    private final UserRepository users;
    private final StringRedisTemplate redis;
    private final PasswordEncoder passwordEncoder;
    private final OidcRsaKeyService rsa;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${app.official.issuer:https://api.ksuser.cn/official}")
    private String issuer = "https://api.ksuser.cn/official";
    @Value("${app.official.authorization-url:https://auth.ksuser.cn/official/authorize}")
    private String authorizationUrl = "https://auth.ksuser.cn/official/authorize";

    public OfficialAuthorizationService(OfficialApplicationRepository apps, UserOfficialAuthorizationRepository grants,
        UserRepository users, StringRedisTemplate redis, PasswordEncoder passwordEncoder, OidcRsaKeyService rsa) {
        this.apps = apps; this.grants = grants; this.users = users; this.redis = redis;
        this.passwordEncoder = passwordEncoder; this.rsa = rsa;
    }

    public AuthorizeContext buildAuthorizeContext(User user, OfficialAuthorizeRequest request) {
        OfficialApplication app = activeClient(request.clientId());
        List<String> requested = validateRequest(app, request);
        if (user != null) checkConsoleRole(app, currentUser(user.getId()));
        List<String> authorized = user == null ? List.of() : grants.findByUserIdAndClientId(user.getId(), app.getClientId())
            .map(grant -> parseScopes(grant.getScopes())).orElse(List.of());
        return new AuthorizeContext(app.getClientId(), app.getClientName(), app.getLogoUrl(), requested,
            authorized, !authorized.containsAll(requested), user != null, CONSOLE_CLIENT_ID.equals(app.getClientId()));
    }

    @Transactional
    public Map<String, String> approveAuthorization(User user, OfficialAuthorizeRequest request) {
        OfficialApplication app = apps.findByClientIdForUpdate(request.clientId())
            .filter(item -> Boolean.TRUE.equals(item.getIsActive())).orElseThrow(() -> invalidClient());
        List<String> requested = validateRequest(app, request);
        User current = currentUser(user.getId());
        checkConsoleRole(app, current);
        if (!Boolean.TRUE.equals(request.approved())) {
            return Map.of("redirectUrl", callback(request.redirectUri(), request.state(), "error", "access_denied"));
        }
        UserOfficialAuthorization grant = saveAuthorization(current, app, requested, request.redirectUri());
        String code = randomValue("ofc_");
        store(CODE_PREFIX + hashOpaque(code), new CodePayload(app.getClientId(), current.getId(), grant.getId(),
            request.redirectUri(), String.join(" ", requested), request.nonce(), request.codeChallenge(),
            System.currentTimeMillis() + CODE_TTL.toMillis()), CODE_TTL);
        return Map.of("redirectUrl", callback(request.redirectUri(), request.state(), "code", code));
    }

    public void validateConsoleLogin(String redirectUri) {
        validateRequest(activeClient(CONSOLE_CLIENT_ID), consoleLoginRequest(redirectUri));
    }

    /** Native confirmation grants Console access without issuing Passport or OAuth bearer tokens. */
    @Transactional
    public ConsoleExchange approveConsoleLogin(User user, String redirectUri) {
        OfficialApplication app = apps.findByClientIdForUpdate(CONSOLE_CLIENT_ID)
            .filter(item -> Boolean.TRUE.equals(item.getIsActive())).orElseThrow(() -> invalidClient());
        List<String> requested = validateRequest(app, consoleLoginRequest(redirectUri));
        User current = currentUser(user.getId());
        checkConsoleRole(app, current);
        UserOfficialAuthorization grant = saveAuthorization(current, app, requested, redirectUri);
        return new ConsoleExchange(current, grant.getId(), app.getClientId());
    }

    private static OfficialAuthorizeRequest consoleLoginRequest(String redirectUri) {
        return new OfficialAuthorizeRequest(CONSOLE_CLIENT_ID, redirectUri, "code", "openid profile email",
            randomValue(""), randomValue(""), PkceUtil.toS256CodeChallenge(randomValue("")), "S256", true);
    }

    private UserOfficialAuthorization saveAuthorization(User current, OfficialApplication app,
                                                        List<String> requested, String redirectUri) {
        UserOfficialAuthorization grant = grants.findByUserIdAndClientId(current.getId(), app.getClientId())
            .orElseGet(UserOfficialAuthorization::new);
        LinkedHashSet<String> combined = new LinkedHashSet<>(parseScopes(grant.getScopes()));
        combined.addAll(requested);
        grant.setUserId(current.getId()); grant.setClientId(app.getClientId());
        grant.setScopes(String.join(" ", ALL_SCOPES.stream().filter(combined::contains).toList()));
        grant.setRedirectUri(redirectUri); grant.setLastAuthorizedAt(LocalDateTime.now());
        return grants.saveAndFlush(grant);
    }

    public Map<String, Object> exchangeAuthorizationCode(String grantType, String code, String clientId,
        String clientSecret, String redirectUri, String verifier, String origin) {
        if (!"authorization_code".equals(grantType)) throw failure(HttpStatus.BAD_REQUEST, "unsupported_grant_type", ApiResultCode.OFFICIAL_REQUEST_INVALID);
        OfficialApplication app = activeClient(clientId);
        assertBrowserOrigin(app, origin);
        if ("confidential".equals(app.getClientType())) {
            if (clientSecret == null || app.getClientSecretHash() == null || !passwordEncoder.matches(clientSecret, app.getClientSecretHash())) throw invalidClient();
        } else if (!"public".equals(app.getClientType()) || clientSecret != null) {
            throw invalidClient();
        }
        CodePayload payload = consume(app, code, redirectUri, verifier, null);
        User user = currentUser(payload.userId());
        checkConsoleRole(app, user);
        validateGrant(payload.userId(), app, payload.authorizationId(), payload.scope());
        String token = randomValue("ofa_");
        store(TOKEN_PREFIX + hashOpaque(token), new AccessPayload(app.getClientId(), user.getId(),
            payload.authorizationId(), payload.scope(), System.currentTimeMillis() + TOKEN_TTL.toMillis()), TOKEN_TTL);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("access_token", token); response.put("id_token", idToken(app, user, payload));
        response.put("token_type", "Bearer"); response.put("expires_in", TOKEN_TTL.toSeconds()); response.put("scope", payload.scope());
        return response;
    }

    public ConsoleExchange exchangeForConsole(String code, String redirectUri, String verifier, String expectedNonce) {
        if (expectedNonce == null || expectedNonce.isBlank()) throw invalidGrant();
        OfficialApplication app = activeClient(CONSOLE_CLIENT_ID);
        CodePayload payload = consume(app, code, redirectUri, verifier, expectedNonce);
        User user = currentUser(payload.userId()); checkConsoleRole(app, user);
        validateGrant(user.getId(), app, payload.authorizationId(), payload.scope());
        return new ConsoleExchange(user, payload.authorizationId(), app.getClientId());
    }

    public User exchangeConsoleCode(String code, String redirectUri, String verifier, String expectedNonce) {
        return exchangeForConsole(code, redirectUri, verifier, expectedNonce).user();
    }

    public boolean isConsoleAuthorizationActive(String uuid, Long authorizationId) {
        if (uuid == null || authorizationId == null) return false;
        Optional<User> user = users.findByUuid(uuid);
        Optional<OfficialApplication> app = apps.findByClientId(CONSOLE_CLIENT_ID);
        return user.filter(item -> "SUPER_ADMIN".equals(item.getSystemRole())).isPresent()
            && app.filter(item -> Boolean.TRUE.equals(item.getIsActive()) && parseScopes(item.getScopes()).contains("openid")).isPresent()
            && grants.findByUserIdAndClientId(user.get().getId(), CONSOLE_CLIENT_ID)
                .filter(item -> authorizationId.equals(item.getId()) && parseScopes(item.getScopes()).contains("openid")).isPresent();
    }

    public void validateConsoleAccess(String uuid) {
        User user = users.findByUuid(uuid).orElseThrow(() -> invalidGrant());
        OfficialApplication app = activeClient(CONSOLE_CLIENT_ID); checkConsoleRole(app, user);
        if (!parseScopes(app.getScopes()).contains("openid") || grants.findByUserIdAndClientId(user.getId(), CONSOLE_CLIENT_ID)
            .filter(grant -> parseScopes(grant.getScopes()).contains("openid")).isEmpty()) throw invalidGrant();
    }

    public Map<String, Object> buildUserInfo(String token, String origin) {
        if (token == null || token.isBlank() || token.length() > 256) throw invalidToken();
        AccessPayload payload = read(redis.opsForValue().get(TOKEN_PREFIX + hashOpaque(token)), AccessPayload.class);
        if (payload == null || payload.expiresAt() <= System.currentTimeMillis()) throw invalidToken();
        OfficialApplication app = apps.findByClientId(payload.clientId())
            .filter(item -> Boolean.TRUE.equals(item.getIsActive())).orElseThrow(() -> invalidToken());
        assertBrowserOrigin(app, origin);
        User user;
        try {
            validateGrant(payload.userId(), app, payload.authorizationId(), payload.scope());
            user = currentUser(payload.userId());
            checkConsoleRole(app, user);
        } catch (Oauth2Exception ex) {
            throw invalidToken();
        }
        return claims(user, parseScopes(payload.scope()));
    }

    public List<AuthorizedApplication> listAuthorizations(User user) {
        return grants.findAllByUserIdOrderByLastAuthorizedAtDesc(user.getId()).stream().map(grant -> {
            OfficialApplication app = apps.findByClientId(grant.getClientId()).orElse(null);
            return new AuthorizedApplication(grant.getClientId(), app == null ? grant.getClientId() : app.getClientName(),
                app == null ? null : app.getLogoUrl(), parseScopes(grant.getScopes()), grant.getAuthorizedAt(), grant.getLastAuthorizedAt());
        }).toList();
    }

    @Transactional
    public void revokeAuthorization(User user, String clientId) {
        apps.findByClientIdForUpdate(clientId);
        grants.deleteByUserIdAndClientId(user.getId(), clientId);
    }

    public Map<String, Object> discovery() {
        String base = issuer.replaceAll("/+$", "");
        return Map.ofEntries(Map.entry("issuer", base), Map.entry("authorization_endpoint", authorizationUrl),
            Map.entry("token_endpoint", base + "/token"), Map.entry("userinfo_endpoint", base + "/userinfo"),
            Map.entry("jwks_uri", base + "/jwks"), Map.entry("response_types_supported", List.of("code")),
            Map.entry("grant_types_supported", List.of("authorization_code")), Map.entry("subject_types_supported", List.of("public")),
            Map.entry("id_token_signing_alg_values_supported", List.of("RS256")), Map.entry("scopes_supported", ALL_SCOPES),
            Map.entry("code_challenge_methods_supported", List.of("S256")),
            Map.entry("token_endpoint_auth_methods_supported", List.of("none", "client_secret_post", "client_secret_basic")));
    }

    public String jwks() { return rsa.getJwksJson(); }

    private CodePayload consume(OfficialApplication app, String code, String redirectUri, String verifier, String nonce) {
        if (!parseRedirectUris(app.getRedirectUris()).contains(redirectUri)) throw failure(HttpStatus.BAD_REQUEST, "invalid_grant", ApiResultCode.OFFICIAL_REDIRECT_INVALID);
        if (code == null || code.length() > 256) throw invalidGrant();
        CodePayload payload = read(redis.opsForValue().getAndDelete(CODE_PREFIX + hashOpaque(code)), CodePayload.class);
        if (payload == null || payload.expiresAt() <= System.currentTimeMillis() || !app.getClientId().equals(payload.clientId())
            || !redirectUri.equals(payload.redirectUri())
            || ((payload.nonce() == null || payload.nonce().isBlank()) && !allowsMissingNonce(app))
            || payload.challenge() == null || !payload.challenge().matches("[A-Za-z0-9_-]{43}")
            || (nonce != null && !nonce.equals(payload.nonce()))) throw invalidGrant();
        if (verifier == null || !verifier.matches("[A-Za-z0-9._~-]{43,128}")
            || !MessageDigest.isEqual(PkceUtil.toS256CodeChallenge(verifier).getBytes(StandardCharsets.US_ASCII), payload.challenge().getBytes(StandardCharsets.US_ASCII))) {
            throw failure(HttpStatus.BAD_REQUEST, "invalid_grant", ApiResultCode.OFFICIAL_PKCE_INVALID);
        }
        return payload;
    }

    private List<String> validateRequest(OfficialApplication app, OfficialAuthorizeRequest request) {
        if (!"code".equals(request.responseType()) || request.state() == null || request.state().isBlank() || request.state().length() > 512
            || ((request.nonce() == null || request.nonce().isBlank()) && !allowsMissingNonce(app))
            || (request.nonce() != null && request.nonce().length() > 512)) {
            throw failure(HttpStatus.BAD_REQUEST, "invalid_request", ApiResultCode.OFFICIAL_REQUEST_INVALID);
        }
        if (!parseRedirectUris(app.getRedirectUris()).contains(request.redirectUri())) {
            throw failure(HttpStatus.BAD_REQUEST, "invalid_request", ApiResultCode.OFFICIAL_REDIRECT_INVALID);
        }
        if (!"S256".equals(request.codeChallengeMethod()) || request.codeChallenge() == null || !request.codeChallenge().matches("[A-Za-z0-9_-]{43}")) {
            throw failure(HttpStatus.BAD_REQUEST, "invalid_request", ApiResultCode.OFFICIAL_PKCE_INVALID);
        }
        List<String> requested = parseScopes(normalizeScopes(request.scope()));
        if (!parseScopes(app.getScopes()).containsAll(requested)) throw failure(HttpStatus.BAD_REQUEST, "invalid_scope", ApiResultCode.OFFICIAL_SCOPE_INVALID);
        return requested;
    }

    private void validateGrant(Long userId, OfficialApplication app, Long id, String scope) {
        List<String> requested = parseScopes(scope);
        if (id == null || !requested.contains("openid") || !ALL_SCOPES.containsAll(requested)
            || !parseScopes(app.getScopes()).containsAll(requested) || grants.findByUserIdAndClientId(userId, app.getClientId())
            .filter(grant -> id.equals(grant.getId()) && parseScopes(grant.getScopes()).containsAll(requested)).isEmpty()) throw invalidGrant();
    }

    private User currentUser(Long id) { return users.findById(id).orElseThrow(() -> invalidGrant()); }
    private OfficialApplication activeClient(String clientId) {
        if (clientId == null || clientId.length() > 128) throw invalidClient();
        return apps.findByClientId(clientId).filter(app -> Boolean.TRUE.equals(app.getIsActive())).orElseThrow(() -> invalidClient());
    }
    private void checkConsoleRole(OfficialApplication app, User user) {
        if (CONSOLE_CLIENT_ID.equals(app.getClientId()) && !"SUPER_ADMIN".equals(user.getSystemRole())) {
            throw failure(HttpStatus.FORBIDDEN, "access_denied", ApiResultCode.OFFICIAL_ACCESS_DENIED);
        }
    }

    private static boolean allowsMissingNonce(OfficialApplication app) {
        return "confidential".equals(app.getClientType()) && !Boolean.TRUE.equals(app.getReserved())
            && !CONSOLE_CLIENT_ID.equals(app.getClientId()) && Boolean.TRUE.equals(app.getAllowMissingNonce());
    }

    private String idToken(OfficialApplication app, User user, CodePayload payload) {
        long now = System.currentTimeMillis();
        Map<String, Object> claims = claims(user, parseScopes(payload.scope()));
        if (payload.nonce() != null && !payload.nonce().isBlank()) claims.put("nonce", payload.nonce());
        claims.put("type", "official_id_token");
        return Jwts.builder().header().keyId(rsa.getKeyId()).and().claims(claims).issuer(issuer.replaceAll("/+$", ""))
            .subject(user.getUuid()).audience().add(app.getClientId()).and().issuedAt(new Date(now))
            .expiration(new Date(now + TOKEN_TTL.toMillis())).signWith(rsa.getPrivateKey(), Jwts.SIG.RS256).compact();
    }

    private Map<String, Object> claims(User user, List<String> scopes) {
        Map<String, Object> result = new LinkedHashMap<>(); result.put("sub", user.getUuid()); result.put("uuid", user.getUuid());
        if (scopes.contains("profile")) {
            result.put("username", user.getUsername()); result.put("preferred_username", user.getUsername()); result.put("nickname", user.getUsername());
            result.put("name", user.getUsername());
            result.put("avatar_url", user.getAvatarUrl()); result.put("picture", user.getAvatarUrl());
        }
        if (scopes.contains("email")) { result.put("email", user.getEmail()); result.put("email_verified", user.getEmail() != null && !user.getEmail().isBlank()); }
        if (scopes.contains("real_name")) result.put("real_name", user.getRealName());
        if (scopes.contains("gender")) result.put("gender", user.getGender());
        if (scopes.contains("birth_date")) result.put("birth_date", user.getBirthDate() == null ? null : user.getBirthDate().toString());
        if (scopes.contains("region")) result.put("region", user.getRegion());
        if (scopes.contains("bio")) result.put("bio", user.getBio());
        return result;
    }

    private void store(String key, Object value, Duration ttl) {
        try { redis.opsForValue().set(key, mapper.writeValueAsString(value), ttl); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Official authorization serialization failed", e); }
    }
    private <T> T read(String value, Class<T> type) {
        if (value == null) return null;
        try { return mapper.readValue(value, type); } catch (JsonProcessingException ex) { return null; }
    }
    private static String callback(String redirectUri, String state, String key, String value) {
        return UriComponentsBuilder.fromUriString(redirectUri).queryParam(key, URLEncoder.encode(value, StandardCharsets.UTF_8))
            .queryParam("state", URLEncoder.encode(state, StandardCharsets.UTF_8)).build(true).toUriString();
    }
    public static List<String> parseRedirectUris(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("[;\\r\\n]+" )).map(String::trim).filter(item -> !item.isEmpty()).distinct().toList();
    }
    public static List<String> parseScopes(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.trim().split("\\s+")).distinct().toList();
    }
    public static String normalizeScopes(String value) {
        List<String> scopes = parseScopes(value == null || value.isBlank() ? "openid" : value);
        if (!scopes.contains("openid") || !ALL_SCOPES.containsAll(scopes)) throw failure(HttpStatus.BAD_REQUEST, "invalid_scope", ApiResultCode.OFFICIAL_SCOPE_INVALID);
        return String.join(" ", ALL_SCOPES.stream().filter(scopes::contains).toList());
    }
    public static String randomValue(String prefix) {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public static String hashOpaque(String value) {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static String originOf(String redirectUri) {
        try {
            URI uri = URI.create(redirectUri);
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) return null;
            String scheme = uri.getScheme();
            if (!"https".equals(scheme) && !("http".equals(scheme) && Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(uri.getHost()))) return null;
            int port = uri.getPort();
            return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + (port < 0 || (port == 443 && "https".equals(scheme)) || (port == 80 && "http".equals(scheme)) ? "" : ":" + port);
        } catch (IllegalArgumentException e) { return null; }
    }
    public static void assertBrowserOrigin(OfficialApplication app, String origin) {
        if (origin != null && !origin.isBlank() && parseRedirectUris(app.getRedirectUris()).stream().map(OfficialAuthorizationService::originOf).noneMatch(origin::equals)) {
            throw failure(HttpStatus.FORBIDDEN, "access_denied", ApiResultCode.OFFICIAL_ACCESS_DENIED);
        }
    }
    private static Oauth2Exception invalidClient() { return failure(HttpStatus.UNAUTHORIZED, "invalid_client", ApiResultCode.OFFICIAL_CLIENT_INVALID); }
    private static Oauth2Exception invalidGrant() { return failure(HttpStatus.BAD_REQUEST, "invalid_grant", ApiResultCode.OFFICIAL_GRANT_INVALID); }
    private static Oauth2Exception invalidToken() { return failure(HttpStatus.UNAUTHORIZED, "invalid_token", ApiResultCode.OFFICIAL_TOKEN_INVALID); }
    private static Oauth2Exception failure(HttpStatus status, String error, ApiResultCode code) { return new Oauth2Exception(status, error, code); }

    public record AuthorizeContext(String clientId, String clientName, String logoUrl, List<String> requestedScopes,
        List<String> authorizedScopes, boolean consentRequired, boolean loggedIn, boolean consoleApplication) { }
    public record AuthorizedApplication(String clientId, String clientName, String logoUrl, List<String> scopes,
        LocalDateTime authorizedAt, LocalDateTime lastAuthorizedAt) { }
    public record ConsoleExchange(User user, Long authorizationId, String clientId) { }
    public record CodePayload(String clientId, Long userId, Long authorizationId, String redirectUri,
        String scope, String nonce, String challenge, long expiresAt) { }
    public record AccessPayload(String clientId, Long userId, Long authorizationId, String scope, long expiresAt) { }
}
