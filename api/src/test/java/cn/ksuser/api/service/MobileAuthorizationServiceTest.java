package cn.ksuser.api.service;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.dto.*;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.exception.Oauth2Exception;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MobileAuthorizationServiceTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked") private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final Oauth2PlatformService oauth = mock(Oauth2PlatformService.class);
    private final SsoPlatformService sso = mock(SsoPlatformService.class);
    private final Map<String, String> memory = new ConcurrentHashMap<>();
    private MobileAuthorizationService service;
    private final User user = new User();
    private static final String ORIGIN = "https://auth.ksuser.cn";
    private static final String CALLBACK = "https://client.example/callback?existing=1";

    @BeforeEach void setup() {
        user.setUuid("current-user");
        AppProperties properties = new AppProperties();
        properties.getMobileBridge().setAllowedReturnOrigins(List.of(ORIGIN));
        service = new MobileAuthorizationService(redis, new ObjectMapper(), properties, oauth, sso);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(inv -> memory.get(inv.getArgument(0)));
        when(values.getAndDelete(anyString())).thenAnswer(inv -> memory.remove(inv.getArgument(0)));
        doAnswer(inv -> { memory.put(inv.getArgument(0), inv.getArgument(1)); return null; }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(inv -> memory.putIfAbsent(inv.getArgument(0), inv.getArgument(1)) == null);
        when(redis.hasKey(anyString())).thenAnswer(inv -> memory.containsKey(inv.getArgument(0)));
        when(redis.delete(anyString())).thenAnswer(inv -> memory.remove(inv.getArgument(0)) != null);
        when(redis.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(240L);
        when(oauth.buildAuthorizeContext(any(), any(), any(), any(), any())).thenReturn(new Oauth2AuthorizeContextResponse("client", "日历", "https://client.example/logo.png", "help@client.example", CALLBACK, List.of("profile", "email"), false, "PERSISTENT", null));
        when(sso.buildAuthorizeContext(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(new SsoAuthorizeContextResponse("client", "OIDC 应用", null, CALLBACK, List.of("openid", "profile"), false, "PERSISTENT", null));
    }
    private SsoAuthorizeRequest authorization() {
        var a = new SsoAuthorizeRequest();
        a.setClientId("client"); a.setRedirectUri(CALLBACK); a.setResponseType("code"); a.setScope("openid profile");
        a.setState("state +&中文"); a.setNonce("original-nonce"); a.setCodeChallenge("original-challenge"); a.setCodeChallengeMethod("S256");
        return a;
    }
    private MobileAuthorizationService.Created create(String mode) { return service.create(new MobileAuthorizationService.CreateRequest(mode, ORIGIN, authorization())); }
    private MobileAuthorizationService.BrowserRequest browser(MobileAuthorizationService.Created c) { return new MobileAuthorizationService.BrowserRequest(c.ticket(), c.secret()); }
    @Test void exposesValidatedMetadataWithoutPuttingBrowserSecretInAppLink() {
        var c = create("oauth");
        assertFalse(c.appLink().contains(c.secret()));
        var context = service.preview(c.ticket(), user);
        assertEquals("日历", context.appName()); assertEquals("help@client.example", context.contactInfo());
        assertEquals(List.of("profile", "email"), context.requestedScopes());
    }
    @Test void preservesOidcStateNoncePkceAndIssuesOnlyOneCode() {
        when(sso.approveAuthorization(eq(user), any())).thenReturn(new SsoAuthorizeApproveResponse("https://client.example/callback?code=code"));
        var c = create("sso");
        var decision = new MobileAuthorizationService.Decision(c.ticket(), true, "ONE_TIME", null);
        var returned = service.decide(decision, user);
        assertEquals(ORIGIN + "/app/authorize-return#ticket=" + c.ticket() + "&secret=" + c.secret(), returned.returnUrl());
        assertEquals(returned, service.decide(decision, user));
        var captured = ArgumentCaptor.forClass(SsoAuthorizeRequest.class);
        verify(sso, times(1)).approveAuthorization(eq(user), captured.capture());
        var a = captured.getValue();
        assertEquals("state +&中文", a.getState()); assertEquals("original-nonce", a.getNonce());
        assertEquals("original-challenge", a.getCodeChallenge()); assertEquals("S256", a.getCodeChallengeMethod());
        assertEquals("ONE_TIME", a.getGrantMode());
        assertEquals("completed", service.consume(browser(c)).status());
        assertThrows(Oauth2Exception.class, () -> service.consume(browser(c)));
    }
    @Test void oauthApprovalUsesOriginalRequestAndSelectedDuration() {
        when(oauth.approveAuthorization(eq(user), any())).thenReturn(new Oauth2AuthorizeApproveResponse("https://client.example/callback?code=oauth"));
        var c = create("oauth"); service.decide(new MobileAuthorizationService.Decision(c.ticket(), true, "TIME_LIMITED", 3600), user);
        var captured = ArgumentCaptor.forClass(Oauth2AuthorizeRequest.class);
        verify(oauth).approveAuthorization(eq(user), captured.capture());
        assertEquals(CALLBACK, captured.getValue().getRedirectUri());
        assertEquals("state +&中文", captured.getValue().getState());
        assertEquals(3600, captured.getValue().getGrantTtlSeconds());
    }
    @Test void wrongSecretCannotConsumeResultAndDenialPreservesState() {
        var c = create("oauth"); service.decide(new MobileAuthorizationService.Decision(c.ticket(), false, null, null), user);
        assertThrows(Oauth2Exception.class, () -> service.consume(new MobileAuthorizationService.BrowserRequest(c.ticket(), "wrong")));
        String redirect = service.consume(browser(c)).redirectUrl();
        assertTrue(redirect.contains("existing=1")); assertTrue(redirect.contains("error=access_denied"));
        String encodedState = java.util.Arrays.stream(java.net.URI.create(redirect).getRawQuery().split("&"))
            .filter(item -> item.startsWith("state=")).findFirst().orElseThrow().substring(6);
        assertEquals("state +&中文", java.net.URLDecoder.decode(encodedState, java.nio.charset.StandardCharsets.UTF_8));
        verify(oauth, never()).approveAuthorization(any(), any());
    }
    @Test void browserFallbackInvalidatesAppRequest() {
        var c = create("oauth"); service.cancel(browser(c));
        assertThrows(Oauth2Exception.class, () -> service.decide(new MobileAuthorizationService.Decision(c.ticket(), true, "PERSISTENT", null), user));
        verify(oauth, never()).approveAuthorization(any(), any());
    }
    @Test void rejectsUntrustedOriginsAndExpiredRequests() {
        assertThrows(Oauth2Exception.class, () -> service.create(new MobileAuthorizationService.CreateRequest("sso", "https://auth.ksuser.cn.evil.test", authorization())));
        var c = create("sso"); memory.clear();
        assertThrows(Oauth2Exception.class, () -> service.preview(c.ticket(), user));
    }
}
