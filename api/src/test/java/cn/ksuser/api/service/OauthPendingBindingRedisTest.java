package cn.ksuser.api.service;

import cn.ksuser.api.support.LocalRedisServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Runs the actual production Lua on a fresh isolated Redis bound to a random loopback port. */
class OauthPendingBindingRedisTest {
    private LocalRedisServer server;
    private StringRedisTemplate redis;
    private OauthPendingBindingService pending;

    @BeforeEach void setup() throws Exception {
        server = LocalRedisServer.start(); redis = server.template(); pending = new OauthPendingBindingService(redis);
    }
    @AfterEach void cleanup() throws Exception { if (server != null) server.close(); }
    private String identityKey() { return redis.keys("oauth:pending-bind:v2:identity:*").iterator().next(); }

    @Test void callbackCreatesOneReadyIdentityAndAnEmptyQuotaWithTheSameFixedLifetime() {
        String token = pending.create("github", "provider-user", null);
        assertTrue(token.matches("[A-Za-z0-9_-]{32}"));
        assertEquals(new OauthPendingBindingService.Identity("github", "provider-user", null), pending.resolve("github", token));
        assertEquals("0", redis.opsForValue().get(pending.quotaKey(token)));
        long identityTTL = redis.getExpire(identityKey(), TimeUnit.MILLISECONDS);
        long quotaTTL = redis.getExpire(pending.quotaKey(token), TimeUnit.MILLISECONDS);
        assertTrue(identityTTL > 899_000 && identityTTL <= 900_000);
        assertTrue(Math.abs(identityTTL - quotaTTL) < 50);
        assertEquals("READY", redis.opsForHash().get(identityKey(), "state"));
    }

    @Test void oldPendingTokensAndDifferentProviderCannotResolveTheIdentity() {
        String oldToken = "a".repeat(32);
        redis.opsForValue().set("oauth:pending-bind:" + oldToken, "{\"provider\":\"github\",\"providerUserId\":\"old-user\"}", Duration.ofMinutes(15));
        assertThrows(OauthPendingBindingService.BindingFailure.class, () -> pending.resolve("github", oldToken));
        String token = pending.create("github", "provider-user", null);
        assertThrows(OauthPendingBindingService.BindingFailure.class, () -> pending.resolve("google", token));
        assertEquals("provider-user", pending.resolve("github", token).providerUserId());
    }

    @Test void onlyOneConcurrentTargetCanClaimATokenAndItCanBeConsumedOnlyOnce() throws Exception {
        String token = pending.create("github", "provider-user", null);
        List<OauthPendingBindingService.Claim> successes = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<OauthPendingBindingService.Claim>> tasks = new ArrayList<>();
            for (long user = 1; user <= 24; user++) {
                long target = user;
                tasks.add(() -> {
                    try { return pending.claim("github", token, target); }
                    catch (OauthPendingBindingService.BindingFailure ex) { return null; }
                });
            }
            for (Future<OauthPendingBindingService.Claim> result : executor.invokeAll(tasks))
                if (result.get() != null) successes.add(result.get());
        }
        assertEquals(1, successes.size());
        assertThrows(OauthPendingBindingService.BindingFailure.class, () -> pending.resolve("github", token));
        var claim = successes.getFirst();
        pending.consumeClaim(claim);
        assertEquals("CONSUMED", redis.opsForHash().get(identityKey(), "state"));
        assertThrows(OauthPendingBindingService.BindingFailure.class, () -> pending.consumeClaim(claim));
    }

    @Test void claimOwnerTargetAndIdentityAreBoundWithoutDestroyingTheRealOwnersClaim() {
        String token = pending.create("qq", "qq-openid", "qq-union");
        var claim = pending.claim("qq", token, 7L);
        var wrongOwner = new OauthPendingBindingService.Claim(token, "x".repeat(32), 7L, claim.identity());
        var wrongUser = new OauthPendingBindingService.Claim(token, claim.owner(), 8L, claim.identity());
        var wrongIdentity = new OauthPendingBindingService.Claim(token, claim.owner(), 7L,
            new OauthPendingBindingService.Identity("qq", "other-openid", "qq-union"));
        for (var invalid : List.of(wrongOwner, wrongUser, wrongIdentity))
            assertThrows(OauthPendingBindingService.BindingFailure.class, () -> pending.consumeClaim(invalid));
        pending.consumeClaim(claim);
        assertEquals("CONSUMED", redis.opsForHash().get(identityKey(), "state"));
    }

    @Test void resolvingClaimingAndConsumingNeverExtendEitherDeadline() {
        String token = pending.create("github", "provider-user", null);
        String key = identityKey();
        redis.expire(key, Duration.ofSeconds(40)); redis.expire(pending.quotaKey(token), Duration.ofSeconds(40));
        long before = redis.getExpire(key, TimeUnit.MILLISECONDS);
        pending.resolve("github", token);
        var claim = pending.claim("github", token, 1L);
        pending.consumeClaim(claim);
        long after = redis.getExpire(key, TimeUnit.MILLISECONDS);
        assertTrue(after <= before && after > 0);
        assertTrue(redis.getExpire(pending.quotaKey(token), TimeUnit.MILLISECONDS) <= before);
    }

    @Test void expiredOrMissingExpiryRecordsNeverReviveOrComplete() {
        String token = pending.create("github", "provider-user", null);
        String key = identityKey();
        redis.persist(key);
        assertThrows(OauthPendingBindingService.BindingFailure.class, () -> pending.resolve("github", token));
        assertThrows(OauthPendingBindingService.BindingFailure.class, () -> pending.claim("github", token, 1L));
        assertEquals(-1, redis.getExpire(key));
        redis.expire(key, Duration.ofSeconds(10));
        var claim = pending.claim("github", token, 1L);
        redis.delete(key);
        assertThrows(OauthPendingBindingService.BindingFailure.class, () -> pending.consumeClaim(claim));
        assertFalse(redis.hasKey(key));
    }

    @Test void qqIdentityRequiresTheProviderVerifiedUnionId() {
        assertThrows(OauthPendingBindingService.BindingFailure.class, () -> pending.create("qq", "openid", null));
        assertTrue(redis.keys("oauth:pending-bind:v2:*").isEmpty());
    }

    @Test @SuppressWarnings("unchecked") void redisOutageAndUnknownConsumptionCannotAuthorizeCompletion() {
        StringRedisTemplate unavailable = mock(StringRedisTemplate.class);
        OauthPendingBindingService service = new OauthPendingBindingService(unavailable);
        String token = "a".repeat(32);
        when(unavailable.execute(any(RedisScript.class), anyList(), any(Object[].class)))
            .thenThrow(new RedisConnectionFailureException("fixture offline"));
        assertEquals(503, assertThrows(OauthPendingBindingService.BindingFailure.class,
            () -> service.resolve("github", token)).status().value());
        assertEquals(503, assertThrows(OauthPendingBindingService.BindingFailure.class,
            () -> service.create("github", "provider-user", null)).status().value());
    }
}
