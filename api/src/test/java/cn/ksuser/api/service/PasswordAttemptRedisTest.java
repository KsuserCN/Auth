package cn.ksuser.api.service;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.support.LocalRedisServer;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static cn.ksuser.api.service.RateLimitService.PasswordAttemptLimit.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Runs the production reservation Lua against a local process, including parallel password assertions. */
class PasswordAttemptRedisTest {
    private static LocalRedisServer server;
    private StringRedisTemplate redis;
    private RateLimitService limits;

    @BeforeAll static void startRedis() throws Exception { server = LocalRedisServer.start(); }
    @AfterAll static void stopRedis() throws Exception { if (server != null) server.close(); }
    @BeforeEach void setup() {
        redis = server.template();
        try (var connection = redis.getConnectionFactory().getConnection()) { connection.serverCommands().flushDb(); }
        limits = new RateLimitService(redis, new AppProperties());
    }

    @Test void oneAccountHasFiveAttemptsRegardlessOfRouteOrNetwork() throws Exception {
        for (int i = 0; i < 5; i++) assertEquals(ALLOWED, limits.acquirePasswordAttempt("user:7", "192.0.2." + i, null));
        assertEquals(LIMITED, limits.acquirePasswordAttempt("user:7", "192.0.2.99", null));
        assertEquals("5", redis.opsForValue().get(key("account", "user:7", "minute")));
        assertNull(redis.opsForValue().get(key("ip", "192.0.2.99", "minute")));
    }

    @Test void sharedIpHasTenAttemptsAcrossDifferentAccounts() throws Exception {
        for (int i = 0; i < 10; i++) assertEquals(ALLOWED, limits.acquirePasswordAttempt("user:" + i, "192.0.2.1", null));
        assertEquals(LIMITED, limits.acquirePasswordAttempt("user:99", "192.0.2.1", null));
        assertNull(redis.opsForValue().get(key("account", "user:99", "minute")));
        assertEquals("10", redis.opsForValue().get(key("ip", "192.0.2.1", "hour")));
    }

    @Test void hourlyAccountAndNetworkCeilingsRemainIndependentOfMinuteWindows() throws Exception {
        redis.opsForValue().set(key("account", "user:7", "hour"), "60", Duration.ofSeconds(40));
        assertEquals(LIMITED, limits.acquirePasswordAttempt("user:7", "192.0.2.1", null));
        assertNull(redis.opsForValue().get(key("ip", "192.0.2.1", "minute")));
        redis.opsForValue().set(key("ip", "192.0.2.2", "hour"), "120", Duration.ofSeconds(40));
        assertEquals(LIMITED, limits.acquirePasswordAttempt("user:8", "192.0.2.2", null));
        assertNull(redis.opsForValue().get(key("account", "user:8", "hour")));
    }

    @Test void reservationsNeverExtendSharedWindowsOrOriginalTokenExpiry() throws Exception {
        String token = "oauth:pending-bind:v2:password-attempts:fixture";
        redis.opsForValue().set(token, "0", Duration.ofSeconds(20));
        assertEquals(ALLOWED, limits.acquirePasswordAttempt("user:7", "192.0.2.1", token));
        redis.expire(key("account", "user:7", "minute"), Duration.ofSeconds(15));
        redis.expire(key("ip", "192.0.2.1", "minute"), Duration.ofSeconds(16));
        assertEquals(ALLOWED, limits.acquirePasswordAttempt("user:7", "192.0.2.1", token));
        assertTrue(redis.getExpire(token) <= 20);
        assertTrue(redis.getExpire(key("account", "user:7", "minute")) <= 15);
        assertTrue(redis.getExpire(key("ip", "192.0.2.1", "minute")) <= 16);
        assertEquals("2", redis.opsForValue().get(token));
    }

    @Test void absentExpiredAndUnboundedTokensCannotReachPasswordBudgetOrBeRecreated() throws Exception {
        String token = "pending-token";
        assertEquals(LIMITED, limits.acquirePasswordAttempt("user:7", "192.0.2.1", token));
        redis.opsForValue().set(token, "0");
        assertEquals(LIMITED, limits.acquirePasswordAttempt("user:7", "192.0.2.1", token));
        redis.expire(token, Duration.ZERO);
        assertEquals(LIMITED, limits.acquirePasswordAttempt("user:7", "192.0.2.1", token));
        assertFalse(redis.hasKey(token));
        assertNull(redis.opsForValue().get(key("account", "user:7", "minute")));
        assertNull(redis.opsForValue().get(key("ip", "192.0.2.1", "hour")));
    }

