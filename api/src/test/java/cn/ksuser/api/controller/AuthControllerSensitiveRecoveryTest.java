package cn.ksuser.api.controller;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.dto.VerifySensitiveOperationRequest;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserSession;
import cn.ksuser.api.repository.*;
import cn.ksuser.api.security.SecurityValidator;
import cn.ksuser.api.service.*;
import cn.ksuser.api.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Controller tests use local mocks only: no SQL, Redis, Apple or user data. */
class AuthControllerSensitiveRecoveryTest {
    private AuthController controller;
    private UserService users;
    private UserSessionService sessions;
    private TotpService totp;
    private SensitiveOperationService sensitive;
    private EncryptionUtil encryption;
    private JwtUtil jwt;
    private MockHttpServletRequest http;
    private Authentication authentication;
    private UserSession session;
    private ConsoleSessionService console;

    @BeforeEach void setUp() {
        users=mock(UserService.class);sessions=mock(UserSessionService.class);jwt=mock(JwtUtil.class);
        totp=mock(TotpService.class);sensitive=mock(SensitiveOperationService.class);encryption=mock(EncryptionUtil.class);
        RateLimitService rate=mock(RateLimitService.class);when(rate.getClientIp(any())).thenReturn("127.0.0.1");
        AppProperties properties=new AppProperties();
        controller=new AuthController(users,sessions,jwt,mock(EmailService.class),mock(VerificationCodeService.class),rate,
            sensitive,mock(TokenBlacklistService.class),new SecurityValidator(properties),properties,mock(PasskeyService.class),
            totp,encryption,mock(UserSettingsRepository.class),mock(MfaService.class),mock(SensitiveLogUtil.class),
            mock(SessionTransferService.class),mock(MobileBridgeService.class),mock(AccountRecoveryService.class),
            mock(QrChallengeService.class),mock(AdaptiveRiskOrchestrationService.class),mock(AdaptiveRiskMetricsService.class),mock(UserOauthAccountRepository.class),
            mock(RegistrationValidationService.class));
        console=mock(ConsoleSessionService.class);
        controller.setConsoleSessionService(console);
        User user=new User("current-user","name","name@example.com",null);user.setId(17L);
        when(users.findByUuid("current-user")).thenReturn(Optional.of(user));
        when(totp.isTotpEnabled(17L)).thenReturn(true);
        http=new MockHttpServletRequest();http.addHeader("Authorization","Bearer session-token");
        authentication=new UsernamePasswordAuthenticationToken("current-user",null,List.of());
        session=new UserSession();session.setId(42L);session.setUser(user);
        when(jwt.getSessionId("session-token")).thenReturn(42L);
        when(sessions.findActiveSessionById(42L)).thenReturn(Optional.of(session));
    }
    private VerifySensitiveOperationRequest recoveryRequest(String value) {
        VerifySensitiveOperationRequest request=new VerifySensitiveOperationRequest("totp",null,null);
        request.setRecoveryCode(value);return request;
    }
    @Test void recoveryCodeFromIosAndroidJsonIsReadAndConsumedForCurrentUser() throws Exception {
        VerifySensitiveOperationRequest body=new ObjectMapper().readValue("{\"method\":\"totp\",\"recoveryCode\":\"ABCDEFGH\"}",VerifySensitiveOperationRequest.class);
        when(totp.verifyRecoveryCode(17L,"ABCDEFGH")).thenReturn(true);
        var response=controller.verifySensitiveOperation(body,authentication,http);
        assertEquals(200,response.getStatusCode().value());assertEquals(200,response.getBody().getCode());
        verify(totp).verifyRecoveryCode(17L,"ABCDEFGH");
        verify(sensitive).markVerified("current-user","127.0.0.1");
        verify(sessions).markStepUpVerified(session,"totp");
        verify(totp,never()).verifyTotpCode(anyLong(),anyString(),any());verifyNoInteractions(encryption,console);
    }
    @Test void invalidOrAlreadyUsedRecoveryCodeDoesNotGrantSensitiveAccess() {
        when(totp.verifyRecoveryCode(17L,"ABCDEFGH")).thenReturn(false);
        var response=controller.verifySensitiveOperation(recoveryRequest("ABCDEFGH"),authentication,http);
        assertEquals(400,response.getStatusCode().value());
        verify(sensitive,never()).markVerified(anyString(),anyString());verify(sessions,never()).markStepUpVerified(any(),anyString());
    }
    @Test void disabledTotpCannotConsumeRecoveryCode() {
        when(totp.isTotpEnabled(17L)).thenReturn(false);
        var response=controller.verifySensitiveOperation(recoveryRequest("ABCDEFGH"),authentication,http);
        assertEquals(400,response.getStatusCode().value());verify(totp,never()).verifyRecoveryCode(anyLong(),anyString());
    }
    @Test void unauthenticatedRequestCannotConsumeAnyRecoveryCode() {
        var response=controller.verifySensitiveOperation(recoveryRequest("ABCDEFGH"),null,http);
        assertEquals(401,response.getStatusCode().value());verifyNoInteractions(totp,sensitive);
    }
    @Test void emptyRecoveryAndOtpDoNotInvokeCredentialVerification() {
        var response=controller.verifySensitiveOperation(recoveryRequest("   "),authentication,http);
        assertEquals(400,response.getStatusCode().value());verifyNoInteractions(totp,sensitive,encryption);
    }
    @Test void existingOtpCodeVerificationRemainsCompatible() {
        byte[] key=new byte[32];when(encryption.getMasterKey()).thenReturn(key);
        when(totp.verifyTotpCode(17L,"123456",key)).thenReturn(true);
        var response=controller.verifySensitiveOperation(new VerifySensitiveOperationRequest("totp",null,"123456"),authentication,http);
        assertEquals(200,response.getStatusCode().value());verify(totp).verifyTotpCode(17L,"123456",key);
        verify(totp,never()).verifyRecoveryCode(anyLong(),anyString());verify(sensitive).markVerified("current-user","127.0.0.1");
    }
    @Test void consoleProofRequiresAnActualSuccessfulCredentialVerification() {
        VerifySensitiveOperationRequest request=recoveryRequest("ABCDEFGH");
        request.setConsoleVerificationId("console-request");
        when(totp.verifyRecoveryCode(17L,"ABCDEFGH")).thenReturn(true);
        assertEquals(200,controller.verifySensitiveOperation(request,authentication,http).getStatusCode().value());
        var order=inOrder(console,totp);
        order.verify(console).validateVerificationRequest("console-request","current-user");
        order.verify(totp).verifyRecoveryCode(17L,"ABCDEFGH");
        order.verify(console).recordVerificationProof("console-request","current-user");
    }
    @Test void invalidConsoleCredentialNeverCreatesProof() {
        VerifySensitiveOperationRequest request=recoveryRequest("ABCDEFGH");
        request.setConsoleVerificationId("console-request");
        assertEquals(400,controller.verifySensitiveOperation(request,authentication,http).getStatusCode().value());
        verify(console).validateVerificationRequest("console-request","current-user");
        verify(console,never()).recordVerificationProof(anyString(),anyString());
    }
    @Test void wrongConsoleChallengeIsRejectedBeforeCredentialsAreConsumed() {
        VerifySensitiveOperationRequest request=recoveryRequest("ABCDEFGH");
        request.setConsoleVerificationId("wrong-session-request");
        doThrow(new cn.ksuser.api.exception.Oauth2Exception(org.springframework.http.HttpStatus.FORBIDDEN,
            "access_denied",cn.ksuser.api.result.ApiResultCode.CONSOLE_ACCESS_DENIED))
            .when(console).validateVerificationRequest("wrong-session-request","current-user");
        assertThrows(cn.ksuser.api.exception.Oauth2Exception.class,
            () -> controller.verifySensitiveOperation(request,authentication,http));
        verify(totp,never()).verifyRecoveryCode(anyLong(),anyString());
        verify(console,never()).recordVerificationProof(anyString(),anyString());
    }

}
