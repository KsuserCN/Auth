package cn.ksuser.api.service;

import cn.ksuser.api.result.BusinessArgumentException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Redis substitute keeps independent deadlines and serializes transactions across service instances. */
class QrChallengeServiceTest {
    private record Stored(String value, long expiresAt) {}
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Stored> memory = new HashMap<>();
    private final AtomicLong now = new AtomicLong(1_800_000_000L);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked") private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final Clock clock = mock(Clock.class);
    private QrChallengeService service;

    @BeforeEach void setup() {
        when(clock.instant()).thenAnswer(call -> Instant.ofEpochSecond(now.get()));
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(call -> read(call.getArgument(0)));
        doAnswer(call -> {
            synchronized (memory) {
                long seconds = call.getArgument(3, TimeUnit.class).toSeconds(call.getArgument(2, Long.class));
                memory.put(call.getArgument(0), new Stored(call.getArgument(1), now.get() + seconds));
            }
            return null;
        }).when(values).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        when(redis.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenAnswer(call -> {
            String key = call.getArgument(0);
            synchronized (memory) { return read(key) == null ? -2L : memory.get(key).expiresAt() - now.get(); }
        });
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenAnswer(call -> {
            RedisScript<?> script = call.getArgument(0);
            List<String> keys = call.getArgument(1);
            Object[] arguments = call.getArguments();
            Object[] args = arguments.length == 3 && arguments[2] instanceof Object[] packed
                ? packed : Arrays.copyOfRange(arguments, 2, arguments.length);
            synchronized (memory) {
                String raw = read(keys.getFirst());
                if (raw == null || !raw.equals(args[0])) return 0L;
                JsonNode challenge = mapper.readTree(raw);
                if (!"pending".equals(challenge.path("status").asText())
                    || challenge.path("schemaVersion").asInt() != 2
                    || challenge.path("expiresAt").asLong() <= now.get()) return 0L;
                if (script == QrChallengeService.UPDATE_STATUS) {
                    memory.put(keys.getFirst(), new Stored(args[1].toString(), memory.get(keys.getFirst()).expiresAt()));
                    return 1L;
                }
                assertSame(QrChallengeService.APPROVE_PROTECTED, script);
                String proofRaw = read(keys.get(1));
                if (proofRaw == null) return 0L;
                JsonNode proof = mapper.readTree(proofRaw);
                if (proof.path("schemaVersion").asInt() != 2 || !"QR_APPROVE".equals(proof.path("purpose").asText())
                    || !"direct-factor".equals(proof.path("source").asText())
                    || !challenge.path("challengeId").asText().equals(proof.path("challengeId").asText())
                    || !args[1].equals(proof.path("userId").asText())
                    || !args[2].equals(proof.path("sessionId").asText())
                    || !args[3].equals(proof.path("sessionVersion").asText())
                    || proof.path("expiresAt").asLong() <= now.get()
                    || !challenge.path("operationPurpose").equals(proof.path("operationPurpose"))) return 0L;
                if (challenge.hasNonNull("requestedUserId")
                    && !args[1].equals(challenge.get("requestedUserId").asText())) return 0L;
                if (keys.size() == 3 && "sensitive".equals(challenge.path("type").asText()) && (!challenge.hasNonNull("initiatorSessionId")
                    || !challenge.hasNonNull("initiatorSessionVersion"))) return 0L;
                memory.remove(keys.get(1));
                memory.put(keys.getFirst(), new Stored(args[4].toString(), memory.get(keys.getFirst()).expiresAt()));
                if (keys.size() == 3) memory.put(keys.get(2), new Stored(args[5].toString(),
                    now.get() + Math.min(Long.parseLong(args[6].toString()), 900)));
                return 1L;
            }
        });
        service = newService();
    }

    private QrChallengeService newService() {
        return new QrChallengeService(redis, mock(IpLocationService.class), new UserAgentParserService(), clock);
    }

    private String read(String key) {
        synchronized (memory) {
            Stored stored = memory.get(key);
            if (stored != null && stored.expiresAt() <= now.get()) { memory.remove(key); return null; }
            return stored == null ? null : stored.value();
        }
    }

    private QrChallengeService.QrChallengePayload sensitive() {
        return service.createSensitiveChallenge(7L, 11L, 2, "change-password", "web", "192.0.2.8", "fixture");
    }

    private void approve(QrChallengeService.QrChallengePayload challenge, String proof, Long sid, Integer version) {
        service.approveProtectedChallenge(challenge, proof, 7L, sid, version,
            Map.of("verified", true), "session-scoped-grant", "grant-value", 900);
    }

    @Test void sensitiveChallengeContainsFrozenSessionPurposeAndDeadline() {
        var challenge = sensitive();
        assertEquals(2, challenge.getSchemaVersion());
        assertEquals(11L, challenge.getInitiatorSessionId());
        assertEquals(2, challenge.getInitiatorSessionVersion());
        assertEquals("change-password", challenge.getOperationPurpose());
        assertEquals(now.get() + 120, challenge.getExpiresAt());
        assertEquals(challenge.getChallengeId(), service.getByApproveCode(challenge.getApproveCode()).getChallengeId());
        assertTrue(memory.keySet().stream().allMatch(key -> key.startsWith("auth:qr:v2:")));
    }

    @Test void ordinaryApprovalCannotBypassProofForSensitiveOrRecovery() {
        var challenge = sensitive();
        assertThrows(BusinessArgumentException.class,
            () -> service.approveChallenge(challenge.getChallengeId(), 7L, Map.of("verified", true)));
        var recovery = service.createChallenge(QrChallengeService.ChallengeType.RECOVERY,
            null, null, "web", "192.0.2.8", "fixture");
        assertThrows(BusinessArgumentException.class,
            () -> service.approveChallenge(recovery.getChallengeId(), 7L, Map.of("verified", true)));
        assertNull(read("session-scoped-grant"));
    }

    @Test void proofAndGrantArePublishedOnceAndBoundToApproverSession() {
        var challenge = sensitive();
        var proof = service.issueApprovalProof(challenge.getChallengeId(), 7L, 22L, 3, "password");
        assertEquals(120, proof.expiresInSeconds());
        assertThrows(BusinessArgumentException.class, () -> approve(challenge, proof.proofId(), 23L, 3));
        assertThrows(BusinessArgumentException.class, () -> approve(challenge, proof.proofId(), 22L, 4));
        assertNull(read("session-scoped-grant"));
        approve(challenge, proof.proofId(), 22L, 3);
        assertEquals("approved", service.getByChallengeId(challenge.getChallengeId()).getStatus());
        assertEquals("grant-value", read("session-scoped-grant"));
        assertNull(read("auth:qr:v2:proof:" + proof.proofId()));
        assertThrows(BusinessArgumentException.class, () -> approve(challenge, proof.proofId(), 22L, 3));
    }

    @Test void proofCannotBeReusedAcrossChallengeUserOrPurpose() {
        var first = sensitive();
        var second = sensitive();
        var proof = service.issueApprovalProof(first.getChallengeId(), 7L, 22L, 3, "passkey");
        assertThrows(BusinessArgumentException.class, () -> approve(second, proof.proofId(), 22L, 3));
        assertThrows(BusinessArgumentException.class,
            () -> service.issueApprovalProof(first.getChallengeId(), 8L, 22L, 3, "password"));
        assertThrows(BusinessArgumentException.class,
            () -> service.issueApprovalProof(first.getChallengeId(), 7L, 22L, 3, "qr"));
        assertThrows(BusinessArgumentException.class,
            () -> service.issueApprovalProof(first.getChallengeId(), 7L, 22L, 3, "session-transfer"));
        first.setOperationPurpose("delete-account");
        assertThrows(BusinessArgumentException.class, () -> approve(first, proof.proofId(), 22L, 3));
        assertNull(read("session-scoped-grant"));
    }

    @Test void oldPayloadOrMissingProofCannotAuthorize() {
        var challenge = sensitive();
        assertThrows(BusinessArgumentException.class, () -> approve(challenge, null, 22L, 3));
        assertThrows(BusinessArgumentException.class, () -> approve(challenge, "invented", 22L, 3));
        challenge.setSchemaVersion(null);
        assertFalse(service.isProtectedChallengeValid(challenge));
        assertFalse(service.isProtectedChallengeValid(new QrChallengeService.QrChallengePayload()));
        assertFalse(service.isProtectedChallengeValid(null));
    }

    @Test void remainingLifetimeIsNotExtendedByApprovalOrRejection() {
        var challenge = sensitive();
        now.addAndGet(100);
        var proof = service.issueApprovalProof(challenge.getChallengeId(), 7L, 22L, 3, "totp");
        assertEquals(20, proof.expiresInSeconds());
        now.addAndGet(19);
        approve(challenge, proof.proofId(), 22L, 3);
        assertEquals(1, service.getRemainingSeconds(challenge.getChallengeId()));
        now.incrementAndGet();
        assertNull(service.getByChallengeId(challenge.getChallengeId()));
        assertThrows(BusinessArgumentException.class,
            () -> service.rejectChallenge(challenge.getChallengeId(), 7L, Map.of()));
        assertNull(service.getByChallengeId(challenge.getChallengeId()));
    }

    @Test void expiredChallengeCannotIssueProofOrBeRevived() {
        var challenge = sensitive();
        var proof = service.issueApprovalProof(challenge.getChallengeId(), 7L, 22L, 3, "password");
        now.addAndGet(120);
        assertThrows(BusinessArgumentException.class, () -> approve(challenge, proof.proofId(), 22L, 3));
        assertThrows(BusinessArgumentException.class,
            () -> service.issueApprovalProof(challenge.getChallengeId(), 7L, 22L, 3, "password"));
        assertNull(read("session-scoped-grant"));
        assertNull(service.getByChallengeId(challenge.getChallengeId()));
    }

    @Test void competingProofsAcrossInstancesApproveExactlyOnce() throws Exception {
        var challenge = sensitive();
        QrChallengeService otherInstance = newService();
        List<String> proofs = new ArrayList<>();
        for (int i = 0; i < 20; i++) proofs.add(service.issueApprovalProof(
            challenge.getChallengeId(), 7L, 22L, 3, "password").proofId());
        try (var executor = Executors.newFixedThreadPool(8)) {
            var tasks = proofs.stream().<java.util.concurrent.Callable<Boolean>>map(proof -> () -> {
                try {
                    otherInstance.approveProtectedChallenge(challenge, proof, 7L, 22L, 3,
                        Map.of("verified", true), "session-scoped-grant", "grant-value", 900);
                    return true;
                } catch (BusinessArgumentException expected) { return false; }
            }).toList();
            long succeeded = 0;
            for (var future : executor.invokeAll(tasks)) if (future.get()) succeeded++;
            assertEquals(1, succeeded);
        }
        assertEquals("grant-value", read("session-scoped-grant"));
    }

    @Test void consoleAndRecoveryRequireProofWithoutPassportGrant() {
        var challenge = service.createConsoleSensitiveChallenge(7L, "console-request", "console", "192.0.2.8", "fixture");
        assertEquals("console-request", challenge.getConsoleVerificationRequestId());
        assertEquals("console-verification", challenge.getOperationPurpose());
        var proof = service.issueApprovalProof(challenge.getChallengeId(), 7L, 22L, 3, "recovery_code");
        service.approveProtectedChallenge(challenge, proof.proofId(), 7L, 22L, 3,
            Map.of("verified", true), null, null, 0);
        assertNull(read("session-scoped-grant"));
        var recovery = service.createChallenge(QrChallengeService.ChallengeType.RECOVERY,
            null, null, "web", "192.0.2.8", "fixture");
        var recoveryProof = service.issueApprovalProof(recovery.getChallengeId(), 7L, 22L, 3, "password");
        service.approveProtectedChallenge(recovery, recoveryProof.proofId(), 7L, 22L, 3,
            Map.of("verified", true), null, null, 0);
        assertEquals("approved", service.getByChallengeId(recovery.getChallengeId()).getStatus());
    }

    @Test void ordinaryLoginStatusStillTransitionsExactlyOnce() {
        var challenge = service.createChallenge(QrChallengeService.ChallengeType.LOGIN,
            null, null, "web", "192.0.2.8", "fixture");
        now.addAndGet(60);
        service.approveChallenge(challenge.getChallengeId(), 7L, Map.of("transferCode", "fixture"));
        assertEquals(60, service.getRemainingSeconds(challenge.getChallengeId()));
        assertThrows(BusinessArgumentException.class,
            () -> service.rejectChallenge(challenge.getChallengeId(), 7L, Map.of()));
        assertEquals("approved", service.getByChallengeId(challenge.getChallengeId()).getStatus());
    }

    @Test void recoveryPublishesAuthorizationInTheSameAtomicApprovalWithoutAnInitiatorSession() {
        var challenge = service.createChallenge(QrChallengeService.ChallengeType.RECOVERY,
            null, null, "web", "192.0.2.8", "fixture");
        var proof = service.issueApprovalProof(challenge.getChallengeId(), 7L, 22L, 3, "password");
        service.approveProtectedChallenge(challenge, proof.proofId(), 7L, 22L, 3,
            Map.of("verified", true, "recoveryCode", "recovery-code"), "recovery-grant", "recovery-data", 300);
        assertEquals("recovery-data", read("recovery-grant"));
        assertEquals("approved", service.getByChallengeId(challenge.getChallengeId()).getStatus());
        assertThrows(BusinessArgumentException.class, () -> service.approveProtectedChallenge(challenge,
            proof.proofId(), 7L, 22L, 3, Map.of(), "recovery-grant", "second-value", 300));
        assertEquals("recovery-data", read("recovery-grant"));
    }
}
