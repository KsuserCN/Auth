package cn.ksuser.api.service;

import cn.ksuser.api.config.ApnsProperties;
import cn.ksuser.api.entity.*;
import cn.ksuser.api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PushNotificationServiceTest {
    private UserPushDeviceRepository devices;
    private UserSessionRepository sessions;
    private ApnsClient apns;
    private ApnsProperties properties;
    private PushNotificationService service;
    @BeforeEach void setUp() {
        devices = mock(UserPushDeviceRepository.class); sessions = mock(UserSessionRepository.class); apns = mock(ApnsClient.class);
        properties = new ApnsProperties(); properties.setEnabled(true);
        service = new PushNotificationService(devices, sessions, apns, properties);
    }
    private UserSensitiveLog log(String operation, boolean success) {
        var log = new UserSensitiveLog(); log.setId(99L); log.setUserId(1L); log.setOperationType(operation);
        log.setResult(success ? UserSensitiveLog.OperationResult.SUCCESS : UserSensitiveLog.OperationResult.FAILURE);
        log.setRiskScore(0); log.setActionTaken("ALLOW"); return log;
    }
    private UserPushDevice device(long id) {
        var user = new User(); user.setId(1L); user.setUuid("account-1");
        var session = new UserSession(); session.setId(id); session.setUser(user); session.setExpiresAt(LocalDateTime.now().plusDays(1));
        var device = new UserPushDevice(); device.setId(id); device.setVersion(0L); device.setSession(session);
        device.setMode(UserPushDevice.Mode.ALL); device.setDeviceToken("a".repeat(64)); device.setEnvironment(UserPushDevice.Environment.SANDBOX);
        when(sessions.findActiveSessionById(eq(id), any())).thenReturn(Optional.of(session));
        when(devices.findById(id)).thenReturn(Optional.of(device)); return device;
    }
    @Test void modesFilterNormalOperationsAndAnonymousEvents() {
        var change = log("CHANGE_PASSWORD", true);
        assertTrue(service.shouldNotify(UserPushDevice.Mode.ALL, change));
        assertFalse(service.shouldNotify(UserPushDevice.Mode.OFF, change));
        assertFalse(service.shouldNotify(UserPushDevice.Mode.LOGIN_AND_ABNORMAL, change));
        assertTrue(service.shouldNotify(UserPushDevice.Mode.LOGIN_AND_ABNORMAL, log("LOGIN", true)));
        change.setUserId(null); assertFalse(service.shouldNotify(UserPushDevice.Mode.ALL, change));
    }
    @Test void restrictedModeIncludesFailuresLocksRiskAndPolicyDecisions() {
        var change = log("CHANGE_PASSWORD", false);
        assertTrue(service.shouldNotify(UserPushDevice.Mode.LOGIN_AND_ABNORMAL, change));
        change = log("CHANGE_PASSWORD", true); change.setTriggeredRateLimitLock(true);
        assertTrue(service.shouldNotify(UserPushDevice.Mode.LOGIN_AND_ABNORMAL, change));
        change.setTriggeredRateLimitLock(false); change.setTriggeredMultiErrorLock(true);
        assertTrue(service.shouldNotify(UserPushDevice.Mode.LOGIN_AND_ABNORMAL, change));
        change.setTriggeredMultiErrorLock(false); change.setRiskScore(40);
        assertTrue(service.shouldNotify(UserPushDevice.Mode.LOGIN_AND_ABNORMAL, change));
        change.setRiskScore(39); assertFalse(service.shouldNotify(UserPushDevice.Mode.LOGIN_AND_ABNORMAL, change));
        change.setActionTaken("STEP_UP"); assertTrue(service.shouldNotify(UserPushDevice.Mode.LOGIN_AND_ABNORMAL, change));
        change.setActionTaken("FREEZE"); assertTrue(service.shouldNotify(UserPushDevice.Mode.LOGIN_AND_ABNORMAL, change));
    }
    @Test void normalIntermediateEventsAreSkippedButFailuresAreNot() {
        for (String operation : List.of("LOGIN_MFA", "SENSITIVE_VERIFY", "ADAPTIVE_POLICY")) {
            assertFalse(service.shouldNotify(UserPushDevice.Mode.ALL, log(operation, true)));
            assertTrue(service.shouldNotify(UserPushDevice.Mode.LOGIN_AND_ABNORMAL, log(operation, false)));
        }
    }
    @Test void queuedEventStopsWhenSessionIsRevokedOrChangesOwner() throws Exception {
        var device = device(2L); when(devices.findActiveDevices(eq(1L), any())).thenReturn(List.of(device));
        when(sessions.findActiveSessionById(eq(2L), any())).thenReturn(Optional.empty());
        service.notifySensitiveOperation(log("LOGIN", true)); verifyNoInteractions(apns);
        device.getSession().getUser().setId(3L);
        when(sessions.findActiveSessionById(eq(2L), any())).thenReturn(Optional.of(device.getSession()));
        service.notifySensitiveOperation(log("LOGIN", true)); verifyNoInteractions(apns);
    }
    @Test void queuedEventHonorsNewPreferenceOrReassignedToken() throws Exception {
        var device = device(2L); when(devices.findActiveDevices(eq(1L), any())).thenReturn(List.of(device));
        var current = new UserPushDevice(); current.setVersion(1L); current.setMode(UserPushDevice.Mode.OFF);
        when(devices.findById(2L)).thenReturn(Optional.of(current));
        service.notifySensitiveOperation(log("LOGIN", true)); verifyNoInteractions(apns);
    }
    @Test void invalidTokensAreDeletedWithVersionAndOneFailureDoesNotBlockOtherDevices() throws Exception {
        var first = device(2L); var second = device(3L);
        when(devices.findActiveDevices(eq(1L), any())).thenReturn(List.of(first, second));
        when(apns.send(eq(first), anyString(), anyString(), anyString())).thenReturn(new ApnsClient.Delivery(false, true, "Unregistered"));
        when(apns.send(eq(second), anyString(), anyString(), anyString())).thenReturn(new ApnsClient.Delivery(true, false, "Success"));
        service.notifySensitiveOperation(log("LOGIN", true));
        verify(devices).deleteInvalidDevice(2L, 0L); verify(apns).send(eq(second), anyString(), anyString(), eq("99"));
        when(apns.send(eq(first), anyString(), anyString(), anyString())).thenThrow(new java.io.IOException("network"));
        service.notifySensitiveOperation(log("LOGIN", true));
        verify(apns, times(2)).send(eq(second), anyString(), anyString(), eq("99"));
    }
    @Test void disabledProviderNeverQueriesDevicesOrSends() {
        properties.setEnabled(false); service.notifySensitiveOperation(log("LOGIN", true)); verifyNoInteractions(devices, apns);
    }
}
