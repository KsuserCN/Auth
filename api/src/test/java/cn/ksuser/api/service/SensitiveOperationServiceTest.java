package cn.ksuser.api.service;

import cn.ksuser.api.support.SensitiveVerificationRedis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SensitiveOperationServiceTest {
    private static final String USER_UUID = "user-uuid";
    private static final String CLIENT_IP = "192.0.2.1";
    private static final String ONCE_KEY = "sensitive:once:ip:" + USER_UUID;

    private SensitiveVerificationRedis redis;
    private SensitiveOperationService service;

    @BeforeEach
    void setUp() {
        redis = new SensitiveVerificationRedis();
        service = new SensitiveOperationService(redis.template());
    }

    @Test
    void repeatedStatusAndRemainingTimeReadsPreserveOneTimeVerification() {
        service.markVerifiedOnce(USER_UUID, CLIENT_IP);

        assertTrue(service.isVerified(USER_UUID, CLIENT_IP));
        assertEquals(900, service.getRemainingTime(USER_UUID));
        redis.advanceSeconds(45);
        assertTrue(service.isVerified(USER_UUID, CLIENT_IP));
        assertEquals(855, service.getRemainingTime(USER_UUID));
        verify(redis.template(), never()).delete(anyString());
        verify(redis.template(), never()).execute(any(RedisScript.class), anyList(), anyString());

        assertTrue(service.consumeVerification(USER_UUID, CLIENT_IP));
        assertFalse(service.consumeVerification(USER_UUID, CLIENT_IP));
        assertFalse(service.isVerified(USER_UUID, CLIENT_IP));
        assertEquals(-1, service.getRemainingTime(USER_UUID));
        verify(redis.template(), times(2)).execute(any(RedisScript.class), eq(List.of(ONCE_KEY)), eq(CLIENT_IP));
    }

    @Test
    void wrongIpCannotReadOrConsumeAnotherIpsOneTimeVerification() {
        service.markVerifiedOnce(USER_UUID, CLIENT_IP);

        assertFalse(service.isVerified(USER_UUID, "198.51.100.2"));
        assertFalse(service.consumeVerification(USER_UUID, "198.51.100.2"));
        assertTrue(service.isVerified(USER_UUID, CLIENT_IP));
        assertEquals(900, service.getRemainingTime(USER_UUID));
        assertTrue(service.consumeVerification(USER_UUID, CLIENT_IP));
    }

    @Test
    void expiredOneTimeVerificationCannotAuthorizeAnOperation() {
        service.markVerifiedOnce(USER_UUID, CLIENT_IP);
        redis.advanceSeconds(900);

        assertFalse(service.isVerified(USER_UUID, CLIENT_IP));
        assertEquals(-1, service.getRemainingTime(USER_UUID));
        assertFalse(service.consumeVerification(USER_UUID, CLIENT_IP));
    }

    @Test
    void regularVerificationRemainsReusableUntilExpiry() {
        service.markVerified(USER_UUID, CLIENT_IP);

        assertTrue(service.isVerified(USER_UUID, CLIENT_IP));
        assertEquals(900, service.getRemainingTime(USER_UUID));
        assertTrue(service.consumeVerification(USER_UUID, CLIENT_IP));
        assertTrue(service.consumeVerification(USER_UUID, CLIENT_IP));
        assertFalse(service.consumeVerification(USER_UUID, "198.51.100.2"));
        assertTrue(service.isVerified(USER_UUID, CLIENT_IP));
        redis.advanceSeconds(900);
        assertFalse(service.consumeVerification(USER_UUID, CLIENT_IP));
        assertEquals(-1, service.getRemainingTime(USER_UUID));
    }

    @Test
    void oneTimeVerificationKeepsItsIpPriorityOverRegularVerification() {
        service.markVerified(USER_UUID, "198.51.100.2");
        redis.advanceSeconds(30);
        service.markVerifiedOnce(USER_UUID, CLIENT_IP);

        assertEquals(900, service.getRemainingTime(USER_UUID));
        assertFalse(service.isVerified(USER_UUID, "198.51.100.2"));
        assertFalse(service.consumeVerification(USER_UUID, "198.51.100.2"));
        assertTrue(service.consumeVerification(USER_UUID, CLIENT_IP));
        assertTrue(service.consumeVerification(USER_UUID, "198.51.100.2"));
    }

    @Test
    void missingRedisScriptResultDoesNotGrantAccessFromRegularVerification() {
        service.markVerified(USER_UUID, CLIENT_IP);
        when(redis.template().execute(any(RedisScript.class), anyList(), anyString())).thenReturn(null);

        assertFalse(service.consumeVerification(USER_UUID, CLIENT_IP));
    }

    @Test
    void clearingVerificationRevokesBothRegularAndOneTimeGrants() {
        service.markVerified(USER_UUID, CLIENT_IP);
        service.markVerifiedOnce(USER_UUID, CLIENT_IP);
        service.clearVerification(USER_UUID);

        assertFalse(service.isVerified(USER_UUID, CLIENT_IP));
        assertFalse(service.consumeVerification(USER_UUID, CLIENT_IP));
        assertEquals(-1, service.getRemainingTime(USER_UUID));
    }

    @Test
    void missingUserOrIpCannotReadOrConsumeVerification() {
        clearInvocations(redis.template());
        String[][] invalidContexts = {
            {null, CLIENT_IP}, {"", CLIENT_IP}, {"   ", CLIENT_IP},
            {USER_UUID, null}, {USER_UUID, ""}, {USER_UUID, "   "}
        };

        for (String[] context : invalidContexts) {
            assertFalse(service.isVerified(context[0], context[1]));
            assertFalse(service.consumeVerification(context[0], context[1]));
        }
        verifyNoInteractions(redis.template());
    }
}
