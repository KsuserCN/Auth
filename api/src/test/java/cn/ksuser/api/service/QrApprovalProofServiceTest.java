package cn.ksuser.api.service;

import cn.ksuser.api.dto.*;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserSession;
import cn.ksuser.api.result.ApiResultCode;
import cn.ksuser.api.util.EncryptionUtil;
import cn.ksuser.api.util.JwtUtil;
import cn.ksuser.api.util.SensitiveLogUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** No network: validates credential, session and QR binding around the Redis atomic operations. */
class QrApprovalProofServiceTest {
    private QrChallengeService challenges;
    private UserService users;
    private UserSessionService sessions;
    private JwtUtil jwt;
    private TotpService totp;
    private EncryptionUtil encryption;
    private PasskeyService passkeys;
    private VerificationCodeService codes;
    private StringRedisTemplate redis;
    private QrApprovalProofService service;
    private User user;
    private UserSession session;
    private QrChallengeService.QrChallengePayload challenge;
    private MockHttpServletRequest request;
    private Authentication authentication;
    private final Map<String,String> bindings = new HashMap<>();
    private final Map<String,Integer> attempts = new HashMap<>();

    @BeforeEach @SuppressWarnings("unchecked") void setUp() {
        challenges = mock(QrChallengeService.class); users = mock(UserService.class);
        sessions = mock(UserSessionService.class); jwt = mock(JwtUtil.class); totp = mock(TotpService.class);
        encryption = mock(EncryptionUtil.class); passkeys = mock(PasskeyService.class);
        codes = mock(VerificationCodeService.class); redis = mock(StringRedisTemplate.class);
        ValueOperations<String,String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        doAnswer(call -> { bindings.put(call.getArgument(0), call.getArgument(1)); return null; })
            .when(values).set(anyString(), anyString(), any(Duration.class));
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenAnswer(call -> {
            List<String> keys = call.getArgument(1);
            Object[] expanded = call.getArguments();
            Object[] args = expanded.length == 3 && expanded[2] instanceof Object[] packed
                ? packed : Arrays.copyOfRange(expanded, 2, expanded.length);
            if (keys.size() == 1) {
                if (!Objects.equals(bindings.get(keys.getFirst()), args[0])) return 0L;
                bindings.remove(keys.getFirst());
                return 1L;
            }
            // Local state for credential-flow tests; the production reservation is a single Redis Lua operation.
            for (int i=0; i<3; i++) {
                if (attempts.getOrDefault(keys.get(i), 0) >= Integer.parseInt(args[i].toString())) return 0L;
            }
            for (String key : keys) attempts.merge(key, 1, Integer::sum);
            return 1L;
        });
        RateLimitService rate = mock(RateLimitService.class);
        when(rate.getClientIp(any())).thenReturn("192.0.2.10");
        service = new QrApprovalProofService(challenges, users, sessions, jwt, totp, encryption, passkeys,
            codes, rate, redis, mock(SensitiveLogUtil.class));
        user = new User("fixture-uuid", "fixture", "bound@example.invalid", "password-hash"); user.setId(17L);
        session = new UserSession(); session.setId(42L); session.setUser(user); session.setSessionVersion(3);
        when(users.findByUuid(user.getUuid())).thenReturn(Optional.of(user));
        when(sessions.findActiveSessionById(42L)).thenReturn(Optional.of(session));
        request = new MockHttpServletRequest(); request.addHeader("Authorization", "Bearer fixture-token");
        authentication = new UsernamePasswordAuthenticationToken(user.getUuid(), null, List.of());
        when(jwt.isTokenValid("fixture-token")).thenReturn(true);
        when(jwt.getTokenType("fixture-token")).thenReturn("access");
        when(jwt.getUuidFromToken("fixture-token")).thenReturn(user.getUuid());
        when(jwt.getSessionId("fixture-token")).thenReturn(42L);
        when(jwt.getSessionVersion("fixture-token")).thenReturn(3);
        challenge = challenge("qr-one");
        when(challenges.getByApproveCode("approve-one")).thenReturn(challenge);
        when(challenges.issueApprovalProof(anyString(), anyLong(), anyLong(), anyInt(), anyString()))
            .thenReturn(new QrChallengeService.ApprovalProof("fixture-proof", 100));
    }

    private QrChallengeService.QrChallengePayload challenge(String id) {
        var result = new QrChallengeService.QrChallengePayload();
        result.setSchemaVersion(2); result.setChallengeId(id); result.setRequestedUserId(17L);
        result.setStatus("pending"); result.setType("sensitive");
        result.setExpiresAt(Instant.now().getEpochSecond()+110);
        return result;
    }
    private QrApprovalVerifyRequest password(String value) {
        return new QrApprovalVerifyRequest("approve-one", "password", value, null, null);
    }
    private QrApprovalProofService.ApprovalFailure failure(Runnable action, HttpStatus expected) {
        var failure = assertThrows(QrApprovalProofService.ApprovalFailure.class, action::run);
        assertEquals(expected, failure.status()); return failure;
    }

