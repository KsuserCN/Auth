package cn.ksuser.api.service;

import cn.ksuser.api.dto.*;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserSession;
import cn.ksuser.api.result.ApiResultCode;
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
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises actual credential decisions and the context surrounding the atomic Redis operations. */
class TotpEnrollmentProofServiceTest {
    private record Stored(String value, long expiresAt) {}
    private TotpEnrollmentProofService service;
    private UserService users; private UserSessionService sessions; private JwtUtil jwt;
    private PasskeyService passkeys; private VerificationCodeService codes; private StringRedisTemplate redis;
    private User user; private UserSession session; private Authentication authentication;
    private MockHttpServletRequest request;
    private final Map<String, Stored> values = new HashMap<>();
    private final Map<String, Integer> attempts = new HashMap<>();
    private long now;

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        users = mock(UserService.class); sessions = mock(UserSessionService.class); jwt = mock(JwtUtil.class);
        passkeys = mock(PasskeyService.class); codes = mock(VerificationCodeService.class); redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> strings = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(strings);
        doAnswer(call -> {
            values.put(call.getArgument(0), new Stored(call.getArgument(1), now + call.getArgument(2, Duration.class).toSeconds()));
            return null;
        }).when(strings).set(anyString(), anyString(), any(Duration.class));
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenAnswer(call -> {
            List<String> keys = call.getArgument(1); Object[] expanded = call.getArguments();
            Object[] args = expanded.length == 3 && expanded[2] instanceof Object[] packed ? packed : Arrays.copyOfRange(expanded, 2, expanded.length);
            if (keys.size() == 1) {
                Stored stored = values.get(keys.getFirst());
                if (stored == null || stored.expiresAt() <= now || !stored.value().equals(args[0])) return 0L;
                values.remove(keys.getFirst()); return 1L;
            }
            for (int i=0; i<2; i++) if (attempts.getOrDefault(keys.get(i), 0) >= Integer.parseInt(args[i].toString())) return 0L;
            for (String key : keys) attempts.merge(key, 1, Integer::sum);
            return 1L;
        });
        RateLimitService rate = mock(RateLimitService.class); when(rate.getClientIp(any())).thenReturn("192.0.2.1");
        service = new TotpEnrollmentProofService(users, sessions, jwt, passkeys, codes, rate, redis, mock(SensitiveLogUtil.class));
        user = new User("fixture-user", "fixture", "bound@example.invalid", "password-hash"); user.setId(17L);
        session = new UserSession(); session.setId(42L); session.setSessionVersion(3); session.setUser(user);
        when(users.findByUuid(user.getUuid())).thenReturn(Optional.of(user));
        when(sessions.findActiveSessionById(42L)).thenReturn(Optional.of(session));
        request = new MockHttpServletRequest(); request.addHeader("Authorization", "Bearer fixture-token");
        authentication = new UsernamePasswordAuthenticationToken(user.getUuid(), null, List.of());
        when(jwt.getSessionId("fixture-token")).thenReturn(42L); when(jwt.getSessionVersion("fixture-token")).thenReturn(3);
        when(jwt.isTokenValid("fixture-token")).thenReturn(true); when(jwt.getTokenType("fixture-token")).thenReturn("access");
        when(jwt.getUuidFromToken("fixture-token")).thenReturn(user.getUuid());
    }
    private TotpEnrollmentVerifyRequest password(String value) { return new TotpEnrollmentVerifyRequest("password", value, null); }
    private TotpEnrollmentProofService.EnrollmentFailure fails(Runnable action, HttpStatus expected) {
        var failure = assertThrows(TotpEnrollmentProofService.EnrollmentFailure.class, action::run);
        assertEquals(expected, failure.status()); return failure;
    }
    private TotpEnrollmentProofService.EnrollmentProof passwordProof() {
        when(users.verifyPassword("correct", "password-hash")).thenReturn(true);
        return service.verify(password("correct"), authentication, request);
    }
    @Test void ordinaryTokenWithoutCredentialCannotMintProof() {
        fails(() -> service.verify(password(null), authentication, request), HttpStatus.BAD_REQUEST);
        assertTrue(values.isEmpty()); verify(users, never()).verifyPassword(any(), any());
    }
    @Test void actualPasswordCreatesOnlyEnrollmentBoundOpaqueProof() {
        var proof = passwordProof(); assertTrue(proof.proofId().matches("[A-Za-z0-9_-]{43}")); assertEquals(300, proof.expiresInSeconds());
        assertTrue(values.keySet().stream().allMatch(key -> key.startsWith("auth:totp:enrollment:v2:proof:")));
        assertTrue(values.values().stream().allMatch(value -> value.value().startsWith("TOTP_ENROLL:")));
        verify(users).verifyPassword("correct", "password-hash");
    }
    @Test void incorrectPasswordDoesNotMintProof() {
        assertEquals(ApiResultCode.AUTH_130, fails(() -> service.verify(password("wrong"), authentication, request), HttpStatus.BAD_REQUEST).resultCode());
        assertTrue(values.isEmpty());
    }
    @Test void passwordlessAccountCannotUsePasswordPath() {
        user.setPasswordHash(null); fails(() -> service.verify(password("anything"), authentication, request), HttpStatus.BAD_REQUEST);
        verify(users, never()).verifyPassword(any(), any());
    }
    @Test void emailPathUsesOnlyCurrentBoundMailboxAndSensitiveCode() {
        when(codes.verifyCodeForType("bound@example.invalid", "123456", "192.0.2.1", "sensitive-verification")).thenReturn(0);
        assertNotNull(service.verify(new TotpEnrollmentVerifyRequest("email-code", null, " 123456 "), authentication, request).proofId());
        verify(codes).verifyCodeForType("bound@example.invalid", "123456", "192.0.2.1", "sensitive-verification");
    }
    @Test void missingMailboxCannotBypassWithEmailCode() {
        user.setEmail(null); fails(() -> service.verify(new TotpEnrollmentVerifyRequest("email-code", null, "123456"), authentication, request), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(codes); assertTrue(values.isEmpty());
    }
    @Test void newTotpRecoveryQrAndVerifiedFlagsCannotAuthorizeEnrollment() {
        for (String method : List.of("totp", "recovery-code", "qr", "verified", "apple"))
            fails(() -> service.verify(new TotpEnrollmentVerifyRequest(method, "correct", "123456"), authentication, request), HttpStatus.BAD_REQUEST);
        verify(users, never()).verifyPassword(any(), any()); verifyNoInteractions(codes, passkeys);
    }
    @Test void proofCannotCrossUserSessionOrVersionAndCanBeConsumedOnlyOnce() {
        var proof = passwordProof();
        assertFalse(service.consume(proof.proofId(), "other-user", 42L, 3));
        assertFalse(service.consume(proof.proofId(), user.getUuid(), 43L, 3));
        assertFalse(service.consume(proof.proofId(), user.getUuid(), 42L, 4));
        assertTrue(service.consume(proof.proofId(), user.getUuid(), 42L, 3));
        assertFalse(service.consume(proof.proofId(), user.getUuid(), 42L, 3));
    }
    @Test void expiredOrForeignPurposeProofCannotBeConsumed() {
        var proof = passwordProof(); now = 300; assertFalse(service.consume(proof.proofId(), user.getUuid(), 42L, 3)); now = 0;
        String key = "auth:totp:enrollment:v2:proof:" + proof.proofId(); values.put(key, new Stored("QR_APPROVE:fixture", 300));
        assertFalse(service.consume(proof.proofId(), user.getUuid(), 42L, 3)); assertNotNull(values.get(key));
    }
    @Test void missingProofOrContextCannotConsumeOriginal() {
        var proof = passwordProof();
        assertFalse(service.consume(null, user.getUuid(), 42L, 3)); assertFalse(service.consume("fake", user.getUuid(), 42L, 3));
        assertFalse(service.consume(proof.proofId(), null, 42L, 3)); assertFalse(service.consume(proof.proofId(), user.getUuid(), null, 3));
        assertFalse(service.consume(proof.proofId(), user.getUuid(), 42L, null)); assertTrue(service.consume(proof.proofId(), user.getUuid(), 42L, 3));
    }
    @Test void anonymousWrongVersionAndForeignSessionOwnerRejectBeforeCredentialCheck() {
        var anonymous = new AnonymousAuthenticationToken("fixture", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        fails(() -> service.verify(password("correct"), anonymous, request), HttpStatus.UNAUTHORIZED);
        session.setSessionVersion(4); fails(() -> service.verify(password("correct"), authentication, request), HttpStatus.UNAUTHORIZED);
        session.setSessionVersion(3); session.setUser(new User("other-user", "other", null, "hash"));
        fails(() -> service.verify(password("correct"), authentication, request), HttpStatus.UNAUTHORIZED);
        verify(users, never()).verifyPassword(any(), any());
    }
    @Test void sessionRevokedDuringCredentialCheckCannotMintProof() {
        when(users.verifyPassword("correct", "password-hash")).thenReturn(true);
        when(sessions.findActiveSessionById(42L)).thenReturn(Optional.of(session), Optional.empty());
        fails(() -> service.verify(password("correct"), authentication, request), HttpStatus.UNAUTHORIZED); assertTrue(values.isEmpty());
    }
    @Test void attemptsShareAccountBudgetAcrossMethods() {
        for (int i=0; i<5; i++) fails(() -> service.verify(password("wrong"), authentication, request), HttpStatus.BAD_REQUEST);
        fails(() -> service.verify(new TotpEnrollmentVerifyRequest("email-code", null, "123456"), authentication, request), HttpStatus.TOO_MANY_REQUESTS);
        verify(users, times(5)).verifyPassword("wrong", "password-hash"); verifyNoInteractions(codes);
    }
    @Test @SuppressWarnings("unchecked") void redisOutageFailsClosedBeforePasswordOrConsumption() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenThrow(new DataAccessResourceFailureException("fixture outage"));
        fails(() -> service.verify(password("correct"), authentication, request), HttpStatus.SERVICE_UNAVAILABLE);
        fails(() -> service.consume("a".repeat(43), user.getUuid(), 42L, 3), HttpStatus.SERVICE_UNAVAILABLE);
        verify(users, never()).verifyPassword(any(), any());
    }
    @Test @SuppressWarnings("unchecked") void missingRedisConsumptionResultNeverGrantsAccess() {
        var proof = passwordProof(); when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(null);
        fails(() -> service.consume(proof.proofId(), user.getUuid(), 42L, 3), HttpStatus.SERVICE_UNAVAILABLE);
    }
    private PasskeyAuthenticationVerifyRequest assertion() { return new PasskeyAuthenticationVerifyRequest("credential", "client", "authenticator", "signature"); }
    private void passkeyOptions() throws Exception {
        var options = new PasskeyAuthenticationOptionsResponse(); options.setChallengeId("passkey-one"); when(passkeys.generateSensitiveVerificationOptions()).thenReturn(options);
        assertEquals("passkey-one", service.passkeyOptions(authentication, request).getChallengeId());
    }
    @Test void actualPasskeyAssertionProducesProofAndChallengeCannotBeReplayed() throws Exception {
        passkeyOptions(); var assertion = assertion(); var input = new TotpEnrollmentPasskeyVerifyRequest("passkey-one", assertion);
        assertNotNull(service.verifyPasskey(input, authentication, request).proofId()); verify(passkeys).verifySensitiveOperation(user, assertion, "passkey-one");
        fails(() -> service.verifyPasskey(input, authentication, request), HttpStatus.BAD_REQUEST); verify(passkeys, times(1)).verifySensitiveOperation(any(), any(), any());
    }
    @Test void genericPasskeyChallengeWithoutEnrollmentBindingCannotMintProof() {
        fails(() -> service.verifyPasskey(new TotpEnrollmentPasskeyVerifyRequest("generic-challenge", assertion()), authentication, request), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(passkeys); assertTrue(values.isEmpty());
    }
    @Test void passkeyBindingCannotBeUsedFromAnotherSessionVersion() throws Exception {
        passkeyOptions(); session.setSessionVersion(4); when(jwt.getSessionVersion("fixture-token")).thenReturn(4);
        fails(() -> service.verifyPasskey(new TotpEnrollmentPasskeyVerifyRequest("passkey-one", assertion()), authentication, request), HttpStatus.BAD_REQUEST);
        verify(passkeys, never()).verifySensitiveOperation(any(), any(), any()); session.setSessionVersion(3); when(jwt.getSessionVersion("fixture-token")).thenReturn(3);
        assertNotNull(service.verifyPasskey(new TotpEnrollmentPasskeyVerifyRequest("passkey-one", assertion()), authentication, request).proofId());
    }
    @Test void expiredPasskeyBindingOrInvalidSignatureCannotMintProof() throws Exception {
        passkeyOptions(); now = 300;
        fails(() -> service.verifyPasskey(new TotpEnrollmentPasskeyVerifyRequest("passkey-one", assertion()), authentication, request), HttpStatus.BAD_REQUEST);
        verify(passkeys, never()).verifySensitiveOperation(any(), any(), any()); now = 0;
        doThrow(new IllegalArgumentException("invalid signature")).when(passkeys).verifySensitiveOperation(any(), any(), any());
        fails(() -> service.verifyPasskey(new TotpEnrollmentPasskeyVerifyRequest("passkey-one", assertion()), authentication, request), HttpStatus.BAD_REQUEST);
        fails(() -> service.verifyPasskey(new TotpEnrollmentPasskeyVerifyRequest("passkey-one", assertion()), authentication, request), HttpStatus.BAD_REQUEST);
        verify(passkeys, times(1)).verifySensitiveOperation(any(), any(), any());
    }

    @Test void refreshTokenForeignSubjectAndMissingBearerCannotMintProof() {
        when(jwt.getTokenType("fixture-token")).thenReturn("refresh");
        fails(() -> service.verify(password("correct"), authentication, request), HttpStatus.UNAUTHORIZED);
        when(jwt.getTokenType("fixture-token")).thenReturn("access");
        when(jwt.getUuidFromToken("fixture-token")).thenReturn("foreign-user");
        fails(() -> service.verify(password("correct"), authentication, request), HttpStatus.UNAUTHORIZED);
        request.removeHeader("Authorization");
        fails(() -> service.verify(password("correct"), authentication, request), HttpStatus.UNAUTHORIZED);
        verify(users, never()).verifyPassword(any(), any()); assertTrue(values.isEmpty());
    }
}
