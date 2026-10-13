package cn.ksuser.api.service;

import cn.ksuser.api.dto.OfficialAuthorizeRequest;
import cn.ksuser.api.entity.OfficialApplication;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserOfficialAuthorization;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.repository.OfficialApplicationRepository;
import cn.ksuser.api.repository.UserOfficialAuthorizationRepository;
import cn.ksuser.api.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OfficialAuthorizationServiceTest {
    private static final String CALLBACK = "https://example.test/auth/callback";
    private static final String VERIFIER = "012345678901234567890123456789012345678901234567890123456789012345";
    private OfficialApplicationRepository apps;
    private UserOfficialAuthorizationRepository grants;
    private PasswordEncoder passwords;
    private OfficialAuthorizationService service;
    private OfficialApplication app;
    private User user;
    private OidcRsaKeyService rsa;
    private final Map<String, String> storage = new HashMap<>();
    private final AtomicReference<UserOfficialAuthorization> currentGrant = new AtomicReference<>();
    private long sequence;

    @BeforeEach
    void setup() {
        storage.clear(); currentGrant.set(null); sequence = 0;
        apps = mock(OfficialApplicationRepository.class);
        grants = mock(UserOfficialAuthorizationRepository.class);
        UserRepository users = mock(UserRepository.class);
        passwords = mock(PasswordEncoder.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        doAnswer(call -> { storage.put(call.getArgument(0), call.getArgument(1)); return null; })
            .when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.get(anyString())).thenAnswer(call -> storage.get(call.getArgument(0)));
        when(values.getAndDelete(anyString())).thenAnswer(call -> storage.remove(call.getArgument(0)));
        rsa = new OidcRsaKeyService("official-test-seed", "official-secret-32-characters-long");
        service = new OfficialAuthorizationService(apps, grants, users, redis, passwords, rsa);
        app = new OfficialApplication(); app.setId(1L); app.setClientId("official_test"); app.setClientName("Official Test");
        app.setClientType("public"); app.setRedirectUris(CALLBACK); app.setScopes("openid profile email real_name gender birth_date region bio");
        app.setIsActive(true); app.setReserved(false);
        user = new User("e92ca6f9-67ea-4e43-b715-0b37affba178", "ksuser", "hello@example.test", "hash");
        user.setId(1L); user.setRealName("Private name"); user.setSystemRole("SUPER_ADMIN");
        lenient().when(apps.findByClientId(anyString())).thenAnswer(call -> app.getClientId().equals(call.getArgument(0)) ? Optional.of(app) : Optional.empty());
        lenient().when(apps.findByClientIdForUpdate(anyString())).thenAnswer(call -> app.getClientId().equals(call.getArgument(0)) ? Optional.of(app) : Optional.empty());
        lenient().when(users.findById(1L)).thenReturn(Optional.of(user));
        lenient().when(users.findByUuid(user.getUuid())).thenReturn(Optional.of(user));
        lenient().when(grants.findByUserIdAndClientId(eq(1L), anyString())).thenAnswer(call -> Optional.ofNullable(currentGrant.get())
            .filter(item -> item.getClientId().equals(call.getArgument(1))));
        when(grants.saveAndFlush(any(UserOfficialAuthorization.class))).thenAnswer(call -> {
            UserOfficialAuthorization grant = call.getArgument(0);
            if (grant.getId() == null) grant.setId(++sequence);
            currentGrant.set(grant); return grant;
        });
        lenient().doAnswer(call -> { currentGrant.set(null); return null; }).when(grants).deleteByUserIdAndClientId(eq(1L), anyString());
    }

    private OfficialAuthorizeRequest request(String scope) {
        return new OfficialAuthorizeRequest(app.getClientId(), CALLBACK, "code", scope, "state-123", "nonce-123",
            PkceUtil.toS256CodeChallenge(VERIFIER), "S256", true);
    }
    private String issue(String scope) {
        String redirect = service.approveAuthorization(user, request(scope)).get("redirectUrl");
        return Arrays.stream(URI.create(redirect).getRawQuery().split("&")).filter(item -> item.startsWith("code="))
            .map(item -> URLDecoder.decode(item.substring(5), StandardCharsets.UTF_8)).findFirst().orElseThrow();
    }
    private Map<String, Object> exchange(String code) {
        return service.exchangeAuthorizationCode("authorization_code", code, app.getClientId(), null, CALLBACK, VERIFIER, "https://example.test");
    }

    @Test
    void publicOfficialApplicationAllowsOrdinaryAccountsButConsoleStillRequiresSuperAdmin() {
        user.setSystemRole("USER");
        var publicContext=service.buildAuthorizeContext(user,request("openid email"));
        assertFalse(publicContext.consoleApplication());
        String code=issue("openid email");
        assertEquals(user.getUuid(),service.buildUserInfo((String)exchange(code).get("access_token"),"https://example.test").get("uuid"));
        app.setClientId(OfficialAuthorizationService.CONSOLE_CLIENT_ID);
        assertThrows(Oauth2Exception.class,()->service.buildAuthorizeContext(user,request("openid email")));
        assertThrows(Oauth2Exception.class,()->service.approveAuthorization(user,request("openid email")));
    }

    @Test
    void nativeConsoleConfirmationGrantsOnlyRegisteredScopesAndRemainsRevocable() {
        app.setClientId(OfficialAuthorizationService.CONSOLE_CLIENT_ID);
        service.validateConsoleLogin(CALLBACK);
        var grant=service.approveConsoleLogin(user,CALLBACK);
        assertEquals("openid profile email",currentGrant.get().getScopes());
        assertTrue(service.isConsoleAuthorizationActive(user.getUuid(),grant.authorizationId()));
        assertTrue(storage.isEmpty(),"Native confirmation must not mint bearer tokens or authorization codes");
        service.revokeAuthorization(user,app.getClientId());
        assertFalse(service.isConsoleAuthorizationActive(user.getUuid(),grant.authorizationId()));
        user.setSystemRole("ADMIN");
        assertThrows(Oauth2Exception.class,()->service.approveConsoleLogin(user,CALLBACK));
        assertNull(currentGrant.get());
        user.setSystemRole("SUPER_ADMIN");
        assertThrows(Oauth2Exception.class,()->service.approveConsoleLogin(user,CALLBACK+"?wrong=1"));
        app.setScopes("openid profile");
        assertThrows(Oauth2Exception.class,()->service.validateConsoleLogin(CALLBACK));
        app.setIsActive(false);
        assertThrows(Oauth2Exception.class,()->service.approveConsoleLogin(user,CALLBACK));
    }

    @Test
    void firstConsentIsRequiredRememberedScopesSkipConsentAndExpandedScopesRequireConsent() {
        assertTrue(service.buildAuthorizeContext(user, request("openid profile")).consentRequired());
        issue("openid profile");
        assertFalse(service.buildAuthorizeContext(user, request("openid profile")).consentRequired());
        assertTrue(service.buildAuthorizeContext(user, request("openid profile email")).consentRequired());
        issue("openid profile email");
        assertEquals("openid profile email", currentGrant.get().getScopes());
    }

    @Test
    void contextWithoutLoginDoesNotDiscloseAuthorizedScopes() {
        issue("openid email");
        var context = service.buildAuthorizeContext(null, request("openid email"));
        assertFalse(context.loggedIn()); assertTrue(context.consentRequired()); assertTrue(context.authorizedScopes().isEmpty());
    }

    @Test
    void authorizationDenialReturnsStateWithoutCodeOrGrant() {
        var source = request("openid");
        var denied = new OfficialAuthorizeRequest(source.clientId(), CALLBACK, "code", "openid", "opaque state+&", source.nonce(),
            source.codeChallenge(), "S256", false);
        String callback = service.approveAuthorization(user, denied).get("redirectUrl");
        assertTrue(callback.contains("error=access_denied")); assertFalse(callback.contains("code="));
        assertTrue(callback.contains("state=opaque+state%2B%26")); assertNull(currentGrant.get());
    }

    @Test
    void exactCallbackChallengeMethodAndNonceAreRequired() {
        var source = request("openid");
        assertThrows(Oauth2Exception.class, () -> service.buildAuthorizeContext(user,
            new OfficialAuthorizeRequest(source.clientId(), CALLBACK + "?other=1", "code", "openid", source.state(), source.nonce(), source.codeChallenge(), "S256", true)));
        assertThrows(Oauth2Exception.class, () -> service.buildAuthorizeContext(user,
            new OfficialAuthorizeRequest(source.clientId(), CALLBACK, "code", "openid", source.state(), source.nonce(), source.codeChallenge(), "plain", true)));
        assertThrows(Oauth2Exception.class, () -> service.buildAuthorizeContext(user,
            new OfficialAuthorizeRequest(source.clientId(), CALLBACK, "code", "openid", source.state(), null, source.codeChallenge(), "S256", true)));
        assertThrows(Oauth2Exception.class, () -> service.buildAuthorizeContext(user, request("openid unknown")));
    }

    @Test
    void publicExchangeEnforcesPkceAndConsumesAuthorizationCodeOnce() {
        String code = issue("openid");
        exchange(code);
        assertThrows(Oauth2Exception.class, () -> exchange(code));
        String bad = issue("openid");
        assertThrows(Oauth2Exception.class, () -> service.exchangeAuthorizationCode("authorization_code", bad, app.getClientId(), null, CALLBACK,
            "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx", null));
        assertThrows(Oauth2Exception.class, () -> exchange(bad));
    }

    @Test
    void callbackMustMatchTheCodeEvenWhenBothCallbacksAreRegistered() {
        app.setRedirectUris(CALLBACK + "\nhttps://example.test/other");
        String code = issue("openid");
        assertThrows(Oauth2Exception.class, () -> service.exchangeAuthorizationCode("authorization_code", code, app.getClientId(), null,
            "https://example.test/other", VERIFIER, null));
    }

    @Test
    void consoleCodeRequiresExpectedNonceAndCurrentSuperAdmin() {
        app.setClientId(OfficialAuthorizationService.CONSOLE_CLIENT_ID);
        String code = issue("openid profile email");
        assertThrows(Oauth2Exception.class, () -> service.exchangeForConsole(code, CALLBACK, VERIFIER, "wrong-nonce"));
        String valid = issue("openid profile email");
        var result = service.exchangeForConsole(valid, CALLBACK, VERIFIER, "nonce-123");
        assertEquals(user.getUuid(), result.user().getUuid());
        assertTrue(service.isConsoleAuthorizationActive(user.getUuid(), result.authorizationId()));
        user.setSystemRole("ADMIN");
        assertFalse(service.isConsoleAuthorizationActive(user.getUuid(), result.authorizationId()));
        assertThrows(Oauth2Exception.class, () -> service.approveAuthorization(user, request("openid")));
    }

    @Test
    void demotionBetweenAuthorizationAndExchangeDeniesConsoleLogin() {
        app.setClientId(OfficialAuthorizationService.CONSOLE_CLIENT_ID);
        String code = issue("openid"); user.setSystemRole("USER");
        Oauth2Exception denied = assertThrows(Oauth2Exception.class, () -> service.exchangeForConsole(code, CALLBACK, VERIFIER, "nonce-123"));
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatus());
    }

    @Test
    void confidentialClientRequiresSecretAndStillRequiresPkce() {
        app.setClientType("confidential"); app.setClientSecretHash("encoded-secret");
        when(passwords.matches("secret", "encoded-secret")).thenReturn(true);
        String code = issue("openid");
        assertThrows(Oauth2Exception.class, () -> exchange(code));
        assertNotNull(service.exchangeAuthorizationCode("authorization_code", code, app.getClientId(), "secret", CALLBACK, VERIFIER, null).get("id_token"));
    }

    @Test
    void browserOriginMustBelongToTheRequestingApplication() {
        String code = issue("openid");
        assertThrows(Oauth2Exception.class, () -> service.exchangeAuthorizationCode("authorization_code", code, app.getClientId(), null,
            CALLBACK, VERIFIER, "https://another.test"));
        Map<String, Object> response = exchange(code);
        assertThrows(Oauth2Exception.class, () -> service.buildUserInfo((String)response.get("access_token"), "https://another.test"));
    }

    @Test
    void tokensExposeRealIdentityAndOnlyGrantedProfileFields() {
        Map<String, Object> response = exchange(issue("openid profile"));
        String access = (String)response.get("access_token");
        assertTrue(access.startsWith("ofa_")); assertFalse(access.contains("."));
        Map<String, Object> info = service.buildUserInfo(access, "https://example.test");
        assertEquals(user.getUuid(), info.get("sub")); assertEquals(user.getUuid(), info.get("uuid"));
        assertEquals(user.getUsername(), info.get("username")); assertFalse(info.containsKey("email")); assertFalse(info.containsKey("real_name"));
        var token = Jwts.parser().verifyWith((java.security.interfaces.RSAPublicKey)publicKey())
            .build().parseSignedClaims((String)response.get("id_token"));
        assertEquals("RS256", token.getHeader().getAlgorithm()); assertEquals(user.getUuid(), token.getPayload().getSubject());
        assertEquals("nonce-123", token.getPayload().get("nonce")); assertFalse(token.getPayload().containsKey("email"));
        assertEquals(7200L, response.get("expires_in"));
    }

    private java.security.PublicKey publicKey() {
        try {
            var tree = new ObjectMapper().readTree(rsa.getJwksJson()).get("keys").get(0);
            var spec = new java.security.spec.RSAPublicKeySpec(new java.math.BigInteger(1, Base64.getUrlDecoder().decode(tree.get("n").asText())),
                new java.math.BigInteger(1, Base64.getUrlDecoder().decode(tree.get("e").asText())));
            return java.security.KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    @Test
    void revocationAndReapprovalNeverReviveExistingTokensOrConsoleSessions() {
        app.setClientId(OfficialAuthorizationService.CONSOLE_CLIENT_ID);
        Map<String, Object> response = exchange(issue("openid profile"));
        Long oldId = currentGrant.get().getId();
        service.revokeAuthorization(user, app.getClientId());
        Oauth2Exception revoked = assertThrows(Oauth2Exception.class, () -> service.buildUserInfo((String)response.get("access_token"), null));
        assertEquals(HttpStatus.UNAUTHORIZED, revoked.getStatus());
        assertFalse(service.isConsoleAuthorizationActive(user.getUuid(), oldId));
        issue("openid profile"); assertNotEquals(oldId, currentGrant.get().getId());
        assertThrows(Oauth2Exception.class, () -> service.buildUserInfo((String)response.get("access_token"), null));
        assertFalse(service.isConsoleAuthorizationActive(user.getUuid(), oldId));
    }

    @Test
    void disablingApplicationOrReducingItsScopesInvalidatesExistingToken() {
        Map<String, Object> response = exchange(issue("openid email"));
        app.setIsActive(false);
        assertThrows(Oauth2Exception.class, () -> service.buildUserInfo((String)response.get("access_token"), null));
        app.setIsActive(true); app.setScopes("openid");
        Oauth2Exception revoked = assertThrows(Oauth2Exception.class, () -> service.buildUserInfo((String)response.get("access_token"), null));
        assertEquals(HttpStatus.UNAUTHORIZED, revoked.getStatus());
    }

    @Test
    void expiredCodeIsRejectedEvenIfRedisEntryStillExists() throws Exception {
        String code = issue("openid");
        String key = "official:code:" + OfficialAuthorizationService.hashOpaque(code);
        ObjectMapper mapper = new ObjectMapper();
        var payload = mapper.readValue(storage.get(key), OfficialAuthorizationService.CodePayload.class);
        storage.put(key, mapper.writeValueAsString(new OfficialAuthorizationService.CodePayload(payload.clientId(), payload.userId(),
            payload.authorizationId(), payload.redirectUri(), payload.scope(), payload.nonce(), payload.challenge(), 1L)));
        assertThrows(Oauth2Exception.class, () -> exchange(code));
    }

    @Test
    void revokedPendingCodeCannotBeExchangedAfterReconsent() {
        String oldCode = issue("openid profile");
        service.revokeAuthorization(user, app.getClientId());
        issue("openid profile");
        assertThrows(Oauth2Exception.class, () -> exchange(oldCode));
    }

    @Test
    void expiredAccessTokenIsRejectedEvenIfRedisEntryStillExists() throws Exception {
        Map<String, Object> response = exchange(issue("openid"));
        String token = (String)response.get("access_token");
        String key = "official:access:" + OfficialAuthorizationService.hashOpaque(token);
        ObjectMapper mapper = new ObjectMapper();
        var payload = mapper.readValue(storage.get(key), OfficialAuthorizationService.AccessPayload.class);
        storage.put(key, mapper.writeValueAsString(new OfficialAuthorizationService.AccessPayload(payload.clientId(), payload.userId(),
            payload.authorizationId(), payload.scope(), 1L)));
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(Oauth2Exception.class, () -> service.buildUserInfo(token, null)).getStatus());
        assertFalse(storage.containsKey("official:access:" + token));
    }

    @Test
    void registeredOriginOfAnotherClientCannotReadAnOpaqueToken() {
        String code = issue("openid");
        Map<String, Object> response = exchange(code);
        assertThrows(Oauth2Exception.class, () -> service.buildUserInfo((String)response.get("access_token"), "null"));
        assertThrows(Oauth2Exception.class, () -> service.buildUserInfo("passport-access-token", null));
    }

    @Test
    void publicClientCannotSubmitEvenAnEmptySecret() {
        String code = issue("openid");
        assertThrows(Oauth2Exception.class, () -> service.exchangeAuthorizationCode("authorization_code", code, app.getClientId(), "", CALLBACK, VERIFIER, null));
        assertNotNull(exchange(code).get("access_token"));
    }

    @Test
    void malformedStoredCodeAndUnscopedStoredTokenFailClosed() throws Exception {
        String code = issue("openid"); ObjectMapper mapper = new ObjectMapper();
        String codeKey = "official:code:" + OfficialAuthorizationService.hashOpaque(code);
        var payload = mapper.readValue(storage.get(codeKey), OfficialAuthorizationService.CodePayload.class);
        storage.put(codeKey, mapper.writeValueAsString(new OfficialAuthorizationService.CodePayload(payload.clientId(), payload.userId(),
            payload.authorizationId(), payload.redirectUri(), payload.scope(), null, null, payload.expiresAt())));
        assertThrows(Oauth2Exception.class, () -> exchange(code));
        Map<String, Object> response = exchange(issue("openid"));
        String access = (String)response.get("access_token"); String tokenKey = "official:access:" + OfficialAuthorizationService.hashOpaque(access);
        var token = mapper.readValue(storage.get(tokenKey), OfficialAuthorizationService.AccessPayload.class);
        storage.put(tokenKey, mapper.writeValueAsString(new OfficialAuthorizationService.AccessPayload(token.clientId(), token.userId(), token.authorizationId(), "", token.expiresAt())));
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(Oauth2Exception.class, () -> service.buildUserInfo(access, null)).getStatus());
    }

    @Test
    void productionOriginsRequireHttpsAndLocalhostMayUseHttp() {
        assertEquals("https://example.test", OfficialAuthorizationService.originOf(CALLBACK));
        assertEquals("http://localhost:5174", OfficialAuthorizationService.originOf("http://localhost:5174/auth/callback"));
        assertNull(OfficialAuthorizationService.originOf("http://example.test/callback"));
        assertNull(OfficialAuthorizationService.originOf("https://example.test/callback#fragment"));
    }
}
