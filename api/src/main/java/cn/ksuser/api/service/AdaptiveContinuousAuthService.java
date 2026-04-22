package cn.ksuser.api.service;

import cn.ksuser.api.dto.AdaptiveAuthStatusResponse;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserSensitiveLog;
import cn.ksuser.api.entity.UserSession;
import cn.ksuser.api.entity.UserSettings;
import cn.ksuser.api.repository.UserSensitiveLogRepository;
import cn.ksuser.api.repository.UserSettingsRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdaptiveContinuousAuthService {

    private final UserSensitiveLogRepository userSensitiveLogRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final IpLocationService ipLocationService;
    private final SensitiveOperationService sensitiveOperationService;

    public AdaptiveContinuousAuthService(
            UserSensitiveLogRepository userSensitiveLogRepository,
            UserSettingsRepository userSettingsRepository,
            IpLocationService ipLocationService,
            SensitiveOperationService sensitiveOperationService) {
        this.userSensitiveLogRepository = userSensitiveLogRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.ipLocationService = ipLocationService;
        this.sensitiveOperationService = sensitiveOperationService;
    }

    public AdaptiveAuthStatusResponse evaluate(User user, UserSession session, String currentIp) {
        AdaptiveAuthStatusResponse response = new AdaptiveAuthStatusResponse();
        response.setSessionId(session == null ? null : session.getId());
        response.setCurrentIp(currentIp);
        response.setCurrentLocation(currentIp == null || currentIp.isBlank() ? "未知位置" : ipLocationService.getIpLocation(currentIp));

        if (session == null || user == null) {
            response.setRiskScore(100);
            response.setRiskLevel("high");
            response.setTrusted(false);
            response.setRequiresStepUp(true);
            response.setRecommendedAction("当前会话不存在或已失效，请重新登录");
            response.setReasons(List.of("未找到当前活跃会话"));
            return response;
        }

        response.setSessionIp(session.getIpAddress());
        response.setSessionLocation(session.getIpLocation());
        response.setBrowser(session.getBrowser());
        response.setDeviceType(session.getDeviceType());

        boolean sensitiveVerified = sensitiveOperationService.isVerified(user.getUuid(), currentIp);
        long remainingSeconds = sensitiveOperationService.getRemainingTime(user.getUuid());
        response.setSensitiveVerified(sensitiveVerified);
        response.setSensitiveVerificationRemainingSeconds(Math.max(remainingSeconds, 0));

        LocalDateTime now = LocalDateTime.now();
        long authAgeSeconds = session.getAuthTime() == null ? 0 : Math.max(Duration.between(session.getAuthTime(), now).getSeconds(), 0);
        long idleSeconds = session.getLastSeenAt() == null ? 0 : Math.max(Duration.between(session.getLastSeenAt(), now).getSeconds(), 0);
        response.setAuthAgeSeconds(authAgeSeconds);
        response.setIdleSeconds(idleSeconds);

        UserSettings settings = userSettingsRepository.findByUserId(user.getId()).orElse(null);
        boolean detectUnusualLogin = settings == null || Boolean.TRUE.equals(settings.getDetectUnusualLogin());

        int score = 0;
        List<String> reasons = new ArrayList<>();

        if (detectUnusualLogin && currentIp != null && session.getIpAddress() != null && !currentIp.equals(session.getIpAddress())) {
            score += 35;
            reasons.add("当前 IP 与会话建立时不一致");
        }

        if (detectUnusualLogin
                && response.getCurrentLocation() != null
                && session.getIpLocation() != null
                && !response.getCurrentLocation().equals(session.getIpLocation())) {
            score += 20;
            reasons.add("当前位置与会话建立位置发生变化");
        }

        if (authAgeSeconds > 3L * 24 * 3600) {
            score += 20;
            reasons.add("当前会话已持续超过 3 天");
        } else if (authAgeSeconds > 12L * 3600) {
            score += 10;
            reasons.add("当前会话已持续超过 12 小时");
        }

        if (idleSeconds > 2L * 3600) {
            score += 15;
            reasons.add("当前会话空闲时间超过 2 小时");
        } else if (idleSeconds > 30L * 60) {
            score += 8;
            reasons.add("当前会话已空闲超过 30 分钟");
        }

        List<UserSensitiveLog> recentFailures = userSensitiveLogRepository.findRecentFailuresByUser(
                user.getId(),
                now.minusHours(24)
        );
        if (recentFailures.size() >= 3) {
            score += 15;
            reasons.add("24 小时内存在多次失败认证或敏感操作");
        }

        UserSensitiveLog latestLog = userSensitiveLogRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId()).orElse(null);
        if (latestLog != null && latestLog.getRiskScore() != null && latestLog.getRiskScore() >= 70) {
            score += 15;
            reasons.add("最近一次安全事件风险评分较高");
        }

        if (session.getLastMfaVerifiedAt() != null) {
            long lastStepUpSeconds = Math.max(Duration.between(session.getLastMfaVerifiedAt(), now).getSeconds(), 0);
            if (lastStepUpSeconds <= 15L * 60) {
                score -= 25;
            } else if (lastStepUpSeconds <= 60L * 60) {
                score -= 10;
            }
        }

        if (sensitiveVerified) {
            score -= 25;
        }

        score = Math.max(0, Math.min(100, score));
        String level = score >= 70 ? "high" : score >= 40 ? "medium" : "low";
        boolean requiresStepUp = score >= 70 || (score >= 40 && !sensitiveVerified);

        response.setRiskScore(score);
        response.setRiskLevel(level);
        response.setRequiresStepUp(requiresStepUp);
        response.setTrusted(!requiresStepUp);
        response.setReasons(reasons.isEmpty() ? List.of("当前会话环境稳定，未发现需要额外认证的风险信号") : reasons);
        response.setRecommendedAction(buildRecommendedAction(level, sensitiveVerified, response.getSensitiveVerificationRemainingSeconds()));
        return response;
    }

    private String buildRecommendedAction(String level, boolean sensitiveVerified, long remainingSeconds) {
        if ("high".equals(level)) {
            return "建议立即进行一次 step-up 验证，再继续执行敏感操作";
        }
        if ("medium".equals(level) && !sensitiveVerified) {
            return "建议先完成一次敏感验证，以提升当前会话信任等级";
        }
        if (sensitiveVerified && remainingSeconds > 0) {
            return "当前会话处于已验证状态，可继续执行敏感操作";
        }
        return "当前会话风险较低，可继续使用";
    }
}
