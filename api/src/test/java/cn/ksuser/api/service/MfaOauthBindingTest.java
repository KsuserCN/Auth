package cn.ksuser.api.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class MfaOauthBindingTest {
    private static final String IP = "192.0.2.1";
    private static final String UA = "fixture-browser";

    private OauthPendingBindingService.Claim claim() {
        return new OauthPendingBindingService.Claim("token", "owner", 1L,
            new OauthPendingBindingService.Identity("github", "subject", null));
    }

    @Test void bindingChallengeRetainsTrustedClaimAndCannotUseQrOrDifferentContext() {
        MfaService service = new MfaService();
        var claim = claim();
        String id = service.createOauthBindingChallenge(1L, IP, UA, "github",
            Set.of("totp", "passkey", "qr"), claim);
        assertEquals(Set.of("totp", "passkey"), service.getAllowedMethods(id));
        assertFalse(service.isMethodAllowed(id, "qr"));
        assertNull(service.verifyChallenge(id, "192.0.2.2", UA));
        assertNull(service.claimSuccessfulChallenge(id, IP, "different-browser"));
        assertEquals(1L, service.verifyChallenge(id, IP, UA));
        var completed = service.claimSuccessfulChallenge(id, IP, UA);
        assertSame(claim, completed.oauthBindingClaim());
        assertEquals("github", completed.loginMethod());
        assertNull(service.claimSuccessfulChallenge(id, IP, UA));
    }

    @Test void invalidMethodOrTargetCannotCreateBindingChallenge() {
        MfaService service = new MfaService();
        for (Set<String> methods : List.of(Set.of("qr"), Set.of("unknown"), Set.<String>of())) {
            assertThrows(IllegalArgumentException.class,
                () -> service.createOauthBindingChallenge(1L, IP, UA, "github", methods, claim()));
        }
        assertThrows(IllegalArgumentException.class,
            () -> service.createOauthBindingChallenge(2L, IP, UA, "github", Set.of("totp"), claim()));
    }

    @Test void concurrentSuccessfulRequestsReceiveOnlyOneCompletion() throws Exception {
        MfaService service = new MfaService();
        String id = service.createOauthBindingChallenge(1L, IP, UA, "github", Set.of("totp"), claim());
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<MfaService.Completion>> requests = new ArrayList<>();
            for (int i = 0; i < 32; i++) requests.add(() -> service.claimSuccessfulChallenge(id, IP, UA));
            int successes = 0;
            for (var future : executor.invokeAll(requests)) if (future.get() != null) successes++;
            assertEquals(1, successes);
        }
    }

    @Test void cancelledExpiredAndExhaustedChallengesCannotCompleteBinding() {
        MutableClock clock = new MutableClock();
        MfaService service = new MfaService(clock);
        String cancelled = service.createOauthBindingChallenge(1L, IP, UA, "github", Set.of("totp"), claim());
        service.consumeChallenge(cancelled);
        assertNull(service.claimSuccessfulChallenge(cancelled, IP, UA));
        String exhausted = service.createOauthBindingChallenge(1L, IP, UA, "github", Set.of("totp"), claim());
        for (int i = 0; i < 5; i++) service.recordFailedAttempt(exhausted);
        assertEquals(0, service.getRemainingAttempts(exhausted));
        assertNull(service.claimSuccessfulChallenge(exhausted, IP, UA));
        String expired = service.createOauthBindingChallenge(1L, IP, UA, "github", Set.of("totp"), claim());
        clock.now = clock.now.plusSeconds(300);
        assertNull(service.claimSuccessfulChallenge(expired, IP, UA));
    }

    @Test void ordinaryQrChallengeStillSupportsItsExistingCrossDeviceFlow() {
        MfaService service = new MfaService();
        String id = service.createChallenge(1L, IP, UA, "password", Set.of("qr"));
        assertEquals(1L, service.verifyChallenge(id, "192.0.2.2", "qr-mfa"));
        assertTrue(service.isMethodAllowed(id, "qr"));
        assertNull(service.claimSuccessfulChallenge(id, IP, UA).oauthBindingClaim());
    }

    @Test void missingOrSurroundingWhitespaceUserAgentUsesTheSameNormalizedContext() {
        MfaService service = new MfaService();
        String missing = service.createOauthBindingChallenge(1L, IP, "unknown", "github", Set.of("totp"), claim());
        assertEquals(1L, service.verifyChallenge(missing, IP, null));
        assertNotNull(service.claimSuccessfulChallenge(missing, IP, "  "));
        String trimmed = service.createOauthBindingChallenge(1L, IP, UA, "github", Set.of("totp"), claim());
        assertNotNull(service.claimSuccessfulChallenge(trimmed, IP, "  " + UA + "  "));
    }

    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-27T12:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
