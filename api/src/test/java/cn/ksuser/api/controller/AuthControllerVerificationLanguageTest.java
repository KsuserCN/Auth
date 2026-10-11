package cn.ksuser.api.controller;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.dto.SendCodeRequest;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.repository.*;
import cn.ksuser.api.security.SecurityValidator;
import cn.ksuser.api.service.*;
import cn.ksuser.api.util.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AuthControllerVerificationLanguageTest {
    private AuthController controller;
    private EmailService emails;
    private VerificationCodeService codes;
    private RateLimitService rate;
    private UserService users;

    @BeforeEach void setUp() {
        emails = mock(EmailService.class); codes = mock(VerificationCodeService.class);
        rate = mock(RateLimitService.class); users = mock(UserService.class);
        when(rate.getClientIp(any())).thenReturn("127.0.0.1");
        when(rate.getClientUserAgent(any())).thenReturn("fixture");
        when(rate.isIpAllowed(anyString())).thenReturn(true);
        when(rate.isEmailAllowed(anyString())).thenReturn(true);
        when(codes.generateCode()).thenReturn("012345");
        AppProperties properties = new AppProperties();
        controller = new AuthController(users, mock(UserSessionService.class), mock(JwtUtil.class), emails, codes, rate,
            mock(SensitiveOperationService.class), mock(TokenBlacklistService.class), new SecurityValidator(properties),
            properties, mock(PasskeyService.class), mock(TotpService.class), mock(EncryptionUtil.class),
            mock(UserSettingsRepository.class), mock(MfaService.class), mock(SensitiveLogUtil.class),
            mock(SessionTransferService.class), mock(MobileBridgeService.class), mock(AccountRecoveryService.class),
            mock(QrChallengeService.class), mock(AdaptiveRiskOrchestrationService.class), mock(AdaptiveRiskMetricsService.class),
            mock(UserOauthAccountRepository.class));
        when(users.findByUuid("current-user")).thenReturn(Optional.of(new User("current-user", "fixture", "bound@example.com", null)));
    }

    @ParameterizedTest
    @CsvSource({"register,en,ENGLISH", "login,zh-Hans,SIMPLIFIED_CHINESE", "change-email,en,ENGLISH", "sensitive-verification,en,ENGLISH"})
    void forwardsLanguageWithoutChangingCodeOrRecipientRules(String type, String tag, ClientLanguage expected) throws Exception {
        MockHttpServletRequest http = new MockHttpServletRequest(); http.addHeader("Accept-Language", tag);
        SendCodeRequest request = new SendCodeRequest(); request.setType(type); request.setEmail("typed@example.com");
        var response = controller.sendVerificationCode(http, request, new UsernamePasswordAuthenticationToken("current-user", null, List.of()));
        assertEquals(200, response.getStatusCode().value());
        String recipient = type.equals("sensitive-verification") ? "bound@example.com" : "typed@example.com";
        verify(emails).sendVerificationCode(recipient, "012345", type, expected);
        if (type.equals("sensitive-verification")) verify(codes).saveCodeWithType(recipient, "012345", "127.0.0.1", type);
        else verify(codes).saveCode(recipient, "012345", "127.0.0.1");
        verify(rate).recordEmailRequest(recipient); verify(rate).recordIpRequest("127.0.0.1");
    }

    @Test void missingHeaderKeepsChineseAndLanguageCannotBypassAuthenticationOrRateLimits() throws Exception {
        SendCodeRequest request = new SendCodeRequest(); request.setEmail("typed@example.com"); request.setType("login");
        assertEquals(200, controller.sendVerificationCode(new MockHttpServletRequest(), request, null).getStatusCode().value());
        verify(emails).sendVerificationCode("typed@example.com", "012345", "login", ClientLanguage.SIMPLIFIED_CHINESE);
        reset(emails, codes);
        MockHttpServletRequest http = new MockHttpServletRequest(); http.addHeader("Accept-Language", "en");
        request.setType("sensitive-verification");
        assertEquals(401, controller.sendVerificationCode(http, request, null).getStatusCode().value());
        request.setType("login"); when(rate.isEmailAllowed(anyString())).thenReturn(false);
        assertEquals(429, controller.sendVerificationCode(http, request, null).getStatusCode().value());
        verifyNoInteractions(emails);
        verify(codes, never()).generateCode();
    }
}