    @Test void exhaustedTokenDoesNotConsumeOtherAccountsOrNetworks() throws Exception {
        redis.opsForValue().set("pending-token", "5", Duration.ofMinutes(15));
        assertEquals(LIMITED, limits.acquirePasswordAttempt("user:7", "192.0.2.1", "pending-token"));
        assertNull(redis.opsForValue().get(key("account", "user:7", "minute")));
        assertNull(redis.opsForValue().get(key("ip", "192.0.2.1", "hour")));
        assertEquals("5", redis.opsForValue().get("pending-token"));
    }

    @Test void sameTokenAllowsAtMostFiveConcurrentActualPasswordChecksAcrossDifferentTargetAccounts() throws Exception {
        String token = "pending-token";
        redis.opsForValue().set(token, "0", Duration.ofMinutes(15));
        UserService users = mock(UserService.class);
        when(users.findByEmail(anyString())).thenAnswer(call -> {
            String email = call.getArgument(0);
            long id = Long.parseLong(email.substring(4, email.indexOf('@')));
            User user = new User("uuid-" + id, "user" + id, email, "hash"); user.setId(id);
            return Optional.of(user);
        });
        PasswordAuthenticationService authentication = new PasswordAuthenticationService(users, limits);
        var pool = Executors.newFixedThreadPool(12);
        var futures = new ArrayList<java.util.concurrent.Future<Integer>>();
        try {
            for (int i = 0; i < 40; i++) {
                final int id = i;
                futures.add(pool.submit(() -> {
                    try {
                        authentication.authenticate("user" + id + "@example.invalid", "wrong", "192.0.2." + id, token);
                        return 1;
                    } catch (PasswordAuthenticationService.AuthenticationFailure ex) {
                        assertEquals(429, ex.status().value()); return 0;
                    }
                }));
            }
            int allowed = 0;
            for (var future : futures) allowed += future.get(10, TimeUnit.SECONDS);
            assertEquals(5, allowed);
            verify(users, times(5)).verifyPassword("wrong", "hash");
            assertEquals("5", redis.opsForValue().get(token));
        } finally { pool.shutdownNow(); }
    }

    @Test void corruptOrUnboundedSharedCounterFailsClosedWithoutPartiallyReservingOtherCounters() throws Exception {
        String account = key("account", "user:7", "minute");
        for (String corrupt : new String[]{"bad", "-1", "1.5"}) {
            redis.opsForValue().set(account, corrupt, Duration.ofMinutes(1));
            assertEquals(UNAVAILABLE, limits.acquirePasswordAttempt("user:7", "192.0.2.1", null));
            assertNull(redis.opsForValue().get(key("ip", "192.0.2.1", "minute")));
        }
        redis.opsForValue().set(account, "1");
        assertEquals(UNAVAILABLE, limits.acquirePasswordAttempt("user:7", "192.0.2.1", null));
        assertNull(redis.opsForValue().get(key("ip", "192.0.2.1", "hour")));
    }

    @Test void successfulAuthenticationCompatibilityHookDoesNotResetSharedPasswordOrSendCodeBudgets() throws Exception {
        assertEquals(ALLOWED, limits.acquirePasswordAttempt("user:7", "192.0.2.1", null));
        assertEquals(RateLimitService.SendCodeLimit.ALLOWED, limits.acquireSendCodePermit("fixture@example.invalid", "192.0.2.1"));
        limits.clearAllLimits("fixture@example.invalid", "192.0.2.1");
        assertEquals("1", redis.opsForValue().get(key("account", "user:7", "minute")));
        assertEquals("1", redis.opsForValue().get(key("ip", "192.0.2.1", "hour")));
        assertEquals(RateLimitService.SendCodeLimit.EMAIL_MINUTE,
            limits.acquireSendCodePermit("fixture@example.invalid", "192.0.2.1"));
    }

    @Test void reservationUsesConfiguredPasswordLimits() {
        AppProperties properties = new AppProperties();
        properties.getRateLimit().setLoginEmailPerMinute(2);
        properties.getRateLimit().setLoginEmailPerHour(3);
        properties.getRateLimit().setLoginIpPerMinute(3);
        properties.getRateLimit().setLoginIpPerHour(4);
        RateLimitService configured = new RateLimitService(redis, properties);
        assertEquals(ALLOWED, configured.acquirePasswordAttempt("user:1", "192.0.2.1", null));
        assertEquals(ALLOWED, configured.acquirePasswordAttempt("user:1", "192.0.2.1", null));
        assertEquals(LIMITED, configured.acquirePasswordAttempt("user:1", "192.0.2.1", null));
        assertEquals(ALLOWED, configured.acquirePasswordAttempt("user:2", "192.0.2.1", null));
        assertEquals(LIMITED, configured.acquirePasswordAttempt("user:3", "192.0.2.1", null));
    }

    private static String key(String kind, String identifier, String period) throws Exception {
        return "ratelimit:password:v1:" + kind + ":" + HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(identifier.getBytes(StandardCharsets.UTF_8))) + ":" + period;
    }
}
