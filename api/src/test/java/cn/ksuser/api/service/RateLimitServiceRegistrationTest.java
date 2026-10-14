package cn.ksuser.api.service;

import cn.ksuser.api.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RateLimitServiceRegistrationTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private RateLimitService service;
    private Map<String, String> store;
    private Map<String, Long> ttlSeconds;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        store = new HashMap<>();
        ttlSeconds = new HashMap<>();
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            long count = Long.parseLong(store.getOrDefault(key, "0")) + 1;
            store.put(key, Long.toString(count));
            return count;
        });
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Duration duration = invocation.getArgument(2);
            store.put(key, invocation.getArgument(1));
            ttlSeconds.put(key, duration.toSeconds());
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(redis.expire(anyString(), anyLong(), eq(TimeUnit.SECONDS))).thenAnswer(invocation -> {
            ttlSeconds.put(invocation.getArgument(0), invocation.getArgument(1));
            return true;
        });
        when(redis.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenAnswer(invocation ->
            ttlSeconds.getOrDefault(invocation.getArgument(0), -2L));
        service = new RateLimitService(redis, new AppProperties());
    }

    @Test
    void registrationsFromDifferentIpsDoNotShareQuotaOrAccessUserAgentKeys() {
        // Identical App versions can share a User-Agent; each IP must keep its own quota.
        for (int suffix = 1; suffix <= 5; suffix++) {
            String ip = "203.0.113." + suffix;
            service.recordRegisterSuccess(ip);

            assertFalse(service.isRegisterLocked(ip));
            assertEquals(0, service.getRegisterLockRemainingSeconds(ip));
            assertEquals("1", store.get(countKey(ip)));
        }

        assertEquals(5, store.size());
        assertNoUserAgentKeysAccessed();
    }

    @Test
    void historicalGlobalUserAgentLockDoesNotBlockRegistrationFromAnotherIp() {
        String oldUaLock = "register:lock:ua:" + "a".repeat(64);
        store.put(oldUaLock, "locked");
        ttlSeconds.put(oldUaLock, Duration.ofDays(1).toSeconds());
        String ip = "203.0.113.20";

        assertFalse(service.isRegisterLocked(ip));
        assertEquals(0, service.getRegisterLockRemainingSeconds(ip));

        service.recordRegisterSuccess(ip);

        assertFalse(service.isRegisterLocked(ip));
        assertEquals("1", store.get(countKey(ip)));
        assertEquals(Duration.ofDays(1).toSeconds(), ttlSeconds.get(oldUaLock));
        assertNoUserAgentKeysAccessed();
    }

    @Test
    void repeatedRegistrationsFromSameIpRetainEscalatingLockDurations() {
        String ip = "203.0.113.30";
        String lockKey = "register:lock:ip:" + ip;

        service.recordRegisterSuccess(ip);
        assertFalse(service.isRegisterLocked(ip));

        service.recordRegisterSuccess(ip);
        assertTrue(service.isRegisterLocked(ip));
        assertEquals(Duration.ofMinutes(10).toSeconds(), service.getRegisterLockRemainingSeconds(ip));

        // Simulate waiting out the lock while retaining the same day's successful count.
        expire(lockKey);
        assertFalse(service.isRegisterLocked(ip));
        service.recordRegisterSuccess(ip);
        assertTrue(service.isRegisterLocked(ip));
        assertEquals(Duration.ofHours(1).toSeconds(), service.getRegisterLockRemainingSeconds(ip));

        expire(lockKey);
        service.recordRegisterSuccess(ip);
        assertTrue(service.isRegisterLocked(ip));
        assertEquals(Duration.ofDays(1).toSeconds(), service.getRegisterLockRemainingSeconds(ip));
        assertEquals("4", store.get(countKey(ip)));
        assertFalse(service.isRegisterLocked("203.0.113.31"));
        assertNoUserAgentKeysAccessed();
    }

    @Test
    void dailySuccessCountExpiresAtDayBoundaryWithoutExtendingOnLaterRegistrations() {
        String ip = "203.0.113.40";
        service.recordRegisterSuccess(ip);
        String countKey = countKey(ip);
        long firstTtl = ttlSeconds.get(countKey);

        assertTrue(countKey.matches("register:success:ip:203\\.0\\.113\\.40:\\d{4}-\\d{2}-\\d{2}"));
        assertTrue(firstTtl >= 1 && firstTtl <= Duration.ofDays(1).toSeconds());

        service.recordRegisterSuccess(ip);
        assertEquals(firstTtl, ttlSeconds.get(countKey));
        verify(redis, times(1)).expire(eq(countKey), anyLong(), eq(TimeUnit.SECONDS));

        // Redis removes the daily counter at expiry, so a new day's quota starts fresh.
        expire(countKey);
        expire("register:lock:ip:" + ip);
        service.recordRegisterSuccess(ip);

        assertEquals("1", store.get(countKey(ip)));
        assertFalse(service.isRegisterLocked(ip));
        assertNoUserAgentKeysAccessed();
    }

    private String countKey(String ip) {
        return store.keySet().stream()
            .filter(key -> key.startsWith("register:success:ip:" + ip + ":"))
            .findFirst()
            .orElseThrow();
    }

    private void expire(String key) {
        store.remove(key);
        ttlSeconds.remove(key);
    }

    private void assertNoUserAgentKeysAccessed() {
        Stream.concat(mockingDetails(redis).getInvocations().stream(), mockingDetails(values).getInvocations().stream())
            .flatMap(invocation -> Stream.of(invocation.getArguments()))
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .forEach(argument -> assertFalse(argument.startsWith("register:") && argument.contains(":ua:"),
                "Registration limits must not read or write User-Agent keys: " + argument));
    }
}
