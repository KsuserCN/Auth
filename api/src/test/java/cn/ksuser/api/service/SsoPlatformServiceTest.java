package cn.ksuser.api.service;

import cn.ksuser.api.dto.SsoClientCreateRequest;
import cn.ksuser.api.dto.SsoClientCreateResponse;
import cn.ksuser.api.dto.SsoAuthorizeApproveResponse;
import cn.ksuser.api.dto.SsoAuthorizeRequest;
import cn.ksuser.api.entity.OidcClient;
import cn.ksuser.api.entity.User;
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
    private PasswordEncoder passwordEncoder;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private SsoPlatformService service;

    @BeforeEach
    void setUp() {
        oidcClientRepository = mock(OidcClientRepository.class);
        authorizationRepository = mock(UserSsoAuthorizationRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        SsoTokenService ssoTokenService = mock(SsoTokenService.class);
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
        verify(valueOperations).set(eq("sso:auth-code:" + redirectUrl.substring(redirectUrl.indexOf("code=") + 5, redirectUrl.indexOf("&state="))), anyString(), any());
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
