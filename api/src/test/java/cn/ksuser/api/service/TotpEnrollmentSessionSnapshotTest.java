package cn.ksuser.api.service;

import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserSession;
import cn.ksuser.api.repository.*;
import cn.ksuser.api.util.EncryptionUtil;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TotpEnrollmentSessionSnapshotTest {
    @Test void entityRefreshCannotRewriteTheVersionThatWasOriginallyAuthorized() {
        var proofs = mock(TotpEnrollmentProofService.class);
        var users = mock(UserRepository.class);
        var sessions = mock(UserSessionRepository.class);
        var configs = mock(UserTotpRepository.class);
        var enrollments = mock(TotpEnrollmentRepository.class);
        var transactions = mock(PlatformTransactionManager.class);
        var user = new User("user-uuid", "fixture", "fixture@example.invalid", "hash"); user.setId(1L);
        var mutableSession = new UserSession(); mutableSession.setId(9L); mutableSession.setUser(user); mutableSession.setSessionVersion(0);
        var auth = new UsernamePasswordAuthenticationToken(user.getUuid(), null, List.of());
        var request = new MockHttpServletRequest();
        when(proofs.requireContext(auth, request)).thenReturn(new TotpEnrollmentProofService.Context(user, mutableSession));
        when(proofs.consume("proof", user.getUuid(), 9L, 0)).thenReturn(true);
        when(users.findByUuidForUpdate(user.getUuid())).thenReturn(Optional.of(user));
        when(sessions.findActiveSessionByIdForUpdate(eq(9L), any())).thenReturn(Optional.of(mutableSession));
        // Models a managed entity being refreshed after authorization but before the lock-time check.
        when(transactions.getTransaction(any())).thenAnswer(call -> {
            mutableSession.setSessionVersion(7);
            return new SimpleTransactionStatus();
        });
        var service = new TotpEnrollmentService(proofs, users, sessions, configs, enrollments,
            mock(TotpService.class), mock(EncryptionUtil.class), mock(StringRedisTemplate.class), transactions);
        var failure = assertThrows(TotpEnrollmentProofService.EnrollmentFailure.class,
            () -> service.start(auth, request, "proof"));
        assertEquals(401, failure.status().value());
        verify(proofs).consume("proof", user.getUuid(), 9L, 0);
        verifyNoInteractions(configs, enrollments);
    }
}
