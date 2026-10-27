package cn.ksuser.api.controller;

import cn.ksuser.api.dto.*;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserSession;
import cn.ksuser.api.result.ApiResultCode;
import cn.ksuser.api.security.SecurityValidator;
import cn.ksuser.api.service.*;
import cn.ksuser.api.util.EncryptionUtil;
import cn.ksuser.api.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthControllerPasswordAndOauthMfaTest {
    private final Map<Class<?>, Object> dependencies = new HashMap<>();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private AuthController controller;
    private final MfaService mfa = new MfaService();
    private PasswordAuthenticationService passwords;
    private OauthBindingCompletionService bindings;
    private User user;

    @BeforeEach void setup() throws Exception {
        dependencies.put(MfaService.class, mfa);
        Constructor<?> constructor = AuthController.class.getConstructors()[0];
        Object[] arguments = Arrays.stream(constructor.getParameterTypes())
            .map(type -> dependencies.computeIfAbsent(type, k -> mock(k))).toArray();
        controller = (AuthController) constructor.newInstance(arguments);
        passwords = mock(PasswordAuthenticationService.class);
        bindings = mock(OauthBindingCompletionService.class);
        controller.setPasswordAuthenticationService(passwords);
        controller.setOauthBindingCompletionService(bindings);
        request.addHeader("User-Agent", "fixture-browser");
        when(dependency(RateLimitService.class).getClientIp(request)).thenReturn("192.0.2.1");
        user = new User(); user.setId(1L); user.setUuid("fixture-user");
        when(dependency(UserService.class).findById(1L)).thenReturn(Optional.of(user));
        UserSession session = new UserSession(); session.setId(9L); session.setSessionVersion(0);
        when(dependency(UserSessionService.class).createSession(eq(user), any(), any(), any())).thenReturn(session);
        when(dependency(JwtUtil.class).generateRefreshToken(user.getUuid())).thenReturn("refresh");
        when(dependency(JwtUtil.class).generateAccessToken(user.getUuid(), 9L, 0)).thenReturn("access");
        when(bindings.complete(any())).thenReturn(user);
    }

    private <T> T dependency(Class<T> type) { return type.cast(dependencies.get(type)); }
    private String challenge(Set<String> methods) {
        var claim = new OauthPendingBindingService.Claim("token", "owner", 1L,
            new OauthPendingBindingService.Identity("github", "subject", null));
        return mfa.createOauthBindingChallenge(1L, "192.0.2.1", "fixture-browser", "github", methods, claim);
    }

    @Test void ordinaryPasswordLoginUsesOnlySharedAuthenticationService() {
        when(passwords.authenticate("Fixture@example.invalid", "password", "192.0.2.1")).thenReturn(Optional.of(user));
        assertEquals(200, controller.login(new LoginRequest("Fixture@example.invalid", "password"), request, response).getStatusCode().value());
        verify(passwords).authenticate("Fixture@example.invalid", "password", "192.0.2.1");
        verify(dependency(UserService.class), never()).login(any(), any());
        verify(dependency(RateLimitService.class), never()).recordEmailRequest(any(), any());
        verify(dependency(RateLimitService.class), never()).recordIpRequest(any(), any());
    }

    @Test void quotaAndRedisFailuresCannotCreatePasswordSession() {
        for (HttpStatus status : new HttpStatus[]{HttpStatus.TOO_MANY_REQUESTS, HttpStatus.SERVICE_UNAVAILABLE}) {
            doThrow(new PasswordAuthenticationService.AuthenticationFailure(status, ApiResultCode.AUTH_035))
                .when(passwords).authenticate(any(), any(), any());
            assertEquals(status.value(), controller.login(new LoginRequest("fixture@example.invalid", "password"), request, response).getStatusCode().value());
        }
        verifyNoInteractions(dependency(UserSessionService.class));
        verify(dependency(UserService.class), never()).login(any(), any());
    }

    @Test void emailCodeLoginCannotClearPasswordOrSharedNetworkBudgets() {
        when(dependency(SecurityValidator.class).isValidEmail("fixture@example.invalid")).thenReturn(true);
        when(dependency(VerificationCodeService.class).verifyCode("fixture@example.invalid", "123456", "192.0.2.1")).thenReturn(0);
        when(dependency(UserService.class).findByEmail("fixture@example.invalid")).thenReturn(Optional.of(user));
        var body = new LoginCodeRequest(); body.setEmail("fixture@example.invalid"); body.setCode("123456");
        assertEquals(200, controller.loginWithCode(body, request, response).getStatusCode().value());
        verify(dependency(RateLimitService.class), never()).clearAllLimits(any(), any());
    }

    @Test void failedMfaDoesNotCompleteBindingOrCreateSession() {
        String id = challenge(Set.of("totp"));
        assertEquals(400, controller.verifyTotpForLogin(new MfaTotpVerifyRequest(id, "123456"), request, response).getStatusCode().value());
        verifyNoInteractions(bindings, dependency(UserSessionService.class));
        assertEquals(4, mfa.getRemainingAttempts(id));
    }

    @Test void successfulTotpCompletesBindingBeforeSessionAndCannotReplay() {
        String id = challenge(Set.of("totp"));
        when(dependency(TotpService.class).verifyTotpCode(eq(1L), eq("123456"), any())).thenReturn(true);
        assertEquals(200, controller.verifyTotpForLogin(new MfaTotpVerifyRequest(id, "123456"), request, response).getStatusCode().value());
        var order = inOrder(bindings, dependency(UserSessionService.class));
        order.verify(bindings).complete(any());
        order.verify(dependency(UserSessionService.class)).createSession(eq(user), any(), any(), any());
        assertEquals(400, controller.verifyTotpForLogin(new MfaTotpVerifyRequest(id, "123456"), request, response).getStatusCode().value());
        verify(bindings, times(1)).complete(any());
        verify(dependency(UserSessionService.class), times(1)).createSession(any(), any(), any(), any());
    }

    @Test void successfulPasskeyCompletesBindingBeforeSessionAndCannotReplay() throws Exception {
        String id = challenge(Set.of("passkey"));
        when(dependency(PasskeyService.class).verifyAuthenticationAndGetUserId(any(), eq("passkey-challenge"))).thenReturn(1L);
        var body = new MfaPasskeyVerifyRequest(id, "passkey-challenge", "raw", "client-data", "auth-data", "signature");
        assertEquals(200, controller.verifyPasskeyForLoginMfa(body, request, response).getStatusCode().value());
        var order = inOrder(bindings, dependency(UserSessionService.class));
        order.verify(bindings).complete(any());
        order.verify(dependency(UserSessionService.class)).createSession(eq(user), any(), any(), any());
        assertEquals(400, controller.verifyPasskeyForLoginMfa(body, request, response).getStatusCode().value());
        verify(bindings, times(1)).complete(any());
    }

    @Test void bindingStorageFailureCannotIssueSessionOrBeRetriedAsOrdinaryLogin() {
        String id = challenge(Set.of("totp"));
        when(dependency(TotpService.class).verifyTotpCode(eq(1L), eq("123456"), any())).thenReturn(true);
        when(bindings.complete(any())).thenThrow(new IllegalStateException("storage unavailable"));
        assertEquals(500, controller.verifyTotpForLogin(new MfaTotpVerifyRequest(id, "123456"), request, response).getStatusCode().value());
        verifyNoInteractions(dependency(UserSessionService.class));
        assertEquals(400, controller.verifyTotpForLogin(new MfaTotpVerifyRequest(id, "123456"), request, response).getStatusCode().value());
    }

    @Test void bindingConflictReturnsConflictWithoutSession() {
        String id = challenge(Set.of("totp"));
        when(dependency(TotpService.class).verifyTotpCode(eq(1L), eq("123456"), any())).thenReturn(true);
        when(bindings.complete(any())).thenThrow(new OauthPendingBindingService.BindingFailure(HttpStatus.CONFLICT, ApiResultCode.OAUTH_081));
        assertEquals(409, controller.verifyTotpForLogin(new MfaTotpVerifyRequest(id, "123456"), request, response).getStatusCode().value());
        verifyNoInteractions(dependency(UserSessionService.class));
    }
}
