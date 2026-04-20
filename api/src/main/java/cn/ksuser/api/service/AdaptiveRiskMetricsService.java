package cn.ksuser.api.service;

import cn.ksuser.api.dto.AdaptiveRiskMetricsResponse;
import cn.ksuser.api.entity.UserSensitiveLog;
import cn.ksuser.api.repository.UserSensitiveLogRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdaptiveRiskMetricsService {

    private static final String OP_ADAPTIVE_POLICY = "ADAPTIVE_POLICY";
    private static final String OP_SENSITIVE_VERIFY = "SENSITIVE_VERIFY";
    private static final String ACTION_STEP_UP = "STEP_UP";
    private static final String ACTION_FREEZE = "FREEZE";

    private final UserSensitiveLogRepository userSensitiveLogRepository;

    public AdaptiveRiskMetricsService(UserSensitiveLogRepository userSensitiveLogRepository) {
        this.userSensitiveLogRepository = userSensitiveLogRepository;
    }

    public AdaptiveRiskMetricsResponse buildUserMetrics(Long userId, int windowHours) {
        int normalizedWindowHours = normalizeWindowHours(windowHours);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusHours(normalizedWindowHours);

        List<UserSensitiveLog> decisions = userSensitiveLogRepository
            .findByUserIdAndOperationTypeAndCreatedAtAfterOrderByCreatedAtAsc(
                userId,
                OP_ADAPTIVE_POLICY,
                start
            );

        long total = decisions.size();
        long stepUpCount = decisions.stream()
            .filter(item -> ACTION_STEP_UP.equalsIgnoreCase(item.getActionTaken()))
            .count();
        long freezeCount = decisions.stream()
            .filter(item -> ACTION_FREEZE.equalsIgnoreCase(item.getActionTaken()))
            .count();
        long intercepted = stepUpCount + freezeCount;

        long falsePositiveCount = 0L;
        long latencyCount = 0L;
        long latencySumMs = 0L;

        for (UserSensitiveLog decision : decisions) {
            if (!ACTION_STEP_UP.equalsIgnoreCase(decision.getActionTaken())) {
                continue;
            }
            DecisionEvaluation evaluation = inspectStepUpDecision(decision);
            if (evaluation.falsePositiveCandidate()) {
                falsePositiveCount++;
            }
            if (evaluation.latencyMs() != null) {
                latencyCount++;
                latencySumMs += evaluation.latencyMs();
            }
        }

        AdaptiveRiskMetricsResponse response = new AdaptiveRiskMetricsResponse();
        response.setWindowHours(normalizedWindowHours);
        response.setTotalEvaluations(total);
        response.setInterceptedCount(intercepted);
        response.setStepUpCount(stepUpCount);
        response.setFreezeCount(freezeCount);
        response.setInterceptRatePercent(total == 0 ? 0D : round2(intercepted * 100.0 / total));
        response.setFalsePositiveCount(falsePositiveCount);
        response.setFalsePositiveRatePercent(intercepted == 0 ? 0D : round2(falsePositiveCount * 100.0 / intercepted));
        response.setCompletedStepUpCount(latencyCount);
        response.setAvgVerificationLatencyMs(latencyCount == 0 ? 0L : Math.round((double) latencySumMs / latencyCount));
        response.setGeneratedAt(now);
        return response;
    }

    private DecisionEvaluation inspectStepUpDecision(UserSensitiveLog decision) {
        if (decision == null || decision.getUserId() == null || decision.getCreatedAt() == null) {
            return new DecisionEvaluation(false, null);
        }

        LocalDateTime decisionTime = decision.getCreatedAt();
        LocalDateTime windowEnd = decisionTime.plusMinutes(30);
        List<UserSensitiveLog> around = userSensitiveLogRepository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtAsc(
            decision.getUserId(),
            decisionTime,
            windowEnd
        );

        boolean hasFailure = around.stream()
            .anyMatch(item ->
                item != null
                    && item.getResult() == UserSensitiveLog.OperationResult.FAILURE
                    && !OP_ADAPTIVE_POLICY.equalsIgnoreCase(item.getOperationType())
            );

        UserSensitiveLog sensitiveVerify = around.stream()
            .filter(item ->
                item != null
                    && OP_SENSITIVE_VERIFY.equalsIgnoreCase(item.getOperationType())
                    && item.getResult() == UserSensitiveLog.OperationResult.SUCCESS
                    && item.getCreatedAt() != null
            )
            .findFirst()
            .orElse(null);

        Long latencyMs = null;
        if (sensitiveVerify != null) {
            latencyMs = Math.max(Duration.between(decisionTime, sensitiveVerify.getCreatedAt()).toMillis(), 0L);
        }

        // 经验口径：策略触发后快速完成验证且后续无失败事件，记为潜在误报
        boolean falsePositiveCandidate = latencyMs != null && latencyMs <= 120_000L && !hasFailure;
        return new DecisionEvaluation(falsePositiveCandidate, latencyMs);
    }

    private int normalizeWindowHours(int windowHours) {
        if (windowHours < 1) {
            return 24;
        }
        return Math.min(windowHours, 24 * 30);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record DecisionEvaluation(boolean falsePositiveCandidate, Long latencyMs) {
    }
}
