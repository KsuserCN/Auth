package cn.ksuser.api.service;

import cn.ksuser.api.dto.TotpRegistrationOptionsResponse;
import cn.ksuser.api.entity.*;
import cn.ksuser.api.repository.*;
import cn.ksuser.api.result.ApiResultCode;
import cn.ksuser.api.util.EncryptionUtil;
import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import java.lang.reflect.Method;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** No outer test transaction: locks, committed failures and rollback use real JPA/H2. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:totp-enrollment-transactions;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driverClassName=org.h2.Driver", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.encryption.master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
class TotpEnrollmentTransactionTest {
    @Autowired TotpEnrollmentService service;
    @Autowired UserRepository users;
    @Autowired UserSessionRepository sessions;
    @Autowired UserTotpRepository configs;
    @Autowired TotpEnrollmentRepository enrollments;
    @Autowired TotpService totp;
    @Autowired EncryptionUtil encryption;
    @MockitoSpyBean TotpRecoveryCodeRepository recoveryCodes;
    @MockitoBean TotpEnrollmentProofService proofs;
    @MockitoBean StringRedisTemplate redis;
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private Authentication authentication;
    private User user;
    private UserSession session;
    private Clock clock;

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        cleanup();
        clock = Clock.fixed(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS), ZoneId.systemDefault());
        ReflectionTestUtils.setField(service, "clock", clock);
        user = users.saveAndFlush(new User(UUID.randomUUID().toString(), "totp-user", "totp@example.invalid", "fixture-hash"));
        session = createSession();
        authentication = new UsernamePasswordAuthenticationToken(user.getUuid(), null, List.of());
        when(proofs.requireContext(authentication, request)).thenReturn(new TotpEnrollmentProofService.Context(user, session));
        Set<String> consumed = ConcurrentHashMap.newKeySet();
        when(proofs.consume(any(), eq(user.getUuid()), eq(session.getId()), eq(session.getSessionVersion())))
            .thenAnswer(call -> { String id = call.getArgument(0); return id != null && id.startsWith("proof-") && consumed.add(id); });
        when(redis.execute(any(RedisScript.class), anyList())).thenReturn(1L);
    }

    @AfterEach void cleanup() {
        if (enrollments == null) return;
        enrollments.deleteAllInBatch(); recoveryCodes.deleteAllInBatch(); configs.deleteAllInBatch();
        sessions.deleteAllInBatch(); users.deleteAllInBatch();
    }

    private UserSession createSession() {
        return sessions.saveAndFlush(new UserSession(user, UUID.randomUUID().toString().getBytes(), "argon2id", LocalDateTime.now().plusDays(1)));
    }
    private TotpRegistrationOptionsResponse start() { return service.start(authentication, request, "proof-one"); }
    private String code(TotpRegistrationOptionsResponse options, int offset) throws Exception {
        Method generate = TotpService.class.getDeclaredMethod("generateTotpCode", byte[].class, long.class);
        generate.setAccessible(true);
        return (String) generate.invoke(totp, new Base32().decode(options.getSecret()), clock.instant().getEpochSecond() / 30 + offset);
    }

    @Test void tokenAloneCannotStartOrConfirm() {
        assertThrows(TotpEnrollmentProofService.EnrollmentFailure.class, () -> service.start(authentication, request, null));
        assertFalse(service.confirm(authentication, request, "A".repeat(32), "123456"));
        assertEquals(0, enrollments.count()); assertEquals(0, configs.count());
    }

    @Test void serverRecoveryCodesAndMatchedStepCommitTogetherAndCannotReplay() throws Exception {
        var options = start();
        assertEquals(600, options.getExpiresInSeconds()); assertEquals(32, options.getEnrollmentId().length());
        assertEquals(0, configs.count()); assertEquals(0, recoveryCodes.count());
        String previous = code(options, -1);
        assertTrue(service.confirm(authentication, request, options.getEnrollmentId(), previous));
        UserTotp config = configs.findByUserId(user.getId()).orElseThrow();
        assertTrue(config.getIsEnabled()); assertEquals(clock.instant().getEpochSecond() / 30 - 1, config.getLastUsedStep());
        assertEquals(Set.of(options.getRecoveryCodes()), new HashSet<>(totp.getRecoveryCodes(user.getId(), encryption.getMasterKey())));
        TotpEnrollment saved = enrollments.findById(options.getEnrollmentId()).orElseThrow();
        assertNotNull(saved.getCompletedAt()); assertNull(saved.getPendingSecretCiphertext()); assertNull(saved.getRecoveryCodesCiphertext());
        assertFalse(service.confirm(authentication, request, options.getEnrollmentId(), previous));
        assertFalse(totp.verifyTotpCode(user.getId(), previous, encryption.getMasterKey()));
        assertEquals(10, recoveryCodes.count());
    }

    @Test void proofIsSingleUseAndNewFlowCancelsOnlyPreviousPendingFlow() throws Exception {
        var first = start();
        assertThrows(TotpEnrollmentProofService.EnrollmentFailure.class, () -> service.start(authentication, request, "proof-one"));
        var second = service.start(authentication, request, "proof-two");
        TotpEnrollment old = enrollments.findById(first.getEnrollmentId()).orElseThrow();
        assertNotNull(old.getCancelledAt()); assertNull(old.getPendingSecretCiphertext());
        assertFalse(service.confirm(authentication, request, first.getEnrollmentId(), code(first, 0)));
        assertTrue(service.confirm(authentication, request, second.getEnrollmentId(), code(second, 0)));
        assertEquals(1, configs.count());
    }

    @Test void startingAnotherFlowCannotReplaceAnAlreadyEnabledCredential() throws Exception {
        var options = start();
        assertTrue(service.confirm(authentication, request, options.getEnrollmentId(), code(options, 0)));
        byte[] original = configs.findByUserId(user.getId()).orElseThrow().getSecretKeyCiphertext();
        assertThrows(cn.ksuser.api.result.BusinessArgumentException.class,
            () -> service.start(authentication, request, "proof-two"));
        assertArrayEquals(original, configs.findByUserId(user.getId()).orElseThrow().getSecretKeyCiphertext());
        assertEquals(10, recoveryCodes.count());
    }

    @Test void independentlyAuthorizedStartClearsOnlyLegacyUnconfirmedSecret() {
        UserTotp old = new UserTotp(); old.setUserId(user.getId()); old.setKeyVersion(1); old.setIsEnabled(false);
        old.setPendingSecretCiphertext(new byte[48]); old.setPendingExpiresAt(LocalDateTime.now().plusMinutes(10));
        configs.saveAndFlush(old);
        var options = start();
        UserTotp saved = configs.findByUserId(user.getId()).orElseThrow();
        assertFalse(saved.getIsEnabled()); assertNull(saved.getPendingSecretCiphertext()); assertNull(saved.getPendingExpiresAt());
        assertNotNull(enrollments.findById(options.getEnrollmentId()).orElseThrow().getPendingSecretCiphertext());
    }

    @Test void differentSessionAndChangedVersionCannotConfirm() throws Exception {
        var options = start(); UserSession other = createSession();
        when(proofs.requireContext(authentication, request)).thenReturn(new TotpEnrollmentProofService.Context(user, other));
        assertFalse(service.confirm(authentication, request, options.getEnrollmentId(), code(options, 0)));
        when(proofs.requireContext(authentication, request)).thenReturn(new TotpEnrollmentProofService.Context(user, session));
        UserSession changed = sessions.findById(session.getId()).orElseThrow(); changed.setSessionVersion(1); sessions.saveAndFlush(changed);
        assertThrows(TotpEnrollmentProofService.EnrollmentFailure.class,
            () -> service.confirm(authentication, request, options.getEnrollmentId(), code(options, 0)));
        assertEquals(0, configs.count());
    }

    @Test void revokedSessionCannotConfirmOrCreateAnotherFlow() {
        var options = start();
        UserSession revoked = sessions.findById(session.getId()).orElseThrow(); revoked.setRevokedAt(LocalDateTime.now()); sessions.saveAndFlush(revoked);
        assertThrows(TotpEnrollmentProofService.EnrollmentFailure.class, () -> service.confirm(authentication, request, options.getEnrollmentId(), "123456"));
        assertThrows(TotpEnrollmentProofService.EnrollmentFailure.class, () -> service.start(authentication, request, "proof-two"));
        assertEquals(0, configs.count());
    }

    @Test void expirationAndFifthFailureCommitCancellationBeforeTheirError() {
        var options = start();
        for (int i = 0; i < 4; i++) {
            assertFalse(service.confirm(authentication, request, options.getEnrollmentId(), "wrong"));
            assertEquals(i + 1, enrollments.findById(options.getEnrollmentId()).orElseThrow().getFailedAttempts());
        }
        var exhausted = assertThrows(TotpEnrollmentProofService.EnrollmentFailure.class,
            () -> service.confirm(authentication, request, options.getEnrollmentId(), "wrong"));
        assertEquals(ApiResultCode.RATE_LIMITED, exhausted.resultCode());
        var failed = enrollments.findById(options.getEnrollmentId()).orElseThrow();
        assertEquals(5, failed.getFailedAttempts()); assertNotNull(failed.getCancelledAt()); assertNull(failed.getPendingSecretCiphertext());
        var fresh = service.start(authentication, request, "proof-two");
        ReflectionTestUtils.setField(service, "clock", Clock.offset(clock, Duration.ofSeconds(601)));
        var expired = assertThrows(TotpEnrollmentProofService.EnrollmentFailure.class,
            () -> service.confirm(authentication, request, fresh.getEnrollmentId(), "123456"));
        assertEquals(ApiResultCode.TOTP_006, expired.resultCode());
        assertNotNull(enrollments.findById(fresh.getEnrollmentId()).orElseThrow().getCancelledAt());
        assertEquals(0, configs.count());
    }

    @Test void persistenceFailureRollsBackCredentialCodesAndCompletion() throws Exception {
        var options = start();
        doThrow(new DataIntegrityViolationException("fixture storage failure")).when(recoveryCodes).flush();
        assertThrows(IllegalStateException.class, () -> service.confirm(authentication, request, options.getEnrollmentId(), code(options, 0)));
        assertEquals(0, configs.count()); assertEquals(0, recoveryCodes.count());
        TotpEnrollment saved = enrollments.findById(options.getEnrollmentId()).orElseThrow();
        assertNull(saved.getCompletedAt()); assertNotNull(saved.getPendingSecretCiphertext());
    }

    @Test void concurrentConfirmationsCommitOnlyOneCredentialAndOneRecoverySet() throws Exception {
        var options = start(); String candidate = code(options, 0);
        try (var executor = Executors.newFixedThreadPool(6)) {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < 12; i++) tasks.add(() -> service.confirm(authentication, request, options.getEnrollmentId(), candidate));
            long successes = 0;
            for (Future<Boolean> result : executor.invokeAll(tasks)) if (result.get()) successes++;
            assertEquals(1, successes);
        }
        assertEquals(1, configs.count()); assertEquals(10, recoveryCodes.count());
    }

    @Test void userQuotaAndUnavailableRedisCannotEnableTotp() {
        var options = start();
        when(redis.execute(any(RedisScript.class), anyList())).thenReturn(0L);
        var limited = assertThrows(TotpEnrollmentProofService.EnrollmentFailure.class,
            () -> service.confirm(authentication, request, options.getEnrollmentId(), "123456"));
        assertEquals(ApiResultCode.RATE_LIMITED, limited.resultCode());
        when(redis.execute(any(RedisScript.class), anyList())).thenReturn(null);
        var unavailable = assertThrows(TotpEnrollmentProofService.EnrollmentFailure.class,
            () -> service.confirm(authentication, request, options.getEnrollmentId(), "123456"));
        assertEquals(503, unavailable.status().value()); assertEquals(0, configs.count());
    }

    @Test void legacyUserOnlyConfirmationIsDisabled() {
        assertFalse(totp.confirmTotpRegistration(user.getId(), "123456", new String[]{"ABCDEFGH"}, new byte[32]));
        assertEquals(0, configs.count());
    }
}
