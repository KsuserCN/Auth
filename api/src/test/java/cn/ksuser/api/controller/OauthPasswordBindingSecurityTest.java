package cn.ksuser.api.controller;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.dto.OauthBindRequest;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserSession;
import cn.ksuser.api.entity.UserSettings;
import cn.ksuser.api.repository.*;
import cn.ksuser.api.result.ApiResultCode;
import cn.ksuser.api.service.*;
import cn.ksuser.api.util.JwtUtil;
import cn.ksuser.api.util.SensitiveLogUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OauthPasswordBindingSecurityTest {
    private static final String TOKEN = "A".repeat(32);
    private static final String IP = "192.0.2.50";
    private final UserService users = mock(UserService.class);
    private final UserOauthAccountRepository accounts = mock(UserOauthAccountRepository.class);
    private final UserSettingsRepository settings = mock(UserSettingsRepository.class);
    private final UserPasskeyRepository passkeys = mock(UserPasskeyRepository.class);
    private final UserSessionService sessions = mock(UserSessionService.class);
    private final PasswordAuthenticationService passwords = mock(PasswordAuthenticationService.class);
    private final OauthPendingBindingService pending = mock(OauthPendingBindingService.class);
    private final OauthBindingCompletionService completion = mock(OauthBindingCompletionService.class);
    private final MfaService mfa = new MfaService();
    private final TotpService totp = mock(TotpService.class);
    private final JwtUtil jwt = mock(JwtUtil.class);
    private OauthController controller;
    private MockMvc mvc;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private User user;

    @BeforeEach void setup() {
        RateLimitService limits = mock(RateLimitService.class);
        when(limits.getClientIp(any())).thenReturn(IP);
        when(limits.getClientUserAgent(any())).thenReturn("fixture");
        controller = new OauthController(new AppProperties(), accounts, mock(UserRepository.class), users, jwt,
            sessions, limits, settings, totp, mfa, mock(SensitiveLogUtil.class), passkeys,
            mock(SensitiveOperationService.class), mock(StringRedisTemplate.class), mock(RegistrationValidationService.class));
        controller.configurePasswordBindingSecurity(passwords, pending, completion);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
        request = new MockHttpServletRequest("POST", "/oauth/github/bind-existing");
        response = new MockHttpServletResponse();
        user = new User("target-user", "fixture", "user@example.invalid", "hash"); user.setId(7L);
        when(pending.resolve(anyString(), eq(TOKEN))).thenAnswer(call -> identity(call.getArgument(0)));
        when(pending.quotaKey(TOKEN)).thenReturn("fixture-token-budget");
        when(pending.claim(anyString(), eq(TOKEN), eq(7L))).thenAnswer(call ->
            new OauthPendingBindingService.Claim(TOKEN, "owner", 7L, identity(call.getArgument(0))));
        when(passwords.authenticate(anyString(), anyString(), eq(IP), eq("fixture-token-budget"))).thenReturn(Optional.of(user));
        when(completion.complete(any())).thenReturn(user);
    }
    private OauthPendingBindingService.Identity identity(String provider) {
        return new OauthPendingBindingService.Identity(provider, "provider-user", "qq".equals(provider) ? "qq-union" : null);
    }
    private OauthBindRequest body() {
        OauthBindRequest body = new OauthBindRequest();
        body.setEmail("user@example.invalid"); body.setPassword("password"); body.setBindToken(TOKEN); return body;
    }
    private void enableMfa() {
        UserSettings value = new UserSettings(); value.setUserId(7L); value.setMfaEnabled(true);
        when(settings.findByUserId(7L)).thenReturn(Optional.of(value));
    }
    @ParameterizedTest @ValueSource(strings = {"qq", "github", "google", "microsoft"})
    void allProviderEntrypointsReserveSharedPasswordBudgetBeforeBinding(String provider) throws Exception {
        when(passwords.authenticate(anyString(), anyString(), eq(IP), anyString())).thenThrow(
            new PasswordAuthenticationService.AuthenticationFailure(HttpStatus.TOO_MANY_REQUESTS, ApiResultCode.AUTH_035));
        mvc.perform(post("/oauth/" + provider + "/bind-existing").contentType("application/json")
            .content("{\"email\":\"user@example.invalid\",\"password\":\"wrong\",\"bindToken\":\"" + TOKEN + "\"}"))
            .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.errorCode").value("AUTH_035"));
        verify(passwords).authenticate("user@example.invalid", "wrong", IP, "fixture-token-budget");
        verify(users, never()).login(any(), any());
        verify(pending, never()).claim(any(), any(), any());
        verifyNoInteractions(accounts, sessions, completion, jwt);
    }
    @Test void pendingRedisFailureReturnsUnavailableBeforePasswordAuthentication() throws Exception {
        when(pending.resolve("github", TOKEN)).thenThrow(
            new OauthPendingBindingService.BindingFailure(HttpStatus.SERVICE_UNAVAILABLE, ApiResultCode.INTERNAL_ERROR));
        mvc.perform(post("/oauth/github/bind-existing").contentType("application/json")
            .content("{\"email\":\"user@example.invalid\",\"password\":\"wrong\",\"bindToken\":\"" + TOKEN + "\"}"))
            .andExpect(status().isServiceUnavailable());
        verifyNoInteractions(passwords, accounts, sessions, completion, jwt);
    }
    @Test void incorrectPasswordDoesNotClaimTokenOrWriteBinding() {
        when(passwords.authenticate(anyString(), anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        assertEquals(401, controller.bindExisting(body(), request, response).getStatusCode().value());
        verify(pending, never()).claim(any(), any(), any());
        verifyNoInteractions(accounts, completion, sessions, jwt);
    }
    @Test void noMfaCompletesClaimBeforeIssuingSession() {
        UserSession session = new UserSession(); session.setId(9L); session.setSessionVersion(0);
        when(jwt.generateRefreshToken(user.getUuid())).thenReturn("refresh");
        when(sessions.createSession(user, "refresh", IP, "fixture")).thenReturn(session);
        assertEquals(200, controller.bindExisting(body(), request, response).getStatusCode().value());
        var order = inOrder(passwords, pending, completion, jwt, sessions);
        order.verify(passwords).authenticate("user@example.invalid", "password", IP, "fixture-token-budget");
        order.verify(pending).claim("github", TOKEN, 7L);
        order.verify(completion).complete(any());
        order.verify(jwt).generateRefreshToken(user.getUuid());
        order.verify(sessions).createSession(user, "refresh", IP, "fixture");
        verify(accounts, never()).save(any());
    }
    @Test void requiredMfaReturnsChallengeWithoutPersistingBindingOrIssuingTokens() {
        enableMfa(); when(totp.isTotpEnabled(7L)).thenReturn(true);
        var result = controller.bindExisting(body(), request, response);
        assertEquals(201, result.getStatusCode().value());
        var data = (java.util.Map<?, ?>) result.getBody().getData();
        String challengeId = (String) data.get("challengeId");
        assertEquals(List.of("totp"), data.get("methods"));
        assertFalse(mfa.isMethodAllowed(challengeId, "qr"));
        assertEquals(7L, mfa.verifyChallenge(challengeId, IP, "fixture"));
        assertNull(mfa.verifyChallenge(challengeId, "192.0.2.99", "fixture"));
        verifyNoInteractions(completion, sessions, jwt);
        verify(accounts, never()).save(any());
        assertNull(response.getHeader("Set-Cookie"));
    }
    @Test void mfaWithoutIndependentFactorFailsClosed() {
        enableMfa();
        assertEquals(403, controller.bindExisting(body(), request, response).getStatusCode().value());
        verifyNoInteractions(completion, sessions, jwt);
        verify(accounts, never()).save(any());
    }
    @Test void claimLostToConcurrentRequestCannotIssueSessionOrBind() throws Exception {
        when(pending.claim("github", TOKEN, 7L)).thenThrow(
            new OauthPendingBindingService.BindingFailure(HttpStatus.BAD_REQUEST, ApiResultCode.OAUTH_087));
        mvc.perform(post("/oauth/github/bind-existing").contentType("application/json")
            .content("{\"email\":\"user@example.invalid\",\"password\":\"right\",\"bindToken\":\"" + TOKEN + "\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(completion, sessions, jwt);
        verify(accounts, never()).save(any());
    }
}
