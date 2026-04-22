package cn.ksuser.api.service;

import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserSensitiveLog;
import cn.ksuser.api.repository.UserSensitiveLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 风险评分服务
 * 基于多种因素计算操作的风险评分（0-100）
 * 
 * 评分因素：
 * 1. 操作失败次数（24小时内失败次数 > 3 次：+30分）
 * 2. IP地址异常（新IP地址首次出现：+20分）
 * 3. 地理位置异常（与上次登录地点距离过远：+15分）
 * 4. 设备类型变化（新设备类型首次使用：+10分）
 * 5. 浏览器变化（新浏览器首次使用：+8分）
 * 6. 登录失败（失败操作：+5分）
 * 7. 敏感操作失败（修改密码/邮箱失败：+15分）
 * 8. 非工作时间操作（夜间操作：+5分）
 * 9. 操作频率异常（5分钟内连续多次请求：+20分）
 */
@Service
public class RiskScoringService {

    private static final Logger logger = LoggerFactory.getLogger(RiskScoringService.class);

    @Autowired
    private UserSensitiveLogRepository logRepository;

    /**
     * 计算操作风险评分
     */
    public Integer calculateRiskScore(UserSensitiveLog log, User user) {
        int score = 0;

        try {
            // 因素1：操作失败次数
            score += checkFailureFrequency(log);

            // 因素2：IP地址异常
            score += checkNewIpAddress(log, user);

            // 因素3：地理位置异常
            score += checkLocationAnomaly(log, user);

            // 因素4：设备类型变化
            score += checkNewDeviceType(log, user);

            // 因素5：浏览器变化
            score += checkNewBrowser(log, user);

            // 因素6：登录失败
            score += checkLoginFailure(log);

            // 因素7：敏感操作失败
            score += checkSensitiveOperationFailure(log);

            // 因素8：非工作时间操作
            score += checkUnusualTimeOperation(log);

            // 因素9：操作频率异常
            score += checkRequestFrequencyAnomaly(log, user);

            // 确保评分在0-100之间
            score = Math.max(0, Math.min(100, score));

            logger.debug("Risk score calculated: userId={}, score={}, operation={}", 
                        log.getUserId(), score, log.getOperationType());

        } catch (Exception e) {
            logger.error("Error calculating risk score", e);
            score = 5; // 计算异常时给予较低的风险分数
        }

        return score;
    }

    /**
     * 因素1：检查24小时内失败次数是否过多
     */
    private Integer checkFailureFrequency(UserSensitiveLog log) {
        if (log.getUserId() == null) {
            return 0;
        }

        LocalDateTime oneDayAgo = LocalDateTime.now().minusHours(24);
        List<UserSensitiveLog> recentFailures = logRepository.findRecentFailuresByUser(
            log.getUserId(), 
            oneDayAgo
        );

        logger.debug("Factor 1 - Failure frequency: userId={}, failures in 24h={}", 
                    log.getUserId(), recentFailures != null ? recentFailures.size() : 0);

        // 24小时内失败次数 > 3 次：+30分
        if (recentFailures != null && recentFailures.size() > 3) {
            return 30;
        }
        return 0;
    }

    /**
     * 因素2：检查是否为新IP地址
     */
    private Integer checkNewIpAddress(UserSensitiveLog log, User user) {
        if (log.getUserId() == null || log.getIpAddress() == null) {
            return 0;
        }

        // 查询用户历史中是否出现过该IP
        List<UserSensitiveLog> previousLogs = logRepository.findByUserIdAndIpAddress(
            log.getUserId(),
            log.getIpAddress()
        );

        logger.debug("Factor 2 - New IP: userId={}, ip={}, previous_count={}", 
                    log.getUserId(), log.getIpAddress(), previousLogs != null ? previousLogs.size() : 0);

        // 新IP地址（历史中未出现过）：+20分
        if (previousLogs == null || previousLogs.isEmpty()) {
            return 20;
        }
        return 0;
    }

    /**
     * 因素3：检查地理位置异常（与上次登录地点的差异）
     */
    private Integer checkLocationAnomaly(UserSensitiveLog log, User user) {
        if (log.getUserId() == null || log.getIpLocation() == null) {
            logger.debug("Factor 3 - Location anomaly: userId={}, location_null={}", 
                        log.getUserId(), log.getIpLocation() == null);
            return 0;
        }

        // 查询最近一次的登录日志
        List<UserSensitiveLog> recentLogs = logRepository.findByUserIdAndOperationTypeOrderByCreatedAtDesc(
            log.getUserId(),
            "LOGIN"
        );

        logger.debug("Factor 3 - Location anomaly: userId={}, location={}, previous_logins={}", 
                    log.getUserId(), log.getIpLocation(), recentLogs != null ? recentLogs.size() : 0);

        if (recentLogs == null || recentLogs.isEmpty()) {
            // 首次登录或首次从该位置登录：+15分
            return 15;
        }

        UserSensitiveLog lastLogin = recentLogs.get(0);
        if (lastLogin.getIpLocation() != null && 
            !lastLogin.getIpLocation().equals(log.getIpLocation())) {
            // 登录位置与上次不同：+15分
            return 15;
        }

        return 0;
    }

