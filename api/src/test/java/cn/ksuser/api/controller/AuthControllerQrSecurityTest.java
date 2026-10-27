package cn.ksuser.api.controller;

import cn.ksuser.api.dto.QrApproveRequest;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserSession;
import cn.ksuser.api.service.*;
import cn.ksuser.api.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthControllerQrSecurityTest {
    private AuthController controller;
    private User user;
    private UserSession approver;
    private final Map<Class<?>, Object> dependencies = new HashMap<>();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken("user", null, List.of());
    private QrChallengeService qr;
    private UserSessionService sessions;
    private SensitiveOperationService sensitive;
    private AccountRecoveryService recovery;
    private QrChallengeService.QrChallengePayload challenge;

    @BeforeEach void setup() throws Exception {
        Constructor<?> constructor = AuthController.class.getConstructors()[0];
        Object[] arguments = Arrays.stream(constructor.getParameterTypes())
            .map(type -> dependencies.computeIfAbsent(type, k -> mock(k))).toArray();
        controller = (AuthController) constructor.newInstance(arguments);
        qr = dependency(QrChallengeService.class); sessions = dependency(UserSessionService.class);
        sensitive = dependency(SensitiveOperationService.class); recovery = dependency(AccountRecoveryService.class);
        user = new User(); user.setId(1L); user.setUuid("user");
        when(dependency(UserService.class).findByUuid("user")).thenReturn(Optional.of(user));
        approver = session(42L, 2);
        when(sessions.findActiveSessionById(42L)).thenReturn(Optional.of(approver));
        when(sessions.findActiveSessionById(7L)).thenReturn(Optional.of(session(7L, 1)));
        JwtUtil jwt = dependency(JwtUtil.class);
        when(jwt.getSessionId("token")).thenReturn(42L);
        when(jwt.getSessionVersion("token")).thenReturn(2);
        request.addHeader("Authorization", "Bearer token");
        when(dependency(RateLimitService.class).getClientIp(request)).thenReturn("192.0.2.1");
        when(dependency(RateLimitService.class).getClientUserAgent(request)).thenReturn("fixture");
        challenge = new QrChallengeService.QrChallengePayload();
        challenge.setSchemaVersion(2); challenge.setExpiresAt(Instant.now().getEpochSecond() + 120);
        challenge.setChallengeId("challenge"); challenge.setApproveCode("code"); challenge.setPollToken("poll");
        challenge.setType("sensitive"); challenge.setStatus("pending"); challenge.setRequestedUserId(1L);
        challenge.setInitiatorSessionId(7L); challenge.setInitiatorSessionVersion(1);
        challenge.setOperationPurpose("change-password"); challenge.setWebIp("192.0.2.2");
        when(qr.getByApproveCode("code")).thenReturn(challenge);
        when(qr.isProtectedChallengeValid(challenge)).thenReturn(true);
        when(sensitive.qrGrantKey("user", 7L, 1, "change-password")).thenReturn("grant-for-session-7");
    }

    private <T> T dependency(Class<T> type) { return type.cast(dependencies.get(type)); }
    private UserSession session(long id, int version) {
        UserSession value = new UserSession(); value.setId(id); value.setSessionVersion(version); value.setUser(user);
        return value;
    }
    private QrApproveRequest approval(String proof) {
        QrApproveRequest input = new QrApproveRequest("code"); input.setProofId(proof); return input;
    }

    @Test void accessTokenAloneCannotApproveItsOwnSensitiveChallenge() {
        challenge.setInitiatorSessionId(42L); challenge.setInitiatorSessionVersion(2);
        assertEquals(403, controller.approveQrChallenge(approval(null), auth, request).getStatusCode().value());
        verify(qr, never()).approveProtectedChallenge(any(), any(), any(), any(), any(), any(), any(), any(), anyLong());
        verifyNoInteractions(sensitive, recovery);
    }

    @Test void independentProofPublishesOnlyAnInitiatorSessionAndPurposeGrant() {
        var result = controller.approveQrChallenge(approval("proof"), auth, request);
        assertEquals(200, result.getStatusCode().value());
        assertEquals(true, result.getBody().getData().get("verified"));
        verify(qr).approveProtectedChallenge(eq(challenge), eq("proof"), eq(1L), eq(42L), eq(2),
            argThat(data -> Boolean.TRUE.equals(data.get("independentProofVerified"))),
            eq("grant-for-session-7"), eq("192.0.2.2"), eq(900L));
        verify(sensitive, never()).markVerified(anyString(), anyString());
        verify(sensitive, never()).markVerifiedOnce(anyString(), anyString());
        verify(qr, never()).approveChallenge(anyString(), anyLong(), anyMap());
    }

    @Test void revokedInitiatorCannotReceiveGrant() {
        when(sessions.findActiveSessionById(7L)).thenReturn(Optional.empty());
        assertEquals(401, controller.approveQrChallenge(approval("proof"), auth, request).getStatusCode().value());
        verifyNoInteractions(sensitive);
    }

    @Test void changedInitiatorVersionCannotReceiveGrant() {
        when(sessions.findActiveSessionById(7L)).thenReturn(Optional.of(session(7L, 9)));
        assertEquals(401, controller.approveQrChallenge(approval("proof"), auth, request).getStatusCode().value());
    }

    @Test void changedApproverVersionCannotApprove() {
        approver.setSessionVersion(3);
        assertEquals(401, controller.approveQrChallenge(approval("proof"), auth, request).getStatusCode().value());
    }

    @Test void expiredOrLegacyChallengeCannotUseAProof() {
        when(qr.isProtectedChallengeValid(challenge)).thenReturn(false);
        assertEquals(400, controller.approveQrChallenge(approval("proof"), auth, request).getStatusCode().value());
        verifyNoInteractions(sensitive, recovery);
    }

    @Test void recoveryCannotIssueAuthorizationWithoutIndependentProof() {
        challenge.setType("recovery"); challenge.setRequestedUserId(null);
        assertEquals(403, controller.approveQrChallenge(approval(null), auth, request).getStatusCode().value());
        verifyNoInteractions(recovery);
    }

    @Test void recoveryIsPreparedWithoutPublishingAndIncludedInAtomicApproval() {
        challenge.setType("recovery"); challenge.setRequestedUserId(null); challenge.setOperationPurpose("account-recovery");
        var payload = new AccountRecoveryService.AccountRecoveryPayload(); payload.setRecoveryCode("recovery-code");
        when(recovery.prepareAuthorization(user, 42L, "192.0.2.1", "fixture"))
            .thenReturn(new AccountRecoveryService.PreparedAuthorization(payload, "recovery-key", "recovery-value"));
        var result = controller.approveQrChallenge(approval("proof"), auth, request);
        assertEquals(200, result.getStatusCode().value());
        verify(qr).approveProtectedChallenge(eq(challenge), eq("proof"), eq(1L), eq(42L), eq(2), anyMap(),
            eq("recovery-key"), eq("recovery-value"), eq(300L));
        verify(recovery, never()).createAuthorization(any(), any(), any(), any());
    }

    @Test void sensitiveChallengeCreationRequiresCurrentSessionAndExplicitPurpose() {
        assertEquals(400, controller.initQrSensitive(auth, request).getStatusCode().value());
        request.setParameter("purpose", "change-password");
        when(qr.createSensitiveChallenge(1L, 42L, 2, "change-password", "web", "192.0.2.1", "fixture"))
            .thenReturn(challenge);
        assertEquals(200, controller.initQrSensitive(auth, request).getStatusCode().value());
        verify(qr).createSensitiveChallenge(1L, 42L, 2, "change-password", "web", "192.0.2.1", "fixture");
    }
}
