package cn.ksuser.api.controller;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.dto.ChangeEmailRequest;
import cn.ksuser.api.dto.ChangePasswordRequest;
import cn.ksuser.api.dto.PasskeyRegistrationOptionsRequest;
import cn.ksuser.api.dto.PasskeyRegistrationOptionsResponse;
import cn.ksuser.api.dto.PasskeyRegistrationVerifyRequest;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserPasskey;
import cn.ksuser.api.entity.UserSession;
import cn.ksuser.api.repository.UserOauthAccountRepository;
import cn.ksuser.api.repository.UserSensitiveLogRepository;
import cn.ksuser.api.repository.UserSettingsRepository;
import cn.ksuser.api.security.SecurityValidator;
import cn.ksuser.api.service.*;
import cn.ksuser.api.support.SensitiveVerificationRedis;
import cn.ksuser.api.util.EncryptionUtil;
import cn.ksuser.api.util.JwtUtil;
import cn.ksuser.api.util.SensitiveLogUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises real verification and risk reads so a status request cannot consume a QR grant. */
class AuthControllerSensitiveOnceTest {
    private static final String USER_UUID = "current-user";
    private static final String CLIENT_IP = "192.0.2.1";
    private static final String PASSWORD = "ChangedPassword827!";

    private final UserService users = mock(UserService.class);
    private final UserSessionService sessions = mock(UserSessionService.class);
    private final VerificationCodeService codes = mock(VerificationCodeService.class);
    private final PasskeyService passkeys = mock(PasskeyService.class);
    private final UserSettingsRepository settings = mock(UserSettingsRepository.class);
    private final Authentication authentication =
        new UsernamePasswordAuthenticationToken(USER_UUID, null, List.of());
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private SensitiveOperationService sensitive;
    private SensitiveVerificationRedis redis;
    private AuthController controller;
    private User user;

    @BeforeEach
    void setUp() {
        redis = new SensitiveVerificationRedis();
        sensitive = new SensitiveOperationService(redis.template());
        user = new User(USER_UUID, "current", "current@example.com", "stored-hash");
        user.setId(17L);
        when(users.findByUuid(USER_UUID)).thenReturn(Optional.of(user));
        when(users.save(any())).thenAnswer(call -> call.getArgument(0));

        UserSession session = new UserSession();
        session.setId(42L);
        session.setSessionVersion(0);
        session.setUser(user);
        session.setIpAddress(CLIENT_IP);
        session.setIpLocation("test-location");
        session.setAuthTime(LocalDateTime.now());
        session.setLastSeenAt(LocalDateTime.now());
        when(sessions.findActiveSessionById(42L)).thenReturn(Optional.of(session));
        JwtUtil jwt = mock(JwtUtil.class);
        when(jwt.getSessionId("access-token")).thenReturn(42L);
        when(jwt.getSessionVersion("access-token")).thenReturn(0);
        request.addHeader("Authorization", "Bearer access-token");

        RateLimitService rate = mock(RateLimitService.class);
        when(rate.getClientIp(any())).thenReturn(CLIENT_IP);
        when(rate.getClientUserAgent(any())).thenReturn("test-browser");
        IpLocationService locations = mock(IpLocationService.class);
        when(locations.getIpLocation(CLIENT_IP)).thenReturn("test-location");
        AdaptiveContinuousAuthService evaluator = new AdaptiveContinuousAuthService(
            mock(UserSensitiveLogRepository.class), settings, locations, sensitive);
        AdaptiveRiskOrchestrationService adaptive = new AdaptiveRiskOrchestrationService(
            evaluator, sessions, sensitive, mock(SensitiveLogService.class),
            mock(AdaptiveRiskAlertService.class), redis.template());
        ReflectionTestUtils.setField(adaptive, "mediumThreshold", 40);
        ReflectionTestUtils.setField(adaptive, "highThreshold", 70);
        ReflectionTestUtils.setField(adaptive, "deduplicateSeconds", 60L);

        AppProperties properties = new AppProperties();
        controller = new AuthController(users, sessions, jwt, mock(EmailService.class), codes, rate,
            sensitive, mock(TokenBlacklistService.class), new SecurityValidator(properties), properties,
            passkeys, mock(TotpService.class), mock(EncryptionUtil.class), settings,
            mock(MfaService.class), mock(SensitiveLogUtil.class), mock(SessionTransferService.class),
            mock(MobileBridgeService.class), mock(AccountRecoveryService.class), mock(QrChallengeService.class),
            adaptive, mock(AdaptiveRiskMetricsService.class), mock(UserOauthAccountRepository.class),
            mock(RegistrationValidationService.class));
    }

