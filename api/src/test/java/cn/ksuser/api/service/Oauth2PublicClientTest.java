package cn.ksuser.api.service;

import cn.ksuser.api.dto.Oauth2AppCreateRequest;
import cn.ksuser.api.dto.Oauth2AuthorizeRequest;
import cn.ksuser.api.entity.Oauth2Application;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserOauth2Authorization;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.repository.Oauth2ApplicationRepository;
import cn.ksuser.api.repository.UserOauth2AuthorizationRepository;
import cn.ksuser.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Oauth2PublicClientTest {
    private static final String REDIRECT = "https://native.example/ksuser/callback";
    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
    private final Oauth2ApplicationRepository applications = mock(Oauth2ApplicationRepository.class);
    private final UserOauth2AuthorizationRepository grants = mock(UserOauth2AuthorizationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked") private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final MobileAppPlatformService platforms = mock(MobileAppPlatformService.class);
    private final Oauth2TokenService tokens = mock(Oauth2TokenService.class);
    private final Map<String, String> memory = new HashMap<>();
    private final Map<String, String> currentRevisions = new HashMap<>();
    private final Oauth2Application app = new Oauth2Application();
    private final User user = new User();
    private Oauth2PlatformService service;

    @BeforeEach void setup() {
        service = new Oauth2PlatformService(applications, grants, users, passwords, redis, tokens);
        service.setMobileAppPlatformService(platforms);
        when(platforms.normalize(any())).thenAnswer(inv -> inv.getArgument(0));
        when(platforms.redirectUris(any())).thenReturn(REDIRECT);
        currentRevisions.put("ios", "revision-one");
        when(platforms.nativeRevision(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            String revision = currentRevisions.get(inv.getArgument(1));
            if (revision == null) throw new Oauth2Exception(org.springframework.http.HttpStatus.BAD_REQUEST,
                "invalid_request", cn.ksuser.api.result.ApiResultCode.NATIVE_AUTHORIZATION_INVALID);
            return revision;
        });
        when(platforms.matchingRevisions(anyString(), anyString())).thenAnswer(inv -> Map.copyOf(currentRevisions));
        when(platforms.matchesAnyRevision(anyString(), anyString(), anyMap())).thenAnswer(inv -> {
            Map<String, String> frozen = inv.getArgument(2);
            return frozen.entrySet().stream().anyMatch(entry -> entry.getValue().equals(currentRevisions.get(entry.getKey())));
        });
        ReflectionTestUtils.setField(service, "maxAppsPerUser", 5);
        ReflectionTestUtils.setField(service, "authorizationCodeExpirationSeconds", 300L);
        ReflectionTestUtils.setField(service, "jwtSecret", "ksuser-very-secret-key-2026-public-client-test");
        app.setAppId("native-client"); app.setApplicationType("mobile"); app.setUnionidStatus("approved"); app.setClientType("public"); app.setOwnerUserId(99L);
        app.setAppName("Native App"); app.setRedirectUri(REDIRECT); app.setScopes("profile email");
        app.setContactInfo("support@example.com"); app.setIsActive(true);
        user.setId(1L); user.setUuid("real-user-uuid"); user.setVerificationType("personal");
        when(applications.findByAppId("native-client")).thenReturn(Optional.of(app));
        when(applications.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(users.findById(1L)).thenReturn(Optional.of(user));
        UserOauth2Authorization grant = new UserOauth2Authorization();
        grant.setId(42L); grant.setUserId(1L); grant.setAppId("native-client"); grant.setScopes("profile email");
        when(grants.findByUserIdAndAppId(1L, "native-client")).thenReturn(Optional.of(grant));
        when(grants.save(any())).thenAnswer(inv -> { UserOauth2Authorization saved = inv.getArgument(0); saved.setId(42L); return saved; });
        when(redis.opsForValue()).thenReturn(values);
        doAnswer(inv -> { memory.put(inv.getArgument(0), inv.getArgument(1)); return null; })
            .when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.getAndDelete(anyString())).thenAnswer(inv -> memory.remove(inv.getArgument(0)));
        when(tokens.generateAccessToken(anyString(), anyLong(), anyLong(), anyString(), anyString(), anyString(), nullable(String.class), anyLong()))
            .thenReturn("oauth-access-token");
        when(tokens.getAccessTokenExpiresInSeconds()).thenReturn(7200);
    }

    private Oauth2AuthorizeRequest request() {
        var request = new Oauth2AuthorizeRequest();
        request.setClientId("native-client"); request.setRedirectUri(REDIRECT); request.setResponseType("code");
        request.setScope("profile"); request.setState("original-state");
        request.setCodeChallenge(CHALLENGE); request.setCodeChallengeMethod("S256");
        return request;
    }

    private String approve(Oauth2AuthorizeRequest request) {
        return code(service.approveAuthorization(user, request).getRedirectUrl());
    }

    private String approveNative(String platform) {
        return code(service.approveAuthorization(user, request(), platform, currentRevisions.get(platform)).getRedirectUrl());
    }

    private String code(String redirectUrl) {
        String query = URI.create(redirectUrl).getRawQuery();
        return java.util.Arrays.stream(query.split("&")).filter(part -> part.startsWith("code="))
            .findFirst().orElseThrow().substring(5);
    }

    private Oauth2AppCreateRequest registration(String type) {
        var request = new Oauth2AppCreateRequest();
        request.setAppName("SDK Client"); request.setClientType(type); request.setRedirectUri(REDIRECT);
        if ("public".equals(type)) {
            request.setApplicationType("mobile");
            request.setPlatforms(new cn.ksuser.api.dto.MobileAppPlatforms(
                new cn.ksuser.api.dto.MobileAppPlatforms.Ios("com.example.app", "TEAM123456", REDIRECT, null, null, null, null, null), null));
        }
        request.setContactInfo("support@example.com"); request.setScopes(List.of("profile"));
        return request;
    }

    @Test void publicRegistrationProducesNoSecretAndKeepsTypeInResponses() {
        var created = service.createApplication(user, registration("public"));
        assertEquals("public", created.getClientType()); assertNull(created.getAppSecret());
        ArgumentCaptor<Oauth2Application> captured = ArgumentCaptor.forClass(Oauth2Application.class);
        verify(applications).saveAndFlush(captured.capture());
        assertNull(captured.getValue().getAppSecretHash());
        verify(passwords, never()).encode(any());
        when(applications.findAllByOwnerUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(app));
        assertEquals("public", service.listApplications(user).getApps().get(0).getClientType());
    }

    @Test void omittedClientTypePreservesConfidentialRegistration() {
        when(passwords.encode(any())).thenReturn("encoded-secret");
        var created = service.createApplication(user, registration(null));
        assertEquals("confidential", created.getClientType()); assertTrue(created.getAppSecret().startsWith("ksapp_secret_"));
        ArgumentCaptor<Oauth2Application> captured = ArgumentCaptor.forClass(Oauth2Application.class);
        verify(applications).saveAndFlush(captured.capture()); assertEquals("encoded-secret", captured.getValue().getAppSecretHash());
        app.setClientType(null); assertEquals("confidential", app.getClientType());
    }

    @Test void invalidClientTypeIsRejectedBeforeSaving() {
        assertThrows(Oauth2Exception.class, () -> service.createApplication(user, registration("mobile")));
        verify(applications, never()).saveAndFlush(any());
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", "\t"})
    void publicAuthorizationRequiresNonblankState(String state) {
        var request = request(); request.setState(state);
        assertEquals("invalid_request", assertThrows(Oauth2Exception.class, () -> service.approveAuthorization(user, request)).getError());
        verify(grants, never()).save(any());
    }

    @Test void publicContextAndApprovalValidatePkceBeforeCreatingAnyGrant() {
        assertThrows(Oauth2Exception.class, () -> service.buildAuthorizeContext(null, "native-client", REDIRECT, "code", "profile"));
        assertDoesNotThrow(() -> service.buildAuthorizeContext(null, "native-client", REDIRECT, "code", "profile", "state", CHALLENGE, "S256"));
        var missing = request(); missing.setCodeChallenge(null);
        assertThrows(Oauth2Exception.class, () -> service.approveAuthorization(user, missing));
        var plain = request(); plain.setCodeChallengeMethod("plain");
        assertThrows(Oauth2Exception.class, () -> service.approveAuthorization(user, plain));
        var malformed = request(); malformed.setCodeChallenge("x".repeat(42) + "!");
        assertThrows(Oauth2Exception.class, () -> service.approveAuthorization(user, malformed));
        verify(grants, never()).save(any()); assertTrue(memory.isEmpty());
    }

    @Test void publicCodeExchangesOnlyOnceWithOriginalVerifierAndReturnsOauthIdentity() {
        String code = approve(request());
        assertTrue(memory.values().iterator().next().contains(CHALLENGE));
        Map<String, Object> result = service.exchangeAuthorizationCode("authorization_code", code, "native-client", null, REDIRECT, VERIFIER);
        assertEquals("oauth-access-token", result.get("access_token"));
        assertTrue(result.get("openid").toString().startsWith("oid_"));
        assertTrue(result.get("unionid").toString().startsWith("uid_"));
        assertFalse(result.containsKey("id_token")); assertFalse(result.containsKey("uuid"));
        assertEquals("invalid_grant", assertThrows(Oauth2Exception.class,
            () -> service.exchangeAuthorizationCode("authorization_code", code, "native-client", null, REDIRECT, VERIFIER)).getError());
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"short", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
    void publicCodeRejectsMissingMalformedOrWrongVerifier(String verifier) {
        String code = approve(request());
        assertEquals("invalid_grant", assertThrows(Oauth2Exception.class,
            () -> service.exchangeAuthorizationCode("authorization_code", code, "native-client", null, REDIRECT, verifier)).getError());
        verify(tokens, never()).generateAccessToken(anyString(), anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString(), anyLong());
    }

    @ParameterizedTest @ValueSource(strings = {"", "embedded-secret"})
    void publicClientsRejectAnySubmittedSecretBeforeConsumingCode(String secret) {
        String code = approve(request());
        assertEquals("invalid_client", assertThrows(Oauth2Exception.class,
            () -> service.exchangeAuthorizationCode("authorization_code", code, "native-client", secret, REDIRECT, VERIFIER)).getError());
        assertFalse(memory.isEmpty());
    }

    @Test void codesAreBoundToExactClientAndRegisteredRedirect() {
        app.setRedirectUri(REDIRECT + ";https://native.example/another-callback");
        String code = approve(request());
        assertThrows(Oauth2Exception.class, () -> service.exchangeAuthorizationCode("authorization_code", code, "native-client", null,
            "https://native.example/another-callback", VERIFIER));
        String otherCode = approve(request());
        var other = new Oauth2Application(); other.setAppId("other-client"); other.setClientType("public");
        other.setIsActive(true); other.setRedirectUri(REDIRECT);
        when(applications.findByAppId("other-client")).thenReturn(Optional.of(other));
        assertThrows(Oauth2Exception.class, () -> service.exchangeAuthorizationCode("authorization_code", otherCode, "other-client", null, REDIRECT, VERIFIER));
    }

    @Test void confidentialClientOptingIntoPkceCannotDowngradeAtTokenExchange() {
        app.setClientType("confidential"); app.setAppSecretHash("secret-hash");
        when(passwords.matches("server-secret", "secret-hash")).thenReturn(true);
        String code = approve(request());
        assertThrows(Oauth2Exception.class, () -> service.exchangeAuthorizationCode("authorization_code", code, "native-client", "server-secret", REDIRECT));
        String valid = approve(request());
        assertEquals("oauth-access-token", service.exchangeAuthorizationCode("authorization_code", valid, "native-client", "server-secret", REDIRECT, VERIFIER).get("access_token"));
        var partial = request(); partial.setCodeChallengeMethod(null);
        assertThrows(Oauth2Exception.class, () -> service.approveAuthorization(user, partial));
    }

    @Test void publicClientRejectsLegacyCodesWithoutPkceButConfidentialKeepsThem() {
        String legacy = "{\"userId\":1,\"ownerUserId\":99,\"clientId\":\"native-client\",\"redirectUri\":\"" + REDIRECT
            + "\",\"scope\":\"profile\",\"authorizationId\":42}";
        memory.put("oauth2:auth-code:legacy", legacy);
        assertThrows(Oauth2Exception.class, () -> service.exchangeAuthorizationCode("authorization_code", "legacy", "native-client", null, REDIRECT, VERIFIER));
        app.setApplicationType("website"); app.setClientType(null); app.setAppSecretHash("secret-hash");
        when(passwords.matches("server-secret", "secret-hash")).thenReturn(true);
        memory.put("oauth2:auth-code:legacy", legacy);
        assertEquals("oauth-access-token", service.exchangeAuthorizationCode("authorization_code", "legacy", "native-client", "server-secret", REDIRECT).get("access_token"));
    }

    @Test void websiteCannotRegisterOrAuthorizeAsNativePublicClient() {
        var input = registration("public"); input.setApplicationType("website"); input.setPlatforms(null);
        assertThrows(Oauth2Exception.class, () -> service.createApplication(user, input));
        app.setApplicationType("website");
        assertThrows(Oauth2Exception.class, () -> service.validateNativeClient("native-client", "ios", REDIRECT));
    }

    @Test void unapprovedAppsOmitUnionidFromNewTokensAndUserinfoEvenWithOldClaims() {
        app.setUnionidStatus("revoked");
        String code = approve(request());
        var result = service.exchangeAuthorizationCode("authorization_code", code, "native-client", null, REDIRECT, VERIFIER);
        assertFalse(result.containsKey("unionid"));
        verify(tokens).generateAccessToken(eq("native-client"), eq(99L), eq(1L), anyString(), anyString(), anyString(), isNull(), eq(42L));
        when(tokens.parse("old-token")).thenReturn(new Oauth2TokenService.ParsedOauth2AccessToken(
            "native-client", 99L, 1L, user.getUuid(), "profile", "old-openid", "previous-unionid", 42L,
            new java.util.Date(System.currentTimeMillis() + 60000)));
        assertFalse(service.buildUserInfo("old-token").containsKey("unionid"));
        app.setUnionidStatus("approved");
        String unionid = service.buildUserInfo("old-token").get("unionid").toString();
        assertNotEquals("previous-unionid", unionid);
        app.setOwnerUserId(100L);
        assertNotEquals(unionid, service.buildUserInfo("old-token").get("unionid"));
    }

    @Test void sameOwnerAppsShareUnionidButHaveSeparateOpenids() {
        String one = approve(request());
        var first = service.exchangeAuthorizationCode("authorization_code", one, "native-client", null, REDIRECT, VERIFIER);
        var second = new Oauth2Application(); second.setAppId("second-client"); second.setApplicationType("mobile");
        second.setClientType("public"); second.setOwnerUserId(99L); second.setUnionidStatus("approved");
        second.setIsActive(true); second.setRedirectUri(REDIRECT); second.setScopes("profile");
        when(applications.findByAppId("second-client")).thenReturn(Optional.of(second));
        var grant = new UserOauth2Authorization(); grant.setId(43L); grant.setScopes("profile");
        when(grants.findByUserIdAndAppId(1L, "second-client")).thenReturn(Optional.of(grant));
        var req = request(); req.setClientId("second-client");
        String two = approve(req);
        // The save mock assigns the same grant ID; match the persisted authorization identity.
        grant.setId(42L);
        var other = service.exchangeAuthorizationCode("authorization_code", two, "second-client", null, REDIRECT, VERIFIER);
        assertEquals(first.get("unionid"), other.get("unionid"));
        assertNotEquals(first.get("openid"), other.get("openid"));
    }

    @Test void changingPlatformRegistrationInvalidatesPreviouslyIssuedCodes() {
        String code = approve(request());
        currentRevisions.put("ios", "new-revision");
        assertThrows(Oauth2Exception.class, () -> service.exchangeAuthorizationCode("authorization_code", code, "native-client", null, REDIRECT, VERIFIER));
        verify(tokens, never()).generateAccessToken(anyString(), anyLong(), anyLong(), anyString(), anyString(), anyString(), nullable(String.class), anyLong());
    }

    @Test void androidOnlyNativeAndBrowserRequestsExchangeWithoutAnIosPlatform() {
        currentRevisions.clear(); currentRevisions.put("android", "android-one");
        String nativeCode = approveNative("android");
        assertTrue(memory.get("oauth2:auth-code:" + nativeCode).contains("\"mobilePlatformRevisions\":{\"android\":\"android-one\"}"));
        assertEquals("oauth-access-token", service.exchangeAuthorizationCode("authorization_code", nativeCode,
            "native-client", null, REDIRECT, VERIFIER).get("access_token"));
        String browserCode = approve(request());
        assertEquals("oauth-access-token", service.exchangeAuthorizationCode("authorization_code", browserCode,
            "native-client", null, REDIRECT, VERIFIER).get("access_token"));
        verify(platforms, never()).nativeRevision("native-client", "ios", REDIRECT);
    }

    @Test void nativeAndroidCodeCannotBeRescuedBySharedIosCallbackAfterAndroidChanges() {
        currentRevisions.put("android", "android-one");
        String code = approveNative("android");
        currentRevisions.put("android", "android-two");
        assertThrows(Oauth2Exception.class, () -> service.exchangeAuthorizationCode("authorization_code", code,
            "native-client", null, REDIRECT, VERIFIER));
        verify(tokens, never()).generateAccessToken(anyString(), anyLong(), anyLong(), anyString(), anyString(), anyString(), nullable(String.class), anyLong());
    }

    @Test void changingIosDoesNotInvalidateAnAndroidNativeCode() {
        currentRevisions.put("android", "android-one");
        String code = approveNative("android");
        currentRevisions.put("ios", "ios-two");
        assertEquals("oauth-access-token", service.exchangeAuthorizationCode("authorization_code", code,
            "native-client", null, REDIRECT, VERIFIER).get("access_token"));
    }

    @Test void browserCodeUsesOnlyThePlatformsVerifiedAtIssuance() {
        String iosOnly = approve(request());
        currentRevisions.put("android", "android-one"); currentRevisions.put("ios", "ios-two");
        assertThrows(Oauth2Exception.class, () -> service.exchangeAuthorizationCode("authorization_code", iosOnly,
            "native-client", null, REDIRECT, VERIFIER));
        String shared = approve(request());
        currentRevisions.put("ios", "ios-three");
        assertEquals("oauth-access-token", service.exchangeAuthorizationCode("authorization_code", shared,
            "native-client", null, REDIRECT, VERIFIER).get("access_token"));
    }

    @Test void nativeIssuanceChecksTheFrozenRevisionBeforeRecordingAGrant() {
        currentRevisions.put("android", "android-new");
        assertThrows(Oauth2Exception.class, () -> service.approveAuthorization(user, request(), "android", "android-old"));
        verify(grants, never()).save(any()); assertTrue(memory.isEmpty());
    }

    @Test void oldIosCodeWithoutPlatformSnapshotKeepsItsOriginalIosBinding() throws Exception {
        String code = approveNative("ios");
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var json = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(memory.get("oauth2:auth-code:" + code));
        json.remove("mobilePlatformRevisions"); memory.put("oauth2:auth-code:" + code, mapper.writeValueAsString(json));
        assertEquals("oauth-access-token", service.exchangeAuthorizationCode("authorization_code", code,
            "native-client", null, REDIRECT, VERIFIER).get("access_token"));
        String obsolete = approveNative("ios");
        json = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(memory.get("oauth2:auth-code:" + obsolete));
        json.remove("mobilePlatformRevisions"); memory.put("oauth2:auth-code:" + obsolete, mapper.writeValueAsString(json));
        currentRevisions.remove("ios"); currentRevisions.put("android", "revision-one");
        assertThrows(Oauth2Exception.class, () -> service.exchangeAuthorizationCode("authorization_code", obsolete,
            "native-client", null, REDIRECT, VERIFIER));
    }

    @Test void nativeRegistrationRequiresActivePublicClient() {
        assertDoesNotThrow(() -> service.validateNativeClient("native-client"));
        app.setClientType("confidential"); assertThrows(Oauth2Exception.class, () -> service.validateNativeClient("native-client"));
        app.setClientType("public"); app.setIsActive(false);
        assertThrows(Oauth2Exception.class, () -> service.validateNativeClient("native-client"));
    }
}
