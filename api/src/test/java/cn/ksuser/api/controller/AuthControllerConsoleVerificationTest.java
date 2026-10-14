package cn.ksuser.api.controller;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.dto.PasskeyAuthenticationVerifyRequest;
import cn.ksuser.api.dto.VerifySensitiveOperationRequest;
import cn.ksuser.api.dto.QrApproveRequest;
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
    private final QrChallengeService qr=mock(QrChallengeService.class);
    private final SessionTransferService transfers=mock(SessionTransferService.class);
    private final MfaService mfa=mock(MfaService.class);
    private final OfficialQrAuthorizationService officialQr=mock(OfficialQrAuthorizationService.class);
    private final MobileAuthorizationService mobileAuthorization=mock(MobileAuthorizationService.class);
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
            mock(TotpService.class),mock(EncryptionUtil.class),mock(UserSettingsRepository.class),mfa,mock(SensitiveLogUtil.class),
            transfers,mock(MobileBridgeService.class),mock(AccountRecoveryService.class),qr,
            mock(AdaptiveRiskOrchestrationService.class),mock(AdaptiveRiskMetricsService.class),mock(UserOauthAccountRepository.class),
            mock(RegistrationValidationService.class));
        controller.setConsoleSessionService(console);
        controller.setOfficialQrAuthorizationService(officialQr);
        controller.setMobileAuthorizationService(mobileAuthorization);
        user=new User("super-uuid","super","admin@example.com","stored-hash");user.setId(17L);user.setSystemRole("SUPER_ADMIN");
        when(users.findByUuid("super-uuid")).thenReturn(Optional.of(user));
    }

    @Test
    void applicationQrPreviewProvidesConsentTicketButGenericApprovalCannotBypassNativeConsent() {
        var challenge=new QrChallengeService.QrChallengePayload();challenge.setChallengeId("qr-id");challenge.setType("application");
        challenge.setAuthorizationTicket("ticket");challenge.setStatus("pending");
        when(qr.getByApproveCode("phone-code")).thenReturn(challenge);
        when(qr.getRemainingSeconds("qr-id")).thenReturn(120L);
        when(mobileAuthorization.preview("ticket",null)).thenReturn(new MobileAuthorizationService.Context(
            "ticket","外部日历",null,"help@example.com","https://app.example/callback",List.of("openid","email"),false,"PERSISTENT",120,true,false,false,"https://auth.ksuser.cn"));
        var preview=controller.getQrScanPreview("phone-code",null).getBody().getData();
        assertEquals("approve_application",preview.getCodeType());assertEquals("ticket",preview.getAuthorizationTicket());
        assertEquals("外部日历",preview.getTargetName());assertEquals(List.of("openid","email"),preview.getRequestedScopes());
        var body=new QrApproveRequest();body.setApproveCode("phone-code");
        assertEquals(400,controller.approveQrChallenge(body,authentication,http).getStatusCode().value());
        verifyNoInteractions(transfers,mfa,sensitive,console);
        verify(qr,never()).approveChallenge(any(),any(),any());
    }

    @Test
    void officialQrPreviewUsesRealApplicationScopesAndDelegatesBothDecisionsWithoutPassportTransfer() {
        var challenge=new QrChallengeService.QrChallengePayload();challenge.setChallengeId("official-qr");challenge.setType("official");challenge.setStatus("pending");
        when(qr.getByApproveCode("official-code")).thenReturn(challenge);
        when(qr.getRemainingSeconds("official-qr")).thenReturn(120L);
        when(officialQr.preview(challenge)).thenReturn(new OfficialAuthorizationService.AuthorizeContext("public-app","公共服务","https://app.example/logo.png",List.of("openid","real_name"),List.of(),true,false,false));
        when(officialQr.redirectUri(challenge)).thenReturn("https://app.example/callback");
        var preview=controller.getQrScanPreview("official-code",null).getBody().getData();
        assertEquals("公共服务",preview.getTargetName());assertEquals(List.of("openid","real_name"),preview.getRequestedScopes());
        assertFalse(preview.getConsoleApplication());assertEquals("https://app.example",preview.getTargetOrigin());
        var body=new QrApproveRequest();body.setApproveCode("official-code");
        assertEquals(400,controller.approveQrChallenge(body,authentication,http).getStatusCode().value(),"Old scanner clients must not approve scopes they cannot display");
        verify(officialQr,never()).decide(any(),any(),anyBoolean());
        assertEquals(200,controller.approveOfficialQr(body,authentication).getStatusCode().value());
        verify(officialQr).decide(challenge,user,true);
        assertEquals(200,controller.rejectOfficialQr(body,authentication).getStatusCode().value());
        verify(officialQr).decide(challenge,user,false);
        verifyNoInteractions(transfers,mfa,sensitive,console);
    }

    @Test
    void consoleQrPreviewIdentifiesTargetAndApprovalNeverCreatesPassportCredentials() {
        var challenge=new QrChallengeService.QrChallengePayload();
        challenge.setChallengeId("console-qr");challenge.setType("console_login");challenge.setStatus("pending");
        when(qr.getByApproveCode("approve-code")).thenReturn(challenge);
        when(qr.getRemainingSeconds("console-qr")).thenReturn(120L);
        when(console.consoleOrigin()).thenReturn("https://console.ksuser.cn");
        var preview=controller.getQrScanPreview("approve-code",null).getBody().getData();
        assertEquals("approve_console_login",preview.getCodeType());
        assertEquals("Ksuser Console",preview.getTargetName());
        assertEquals("https://console.ksuser.cn",preview.getTargetOrigin());
        var body=new QrApproveRequest();body.setApproveCode("approve-code");
        var response=controller.approveQrChallenge(body,authentication,http);
        assertEquals(200,response.getStatusCode().value());
        assertEquals(java.util.Map.of("verified",true),response.getBody().getData());
        verify(console).approveQrLogin(challenge,user);
        verify(qr).approveChallenge("console-qr",17L,java.util.Map.of("verified",true));
        verifyNoInteractions(transfers,mfa,sensitive);
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
