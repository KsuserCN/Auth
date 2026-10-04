package cn.ksuser.api.controller;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.dto.OauthCallbackRequest;
import cn.ksuser.api.dto.QqMobileLoginRequest;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserOauthAccount;
import cn.ksuser.api.repository.UserOauthAccountRepository;
import cn.ksuser.api.repository.UserPasskeyRepository;
import cn.ksuser.api.repository.UserRepository;
import cn.ksuser.api.repository.UserSettingsRepository;
import cn.ksuser.api.service.MfaService;
import cn.ksuser.api.service.RateLimitService;
import cn.ksuser.api.service.SensitiveOperationService;
import cn.ksuser.api.service.TotpService;
import cn.ksuser.api.service.UserService;
import cn.ksuser.api.service.UserSessionService;
import cn.ksuser.api.util.JwtUtil;
import cn.ksuser.api.util.SensitiveLogUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OauthControllerQqMobileBindTest {
    private OauthController controller;
    private UserOauthAccountRepository accounts;
    private UserService users;
    private RateLimitService rateLimit;
    private SensitiveOperationService sensitive;
    private UserSessionService sessions;
    private RestTemplate qq;
    private MockHttpServletRequest request;
    private Authentication authentication;
    private AppProperties properties;
    private StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        accounts = mock(UserOauthAccountRepository.class);
        users = mock(UserService.class);
        rateLimit = mock(RateLimitService.class);
        sensitive = mock(SensitiveOperationService.class);
        sessions = mock(UserSessionService.class);
        qq = mock(RestTemplate.class);
        properties = new AppProperties();
        redis = mock(StringRedisTemplate.class);
        controller = new OauthController(properties, accounts, mock(UserRepository.class), users,
            mock(JwtUtil.class), sessions, rateLimit, mock(UserSettingsRepository.class),
            mock(TotpService.class), mock(MfaService.class), mock(SensitiveLogUtil.class),
            mock(UserPasskeyRepository.class), sensitive, redis);
        ReflectionTestUtils.setField(controller, "qqMobileAppId", "12345");
        ReflectionTestUtils.setField(controller, "qqMobileAllowedAppIds", "");
        ReflectionTestUtils.setField(controller, "restTemplate", qq);

        User current = new User("current", "current", "current@example.com", null);
        current.setId(7L);
        when(users.findByUuid("current")).thenReturn(Optional.of(current));
        when(rateLimit.getClientIp(any())).thenReturn("127.0.0.1");
        when(rateLimit.isIpAllowed("127.0.0.1", RateLimitService.TYPE_LOGIN)).thenReturn(true);
        when(sensitive.isVerified("current", "127.0.0.1")).thenReturn(true);
        when(qq.getForObject(anyString(), eq(String.class)))
            .thenReturn("{\"openid\":\"qq-openid\",\"unionid\":\"qq-unionid\",\"client_id\":\"12345\"}");
        request = new MockHttpServletRequest();
        authentication = new UsernamePasswordAuthenticationToken("current", null, List.of());
    }

    private QqMobileLoginRequest credential() {
        QqMobileLoginRequest value = new QqMobileLoginRequest();
        value.setAppId("12345");
        value.setAccessToken("qq-access-token");
        value.setOpenid("qq-openid");
        value.setUnionid("qq-unionid");
        return value;
    }

    @Test
    void bindsVerifiedQqToCurrentUserWithoutIssuingLoginSession() {
        var response = controller.qqMobileBind(credential(), request, authentication);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(200, response.getBody().getCode());
        ArgumentCaptor<UserOauthAccount> saved = ArgumentCaptor.forClass(UserOauthAccount.class);
        verify(accounts).save(saved.capture());
        assertEquals(7L, saved.getValue().getUserId());
        assertEquals("qq", saved.getValue().getProvider());
        assertEquals("qq-openid", saved.getValue().getProviderUserId());
        assertEquals("qq-unionid", saved.getValue().getUnionId());
        assertNotNull(saved.getValue().getLinkedAt());
        assertNotNull(saved.getValue().getCreatedAt());
        verifyNoInteractions(sessions);
    }

    @Test
    void unauthenticatedCallerCannotVerifyOrBindQq() {
        var response = controller.qqMobileBind(credential(), request, null);

        assertEquals(401, response.getStatusCode().value());
        verifyNoInteractions(qq, accounts);
    }

    @Test
    void requiresSensitiveVerificationBeforeContactingQq() {
        when(sensitive.isVerified("current", "127.0.0.1")).thenReturn(false);

        var response = controller.qqMobileBind(credential(), request, authentication);

        assertEquals(202, response.getStatusCode().value());
        verifyNoInteractions(qq, accounts);
    }

    @Test
    void rejectsQqAlreadyBoundToAnotherAccount() {
        UserOauthAccount existing = new UserOauthAccount();
        existing.setUserId(99L);
        when(accounts.findByProviderAndUnionId("qq", "qq-unionid")).thenReturn(Optional.of(existing));

        var response = controller.qqMobileBind(credential(), request, authentication);

        assertEquals(409, response.getStatusCode().value());
        verify(accounts, never()).save(any());
    }

    @Test
    void rejectsMismatchedQqIdentityAndUntrustedAppId() {
        when(qq.getForObject(anyString(), eq(String.class)))
            .thenReturn("{\"openid\":\"other-openid\",\"unionid\":\"qq-unionid\",\"client_id\":\"12345\"}");
        assertEquals(400, controller.qqMobileBind(credential(), request, authentication).getStatusCode().value());
        verify(accounts, never()).save(any());

        QqMobileLoginRequest untrusted = credential();
        untrusted.setAppId("other-app");
        assertEquals(400, controller.qqMobileBind(untrusted, request, authentication).getStatusCode().value());
        verify(qq, times(1)).getForObject(anyString(), eq(String.class));
    }

    @Test
    void databaseUniquenessRaceReturnsConflict() {
        when(accounts.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        var response = controller.qqMobileBind(credential(), request, authentication);

        assertEquals(409, response.getStatusCode().value());
    }

    @Test
    void webCodeCallbackBindsCurrentUserAndPreservesResponseContract() {
        OauthCallbackRequest callback = webCredential();

        var response = controller.qqBindCallback(callback, request, new MockHttpServletResponse(), authentication);

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> data = (Map<?, ?>) response.getBody().getData();
        assertEquals(true, data.get("bound"));
        assertEquals("qq", data.get("provider"));
        assertEquals("qq-openid", data.get("openid"));
        assertEquals("qq-unionid", data.get("unionid"));
        ArgumentCaptor<UserOauthAccount> saved = ArgumentCaptor.forClass(UserOauthAccount.class);
        verify(accounts).save(saved.capture());
        assertEquals(7L, saved.getValue().getUserId());
        assertEquals("qq", saved.getValue().getProvider());
        assertEquals("qq-openid", saved.getValue().getProviderUserId());
        assertEquals("qq-unionid", saved.getValue().getUnionId());
        assertNotNull(saved.getValue().getLinkedAt());
        assertNotNull(saved.getValue().getCreatedAt());
        verify(qq).getForObject(contains("grant_type=authorization_code"), eq(String.class));
        verifyNoInteractions(sessions);
    }

    @Test
    void webCallbackUsesSameUnionIdConflictRuleAsMobileBinding() {
        OauthCallbackRequest callback = webCredential();
        UserOauthAccount existing = new UserOauthAccount();
        existing.setUserId(99L);
        when(accounts.findByProviderAndUnionId("qq", "qq-unionid")).thenReturn(Optional.of(existing));

        var response = controller.qqBindCallback(callback, request, new MockHttpServletResponse(), authentication);

        assertEquals(409, response.getStatusCode().value());
        verify(accounts, never()).save(any());
    }

    @SuppressWarnings("unchecked")
    private OauthCallbackRequest webCredential() {
        properties.getQq().getOauth().setRedirectUris(List.of("https://auth.ksuser.cn/oauth/qq/callback"));
        ReflectionTestUtils.setField(controller, "qqClientId", "web-app-id");
        ReflectionTestUtils.setField(controller, "qqClientSecret", "web-app-key");
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        when(qq.getForObject(contains("oauth2.0/token"), eq(String.class)))
            .thenReturn("{\"access_token\":\"web-access-token\"}");
        when(qq.getForObject(contains("oauth2.0/me"), eq(String.class)))
            .thenReturn("{\"openid\":\"qq-openid\",\"unionid\":\"qq-unionid\",\"client_id\":\"web-app-id\"}");
        OauthCallbackRequest callback = new OauthCallbackRequest();
        callback.setCode("web-code");
        callback.setState("web-nonce;bind;prd");
        return callback;
    }
}
