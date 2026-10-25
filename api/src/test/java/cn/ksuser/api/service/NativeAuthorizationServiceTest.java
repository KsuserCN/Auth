package cn.ksuser.api.service;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.dto.MobileAuthorizeRequest;
import cn.ksuser.api.dto.Oauth2AuthorizeApproveResponse;
import cn.ksuser.api.dto.Oauth2AuthorizeContextResponse;
import cn.ksuser.api.dto.Oauth2AuthorizeRequest;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.exception.Oauth2Exception;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Stateful Redis substitute exercises service behavior; script concurrency is enforced in production by Lua. */
class NativeAuthorizationServiceTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked") private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final Oauth2PlatformService oauth = mock(Oauth2PlatformService.class);
    private final OfficialAuthorizationService official = mock(OfficialAuthorizationService.class);
    private final MobileAppPlatformService platforms = mock(MobileAppPlatformService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, String> memory = new HashMap<>();
    private final AppProperties properties = new AppProperties();
    private final User user = new User();
    private NativeAuthorizationService service;
    private static final String CALLBACK = "https://client.example/auth/ksuser";
    private static final String STATE = "s".repeat(43);

    @BeforeEach void setup() {
        user.setId(7L); user.setUuid("current-user");
        properties.getNativeAuthorization().setEnabled(true);
        service = new NativeAuthorizationService(redis, mapper, properties, oauth, official);
        service.setMobileAppPlatformService(platforms);
        when(platforms.nativeRevision(any(), any(), any())).thenReturn("revision-1");
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(inv -> memory.get(inv.getArgument(0)));
        doAnswer(inv -> { memory.put(inv.getArgument(0), inv.getArgument(1)); return null; })
            .when(values).set(anyString(), anyString(), any(Duration.class));
        when(redis.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenAnswer(inv -> memory.containsKey(inv.getArgument(0)) ? 240L : -2L);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenAnswer(inv -> {
            RedisScript<?> script = inv.getArgument(0);
            List<String> keys = inv.getArgument(1);
            Object[] arguments = inv.getArguments();
            Object[] args = arguments.length == 3 && arguments[2] instanceof Object[] packed ? packed : Arrays.copyOfRange(arguments, 2, arguments.length);
            synchronized (memory) {
                if (script == NativeAuthorizationService.QUOTA) {
                    for (int i = 0; i < 2; i++) if (Integer.parseInt(memory.getOrDefault(keys.get(i), "0")) >= Integer.parseInt(args[i].toString())) return 0L;
                    for (String key : keys) memory.put(key, Integer.toString(Integer.parseInt(memory.getOrDefault(key, "0")) + 1));
                    return 1L;
                }
                if (!memory.containsKey(keys.getFirst())) return script == NativeAuthorizationService.CLAIM ? -1L : 0L;
                String old = memory.get(keys.get(1));
                if (script == NativeAuthorizationService.CLAIM) {
                    if (old != null) return old.equals(args[0]) ? 0L : -2L;
                    memory.put(keys.get(1), args[0].toString()); return 1L;
                }
                if (script == NativeAuthorizationService.CANCEL) {
                    if ("cancelled".equals(old)) return 1L;
                    if (old != null) return 2L;
                    memory.put(keys.get(1), "cancelled"); return 1L;
                }
                if (script == NativeAuthorizationService.PUBLISH) {
                    if (!args[0].equals(old) || memory.containsKey(keys.get(2))) return 0L;
                    memory.put(keys.get(2), args[1].toString()); return 1L;
                }
                throw new AssertionError("Unknown native authorization script");
            }
        });
        when(oauth.buildAuthorizeContext(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(
            new Oauth2AuthorizeContextResponse("client", "Example", null, "help@client.example", CALLBACK,
                List.of("profile", "email"), false, "PERSISTENT", null));
        when(official.buildAuthorizeContext(any(), any())).thenReturn(new OfficialAuthorizationService.AuthorizeContext(
            "official", "Official", null, List.of("openid", "profile"), List.of(), true, false, false));
        when(oauth.approveAuthorization(any(), any(), anyString(), anyString())).thenReturn(new Oauth2AuthorizeApproveResponse(CALLBACK + "?code=kscode_test&state=" + STATE));
        when(official.approveAuthorization(any(), any())).thenReturn(Map.of("redirectUrl", CALLBACK + "?code=ofc_test&state=" + STATE));
    }

    private MobileAuthorizeRequest authorization() {
        var a = new MobileAuthorizeRequest();
        a.setClientId("client"); a.setRedirectUri(CALLBACK); a.setResponseType("code"); a.setScope("profile email");
        a.setState(STATE); a.setNonce("n".repeat(43)); a.setCodeChallenge("c".repeat(43)); a.setCodeChallengeMethod("S256");
        return a;
    }
    private NativeAuthorizationService.Created create() { return create("oauth", authorization()); }
    private NativeAuthorizationService.Created create(String mode, MobileAuthorizeRequest a) { return create(mode, "ios", a); }
    private NativeAuthorizationService.Created create(String mode, String platform, MobileAuthorizeRequest a) { return service.create(new NativeAuthorizationService.CreateRequest(mode, platform, a), "192.0.2.8"); }
    private NativeAuthorizationService.Decision approve(String ticket) { return new NativeAuthorizationService.Decision(ticket, true, "PERSISTENT", null); }

    @Test void onlyPublicMetadataAndCancelCapabilityLeaveCreate() throws Exception {
        var created = create();
        assertTrue(created.appLink().startsWith("https://auth.ksuser.cn/app/sdk-authorize?ticket="));
        assertTrue(created.webAuthorizationUrl().contains("/app/sdk-authorize/web?ticket="));
        assertEquals(300, created.expiresInSeconds());
        assertFalse(created.appLink().contains(created.cancelSecret()));
        assertFalse(created.webAuthorizationUrl().contains(created.cancelSecret()));
        assertFalse(memory.get("native:authorization:" + created.ticket()).contains(created.cancelSecret()));
        var context = service.preview(created.ticket(), user);
        assertEquals(CALLBACK, context.redirectUri()); assertTrue(context.nativeAuthorization());
        String json = mapper.writeValueAsString(context);
        assertFalse(json.contains("cancelSecret")); assertFalse(json.contains("code_challenge")); assertFalse(json.contains("returnUrl"));
        verify(oauth, atLeastOnce()).validateNativeClient("client", "ios", CALLBACK);
    }

    @Test void freezeRequestPreservePkceAndSelectedDuration() {
        var a = authorization(); var created = create("oauth", a);
        a.setRedirectUri("https://evil.example/callback"); a.setScope("email"); a.setCodeChallenge("x".repeat(43));
        var result = service.decide(new NativeAuthorizationService.Decision(created.ticket(), true, "TIME_LIMITED", 3600), user);
        assertTrue(result.completed()); assertEquals(CALLBACK + "?code=kscode_test&state=" + STATE, result.returnUrl());
        var captor = ArgumentCaptor.forClass(Oauth2AuthorizeRequest.class);
        verify(oauth).approveAuthorization(eq(user), captor.capture(), eq("ios"), eq("revision-1"));
        assertEquals(CALLBACK, captor.getValue().getRedirectUri()); assertEquals("profile email", captor.getValue().getScope());
        assertEquals("c".repeat(43), captor.getValue().getCodeChallenge()); assertEquals("S256", captor.getValue().getCodeChallengeMethod());
        assertEquals("TIME_LIMITED", captor.getValue().getGrantMode()); assertEquals(3600, captor.getValue().getGrantTtlSeconds());
    }

    @Test void officialUsesExistingOidcAndRequiresPublicClient() {
        var a = authorization(); a.setClientId("official"); a.setScope("openid profile");
        var created = create("official", a);
        assertTrue(service.preview(created.ticket(), user).officialApplication());
        assertTrue(service.decide(approve(created.ticket()), user).returnUrl().contains("code=ofc_test"));
        verify(official, atLeastOnce()).validateNativeClient("official");
        verify(official).approveAuthorization(eq(user), argThat(request -> STATE.equals(request.state())
            && "n".repeat(43).equals(request.nonce()) && "c".repeat(43).equals(request.codeChallenge())));
        verify(oauth, never()).approveAuthorization(any(), any(), anyString(), anyString());
    }

    @Test void denialReturnsOnlyRegisteredErrorAndState() {
        var created = create();
        String callback = service.decide(new NativeAuthorizationService.Decision(created.ticket(), false, null, null), user).returnUrl();
        assertEquals(CALLBACK + "?error=access_denied&state=" + STATE, callback);
        verify(oauth, never()).approveAuthorization(any(), any(), anyString(), anyString());
    }

    @Test void denialPreservesAlreadyEncodedRegisteredPath() {
        var a = authorization(); a.setRedirectUri("https://client.example/callback%20ios");
        var created = create("oauth", a);
        assertEquals("https://client.example/callback%20ios?error=access_denied&state=" + STATE,
            service.decide(new NativeAuthorizationService.Decision(created.ticket(), false, null, null), user).returnUrl());
    }

    @Test void identicalDecisionRetryDoesNotIssueAnotherCode() {
        var created = create(); var decision = approve(created.ticket());
        assertEquals(service.decide(decision, user), service.decide(decision, user));
        verify(oauth, times(1)).approveAuthorization(any(), any(), anyString(), anyString());
        User other = new User(); other.setId(8L); other.setUuid("other-user");
        assertEquals(HttpStatus.CONFLICT, assertThrows(Oauth2Exception.class, () -> service.decide(decision, other)).getStatus());
        assertThrows(Oauth2Exception.class, () -> service.decide(new NativeAuthorizationService.Decision(created.ticket(), false, null, null), user));
        assertThrows(Oauth2Exception.class, () -> service.decide(new NativeAuthorizationService.Decision(created.ticket(), true, "ONE_TIME", null), user));
    }

    @Test void cancellationWinsBeforeApprovalAndIsIdempotent() {
        var created = create(); var request = new NativeAuthorizationService.CancelRequest(created.ticket(), created.cancelSecret());
        assertEquals("cancelled", service.cancel(request).status()); assertEquals("cancelled", service.cancel(request).status());
        assertThrows(Oauth2Exception.class, () -> service.preview(created.ticket(), user));
        assertThrows(Oauth2Exception.class, () -> service.decide(approve(created.ticket()), user));
        verify(oauth, never()).approveAuthorization(any(), any(), anyString(), anyString());
    }

    @Test void wrongCancelSecretFailsAndCompletedCancelNeverLeaksCallback() throws Exception {
        var created = create();
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(Oauth2Exception.class,
            () -> service.cancel(new NativeAuthorizationService.CancelRequest(created.ticket(), "x".repeat(32)))).getStatus());
        service.decide(approve(created.ticket()), user);
        var cancelled = service.cancel(new NativeAuthorizationService.CancelRequest(created.ticket(), created.cancelSecret()));
        assertEquals("decided", cancelled.status());
        assertFalse(mapper.writeValueAsString(cancelled).contains("code"));
    }

    @Test void cancellationCannotDeleteAnInFlightDecision() {
        var created = create();
        when(oauth.approveAuthorization(any(), any(), anyString(), anyString())).thenAnswer(inv -> {
            assertEquals("decided", service.cancel(new NativeAuthorizationService.CancelRequest(created.ticket(), created.cancelSecret())).status());
            assertThrows(Oauth2Exception.class, () -> service.decide(approve(created.ticket()), user));
            return new Oauth2AuthorizeApproveResponse(CALLBACK + "?code=kscode_test&state=" + STATE);
        });
        assertTrue(service.decide(approve(created.ticket()), user).completed());
        verify(oauth, times(1)).approveAuthorization(any(), any(), anyString(), anyString());
    }

    @Test void failedIssuanceRetainsExclusiveClaimAndCannotIssueAgain() {
        var created = create();
        when(oauth.approveAuthorization(any(), any(), anyString(), anyString())).thenThrow(new IllegalStateException("simulated outage"));
        assertThrows(IllegalStateException.class, () -> service.decide(approve(created.ticket()), user));
        assertEquals("NATIVE_AUTHORIZATION_FAILED", assertThrows(Oauth2Exception.class,
            () -> service.decide(approve(created.ticket()), user)).getResult().errorCode().name());
        verify(oauth, times(1)).approveAuthorization(any(), any(), anyString(), anyString());
    }

    @Test void rejectsUnexpectedIssuerCallbackAndDoesNotPublishIt() {
        var created = create();
        when(oauth.approveAuthorization(any(), any(), anyString(), anyString())).thenReturn(new Oauth2AuthorizeApproveResponse("https://evil.example/callback?code=stolen&state=" + STATE));
        assertThrows(Oauth2Exception.class, () -> service.decide(approve(created.ticket()), user));
        assertThrows(Oauth2Exception.class, () -> service.decide(approve(created.ticket()), user));
        verify(oauth, times(1)).approveAuthorization(any(), any(), anyString(), anyString());
    }

    @Test void expiresWithoutDeliveringIssuedCodeWhenRequestDisappears() {
        var created = create();
        when(oauth.approveAuthorization(any(), any(), anyString(), anyString())).thenAnswer(inv -> {
            memory.remove("native:authorization:" + created.ticket());
            return new Oauth2AuthorizeApproveResponse(CALLBACK + "?code=kscode_test&state=" + STATE);
        });
        assertEquals(HttpStatus.GONE, assertThrows(Oauth2Exception.class, () -> service.decide(approve(created.ticket()), user)).getStatus());
        assertEquals("expired", service.cancel(new NativeAuthorizationService.CancelRequest(created.ticket(), created.cancelSecret())).status());
    }

    @Test void disabledAndAllowlistedRolloutFailBeforeCreatingRequests() {
        properties.getNativeAuthorization().setEnabled(false);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(Oauth2Exception.class, this::create).getStatus());
        properties.getNativeAuthorization().setEnabled(true); properties.getNativeAuthorization().setAllowedClientIds(List.of("other"));
        assertThrows(Oauth2Exception.class, this::create);
        properties.getNativeAuthorization().setAllowedClientIds(List.of("client")); assertNotNull(create());
    }

    @Test void quotaRejectsExcessCreatesWithoutCreatingExtraTransactions() {
        properties.getNativeAuthorization().setCreateIpPerMinute(1);
        create();
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, assertThrows(Oauth2Exception.class, this::create).getStatus());
        assertEquals(1, memory.keySet().stream().filter(key -> !key.contains(":quota:")).count());
    }

    @Test void noUserCannotReadContextOrDecideAndMalformedTicketsFail() {
        var created = create();
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(Oauth2Exception.class, () -> service.preview(created.ticket(), null)).getStatus());
        assertThrows(Oauth2Exception.class, () -> service.decide(approve(created.ticket()), null));
        assertThrows(Oauth2Exception.class, () -> service.preview("../bad", user));
        memory.clear(); assertThrows(Oauth2Exception.class, () -> service.preview(created.ticket(), user));
    }

    @Test void requiresStrongStatePkceAndProductionClaimedCallback() {
        for (String callback : List.of("http://localhost/callback", "myapp://callback", "https://auth.ksuser.cn/callback",
            CALLBACK + "?fixed=1", CALLBACK + "#fragment", "https://client.example:8443/callback", "https://user@client.example/callback", "https://client.example/a/../callback")) {
            var a = authorization(); a.setRedirectUri(callback);
            assertThrows(Oauth2Exception.class, () -> create("oauth", a), callback);
        }
        var a = authorization(); a.setState("short"); assertThrows(Oauth2Exception.class, () -> create("oauth", a));
        a.setState(STATE); a.setCodeChallengeMethod("plain"); assertThrows(Oauth2Exception.class, () -> create("oauth", a));
        a.setCodeChallengeMethod("S256"); a.setCodeChallenge(null); assertThrows(Oauth2Exception.class, () -> create("oauth", a));
    }

    @Test void platformIsMandatoryAndUnknownPlatformIsRejected() {
        assertThrows(Oauth2Exception.class, () -> service.create(new NativeAuthorizationService.CreateRequest("oauth", null, authorization()), "192.0.2.8"));
        assertThrows(Oauth2Exception.class, () -> service.create(new NativeAuthorizationService.CreateRequest("oauth", "windows", authorization()), "192.0.2.8"));
        assertThrows(Oauth2Exception.class, () -> service.create(new NativeAuthorizationService.CreateRequest("official", "ANDROID", authorization()), "192.0.2.8"));
    }

    @Test void androidFreezesItsOwnPlatformAndRevisionThroughApproval() {
        var created = create("oauth", "android", authorization());
        assertFalse(service.preview(created.ticket(), user).officialApplication());
        assertTrue(service.decide(approve(created.ticket()), user).completed());
        verify(oauth).approveAuthorization(eq(user), any(), eq("android"), eq("revision-1"));
        verify(oauth, atLeastOnce()).validateNativeClient("client", "android", CALLBACK);
        verify(platforms, never()).nativeRevision("client", "ios", CALLBACK);
    }

    @Test void androidOfficialUsesIndependentOfficialRegistration() {
        var a = authorization(); a.setClientId("official"); a.setScope("openid profile");
        var created = create("official", "android", a);
        assertTrue(service.preview(created.ticket(), user).officialApplication());
        assertTrue(service.decide(approve(created.ticket()), user).returnUrl().contains("code=ofc_test"));
        verify(official, atLeastOnce()).validateNativeClient("official");
        verifyNoInteractions(platforms);
    }

    @Test void androidRevisionChangeInvalidatesTicketButIosChangeDoesNot() {
        var created = create("oauth", "android", authorization());
        when(platforms.nativeRevision("client", "ios", CALLBACK)).thenReturn("changed-ios");
        assertNotNull(service.preview(created.ticket(), user));
        when(platforms.nativeRevision("client", "android", CALLBACK)).thenReturn("changed-android");
        assertThrows(Oauth2Exception.class, () -> service.preview(created.ticket(), user));
        assertThrows(Oauth2Exception.class, () -> service.decide(approve(created.ticket()), user));
        verify(oauth, never()).approveAuthorization(any(), any(), anyString(), anyString());
    }

    @Test void platformChangeDuringIssuanceNeverPublishesTheCallback() {
        var created = create("oauth", "android", authorization());
        when(oauth.approveAuthorization(any(), any(), anyString(), anyString())).thenAnswer(inv -> {
            when(platforms.nativeRevision("client", "android", CALLBACK)).thenReturn("changed-during-issue");
            return new Oauth2AuthorizeApproveResponse(CALLBACK + "?code=kscode_test&state=" + STATE);
        });
        assertThrows(Oauth2Exception.class, () -> service.decide(approve(created.ticket()), user));
        assertFalse(memory.containsKey("native:authorization:" + created.ticket() + ":result")
            && memory.get("native:authorization:" + created.ticket() + ":result").contains("kscode_test"));
    }

    @Test void platformConfigurationChangeInvalidatesFrozenTicketEvenAfterReverification() {
        var created = create();
        when(platforms.nativeRevision("client", "ios", CALLBACK)).thenReturn("revision-2");
        assertThrows(Oauth2Exception.class, () -> service.preview(created.ticket(), user));
        assertThrows(Oauth2Exception.class, () -> service.decide(approve(created.ticket()), user));
        assertThrows(Oauth2Exception.class, () -> service.decide(new NativeAuthorizationService.Decision(created.ticket(), false, null, null), user));
        verify(oauth, never()).approveAuthorization(any(), any(), anyString(), anyString());
    }

    @Test void removedPlatformVerificationInvalidatesTicket() {
        var created = create();
        when(platforms.nativeRevision("client", "ios", CALLBACK)).thenThrow(new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_request", cn.ksuser.api.result.ApiResultCode.NATIVE_AUTHORIZATION_INVALID));
        assertThrows(Oauth2Exception.class, () -> service.preview(created.ticket(), user));
        assertThrows(Oauth2Exception.class, () -> service.decide(approve(created.ticket()), user));
    }
}
