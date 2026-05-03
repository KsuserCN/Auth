package cn.ksuser.api.service;

import cn.ksuser.api.dto.SsoClientCreateRequest;
import cn.ksuser.api.dto.SsoClientCreateResponse;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SsoPlatformServiceTest {

    private OidcClientRepository oidcClientRepository;
    private PasswordEncoder passwordEncoder;
    private SsoPlatformService service;

    @BeforeEach
    void setUp() {
        oidcClientRepository = mock(OidcClientRepository.class);
        UserSsoAuthorizationRepository authorizationRepository = mock(UserSsoAuthorizationRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        SsoTokenService ssoTokenService = mock(SsoTokenService.class);

        service = new SsoPlatformService(
            oidcClientRepository,
            authorizationRepository,
            userRepository,
            passwordEncoder,
            redisTemplate,
            ssoTokenService
        );
        ReflectionTestUtils.setField(service, "maxClients", 20);
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

    private User buildAdminUser() {
        User user = new User();
        user.setId(1L);
        user.setVerificationType("admin");
        return user;
    }
}
