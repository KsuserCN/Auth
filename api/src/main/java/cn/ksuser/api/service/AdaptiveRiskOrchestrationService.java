package cn.ksuser.api.service;

import cn.ksuser.api.dto.AdaptiveAuthStatusResponse;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserSensitiveLog;
import cn.ksuser.api.entity.UserSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class AdaptiveRiskOrchestrationService {

    private static final String POLICY_VERSION = "2.0.0";
    private static final String DECISION_CACHE_PREFIX = "adaptive:risk:last-decision:";

    private final AdaptiveContinuousAuthService adaptiveContinuousAuthService;
    private final UserSessionService userSessionService;
    private final SensitiveOperationService sensitiveOperationService;
    private final SensitiveLogService sensitiveLogService;
    private final AdaptiveRiskAlertService adaptiveRiskAlertService;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.adaptive.policy.medium-threshold:40}")
    private int mediumThreshold;

    @Value("${app.adaptive.policy.high-threshold:70}")
    private int highThreshold;

    @Value("${app.adaptive.policy.deduplicate-seconds:60}")
    private long deduplicateSeconds;

    public AdaptiveRiskOrchestrationService(
        AdaptiveContinuousAuthService adaptiveContinuousAuthService,
        UserSessionService userSessionService,
        SensitiveOperationService sensitiveOperationService,
        SensitiveLogService sensitiveLogService,
        AdaptiveRiskAlertService adaptiveRiskAlertService,
        StringRedisTemplate redisTemplate
    ) {
        this.adaptiveContinuousAuthService = adaptiveContinuousAuthService;
        this.userSessionService = userSessionService;
        this.sensitiveOperationService = sensitiveOperationService;
        this.sensitiveLogService = sensitiveLogService;
        this.adaptiveRiskAlertService = adaptiveRiskAlertService;
        this.redisTemplate = redisTemplate;
    }

    public AdaptiveAuthStatusResponse evaluateAndApply(
        User user,
        UserSession session,
        String clientIp,
        String userAgent,
        String source
    ) {
        AdaptiveAuthStatusResponse response = adaptiveContinuousAuthService.evaluate(user, session, clientIp);
        PolicyDecision decision = resolveDecision(response.getRiskScore());

        response.setPolicyDecision(decision.name());
        response.setPolicyVersion(POLICY_VERSION);
        response.setSessionFrozen(false);

        if (user != null && user.getUuid() != null) {
            if (decision == PolicyDecision.STEP_UP) {
                sensitiveOperationService.clearVerification(user.getUuid());
                response.setRequiresStepUp(true);
                response.setTrusted(false);
                adaptiveRiskAlertService.publishStepUpAlert(user.getId(), response.getRiskScore(), response.getReasons());
            } else if (decision == PolicyDecision.FREEZE) {
                sensitiveOperationService.clearVerification(user.getUuid());
                response.setRequiresStepUp(true);
                response.setTrusted(false);
                response.setSessionFrozen(true);
                if (session != null) {
                    userSessionService.revokeSession(session);
                }
                adaptiveRiskAlertService.publishFreezeAlert(user.getId(), response.getRiskScore(), response.getReasons());
            }
        }

        attachAlert(user, response);
        maybeRecordDecisionLog(user, session, clientIp, userAgent, source, response, decision);
        adjustRecommendedAction(response, decision);
        return response;
    }

    private void attachAlert(User user, AdaptiveAuthStatusResponse response) {
        if (user == null || user.getId() == null) {
            return;
        }
        AdaptiveRiskAlertService.AlertPayload alert = adaptiveRiskAlertService.getActiveAlert(user.getId());
        if (alert == null) {
            response.setMultiEndpointAlert(false);
            response.setAlertLevel(null);
            response.setAlertTitle(null);
            response.setAlertMessage(null);
            response.setAlertRemainingSeconds(0);
            return;
        }
        response.setMultiEndpointAlert(true);
        response.setAlertLevel(alert.getLevel());
        response.setAlertTitle(alert.getTitle());
        response.setAlertMessage(alert.getMessage());
        response.setAlertRemainingSeconds(adaptiveRiskAlertService.getRemainingSeconds(user.getId()));
    }

    private void adjustRecommendedAction(AdaptiveAuthStatusResponse response, PolicyDecision decision) {
        if (decision == PolicyDecision.FREEZE) {
            response.setRecommendedAction("当前会话风险极高，系统已冻结会话，请重新登录并完成验证");
            return;
        }
        if (decision == PolicyDecision.STEP_UP) {
            response.setRecommendedAction("当前会话风险中等，需完成 step-up 验证后继续敏感操作");
        }
    }

    private PolicyDecision resolveDecision(int score) {
        if (score >= Math.max(highThreshold, mediumThreshold + 1)) {
            return PolicyDecision.FREEZE;
        }
        if (score >= mediumThreshold) {
            return PolicyDecision.STEP_UP;
        }
        return PolicyDecision.ALLOW;
    }

    private void maybeRecordDecisionLog(
        User user,
        UserSession session,
        String clientIp,
        String userAgent,
        String source,
        AdaptiveAuthStatusResponse response,
        PolicyDecision decision
    ) {
        if (user == null || user.getId() == null) {
            return;
        }
        String dedupeKey = decisionDedupeKey(user.getId(), session == null ? null : session.getId());
        String dedupeValue = decision.name() + ":" + response.getRiskScore();
        String previous = redisTemplate.opsForValue().get(dedupeKey);
        if (dedupeValue.equals(previous)) {
            return;
        }
        redisTemplate.opsForValue().set(dedupeKey, dedupeValue, Duration.ofSeconds(Math.max(deduplicateSeconds, 1)));

        UserSensitiveLog log = new UserSensitiveLog();
        log.setUserId(user.getId());
        log.setOperationType("ADAPTIVE_POLICY");
        log.setLoginMethod(source == null || source.isBlank() ? "adaptive" : source);
        log.setResult(UserSensitiveLog.OperationResult.SUCCESS);
        log.setFailureReason(composeReason(response.getReasons(), response.getRiskScore()));
        log.setIpAddress(resolveIp(clientIp, session));
        log.setUserAgent(resolveUserAgent(userAgent, session));
        log.setRiskScore(response.getRiskScore());
        log.setActionTaken(decision.name());
        log.setTriggeredMultiErrorLock(false);
        log.setTriggeredRateLimitLock(false);
        sensitiveLogService.logAsync(log);
    }

    private String decisionDedupeKey(Long userId, Long sessionId) {
        if (sessionId != null) {
            return DECISION_CACHE_PREFIX + "s:" + sessionId;
        }
        return DECISION_CACHE_PREFIX + "u:" + userId;
    }

    private String resolveIp(String clientIp, UserSession session) {
        if (clientIp != null && !clientIp.isBlank()) {
            return clientIp;
        }
        if (session != null && session.getIpAddress() != null && !session.getIpAddress().isBlank()) {
            return session.getIpAddress();
        }
        return "unknown";
    }

    private String resolveUserAgent(String userAgent, UserSession session) {
        if (userAgent != null && !userAgent.isBlank()) {
            return userAgent;
        }
        return session == null ? null : session.getUserAgent();
    }

    private String composeReason(List<String> reasons, int riskScore) {
        String reasonText = reasons == null || reasons.isEmpty() ? "" : reasons.get(0);
        if (reasonText == null) {
            reasonText = "";
        }
        String merged = ("risk=" + riskScore + (reasonText.isBlank() ? "" : "; " + reasonText));
        return merged.length() <= 255 ? merged : merged.substring(0, 255);
    }

    public enum PolicyDecision {
        ALLOW,
        STEP_UP,
        FREEZE
    }
}
