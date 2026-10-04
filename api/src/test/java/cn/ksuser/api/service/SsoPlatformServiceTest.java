package cn.ksuser.api.service;

import cn.ksuser.api.dto.SsoClientCreateRequest;
import cn.ksuser.api.dto.SsoClientCreateResponse;
import cn.ksuser.api.dto.SsoAuthorizeApproveResponse;
import cn.ksuser.api.dto.SsoAuthorizeRequest;
import cn.ksuser.api.entity.OidcClient;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserSsoAuthorization;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.repository.OidcClientRepository;
import cn.ksuser.api.repository.UserRepository;
import cn.ksuser.api.repository.UserSsoAuthorizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Date;
import java.util.Map;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SsoPlatformServiceTest {

    private OidcClientRepository oidcClientRepository;
    private UserSsoAuthorizationRepository authorizationRepository;
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private SsoPlatformService service;
    private SsoTokenService ssoTokenService;

    private UserSsoAuthorization authorization(long id) {
        UserSsoAuthorization record = new UserSsoAuthorization();
        record.setId(id);
        record.setUserId(1L);
        record.setClientId("ksuser_auth_1");
        record.setScopes("openid profile email");
        return record;
    }

    private void stubUserInfoToken(Long authorizationId, String scope) {
        when(ssoTokenService.parseAccessToken("access-token-demo")).thenReturn(
            new SsoTokenService.ParsedSsoAccessToken(
                "ksuser_auth_1", 1L, "sub-demo", scope, "ksuser-auth", authorizationId,
                new Date(System.currentTimeMillis() + 60_000)
            )
        );
        when(oidcClientRepository.findByClientId("ksuser_auth_1")).thenReturn(Optional.of(buildClient()));
    }

    @BeforeEach
    void setUp() {
        oidcClientRepository = mock(OidcClientRepository.class);
        authorizationRepository = mock(UserSsoAuthorizationRepository.class);
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        ssoTokenService = mock(SsoTokenService.class);
        OidcRsaKeyService oidcRsaKeyService = new OidcRsaKeyService("test-oidc-rsa-key-seed", "ksuser-very-secret-key-2026-abc");

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service = new SsoPlatformService(
            oidcClientRepository,
            authorizationRepository,
            userRepository,
            passwordEncoder,
            redisTemplate,
            ssoTokenService,
            oidcRsaKeyService
        );
        ReflectionTestUtils.setField(service, "maxClients", 20);
        ReflectionTestUtils.setField(service, "jwtSecret", "ksuser-very-secret-key-2026-abc-platform-test");
    }

    @Test
    void shouldCreateClientWithSemicolonSeparatedRedirectUris() {
        when(oidcClientRepository.count()).thenReturn(0L);
        when(oidcClientRepository.existsByClientId(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-secret");
        when(oidcClientRepository.save(any(OidcClient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SsoClientCreateRequest request = new SsoClientCreateRequest();
        request.setClientName("OIDC Demo");
        request.setRedirectUris(List.of(
            "https://demo.example.com/callback; http://localhost:9002/callback",
            "https://demo.example.com/callback"
        ));
        request.setPostLogoutRedirectUris(List.of("https://demo.example.com/logout;http://localhost:9002/logout"));
        request.setScopes(List.of("openid", "profile"));
        request.setAudiences(List.of("ksuser-auth"));
        request.setRequirePkce(true);

        SsoClientCreateResponse response = service.createClient(buildAdminUser(), request);

        assertEquals(List.of("https://demo.example.com/callback", "http://localhost:9002/callback"), response.getRedirectUris());
        assertEquals(List.of("https://demo.example.com/logout", "http://localhost:9002/logout"), response.getPostLogoutRedirectUris());

        ArgumentCaptor<OidcClient> captor = ArgumentCaptor.forClass(OidcClient.class);
        verify(oidcClientRepository).save(captor.capture());
        assertEquals("https://demo.example.com/callback;http://localhost:9002/callback", captor.getValue().getRedirectUris());
        assertEquals("https://demo.example.com/logout;http://localhost:9002/logout", captor.getValue().getPostLogoutRedirectUris());
    }

    @Test
    void shouldRejectNonHttpsNonLocalhostRedirectUri() {
        when(oidcClientRepository.count()).thenReturn(0L);

        SsoClientCreateRequest request = new SsoClientCreateRequest();
        request.setClientName("OIDC Demo");
        request.setRedirectUris(List.of("https://demo.example.com/callback;http://127.0.0.1:9002/callback"));

        Oauth2Exception exception = assertThrows(
            Oauth2Exception.class,
            () -> service.createClient(buildAdminUser(), request)
        );

        assertEquals("invalid_request", exception.getError());
        assertEquals(400, exception.getStatus().value());
    }

    @Test
    void shouldEncodeStateWhenApprovingAuthorization() {
        User user = buildAdminUser();
        OidcClient client = buildClient();

        when(oidcClientRepository.findByClientId("ksuser_auth_1")).thenReturn(Optional.of(client));
        when(authorizationRepository.save(any(UserSsoAuthorization.class))).thenAnswer(invocation -> {
            UserSsoAuthorization saved = invocation.getArgument(0);
            saved.setId(42L);
            return saved;
        });

        SsoAuthorizeRequest request = new SsoAuthorizeRequest();
        request.setClientId("ksuser_auth_1");
        request.setRedirectUri("https://ezbookkeeping.example.com/oauth2/callback/ksuser");
        request.setResponseType("code");
        request.setScope("openid profile email");
        request.setState("eyJ1cmwiOiIvIiwidiI6IjEuMCIsIm4iOiJhK2I9PSJ9");

        SsoAuthorizeApproveResponse response = service.approveAuthorization(user, request);

        String redirectUrl = response.getRedirectUrl();
        org.junit.jupiter.api.Assertions.assertTrue(redirectUrl.contains("code=ssocode_"));
        org.junit.jupiter.api.Assertions.assertTrue(redirectUrl.contains("state=eyJ1cmwiOiIvIiwidiI6IjEuMCIsIm4iOiJhK2I9PSJ9"));
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("sso:auth-code:" + redirectUrl.substring(redirectUrl.indexOf("code=") + 5, redirectUrl.indexOf("&state="))), payload.capture(), any());
        org.junit.jupiter.api.Assertions.assertTrue(payload.getValue().contains("\"authorizationId\":42"));
    }

    @Test
    void shouldExchangeCodeForCurrentAuthorization() {
        when(oidcClientRepository.findByClientId("ksuser_auth_1")).thenReturn(Optional.of(buildClient()));
        when(passwordEncoder.matches("client-secret-demo", "encoded-secret")).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(buildAdminUser()));
        when(valueOperations.getAndDelete("sso:auth-code:ssocode_demo")).thenReturn(
            "{" +
                "\"userId\":1," +
                "\"clientId\":\"ksuser_auth_1\"," +
                "\"redirectUri\":\"https://ezbookkeeping.example.com/oauth2/callback/ksuser\"," +
                "\"scope\":\"openid profile\"," +
                "\"nonce\":null," +
                "\"codeChallenge\":null," +
                "\"codeChallengeMethod\":null," +
                "\"authorizationId\":42" +
                "}"
        );
        when(authorizationRepository.findByUserIdAndClientId(1L, "ksuser_auth_1"))
            .thenReturn(Optional.of(authorization(42L)));
        when(ssoTokenService.generateAccessToken(eq("ksuser_auth_1"), eq(1L), anyString(),
            eq("openid profile"), eq("ksuser-auth"), eq(42L))).thenReturn("access-token-demo");
        when(ssoTokenService.generateIdToken(eq("ksuser_auth_1"), anyString(), eq(null),
            eq("openid profile"), any(), eq(null), eq(null))).thenReturn("id-token-demo");
        when(ssoTokenService.getAccessTokenExpiresInSeconds()).thenReturn(7200);

        Map<String, Object> result = service.exchangeAuthorizationCode(
            "authorization_code", "ssocode_demo", "ksuser_auth_1", "client-secret-demo",
            "https://ezbookkeeping.example.com/oauth2/callback/ksuser", null
        );

        assertEquals("access-token-demo", result.get("access_token"));
        assertEquals("id-token-demo", result.get("id_token"));
    }

    @Test
    void revokedAuthorizationRejectsExistingAccessTokenEvenAfterReapproval() {
        stubUserInfoToken(42L, "openid profile");
        when(authorizationRepository.findByUserIdAndClientId(1L, "ksuser_auth_1"))
            .thenReturn(Optional.empty(), Optional.of(authorization(43L)));

        service.revokeAuthorization(buildAdminUser(), "ksuser_auth_1");
        verify(authorizationRepository).deleteByUserIdAndClientId(1L, "ksuser_auth_1");
        assertEquals("invalid_token", assertThrows(Oauth2Exception.class,
            () -> service.buildUserInfo("access-token-demo")).getError());
        assertEquals("invalid_token", assertThrows(Oauth2Exception.class,
            () -> service.buildUserInfo("access-token-demo")).getError());
    }

    @Test
    void currentAuthorizationAllowsUserInfo() {
        stubUserInfoToken(42L, "openid profile");
        when(authorizationRepository.findByUserIdAndClientId(1L, "ksuser_auth_1"))
            .thenReturn(Optional.of(authorization(42L)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(buildAdminUser()));

        assertEquals("sub-demo", service.buildUserInfo("access-token-demo").get("sub"));
    }

    @Test
    void legacyAccessTokenWithoutAuthorizationIdFailsClosed() {
        stubUserInfoToken(null, "openid profile");
        when(authorizationRepository.findByUserIdAndClientId(1L, "ksuser_auth_1"))
            .thenReturn(Optional.of(authorization(42L)));

        Oauth2Exception error = assertThrows(Oauth2Exception.class,
            () -> service.buildUserInfo("access-token-demo"));

        assertEquals("invalid_token", error.getError());
        assertEquals(401, error.getStatus().value());
    }

    @Test
    void expiredTimeLimitedAuthorizationDeniesUserInfo() {
        stubUserInfoToken(42L, "openid profile");
        UserSsoAuthorization expired = authorization(42L);
        expired.setGrantMode(AuthorizationGrantPolicy.MODE_TIME_LIMITED);
        expired.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(authorizationRepository.findByUserIdAndClientId(1L, "ksuser_auth_1"))
            .thenReturn(Optional.of(expired));

        assertEquals("invalid_token", assertThrows(Oauth2Exception.class,
            () -> service.buildUserInfo("access-token-demo")).getError());
    }

    @Test
    void narrowedScopesDenyOldAccessToken() {
        stubUserInfoToken(42L, "openid profile email");
        UserSsoAuthorization narrowed = authorization(42L);
        narrowed.setScopes("openid profile");
        when(authorizationRepository.findByUserIdAndClientId(1L, "ksuser_auth_1"))
            .thenReturn(Optional.of(narrowed));

        assertEquals("invalid_token", assertThrows(Oauth2Exception.class,
            () -> service.buildUserInfo("access-token-demo")).getError());
    }

    @Test
    void revokedAuthorizationRejectsPendingCodeAfterReapproval() {
        when(oidcClientRepository.findByClientId("ksuser_auth_1")).thenReturn(Optional.of(buildClient()));
        when(passwordEncoder.matches("client-secret-demo", "encoded-secret")).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(buildAdminUser()));
        when(valueOperations.getAndDelete("sso:auth-code:ssocode_demo")).thenReturn(
            "{" +
                "\"userId\":1," +
                "\"clientId\":\"ksuser_auth_1\"," +
                "\"redirectUri\":\"https://ezbookkeeping.example.com/oauth2/callback/ksuser\"," +
                "\"scope\":\"openid profile\"," +
                "\"nonce\":null," +
                "\"codeChallenge\":null," +
                "\"codeChallengeMethod\":null," +
                "\"authorizationId\":42" +
                "}"
        );
        when(authorizationRepository.findByUserIdAndClientId(1L, "ksuser_auth_1"))
            .thenReturn(Optional.of(authorization(43L)));

        Oauth2Exception error = assertThrows(Oauth2Exception.class, () -> service.exchangeAuthorizationCode(
            "authorization_code", "ssocode_demo", "ksuser_auth_1", "client-secret-demo",
            "https://ezbookkeeping.example.com/oauth2/callback/ksuser", null
        ));

        assertEquals("invalid_grant", error.getError());
    }

    private User buildAdminUser() {
        User user = new User();
        user.setId(1L);
        user.setUuid("user-uuid-demo");
        user.setVerificationType("admin");
        return user;
    }

    private OidcClient buildClient() {
        OidcClient client = new OidcClient();
        client.setClientId("ksuser_auth_1");
        client.setClientSecretHash("encoded-secret");
        client.setClientName("EzBookKeeping");
        client.setRedirectUris("https://ezbookkeeping.example.com/oauth2/callback/ksuser");
        client.setScopes("openid profile email");
        client.setAudiences("ksuser-auth");
        client.setRequirePkce(false);
        client.setIsActive(true);
        return client;
    }
}
