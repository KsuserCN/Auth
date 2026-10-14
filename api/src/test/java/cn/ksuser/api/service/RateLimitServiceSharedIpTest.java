package cn.ksuser.api.service;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.support.SendCodeRateLimitRedis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static cn.ksuser.api.service.RateLimitService.SendCodeLimit.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RateLimitServiceSharedIpTest {
    private static final String IP = "203.0.113.10";
    private SendCodeRateLimitRedis redis;
    private RateLimitService service;

    @BeforeEach
    void setUp() {
        redis = new SendCodeRateLimitRedis();
        service = new RateLimitService(redis.template(), new AppProperties());
    }

    @Test
    void sixtyDifferentMailboxesCanSendFromOneSharedIpWithinAMinute() {
        for (int user = 0; user < 60; user++) {
            assertEquals(ALLOWED, service.acquireSendCodePermit("user" + user + "@example.com", IP));
        }
        assertEquals(IP_MINUTE, service.acquireSendCodePermit("next@example.com", IP));
        assertNull(redis.get(minute("next@example.com")), "A rejected IP request must not consume its mailbox quota");
        assertEquals(ALLOWED, service.acquireSendCodePermit("next@example.com", "203.0.113.11"));
        assertEquals("60", redis.get(minute(IP)));
    }

    @Test
    void mailboxLimitAppliesAcrossIpsWithoutConsumingOtherUsersSharedQuota() {
        assertEquals(ALLOWED, service.acquireSendCodePermit("same@example.com", IP));
        assertEquals(EMAIL_MINUTE, service.acquireSendCodePermit("same@example.com", "203.0.113.11"));
        assertNull(redis.get(minute("203.0.113.11")));
        assertEquals(ALLOWED, service.acquireSendCodePermit("other@example.com", IP));
        assertEquals("2", redis.get(minute(IP)));
    }

    @Test
    void mailboxHourlyLimitRemainsFourteenDespiteChangingIp() {
        for (int attempt = 0; attempt < 14; attempt++) {
            assertEquals(ALLOWED, service.acquireSendCodePermit("same@example.com", "203.0.113." + (10 + attempt)));
            redis.advanceSeconds(61);
        }
        assertEquals(EMAIL_HOUR, service.acquireSendCodePermit("same@example.com", IP));
        assertEquals("14", redis.get(hour("same@example.com")));
    }

    @Test
    void sharedIpStillHasSixHundredPerHourCeiling() {
        for (int minute = 0; minute < 10; minute++) {
            for (int user = 0; user < 60; user++) {
                assertEquals(ALLOWED, service.acquireSendCodePermit("user" + minute + "_" + user + "@example.com", IP));
            }
            redis.advanceSeconds(61);
        }
        assertEquals(IP_HOUR, service.acquireSendCodePermit("next@example.com", IP));
        assertNull(redis.get(hour("next@example.com")));
        assertEquals("600", redis.get(hour(IP)));
        redis.advanceSeconds(3600);
        assertEquals(ALLOWED, service.acquireSendCodePermit("next@example.com", IP));
    }

    @Test
    void existingCountersAreHonoredAndLaterRequestsDoNotExtendTheirWindow() {
        redis.put(hour("same@example.com"), "14", 100);
        assertEquals(EMAIL_HOUR, service.acquireSendCodePermit("same@example.com", IP));
        assertNull(redis.get(minute(IP)));
        assertEquals(100, redis.ttl(hour("same@example.com")));

        assertEquals(ALLOWED, service.acquireSendCodePermit("first@example.com", IP));
        redis.advanceSeconds(20);
        assertEquals(ALLOWED, service.acquireSendCodePermit("second@example.com", IP));
        assertEquals(40, redis.ttl(minute(IP)));
        assertEquals(3580, redis.ttl(hour(IP)));
    }

    @Test
    void successfulLoginOnlyClearsLoginCountersAndPreservesSharedVerificationQuota() {
        String email = "same@example.com";
        assertEquals(ALLOWED, service.acquireSendCodePermit(email, IP));
        service.recordEmailRequest(email, RateLimitService.TYPE_LOGIN);
        service.recordIpRequest(IP, RateLimitService.TYPE_LOGIN);

        service.clearAllLimits(email, IP);

        assertNull(redis.get("ratelimit:minute:login:" + email));
        assertNull(redis.get("ratelimit:hour:login:" + email));
        assertNull(redis.get("ratelimit:minute:login:" + IP));
        assertNull(redis.get("ratelimit:hour:login:" + IP));
        assertEquals("1", redis.get(minute(email)));
        assertEquals("1", redis.get(hour(email)));
        assertEquals("1", redis.get(minute(IP)));
        assertEquals("1", redis.get(hour(IP)));
        assertEquals(EMAIL_MINUTE, service.acquireSendCodePermit(email, IP));
    }

    @Test
    void historicalCounterWithoutExpiryRecoversAfterItsWindowInsteadOfPermanentlyBlockingSharedIp() {
        redis.putWithoutExpiry(minute(IP), "60");
        redis.putWithoutExpiry(hour("same@example.com"), "14");

        assertEquals(IP_MINUTE, service.acquireSendCodePermit("other@example.com", IP));
        assertEquals(EMAIL_HOUR, service.acquireSendCodePermit("same@example.com", IP));
        assertEquals(60, redis.ttl(minute(IP)));
        assertEquals(3600, redis.ttl(hour("same@example.com")));
        assertEquals("60", redis.get(minute(IP)));
        assertEquals("14", redis.get(hour("same@example.com")));
        redis.advanceSeconds(61);
        assertEquals(ALLOWED, service.acquireSendCodePermit("other@example.com", IP));
        assertEquals(EMAIL_HOUR, service.acquireSendCodePermit("same@example.com", IP));
        redis.advanceSeconds(3600);
        assertEquals(ALLOWED, service.acquireSendCodePermit("same@example.com", IP));
    }

    @Test
    void configuredMailboxAndIpLimitsArePassedToReservationInsteadOfUsingHardCodedDefaults() {
        AppProperties properties = new AppProperties();
        properties.getRateLimit().setSendCodeEmailPerMinute(2);
        properties.getRateLimit().setSendCodeEmailPerHour(3);
        properties.getRateLimit().setSendCodeIpPerMinute(3);
        properties.getRateLimit().setSendCodeIpPerHour(4);
        RateLimitService configured = new RateLimitService(redis.template(), properties);

        assertEquals(ALLOWED, configured.acquireSendCodePermit("first@example.com", IP));
        assertEquals(ALLOWED, configured.acquireSendCodePermit("first@example.com", IP));
        assertEquals(EMAIL_MINUTE, configured.acquireSendCodePermit("first@example.com", IP));
        assertEquals(ALLOWED, configured.acquireSendCodePermit("second@example.com", IP));
        assertEquals(IP_MINUTE, configured.acquireSendCodePermit("second@example.com", IP));
        redis.advanceSeconds(61);
        assertEquals(ALLOWED, configured.acquireSendCodePermit("first@example.com", IP));
        assertEquals(EMAIL_HOUR, configured.acquireSendCodePermit("first@example.com", IP));
        assertEquals(IP_HOUR, configured.acquireSendCodePermit("second@example.com", IP));
        assertEquals("4", redis.get(hour(IP)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingOrUnknownScriptResultFailsClosed() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        when(template.execute(any(RedisScript.class), anyList(), any(Object[].class)))
            .thenReturn(null, -1L, 99L);
        RateLimitService unavailable = new RateLimitService(template, new AppProperties());

        assertEquals(UNAVAILABLE, unavailable.acquireSendCodePermit("same@example.com", IP));
        assertEquals(UNAVAILABLE, unavailable.acquireSendCodePermit("same@example.com", IP));
        assertEquals(UNAVAILABLE, unavailable.acquireSendCodePermit("same@example.com", IP));
        verify(template, never()).opsForValue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisFailureDoesNotFallBackToSeparateCounterReads() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        when(template.execute(any(RedisScript.class), anyList(), any(Object[].class)))
            .thenThrow(new RedisConnectionFailureException("offline"));
        RateLimitService unavailable = new RateLimitService(template, new AppProperties());

        assertEquals(UNAVAILABLE, unavailable.acquireSendCodePermit("same@example.com", IP));
        verify(template, never()).opsForValue();
    }

    private static String minute(String identity) { return "ratelimit:minute:verify:" + identity; }
    private static String hour(String identity) { return "ratelimit:hour:verify:" + identity; }
}