    @Test void ordinaryTokenWithoutCredentialCannotMintProof() {
        failure(() -> service.verify(password(null), authentication, request), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(totp, encryption, passkeys, codes);
        verify(challenges, never()).issueApprovalProof(any(), any(), any(), any(), any());
    }
    @Test void validPasswordMintsOnlyProofForCurrentSessionAndChallenge() {
        when(users.verifyPassword("correct", "password-hash")).thenReturn(true);
        assertEquals("fixture-proof", service.verify(password("correct"), authentication, request).proofId());
        verify(challenges).issueApprovalProof("qr-one",17L,42L,3,"password");
        verify(users, times(1)).verifyPassword("correct", "password-hash");
    }
    @Test void incorrectPasswordDoesNotMintProof() {
        assertEquals(ApiResultCode.AUTH_130,
            failure(() -> service.verify(password("wrong"), authentication, request), HttpStatus.BAD_REQUEST).resultCode());
        verify(challenges, never()).issueApprovalProof(any(), any(), any(), any(), any());
    }
    @Test void passwordlessAccountCannotAuthenticateWithPassword() {
        user.setPasswordHash(null);
        failure(() -> service.verify(password("anything"), authentication, request), HttpStatus.BAD_REQUEST);
        verify(users, never()).verifyPassword(any(), any());
    }
    @Test void emailProofVerifiesBoundMailboxAndConsumesSensitiveCode() {
        when(codes.verifyCodeForType("bound@example.invalid","123456","192.0.2.10","sensitive-verification"))
            .thenReturn(0);
        service.verify(new QrApprovalVerifyRequest("approve-one","email-code",null,"123456",null),authentication,request);
        verify(challenges).issueApprovalProof("qr-one",17L,42L,3,"email-code");
    }
    @Test void recoveryCodeIsNormalizedAndRequiresExistingEnabledTotp() {
        when(totp.isTotpEnabled(17L)).thenReturn(true);
        when(totp.verifyRecoveryCode(17L,"ABCDEFGH")).thenReturn(true);
        service.verify(new QrApprovalVerifyRequest("approve-one","totp",null,null,"abcdefgh"),authentication,request);
        verify(challenges).issueApprovalProof("qr-one",17L,42L,3,"recovery_code");
        verifyNoInteractions(encryption);
    }
    @Test void disabledTotpCannotProduceProof() {
        failure(() -> service.verify(new QrApprovalVerifyRequest("approve-one","totp",null,"123456",null),
            authentication,request),HttpStatus.BAD_REQUEST);
        verify(totp,never()).verifyTotpCode(any(),any(),any());
    }
    @Test void totpUsesServerEncryptionKeyAndUser() {
        byte[] key = new byte[32]; when(encryption.getMasterKey()).thenReturn(key);
        when(totp.isTotpEnabled(17L)).thenReturn(true); when(totp.verifyTotpCode(17L,"123456",key)).thenReturn(true);
        service.verify(new QrApprovalVerifyRequest("approve-one","totp",null,"123456",null),authentication,request);
        verify(challenges).issueApprovalProof("qr-one",17L,42L,3,"totp");
    }
    @Test void globalVerificationOrQrMethodCannotReplaceCredential() {
        failure(() -> service.verify(new QrApprovalVerifyRequest("approve-one","qr",null,null,null),
            authentication,request),HttpStatus.BAD_REQUEST);
        verify(users,never()).verifyPassword(any(),any());
    }
    @Test void wrongUserAndOldOrExpiredChallengesRejectBeforeCredentialCheck() {
        challenge.setRequestedUserId(999L);
        failure(() -> service.verify(password("correct"),authentication,request),HttpStatus.FORBIDDEN);
        challenge.setRequestedUserId(17L); challenge.setSchemaVersion(0);
        failure(() -> service.verify(password("correct"),authentication,request),HttpStatus.BAD_REQUEST);
        challenge.setSchemaVersion(2);challenge.setExpiresAt(Instant.now().getEpochSecond()-1);
        failure(() -> service.verify(password("correct"),authentication,request),HttpStatus.BAD_REQUEST);
        verify(users,never()).verifyPassword(any(),any());
    }
    @Test void anonymousAndWrongSessionVersionCannotProduceProof() {
        var anonymous = new AnonymousAuthenticationToken("fixture","anonymousUser",List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        failure(() -> service.verify(password("correct"),anonymous,request),HttpStatus.UNAUTHORIZED);
        session.setSessionVersion(4);
        failure(() -> service.verify(password("correct"),authentication,request),HttpStatus.UNAUTHORIZED);
        verify(users,never()).verifyPassword(any(),any());
    }
    @Test void sessionRevokedDuringCredentialCheckCannotMintProof() {
        when(users.verifyPassword("correct","password-hash")).thenReturn(true);
        when(sessions.findActiveSessionById(42L)).thenReturn(Optional.of(session),Optional.empty());
        failure(() -> service.verify(password("correct"),authentication,request),HttpStatus.UNAUTHORIZED);
        verify(challenges,never()).issueApprovalProof(any(),any(),any(),any(),any());
    }
    @Test void attemptsShareAccountBudgetAcrossChallengesAndCredentialMethods() {
        for (int i=0; i<5; i++) {
            var next = challenge("qr-"+i); when(challenges.getByApproveCode("approve-one")).thenReturn(next);
            failure(() -> service.verify(password("wrong"),authentication,request),HttpStatus.BAD_REQUEST);
        }
        failure(() -> service.verify(new QrApprovalVerifyRequest("approve-one","totp",null,"123456",null),
            authentication,request),HttpStatus.TOO_MANY_REQUESTS);
        verify(users,times(5)).verifyPassword("wrong","password-hash"); verifyNoInteractions(totp);
    }
    @Test @SuppressWarnings("unchecked") void redisOutageFailsClosedBeforeCredentialVerification() {
        when(redis.execute(any(RedisScript.class),anyList(),any(Object[].class)))
            .thenThrow(new DataAccessResourceFailureException("fixture outage"));
        failure(() -> service.verify(password("correct"),authentication,request),HttpStatus.SERVICE_UNAVAILABLE);
        verify(users,never()).verifyPassword(any(),any());
    }

    private PasskeyAuthenticationVerifyRequest assertion() {
        return new PasskeyAuthenticationVerifyRequest("credential","client","authenticator","signature");
    }
    private void passkeyOptions() throws Exception {
        var options = new PasskeyAuthenticationOptionsResponse(); options.setChallengeId("passkey-one");
        when(passkeys.generateSensitiveVerificationOptions()).thenReturn(options);
        assertEquals("passkey-one",service.passkeyOptions("approve-one",authentication,request).getChallengeId());
    }
    @Test void passkeyIsCryptographicallyVerifiedAndQrBindingConsumedOnce() throws Exception {
        passkeyOptions(); var assertion = assertion();
        var input = new QrApprovalPasskeyVerifyRequest("approve-one","passkey-one",assertion);
        service.verifyPasskey(input,authentication,request);
        verify(passkeys).verifySensitiveOperation(user,assertion,"passkey-one");
        verify(challenges).issueApprovalProof("qr-one",17L,42L,3,"passkey");
        failure(() -> service.verifyPasskey(input,authentication,request),HttpStatus.BAD_REQUEST);
        verify(passkeys,times(1)).verifySensitiveOperation(any(),any(),any());
    }
    @Test void passkeyFromOtherQrCannotProduceProofOrConsumeOriginalBinding() throws Exception {
        passkeyOptions(); when(challenges.getByApproveCode("approve-two")).thenReturn(challenge("qr-two"));
        failure(() -> service.verifyPasskey(new QrApprovalPasskeyVerifyRequest("approve-two","passkey-one",assertion()),
            authentication,request),HttpStatus.BAD_REQUEST);
        verify(passkeys,never()).verifySensitiveOperation(any(),any(),any());
        service.verifyPasskey(new QrApprovalPasskeyVerifyRequest("approve-one","passkey-one",assertion()),authentication,request);
        verify(challenges).issueApprovalProof("qr-one",17L,42L,3,"passkey");
    }
    @Test void passkeyBindingCannotBeUsedFromAnotherSessionVersion() throws Exception {
        passkeyOptions(); session.setSessionVersion(4); when(jwt.getSessionVersion("fixture-token")).thenReturn(4);
        failure(() -> service.verifyPasskey(new QrApprovalPasskeyVerifyRequest("approve-one","passkey-one",assertion()),
            authentication,request),HttpStatus.BAD_REQUEST);
        verify(passkeys,never()).verifySensitiveOperation(any(),any(),any());
    }
    @Test void invalidPasskeySignatureConsumesChallengeWithoutMintingProof() throws Exception {
        passkeyOptions(); doThrow(new IllegalArgumentException("fixture invalid signature"))
            .when(passkeys).verifySensitiveOperation(any(),any(),any());
        var input = new QrApprovalPasskeyVerifyRequest("approve-one","passkey-one",assertion());
        failure(() -> service.verifyPasskey(input,authentication,request),HttpStatus.BAD_REQUEST);
        failure(() -> service.verifyPasskey(input,authentication,request),HttpStatus.BAD_REQUEST);
        verify(passkeys,times(1)).verifySensitiveOperation(any(),any(),any());
        verify(challenges,never()).issueApprovalProof(any(),any(),any(),any(),any());
    }

    @Test void consoleQrCannotDowngradeItsCredentialPolicyToEmail() {
        challenge.setConsoleVerificationRequestId("console-request");
        var failure = assertThrows(QrApprovalProofService.ApprovalFailure.class, () -> service.verify(
            new QrApprovalVerifyRequest("approve-one", "email-code", null, "123456", null), authentication, request));
        assertEquals(ApiResultCode.CONSOLE_FLOW_INVALID, failure.resultCode());
        verifyNoInteractions(codes);
        verify(challenges, never()).issueApprovalProof(any(), any(), any(), any(), any());
    }
}
