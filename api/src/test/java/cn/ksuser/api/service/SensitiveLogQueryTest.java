package cn.ksuser.api.service;

import cn.ksuser.api.dto.SensitiveLogResponse;
import cn.ksuser.api.entity.UserSensitiveLog;
import cn.ksuser.api.repository.UserSensitiveLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class SensitiveLogQueryTest {
    @Autowired UserSensitiveLogRepository repository;
    @Autowired SensitiveLogService service;

    private UserSensitiveLog save(String operation, String action) {
        var log = new UserSensitiveLog();
        log.setUserId(987654L); log.setOperationType(operation); log.setActionTaken(action);
        log.setIpAddress("192.0.2.1"); log.setResult(UserSensitiveLog.OperationResult.SUCCESS);
        log.setLoginMethod("adaptive-auth-status");
        return repository.saveAndFlush(log);
    }

    @Test void hidesHistoricalRoutineChecksWithoutBreakingPaginationOrUserActions() {
        save("ADAPTIVE_POLICY", "ALLOW"); save("LOGIN", "ALLOW"); save("ADAPTIVE_POLICY", "STEP_UP");
        var page = repository.findByUserIdWithFilters(987654L, null, null, null, null, PageRequest.of(0, 1));
        assertEquals(2, page.getTotalElements());
        assertEquals(2, page.getTotalPages());
        var policy = repository.findByUserIdWithFilters(987654L, null, null, "ADAPTIVE_POLICY", null, PageRequest.of(0, 20));
        assertEquals(1, policy.getTotalElements());
        assertEquals("STEP_UP", policy.getContent().get(0).getActionTaken());
        var response = new SensitiveLogResponse(policy.getContent().get(0));
        assertNull(response.getLoginMethod());
        assertTrue(response.getLoginMethods().isEmpty());
    }

    @Test void passivePolicyEventsCannotReplaceLatestUserActivityForRiskScoring() {
        var login = save("LOGIN", "ALLOW"); save("ADAPTIVE_POLICY", "FREEZE");
        assertEquals(login.getId(), repository.findTopByUserIdAndOperationTypeNotOrderByCreatedAtDesc(987654L, "ADAPTIVE_POLICY").orElseThrow().getId());
    }

    @Test void notificationDetailIsIndependentOfPaginationAndScopedToItsOwner() {
        var target = save("LOGIN", "ALLOW");
        for (int i = 0; i < 25; i++) { save("CHANGE_PASSWORD", "ALLOW"); }

        assertEquals(target.getId(), service.findLog(987654L, target.getId()).orElseThrow().getId());
        assertTrue(service.findLog(123456L, target.getId()).isEmpty());
        assertTrue(service.findLog(987654L, Long.MAX_VALUE).isEmpty());
        assertTrue(service.findLog(987654L, 0L).isEmpty());
    }
}