    @Test
    void passwordChangeAfterRepeatedStatusAndRiskReadsUsesQrGrantExactlyOnce() {
        grant("change-password");
        assertStatusAndRiskRemainVerified();
        assertStatusAndRiskRemainVerified();

        assertEquals(200, controller.changePassword(new ChangePasswordRequest(PASSWORD), authentication, request)
            .getStatusCode().value());
        assertEquals(403, controller.changePassword(new ChangePasswordRequest(PASSWORD), authentication, request)
            .getStatusCode().value());
        verify(users).updatePassword(user, PASSWORD);
    }

    @Test
    void emailChangeAfterRepeatedStatusAndRiskReadsUsesQrGrantExactlyOnce() {
        grant("change-email");
        assertStatusAndRiskRemainVerified();
        assertStatusAndRiskRemainVerified();
        ChangeEmailRequest body = new ChangeEmailRequest("changed@example.com", "123456");

        assertEquals(200, controller.changeEmail(body, authentication, request).getStatusCode().value());
        assertEquals("changed@example.com", user.getEmail());
        assertEquals(403, controller.changeEmail(body, authentication, request).getStatusCode().value());
        verify(users).save(user);
        verify(codes).verifyCode("changed@example.com", "123456", CLIENT_IP);
    }

    @Test
    void correctingInvalidPasswordDoesNotRequireScanningAgain() {
        grant("change-password");

        assertEquals(400, controller.changePassword(new ChangePasswordRequest(""), authentication, request)
            .getStatusCode().value());
        assertStatusAndRiskRemainVerified();
        assertEquals(200, controller.changePassword(new ChangePasswordRequest(PASSWORD), authentication, request)
            .getStatusCode().value());
        verify(users).updatePassword(user, PASSWORD);
    }

    @Test
    void correctingEmailCodeDoesNotRequireScanningAgain() {
        grant("change-email");
        when(codes.verifyCode("changed@example.com", "wrong", CLIENT_IP)).thenReturn(2);

        assertEquals(400, controller.changeEmail(new ChangeEmailRequest("changed@example.com", "wrong"),
            authentication, request).getStatusCode().value());
        assertStatusAndRiskRemainVerified();
        assertEquals(200, controller.changeEmail(new ChangeEmailRequest("changed@example.com", "123456"),
            authentication, request).getStatusCode().value());
        verify(users).save(user);
    }

    @Test
    void passkeyOptionsPreserveQrGrantUntilRegistrationCompletesExactlyOnce() throws Exception {
        grant("login-options");
        PasskeyRegistrationOptionsRequest optionsRequest =
            new PasskeyRegistrationOptionsRequest("Security key", "cross-platform");
        PasskeyRegistrationOptionsResponse options = new PasskeyRegistrationOptionsResponse();
        options.setChallenge("registration-challenge");
        when(passkeys.generateRegistrationOptions(user, "cross-platform")).thenReturn(options);

        for (int requestNumber = 0; requestNumber < 2; requestNumber++) {
            var response = controller.generatePasskeyRegistrationOptions(optionsRequest, authentication, request);
            assertEquals(200, response.getStatusCode().value());
            assertEquals("registration-challenge", response.getBody().getData().getChallenge());
            assertStatusAndRiskRemainVerified();
        }

        PasskeyRegistrationVerifyRequest registration = new PasskeyRegistrationVerifyRequest();
        UserPasskey savedPasskey = new UserPasskey();
        savedPasskey.setId(81L);
        savedPasskey.setUserId(user.getId());
        savedPasskey.setName("Security key");
        savedPasskey.setCreatedAt(LocalDateTime.now());
        when(passkeys.verifyRegistration(user, registration)).thenAnswer(call -> {
            assertFalse(sensitive.isVerified(USER_UUID, CLIENT_IP, 42L, 0, "login-options"),
                "One-time verification must be consumed before registering the credential");
            return savedPasskey;
        });

        var response = controller.verifyPasskeyRegistration(registration, authentication, request);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(81L, response.getBody().getData().getPasskeyId());
        assertEquals("Security key", response.getBody().getData().getPasskeyName());
        assertEquals(403, controller.verifyPasskeyRegistration(registration, authentication, request)
            .getStatusCode().value());
        verify(passkeys).verifyRegistration(user, registration);
        verify(passkeys, times(2)).generateRegistrationOptions(user, "cross-platform");
    }

    private void grant(String purpose) {
        redis.template().opsForValue().set(sensitive.qrGrantKey(USER_UUID, 42L, 0, purpose),
            CLIENT_IP, java.time.Duration.ofMinutes(15));
    }

    private void assertStatusAndRiskRemainVerified() {
        var status = controller.checkSensitiveVerification(authentication, request);
        assertEquals(200, status.getStatusCode().value());
        assertTrue(status.getBody().getData().isVerified());
        assertEquals(900, status.getBody().getData().getRemainingSeconds());
        var risk = controller.getAdaptiveAuthStatus(authentication, request);
        assertEquals(200, risk.getStatusCode().value());
        assertTrue(risk.getBody().getData().isSensitiveVerified());
        assertEquals(900, risk.getBody().getData().getSensitiveVerificationRemainingSeconds());
    }
}
