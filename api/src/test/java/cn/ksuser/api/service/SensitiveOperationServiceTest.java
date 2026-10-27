package cn.ksuser.api.service;

import cn.ksuser.api.support.SensitiveVerificationRedis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.script.RedisScript;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SensitiveOperationServiceTest {
    private static final String USER = "user-uuid";
    private static final String IP = "192.0.2.1";
    private static final String PURPOSE = "change-password";
    private SensitiveVerificationRedis redis;
    private SensitiveOperationService service;

    @BeforeEach void setup() {
        redis = new SensitiveVerificationRedis();
        service = new SensitiveOperationService(redis.template());
    }

    private void grant() {
        redis.template().opsForValue().set(service.qrGrantKey(USER, 42L, 1, PURPOSE), IP, Duration.ofMinutes(15));
    }

    @Test void statusReadsPreserveScopedGrantAndOneSuccessfulOperationConsumesIt() {
        grant();
        assertTrue(service.isVerified(USER, IP, 42L, 1, PURPOSE));
        assertEquals(900, service.getRemainingTime(USER, 42L, 1, PURPOSE));
        redis.advanceSeconds(45);
        assertTrue(service.isVerifiedForAnyPurpose(USER, IP, 42L, 1));
        assertEquals(855, service.getRemainingTime(USER, 42L, 1, null));
        assertTrue(service.consumeVerification(USER, IP, 42L, 1, PURPOSE));
        assertFalse(service.consumeVerification(USER, IP, 42L, 1, PURPOSE));
        assertFalse(service.isVerified(USER, IP, 42L, 1, PURPOSE));
    }

    @Test void anotherSessionVersionUserPurposeOrIpCannotUseTheGrant() {
        grant();
        assertFalse(service.consumeVerification(USER, IP, 43L, 1, PURPOSE));
        assertFalse(service.consumeVerification(USER, IP, 42L, 2, PURPOSE));
        assertFalse(service.consumeVerification("other", IP, 42L, 1, PURPOSE));
        assertFalse(service.consumeVerification(USER, IP, 42L, 1, "delete-account"));
        assertFalse(service.consumeVerification(USER, "198.51.100.1", 42L, 1, PURPOSE));
        assertFalse(service.isVerifiedForAnyPurpose(USER, IP, 43L, 1));
        assertTrue(service.consumeVerification(USER, IP, 42L, 1, PURPOSE));
    }

    @Test void legacyUserIpOnlyQrGrantsCannotAuthorizeAnyOperation() {
        redis.template().opsForValue().set("sensitive:once:ip:" + USER, IP, Duration.ofMinutes(15));
        assertFalse(service.isVerified(USER, IP));
        assertFalse(service.consumeVerification(USER, IP));
        assertFalse(service.isVerified(USER, IP, 42L, 1, PURPOSE));
        assertFalse(service.consumeVerification(USER, IP, 42L, 1, PURPOSE));
        assertThrows(IllegalStateException.class, () -> service.markVerifiedOnce(USER, IP));
    }

    @Test void expiryCannotAuthorizeAnOperation() {
        grant(); redis.advanceSeconds(900);
        assertFalse(service.isVerified(USER, IP, 42L, 1, PURPOSE));
        assertFalse(service.consumeVerification(USER, IP, 42L, 1, PURPOSE));
        assertEquals(-1, service.getRemainingTime(USER, 42L, 1, null));
    }

    @Test void ordinaryDirectVerificationKeepsItsExistingLifetime() {
        service.markVerified(USER, IP);
        assertTrue(service.consumeVerification(USER, IP));
        assertTrue(service.consumeVerification(USER, IP, 42L, 1, PURPOSE));
        assertFalse(service.consumeVerification(USER, "198.51.100.1"));
        redis.advanceSeconds(900);
        assertFalse(service.consumeVerification(USER, IP));
    }

    @Test void missingAtomicConsumptionResultFailsClosed() {
        grant(); service.markVerified(USER, IP);
        when(redis.template().execute(any(RedisScript.class), anyList(), anyString())).thenReturn(null);
        assertFalse(service.consumeVerification(USER, IP, 42L, 1, PURPOSE));
    }

    @Test void clearingVerificationRevokesAllSessionsAndExistingGrantKeys() {
        grant();
        String oldKey = service.qrGrantKey(USER, 42L, 1, PURPOSE);
        redis.template().opsForValue().set(service.qrGrantKey(USER, 43L, 1, "login-options"), IP, Duration.ofMinutes(15));
        service.markVerified(USER, IP);
        service.clearVerification(USER);
        assertNotEquals(oldKey, service.qrGrantKey(USER, 42L, 1, PURPOSE));
        assertFalse(service.consumeVerification(USER, IP, 42L, 1, PURPOSE));
        assertFalse(service.consumeVerification(USER, IP, 43L, 1, "login-options"));
        assertFalse(service.consumeVerification(USER, IP));
    }

    @Test void missingContextCannotAccessQrGrant() {
        grant();
        assertFalse(service.consumeVerification(USER, IP, null, 1, PURPOSE));
        assertFalse(service.consumeVerification(USER, IP, 42L, null, PURPOSE));
        assertFalse(service.consumeVerification(USER, IP, 42L, 1, null));
        assertFalse(service.consumeVerification(USER, IP, 42L, 1, "unsupported"));
        assertFalse(service.consumeVerification(null, IP, 42L, 1, PURPOSE));
        assertFalse(service.consumeVerification(USER, "", 42L, 1, PURPOSE));
    }
}
