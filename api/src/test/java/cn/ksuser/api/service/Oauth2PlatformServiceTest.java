package cn.ksuser.api.service;

import cn.ksuser.api.entity.Oauth2Application;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.repository.Oauth2ApplicationRepository;
import cn.ksuser.api.repository.UserOauth2AuthorizationRepository;
import cn.ksuser.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
                "\"scope\":\"profile email\"" +
                "}"
        );
        when(oauth2TokenService.generateAccessToken(
            eq("ksapp_demo"),
            eq(99L),
            eq(1L),
            eq("user-uuid-demo"),
            eq("profile email"),
            anyString(),
            anyString()
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
