package cn.ksuser.api.service;

import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserSensitiveLog;
import cn.ksuser.api.repository.UserSensitiveLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RiskScoringServiceTest {
    @Test void systemPollingDoesNotRaiseTheUserOperationFrequencyScore() {
        var repository = mock(UserSensitiveLogRepository.class);
        var service = new RiskScoringService(); ReflectionTestUtils.setField(service, "logRepository", repository);
        var action = new UserSensitiveLog(); action.setUserId(1L); action.setOperationType("LOGIN");
        action.setResult(UserSensitiveLog.OperationResult.SUCCESS); action.setCreatedAt(LocalDateTime.now().withHour(12));
        int baseline = service.calculateRiskScore(action, new User());
        var policy = new UserSensitiveLog(); policy.setOperationType("ADAPTIVE_POLICY");
        when(repository.findByUserIdAndCreatedAtAfter(eq(1L), any())).thenReturn(Collections.nCopies(20, policy));
        assertEquals(baseline, service.calculateRiskScore(action, new User()));
        when(repository.findByUserIdAndCreatedAtAfter(eq(1L), any())).thenReturn(Collections.nCopies(6, action));
        assertEquals(baseline + 20, service.calculateRiskScore(action, new User()));
    }
}
