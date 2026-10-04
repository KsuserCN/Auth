package cn.ksuser.api.service;

import cn.ksuser.api.dto.Oauth2AppCreateRequest;
import cn.ksuser.api.dto.Oauth2AuthorizeApproveResponse;
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
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Date;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Oauth2PlatformServiceTest {

    private Oauth2ApplicationRepository applicationRepository;
    private UserOauth2AuthorizationRepository authorizationRepository;
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private Oauth2TokenService oauth2TokenService;
    private Oauth2PlatformService service;

    private UserOauth2Authorization authorization(long id) {
        UserOauth2Authorization record = new UserOauth2Authorization();
        record.setId(id);
        record.setUserId(1L);
        record.setAppId("ksapp_demo");
        record.setScopes("profile email");
        return record;
    }

    private void stubUserInfoToken(Long authorizationId, String scope) {
        when(oauth2TokenService.parse("access-token-demo")).thenReturn(
            new Oauth2TokenService.ParsedOauth2AccessToken(
                "ksapp_demo", 99L, 1L, "user-uuid-demo", scope, "openid", "unionid",
                authorizationId, new Date(System.currentTimeMillis() + 60_000)
            )
        );
        when(applicationRepository.findByAppId("ksapp_demo")).thenReturn(Optional.of(buildApplication()));
    }

    @BeforeEach
    void setUp() {
        applicationRepository = mock(Oauth2ApplicationRepository.class);
        authorizationRepository = mock(UserOauth2AuthorizationRepository.class);
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        oauth2TokenService = mock(Oauth2TokenService.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service = new Oauth2PlatformService(
            applicationRepository,
            authorizationRepository,
            userRepository,
            passwordEncoder,
            redisTemplate,
            oauth2TokenService
        );
        ReflectionTestUtils.setField(service, "jwtSecret", "ksuser-very-secret-key-2026-abc-platform-test");
        ReflectionTestUtils.setField(service, "maxAppsPerUser", 5);
    }

    @Test
    void shouldExchangeAuthorizationCodeForPureOauthToken() {
        Oauth2Application application = buildApplication();
        User user = buildUser();

        when(applicationRepository.findByAppId("ksapp_demo")).thenReturn(Optional.of(application));
        when(passwordEncoder.matches("kssecret_demo", "encoded-secret")).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(valueOperations.getAndDelete("oauth2:auth-code:kscode_demo")).thenReturn(
            "{" +
                "\"userId\":1," +
                "\"ownerUserId\":99," +
                "\"clientId\":\"ksapp_demo\"," +
                "\"redirectUri\":\"http://localhost:9000/callback\"," +
                "\"scope\":\"profile email\"," +
                "\"authorizationId\":42" +
                "}"
        );
        when(authorizationRepository.findByUserIdAndAppId(1L, "ksapp_demo"))
            .thenReturn(Optional.of(authorization(42L)));
        when(oauth2TokenService.generateAccessToken(
            eq("ksapp_demo"),
            eq(99L),
            eq(1L),
            eq("user-uuid-demo"),
            eq("profile email"),
            anyString(),
            anyString(),
            eq(42L)
        )).thenReturn("access-token-demo");
        when(oauth2TokenService.getAccessTokenExpiresInSeconds()).thenReturn(7200);

        Map<String, Object> response = service.exchangeAuthorizationCode(
            "authorization_code",
            "kscode_demo",
            "ksapp_demo",
            "kssecret_demo",
            "http://localhost:9000/callback"
        );

        assertEquals("access-token-demo", response.get("access_token"));
        assertEquals("Bearer", response.get("token_type"));
        assertEquals(7200, response.get("expires_in"));
        assertEquals("profile email", response.get("scope"));
        assertTrue(response.containsKey("openid"));
        assertTrue(response.containsKey("unionid"));
        assertFalse(response.containsKey("id_token"));
    }

    @Test
    void shouldRejectExpiredOrConsumedAuthorizationCode() {
        Oauth2Application application = buildApplication();

        when(applicationRepository.findByAppId("ksapp_demo")).thenReturn(Optional.of(application));
        when(passwordEncoder.matches("kssecret_demo", "encoded-secret")).thenReturn(true);
        when(valueOperations.getAndDelete("oauth2:auth-code:kscode_demo")).thenReturn(null);

        Oauth2Exception exception = assertThrows(
            Oauth2Exception.class,
            () -> service.exchangeAuthorizationCode(
                "authorization_code",
                "kscode_demo",
                "ksapp_demo",
                "kssecret_demo",
                "http://localhost:9000/callback"
            )
        );

        assertEquals("invalid_grant", exception.getError());
        assertEquals(400, exception.getStatus().value());
    }

    @Test
    void shouldExchangeAuthorizationCodeWithAnyRegisteredRedirectUri() {
        Oauth2Application application = buildApplication();
        application.setRedirectUri("http://localhost:9000/callback;https://demo.example.com/oauth/callback");
        User user = buildUser();

        when(applicationRepository.findByAppId("ksapp_demo")).thenReturn(Optional.of(application));
        when(passwordEncoder.matches("kssecret_demo", "encoded-secret")).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(valueOperations.getAndDelete("oauth2:auth-code:kscode_demo")).thenReturn(
            "{" +
                "\"userId\":1," +
                "\"ownerUserId\":99," +
                "\"clientId\":\"ksapp_demo\"," +
                "\"redirectUri\":\"https://demo.example.com/oauth/callback\"," +
                "\"scope\":\"profile\"," +
                "\"authorizationId\":42" +
                "}"
        );
        when(authorizationRepository.findByUserIdAndAppId(1L, "ksapp_demo"))
            .thenReturn(Optional.of(authorization(42L)));
        when(oauth2TokenService.generateAccessToken(
            eq("ksapp_demo"),
            eq(99L),
            eq(1L),
            eq("user-uuid-demo"),
            eq("profile"),
            anyString(),
            anyString(),
            eq(42L)
        )).thenReturn("access-token-demo");
        when(oauth2TokenService.getAccessTokenExpiresInSeconds()).thenReturn(7200);

        Map<String, Object> response = service.exchangeAuthorizationCode(
            "authorization_code",
            "kscode_demo",
            "ksapp_demo",
            "kssecret_demo",
            "https://demo.example.com/oauth/callback"
        );

        assertEquals("access-token-demo", response.get("access_token"));
        assertEquals("profile", response.get("scope"));
    }

    @Test
    void shouldEncodeStateWhenApprovingAuthorization() {
        Oauth2Application application = buildApplication();
        User user = buildUser();

        when(applicationRepository.findByAppId("ksapp_demo")).thenReturn(Optional.of(application));
        when(authorizationRepository.save(any(UserOauth2Authorization.class))).thenAnswer(invocation -> {
            UserOauth2Authorization saved = invocation.getArgument(0);
            saved.setId(42L);
            return saved;
        });

        Oauth2AuthorizeRequest request = new Oauth2AuthorizeRequest();
        request.setClientId("ksapp_demo");
        request.setRedirectUri("http://localhost:9000/callback");
        request.setResponseType("code");
        request.setScope("profile email");
        request.setState("abc+123==");

        Oauth2AuthorizeApproveResponse response = service.approveAuthorization(user, request);

        String redirectUrl = response.getRedirectUrl();
        assertTrue(redirectUrl.contains("code=kscode_"));
        assertTrue(redirectUrl.contains("state=abc%2B123%3D%3D"));
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("oauth2:auth-code:" + redirectUrl.substring(redirectUrl.indexOf("code=") + 5, redirectUrl.indexOf("&state="))), payload.capture(), any());
        assertTrue(payload.getValue().contains("\"authorizationId\":42"));
    }

    @Test
    void revokedAuthorizationRejectsExistingAccessTokenEvenAfterReapproval() {
        stubUserInfoToken(42L, "profile");
        when(authorizationRepository.findByUserIdAndAppId(1L, "ksapp_demo"))
            .thenReturn(Optional.empty(), Optional.of(authorization(43L)));

        service.revokeAuthorization(buildUser(), "ksapp_demo");
        verify(authorizationRepository).deleteByUserIdAndAppId(1L, "ksapp_demo");
        assertEquals("invalid_token", assertThrows(Oauth2Exception.class,
            () -> service.buildUserInfo("access-token-demo")).getError());
        assertEquals("invalid_token", assertThrows(Oauth2Exception.class,
            () -> service.buildUserInfo("access-token-demo")).getError());
    }

    @Test
    void currentAuthorizationAllowsUserInfo() {
        stubUserInfoToken(42L, "profile");
        when(authorizationRepository.findByUserIdAndAppId(1L, "ksapp_demo"))
            .thenReturn(Optional.of(authorization(42L)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(buildUser()));

        assertEquals("openid", service.buildUserInfo("access-token-demo").get("openid"));
    }

    @Test
    void legacyAccessTokenWithoutAuthorizationIdFailsClosed() {
        stubUserInfoToken(null, "profile");
        when(authorizationRepository.findByUserIdAndAppId(1L, "ksapp_demo"))
            .thenReturn(Optional.of(authorization(42L)));

        Oauth2Exception error = assertThrows(Oauth2Exception.class,
            () -> service.buildUserInfo("access-token-demo"));

        assertEquals("invalid_token", error.getError());
        assertEquals(401, error.getStatus().value());
    }

    @Test
    void expiredTimeLimitedAuthorizationDeniesUserInfo() {
        stubUserInfoToken(42L, "profile");
        UserOauth2Authorization expired = authorization(42L);
        expired.setGrantMode(AuthorizationGrantPolicy.MODE_TIME_LIMITED);
        expired.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(authorizationRepository.findByUserIdAndAppId(1L, "ksapp_demo"))
            .thenReturn(Optional.of(expired));

        assertEquals("invalid_token", assertThrows(Oauth2Exception.class,
            () -> service.buildUserInfo("access-token-demo")).getError());
    }

    @Test
    void narrowedScopesDenyOldAccessToken() {
        stubUserInfoToken(42L, "profile email");
        UserOauth2Authorization narrowed = authorization(42L);
        narrowed.setScopes("profile");
        when(authorizationRepository.findByUserIdAndAppId(1L, "ksapp_demo"))
            .thenReturn(Optional.of(narrowed));

        assertEquals("invalid_token", assertThrows(Oauth2Exception.class,
            () -> service.buildUserInfo("access-token-demo")).getError());
    }

    @Test
    void revokedAuthorizationRejectsPendingCodeAfterReapproval() {
        when(applicationRepository.findByAppId("ksapp_demo")).thenReturn(Optional.of(buildApplication()));
        when(passwordEncoder.matches("kssecret_demo", "encoded-secret")).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(buildUser()));
        when(valueOperations.getAndDelete("oauth2:auth-code:kscode_demo")).thenReturn(
            "{" +
                "\"userId\":1," +
                "\"ownerUserId\":99," +
                "\"clientId\":\"ksapp_demo\"," +
                "\"redirectUri\":\"http://localhost:9000/callback\"," +
                "\"scope\":\"profile\"," +
                "\"authorizationId\":42" +
                "}"
        );
        when(authorizationRepository.findByUserIdAndAppId(1L, "ksapp_demo"))
            .thenReturn(Optional.of(authorization(43L)));

        Oauth2Exception error = assertThrows(Oauth2Exception.class, () -> service.exchangeAuthorizationCode(
            "authorization_code", "kscode_demo", "ksapp_demo", "kssecret_demo", "http://localhost:9000/callback"
        ));

        assertEquals("invalid_grant", error.getError());
    }

    @Test
    void shouldCreateApplicationWithSemicolonSeparatedRedirectUris() {
        User user = buildUser();
        user.setVerificationType("personal");

        when(applicationRepository.countByOwnerUserId(1L)).thenReturn(0L);
        when(applicationRepository.existsByAppId(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-secret");
        when(applicationRepository.save(any(Oauth2Application.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Oauth2AppCreateRequest request = new Oauth2AppCreateRequest();
        request.setAppName("OAuth Demo");
        request.setRedirectUri("https://demo.example.com/oauth/callback; http://localhost:9000/callback;https://demo.example.com/oauth/callback");
        request.setContactInfo("test@example.com");
        request.setScopes(List.of("profile"));

        service.createApplication(user, request);

        ArgumentCaptor<Oauth2Application> captor = ArgumentCaptor.forClass(Oauth2Application.class);
        verify(applicationRepository).save(captor.capture());
        assertEquals("https://demo.example.com/oauth/callback;http://localhost:9000/callback", captor.getValue().getRedirectUri());
    }

    @Test
    void shouldRejectNonHttpsNonLocalhostOauthRedirectUri() {
        User user = buildUser();
        user.setVerificationType("personal");
        when(applicationRepository.countByOwnerUserId(1L)).thenReturn(0L);

        Oauth2AppCreateRequest request = new Oauth2AppCreateRequest();
        request.setAppName("OAuth Demo");
        request.setRedirectUri("https://demo.example.com/oauth/callback;http://127.0.0.1:9000/callback");
        request.setContactInfo("test@example.com");

        Oauth2Exception exception = assertThrows(
            Oauth2Exception.class,
            () -> service.createApplication(user, request)
        );

        assertEquals("invalid_request", exception.getError());
        assertEquals(400, exception.getStatus().value());
    }

    private Oauth2Application buildApplication() {
        Oauth2Application application = new Oauth2Application();
        application.setAppId("ksapp_demo");
        application.setAppSecretHash("encoded-secret");
        application.setAppName("Demo App");
        application.setRedirectUri("http://localhost:9000/callback");
        application.setContactInfo("test@example.com");
        application.setScopes("profile email");
        application.setOwnerUserId(99L);
        application.setIsActive(true);
        return application;
    }

    private User buildUser() {
        User user = new User();
        user.setId(1L);
        user.setUuid("user-uuid-demo");
        user.setUsername("demo-user");
        user.setEmail("demo@example.com");
        user.setAvatarUrl("https://cdn.example.com/avatar.png");
        return user;
    }
}
