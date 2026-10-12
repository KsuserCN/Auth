package cn.ksuser.api.controller;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.dto.PasskeyAuthenticationVerifyRequest;
import cn.ksuser.api.dto.VerifySensitiveOperationRequest;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.repository.UserOauthAccountRepository;
import cn.ksuser.api.repository.UserSettingsRepository;
import cn.ksuser.api.result.ApiResultCode;
import cn.ksuser.api.security.SecurityValidator;
import cn.ksuser.api.service.*;
import cn.ksuser.api.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Native credential success, rather than a preexisting general verification marker, grants a Console proof. */
class AuthControllerConsoleVerificationTest {
    private final UserService users=mock(UserService.class);
    private final ConsoleSessionService console=mock(ConsoleSessionService.class);
    private final PasskeyService passkeys=mock(PasskeyService.class);
    private final VerificationCodeService codes=mock(VerificationCodeService.class);
    private final SensitiveOperationService sensitive=mock(SensitiveOperationService.class);
    private final Authentication authentication=new UsernamePasswordAuthenticationToken("super-uuid",null,List.of());
    private final MockHttpServletRequest http=new MockHttpServletRequest();
    private User user;
    private AuthController controller;
    @BeforeEach
    void setup() {
        AppProperties properties=new AppProperties();
        RateLimitService rate=mock(RateLimitService.class);when(rate.getClientIp(any())).thenReturn("127.0.0.1");
        controller=new AuthController(users,mock(UserSessionService.class),mock(JwtUtil.class),mock(EmailService.class),codes,rate,
            sensitive,mock(TokenBlacklistService.class),new SecurityValidator(properties),properties,passkeys,
            mock(TotpService.class),mock(EncryptionUtil.class),mock(UserSettingsRepository.class),mock(MfaService.class),mock(SensitiveLogUtil.class),
            mock(SessionTransferService.class),mock(MobileBridgeService.class),mock(AccountRecoveryService.class),mock(QrChallengeService.class),
            mock(AdaptiveRiskOrchestrationService.class),mock(AdaptiveRiskMetricsService.class),mock(UserOauthAccountRepository.class));
        controller.setConsoleSessionService(console);
        user=new User("super-uuid","super","admin@example.com","stored-hash");user.setId(17L);user.setSystemRole("SUPER_ADMIN");
        when(users.findByUuid("super-uuid")).thenReturn(Optional.of(user));
    }

    @Test
    void validPasswordCreatesProofOnlyAfterBoundRequestAndCredentialValidation() {
        when(users.verifyPassword("correct","stored-hash")).thenReturn(true);
        assertEquals(200,controller.verifySensitiveOperation(password("correct"),authentication,http).getStatusCode().value());
        var order=inOrder(console,users);
        order.verify(users).findByUuid("super-uuid");
        order.verify(console).validateVerificationRequest("console-request","super-uuid");
        order.verify(users).verifyPassword("correct","stored-hash");
        order.verify(console).recordVerificationProof("console-request","super-uuid");
    }

    @Test
    void invalidPasswordCannotReuseGeneralVerifiedState() {
        assertEquals(400,controller.verifySensitiveOperation(password("incorrect"),authentication,http).getStatusCode().value());
        verify(console).validateVerificationRequest("console-request","super-uuid");
        verify(console,never()).recordVerificationProof(anyString(),anyString());
        verify(sensitive,never()).markVerified(anyString(),anyString());
    }

    @Test
    void emailCodeCannotCreateConsoleProofEvenWhenItWouldBeValidNormally() {
        VerifySensitiveOperationRequest request=new VerifySensitiveOperationRequest("email-code",null,"123456");
        request.setConsoleVerificationId("console-request");
        assertEquals(400,controller.verifySensitiveOperation(request,authentication,http).getStatusCode().value());
        verify(console).validateVerificationRequest("console-request","super-uuid");
        verify(console,never()).recordVerificationProof(anyString(),anyString());
        verifyNoInteractions(codes,sensitive);
    }

    @Test
    void validPasskeyCreatesProofAfterActualSignatureVerification() throws Exception {
        PasskeyAuthenticationVerifyRequest credential=new PasskeyAuthenticationVerifyRequest();
        assertEquals(200,controller.verifySensitiveOperationWithPasskey(credential,"challenge-id","console-request",authentication,http).getStatusCode().value());
        var order=inOrder(console,passkeys);
        order.verify(console).validateVerificationRequest("console-request","super-uuid");
        order.verify(passkeys).verifySensitiveOperation(user,credential,"challenge-id");
        order.verify(console).recordVerificationProof("console-request","super-uuid");
    }

    @Test
    void invalidPasskeyNeverCreatesConsoleProof() throws Exception {
        PasskeyAuthenticationVerifyRequest credential=new PasskeyAuthenticationVerifyRequest();
        doThrow(new IllegalArgumentException("invalid-signature")).when(passkeys).verifySensitiveOperation(user,credential,"challenge-id");
        assertEquals(400,controller.verifySensitiveOperationWithPasskey(credential,"challenge-id","console-request",authentication,http).getStatusCode().value());
        verify(console,never()).recordVerificationProof(anyString(),anyString());
        verifyNoInteractions(sensitive);
    }

    @Test
    void expirationBetweenPasskeyVerificationAndProofKeepsOriginalFailureCode() {
        doThrow(new Oauth2Exception(HttpStatus.BAD_REQUEST,"invalid_request",ApiResultCode.CONSOLE_FLOW_INVALID))
            .when(console).recordVerificationProof("console-request","super-uuid");
        Oauth2Exception failure=assertThrows(Oauth2Exception.class,()->controller.verifySensitiveOperationWithPasskey(
            new PasskeyAuthenticationVerifyRequest(),"challenge-id","console-request",authentication,http));
        assertEquals(ApiResultCode.CONSOLE_FLOW_INVALID,failure.getResult().errorCode());
        verifyNoInteractions(sensitive);
    }

    private static VerifySensitiveOperationRequest password(String value) {
        VerifySensitiveOperationRequest request=new VerifySensitiveOperationRequest("password",value,null);
        request.setConsoleVerificationId("console-request");return request;
    }
}