    /**
     * 因素4：检查新设备类型
     */
    private Integer checkNewDeviceType(UserSensitiveLog log, User user) {
        if (log.getUserId() == null || log.getDeviceType() == null) {
            logger.debug("Factor 4 - New device type: userId={}, deviceType_null={}", 
                        log.getUserId(), log.getDeviceType() == null);
            return 0;
        }

        // 查询用户历史中是否出现过该设备类型
        List<UserSensitiveLog> previousLogs = logRepository.findByUserIdAndDeviceType(
            log.getUserId(),
            log.getDeviceType()
        );

        logger.debug("Factor 4 - New device type: userId={}, deviceType={}, previous_count={}", 
                    log.getUserId(), log.getDeviceType(), previousLogs != null ? previousLogs.size() : 0);

        // 新设备类型：+10分
        if (previousLogs == null || previousLogs.isEmpty()) {
            return 10;
        }
        return 0;
    }

    /**
     * 因素5：检查新浏览器
     */
    private Integer checkNewBrowser(UserSensitiveLog log, User user) {
        if (log.getUserId() == null || log.getBrowser() == null) {
            logger.debug("Factor 5 - New browser: userId={}, browser_null={}", 
                        log.getUserId(), log.getBrowser() == null);
            return 0;
        }

        // 查询用户历史中是否使用过该浏览器
        List<UserSensitiveLog> previousLogs = logRepository.findByUserIdAndBrowser(
            log.getUserId(),
            log.getBrowser()
        );

        logger.debug("Factor 5 - New browser: userId={}, browser={}, previous_count={}", 
                    log.getUserId(), log.getBrowser(), previousLogs != null ? previousLogs.size() : 0);

        // 新浏览器：+8分
        if (previousLogs == null || previousLogs.isEmpty()) {
            return 8;
        }
        return 0;
    }

    /**
     * 因素6：检查是否为登录失败
     */
    private Integer checkLoginFailure(UserSensitiveLog log) {
        if ("LOGIN".equals(log.getOperationType()) && 
            UserSensitiveLog.OperationResult.FAILURE.equals(log.getResult())) {
            logger.debug("Factor 6 - Login failure: userId={}", log.getUserId());
            // 登录失败：+5分
            return 5;
        }
        return 0;
    }

    /**
     * 因素7：检查敏感操作是否失败
     */
    private Integer checkSensitiveOperationFailure(UserSensitiveLog log) {
        String operationType = log.getOperationType();
        
        // 定义敏感操作
        boolean isSensitiveOp = "CHANGE_PASSWORD".equals(operationType) ||
                               "CHANGE_EMAIL".equals(operationType) ||
                               "ADD_PASSKEY".equals(operationType) ||
                               "DELETE_PASSKEY".equals(operationType) ||
                               "ENABLE_TOTP".equals(operationType) ||
                               "DISABLE_TOTP".equals(operationType);

        if (isSensitiveOp && UserSensitiveLog.OperationResult.FAILURE.equals(log.getResult())) {
            logger.debug("Factor 7 - Sensitive operation failure: userId={}, operation={}", 
                        log.getUserId(), operationType);
            // 敏感操作失败：+15分
            return 15;
        }
        return 0;
    }

    /**
     * 因素8：检查非工作时间操作（夜间操作：23:00-06:00）
     */
    private Integer checkUnusualTimeOperation(UserSensitiveLog log) {
        if (log.getCreatedAt() == null) {
            return 0;
        }

        int hour = log.getCreatedAt().getHour();
        logger.debug("Factor 8 - Unusual time: userId={}, hour={}", log.getUserId(), hour);
        
        if (hour >= 23 || hour < 6) {
            logger.debug("Factor 8 - Night operation detected: userId={}, hour={}", log.getUserId(), hour);
            // 夜间操作：+5分
            return 5;
        }
        return 0;
    }

    /**
     * 因素9：检查操作频率异常（5分钟内连续多次请求）
     */
    private Integer checkRequestFrequencyAnomaly(UserSensitiveLog log, User user) {
        if (log.getUserId() == null) {
            return 0;
        }

        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
        List<UserSensitiveLog> recentLogs = logRepository.findByUserIdAndCreatedAtAfter(
            log.getUserId(),
            fiveMinutesAgo
        );

        logger.debug("Factor 9 - Request frequency anomaly: userId={}, requests_in_5min={}", 
                    log.getUserId(), recentLogs != null ? recentLogs.size() : 0);

        // 5分钟内超过5次请求：+20分
        if (recentLogs != null && recentLogs.size() > 5) {
            return 20;
        }
        return 0;
    }
}
