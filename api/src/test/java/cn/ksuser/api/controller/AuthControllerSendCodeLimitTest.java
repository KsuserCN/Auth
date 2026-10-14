package cn.ksuser.api.controller;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.dto.RegisterRequest;
import cn.ksuser.api.dto.RegisterResult;
import cn.ksuser.api.dto.SendCodeRequest;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserSession;
import cn.ksuser.api.repository.UserOauthAccountRepository;
import cn.ksuser.api.repository.UserSettingsRepository;
import cn.ksuser.api.result.ApiResultCode;
import cn.ksuser.api.security.SecurityValidator;
import cn.ksuser.api.service.*;
import cn.ksuser.api.support.SendCodeRateLimitRedis;
import cn.ksuser.api.util.*;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static cn.ksuser.api.service.RateLimitService.SendCodeLimit.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthControllerSendCodeLimitTest {
    private static final String IP = "203.0.113.10";
    private static final String CODE = "012345";
    private static final String PASSWORD = "StrongPass123";
    private AuthController controller;
    private EmailService emails;
    private VerificationCodeService codes;
    private RateLimitService rate;
    private UserService users;
    private UserSessionService sessions;
    private JwtUtil jwt;
    private SendCodeRateLimitRedis redis;
    private MockHttpServletRequest http;

    @BeforeEach
    void setUp() {
        redis = new SendCodeRateLimitRedis();
        AppProperties properties = new AppProperties();
        rate = spy(new RateLimitService(redis.template(), properties));
        codes = spy(new VerificationCodeService(redis.template()));
        when(codes.generateCode()).thenReturn(CODE);
        emails = mock(EmailService.class);
        users = mock(UserService.class);
        sessions = mock(UserSessionService.class);
        jwt = mock(JwtUtil.class);
        UserSettingsRepository settings = mock(UserSettingsRepository.class);
        when(settings.save(any())).thenAnswer(call -> call.getArgument(0));
        controller = new AuthController(users, sessions, jwt, emails, codes, rate,
            mock(SensitiveOperationService.class), mock(TokenBlacklistService.class), new SecurityValidator(properties),
            properties, mock(PasskeyService.class), mock(TotpService.class), mock(EncryptionUtil.class),
            settings, mock(MfaService.class), mock(SensitiveLogUtil.class), mock(SessionTransferService.class),
            mock(MobileBridgeService.class), mock(AccountRecoveryService.class), mock(QrChallengeService.class),
            mock(AdaptiveRiskOrchestrationService.class), mock(AdaptiveRiskMetricsService.class),
            mock(UserOauthAccountRepository.class),
            new RegistrationValidationService(properties, new SecurityValidator(properties), codes));
        http = new MockHttpServletRequest();
        http.setRemoteAddr(IP);
        http.addHeader("User-Agent", "same-school-app-version");
    }

    @ParameterizedTest
    @CsvSource({"EMAIL_MINUTE,AUTH_012", "EMAIL_HOUR,AUTH_014", "IP_MINUTE,AUTH_012", "IP_HOUR,AUTH_013"})
    void exhaustedPermitRejectsBeforeCreatingCodeOrSendingMail(RateLimitService.SendCodeLimit limit,
                                                              ApiResultCode result) {
        doReturn(limit).when(rate).acquireSendCodePermit("member@example.com", IP);

        var response = controller.sendVerificationCode(http, request("member@example.com"), null);

        assertEquals(429, response.getStatusCode().value());
        assertEquals(result, response.getBody().getErrorCode());
        verify(codes, never()).generateCode();
        verifyNoInteractions(emails);
        assertNull(redis.get("verification:code:member@example.com"));
    }

    @Test
    void unavailableQuotaReturnsServiceUnavailableBeforeCreatingCodeOrSendingMail() {
        doReturn(UNAVAILABLE).when(rate).acquireSendCodePermit("member@example.com", IP);

        var response = controller.sendVerificationCode(http, request("member@example.com"), null);

        assertEquals(503, response.getStatusCode().value());
        assertEquals(ApiResultCode.INTERNAL_ERROR, response.getBody().getErrorCode());
        verify(codes, never()).generateCode();
        verifyNoInteractions(emails);
    }

    @Test
    void mailFailureKeepsItsReservedQuotaAndDoesNotBlockOtherMailboxes() throws Exception {
        doThrow(new MessagingException("SMTP unavailable")).doNothing().when(emails)
            .sendVerificationCode(anyString(), anyString(), anyString(), any(ClientLanguage.class));

        assertEquals(500, controller.sendVerificationCode(http, request("first@example.com"), null).getStatusCode().value());
        assertEquals(429, controller.sendVerificationCode(http, request("first@example.com"), null).getStatusCode().value());
        assertEquals(200, controller.sendVerificationCode(http, request("second@example.com"), null).getStatusCode().value());

        assertEquals("1", redis.get("ratelimit:minute:verify:first@example.com"));
        assertEquals("1", redis.get("ratelimit:hour:verify:first@example.com"));
        assertEquals("2", redis.get("ratelimit:minute:verify:" + IP));
        verify(emails, times(2)).sendVerificationCode(anyString(), eq(CODE), eq("register"), any(ClientLanguage.class));
        verify(rate, never()).recordEmailRequest(anyString());
        verify(rate, never()).recordIpRequest(anyString());
    }

    @Test
    void sharedIpUsersCanRequestCodesAndRegisterWithoutLockingEachOtherOrResettingSendQuota() {
        redis.put("register:lock:ip:" + IP, "locked", 86400);
        for (int index = 1; index <= 4; index++) {
            String email = "member" + index + "@example.com";
            String username = "Member_" + index;
            User user = new User("registered-" + index, username, email, "stored-hash");
            user.setId((long) index);
            when(users.register(username, email, PASSWORD))
                .thenReturn(new RegisterResult(RegisterResult.Status.SUCCESS, user));
            when(jwt.generateRefreshToken(user.getUuid())).thenReturn("refresh-" + index);
            UserSession session = new UserSession();
            session.setId((long) index);
            session.setSessionVersion(0);
            when(sessions.createSession(eq(user), anyString(), eq(IP), anyString())).thenReturn(session);

            assertEquals(200, controller.sendVerificationCode(http, request(email), null).getStatusCode().value());
            var registered = controller.register(new RegisterRequest(username, email, PASSWORD, CODE), http,
                new MockHttpServletResponse());
            assertEquals(200, registered.getStatusCode().value());
            assertEquals(ApiResultCode.AUTH_032, registered.getBody().getErrorCode());
            assertEquals("1", redis.get("ratelimit:minute:verify:" + email));
            assertEquals(Integer.toString(index), redis.get("ratelimit:minute:verify:" + IP));
        }

        assertEquals("locked", redis.get("register:lock:ip:" + IP));
        assertEquals(429, controller.sendVerificationCode(http, request("member1@example.com"), null).getStatusCode().value());
        assertEquals(200, controller.sendVerificationCode(http, request("member5@example.com"), null).getStatusCode().value());
        verify(users, times(4)).register(anyString(), anyString(), anyString());
    }

    private static SendCodeRequest request(String email) {
        SendCodeRequest request = new SendCodeRequest();
        request.setType("register");
        request.setEmail(email);
        return request;
    }
}
