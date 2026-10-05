package cn.ksuser.api.service;

import cn.ksuser.api.entity.UserSensitiveLog;
import cn.ksuser.api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.concurrent.RejectedExecutionException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SensitiveLogPushTest {
    private SensitiveLogService logs;
    private UserSensitiveLogRepository repository;
    private PushNotificationService push;
    @BeforeEach void setUp() {
        logs = new SensitiveLogService(); repository = mock(UserSensitiveLogRepository.class); push = mock(PushNotificationService.class);
        ReflectionTestUtils.setField(logs, "logRepository", repository);
        ReflectionTestUtils.setField(logs, "pushNotificationService", push);
        ReflectionTestUtils.setField(logs, "userService", mock(UserService.class));
        ReflectionTestUtils.setField(logs, "riskScoringService", mock(RiskScoringService.class));
        ReflectionTestUtils.setField(logs, "userSettingsRepository", mock(UserSettingsRepository.class));
    }
    private UserSensitiveLog event() {
        var log = new UserSensitiveLog(); log.setUserId(1L); log.setOperationType("CHANGE_PASSWORD");
        log.setResult(UserSensitiveLog.OperationResult.SUCCESS); return log;
    }
    @Test void bothLogPathsScheduleOnePushAfterSaving() {
        for (boolean synchronous : new boolean[]{true, false}) {
            var event = event();
            if (synchronous) logs.logSync(event); else logs.logAsync(event);
            var order = inOrder(repository, push); order.verify(repository).save(event); order.verify(push).notifySensitiveOperation(event);
        }
    }
    @Test void rejectedNotificationTaskCannotFailOriginalSensitiveOperation() {
        var event = event(); doThrow(new RejectedExecutionException()).when(push).notifySensitiveOperation(event);
        assertDoesNotThrow(() -> logs.logSync(event)); verify(repository).save(event);
    }
}
