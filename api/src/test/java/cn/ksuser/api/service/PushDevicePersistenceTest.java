package cn.ksuser.api.service;

import cn.ksuser.api.dto.PushDeviceRequest;
import cn.ksuser.api.entity.*;
import cn.ksuser.api.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class PushDevicePersistenceTest {
    @Autowired private PushNotificationService push;
    @Autowired private UserPushDeviceRepository devices;
    @Autowired private UserSessionRepository sessions;
    @Autowired private UserRepository users;
    @Autowired private EntityManager entityManager;
    private UserSession session(User user) {
        return sessions.saveAndFlush(new UserSession(user, UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), "argon2id", LocalDateTime.now().plusDays(1)));
    }
    private User user() {
        String id = UUID.randomUUID().toString();
        return users.saveAndFlush(new User(id, id, id + "@example.invalid", null));
    }
    private PushDeviceRequest request(String token, UserPushDevice.Mode mode) {
        return new PushDeviceRequest(token.repeat(64), UserPushDevice.Environment.SANDBOX, mode);
    }
    @Test void tokenRebindsAcrossAccountsAndOldSessionCannotUnregisterNewOwner() {
        var firstUser = user(); var secondUser = user(); var first = session(firstUser); var second = session(secondUser);
        push.register(first, request("A", UserPushDevice.Mode.ALL)); devices.flush();
        assertEquals(1, devices.findActiveDevices(firstUser.getId(), LocalDateTime.now()).size());
        var original = devices.findBySessionId(first.getId()).orElseThrow(); Long id = original.getId(); Long oldVersion = original.getVersion();
        push.register(second, request("a", UserPushDevice.Mode.LOGIN_AND_ABNORMAL)); devices.flush();
        push.unregister(first.getId());
        assertTrue(devices.findActiveDevices(firstUser.getId(), LocalDateTime.now()).isEmpty());
        assertEquals(1, devices.findActiveDevices(secondUser.getId(), LocalDateTime.now()).size());
        assertEquals(0, devices.deleteInvalidDevice(id, oldVersion));
        entityManager.clear(); assertEquals(UserPushDevice.Mode.LOGIN_AND_ABNORMAL, devices.findById(id).orElseThrow().getMode());
    }
    @Test void tokenRotationKeepsOneRegistrationPerSessionAndAllowsIndependentEnvironments() {
        var user = user(); var first = session(user); var second = session(user);
        push.register(first, request("a", UserPushDevice.Mode.ALL)); devices.flush();
        push.register(first, request("b", UserPushDevice.Mode.OFF)); devices.flush();
        assertTrue(devices.findByDeviceTokenAndEnvironment("a".repeat(64), UserPushDevice.Environment.SANDBOX).isEmpty());
        push.register(second, new PushDeviceRequest("b".repeat(64), UserPushDevice.Environment.PRODUCTION, UserPushDevice.Mode.ALL)); devices.flush();
        assertEquals(2, devices.findActiveDevices(user.getId(), LocalDateTime.now()).size());
        push.unregister(first.getId()); devices.flush(); assertEquals(1, devices.findActiveDevices(user.getId(), LocalDateTime.now()).size());
    }
    @Test void revokedExpiredAndDeletedSessionsStopBeingRecipients() {
        var user = user(); var revoked = session(user); var expired = session(user); var active = session(user);
        push.register(revoked, request("a", UserPushDevice.Mode.ALL)); push.register(expired, request("b", UserPushDevice.Mode.ALL));
        push.register(active, request("c", UserPushDevice.Mode.ALL)); devices.flush();
        revoked.setRevokedAt(LocalDateTime.now()); expired.setExpiresAt(LocalDateTime.now().minusSeconds(1)); sessions.flush();
        assertEquals(1, devices.findActiveDevices(user.getId(), LocalDateTime.now()).size());
        assertEquals(2, devices.deleteInactiveDevices(LocalDateTime.now())); entityManager.clear();
        sessions.deleteById(active.getId()); sessions.flush(); entityManager.clear();
        assertTrue(devices.findActiveDevices(user.getId(), LocalDateTime.now()).isEmpty());
        assertTrue(devices.findBySessionId(active.getId()).isEmpty());
    }
}
