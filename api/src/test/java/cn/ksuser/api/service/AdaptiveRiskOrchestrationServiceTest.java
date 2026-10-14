package cn.ksuser.api.service;

import cn.ksuser.api.dto.AdaptiveAuthStatusResponse;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserSensitiveLog;
import cn.ksuser.api.entity.UserSession;
import cn.ksuser.api.result.ApiResult;
import cn.ksuser.api.result.ApiResultCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdaptiveRiskOrchestrationServiceTest {
    private final AdaptiveContinuousAuthService evaluator = mock(AdaptiveContinuousAuthService.class);
    private final SensitiveLogService logs = mock(SensitiveLogService.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private AdaptiveRiskOrchestrationService service;
    private User user;
    private UserSession session;
    private AdaptiveAuthStatusResponse status;

    @BeforeEach void setup() {
        service = new AdaptiveRiskOrchestrationService(evaluator, mock(UserSessionService.class),
            mock(SensitiveOperationService.class), logs, mock(AdaptiveRiskAlertService.class), redis);
        ReflectionTestUtils.setField(service, "mediumThreshold", 40);
        ReflectionTestUtils.setField(service, "highThreshold", 70);
        ReflectionTestUtils.setField(service, "deduplicateSeconds", 60L);
        user = new User(); user.setId(1L); user.setUuid("user");
        session = new UserSession(); session.setId(2L); session.setExpiresAt(LocalDateTime.now().plusDays(7));
        status = new AdaptiveAuthStatusResponse();
        status.setReasonCodes(List.of(ApiResult.of(ApiResultCode.ADAPTIVE_REASON_IP_CHANGED)));
        when(evaluator.evaluate(user, session, "192.0.2.1")).thenReturn(status);
    }

    private void evaluate(int score) {
        status.setRiskScore(score);
        service.evaluateAndApply(user, session, "192.0.2.1", null, "adaptive-auth-status");
    }

    @Test void normalPollingDoesNotCreateUserActivity() {
        evaluate(0); evaluate(10);
        verifyNoInteractions(logs);
    }

    @Test void scoreChangesDoNotDuplicateTheSameSecurityAction() {
        when(redis.execute(any(RedisScript.class), anyList(), eq("STEP_UP"), anyString()))
            .thenReturn("ALLOW", "STEP_UP");
        evaluate(45); evaluate(55);
        ArgumentCaptor<UserSensitiveLog> entry = ArgumentCaptor.forClass(UserSensitiveLog.class);
        verify(logs).logAsync(entry.capture());
        assertNull(entry.getValue().getLoginMethod());
        assertEquals(45, entry.getValue().getRiskScore());
        assertEquals("STEP_UP", entry.getValue().getActionTaken());
        assertEquals("ADAPTIVE_REASON_IP_CHANGED", entry.getValue().getFailureReason());
    }

    @Test void escalationAndRiskReturningAfterRecoveryRemainRecorded() {
        when(redis.execute(any(RedisScript.class), anyList(), eq("STEP_UP"), anyString()))
            .thenReturn("ALLOW", "ALLOW");
        when(redis.execute(any(RedisScript.class), anyList(), eq("FREEZE"), anyString()))
            .thenReturn("STEP_UP");
        evaluate(45); evaluate(80); evaluate(0); evaluate(45);
        verify(logs, times(3)).logAsync(any());
    }
}
