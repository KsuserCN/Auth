package cn.ksuser.api.util;

import cn.ksuser.api.entity.UserSensitiveLog;
import cn.ksuser.api.service.SensitiveLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SensitiveLogUtil {

    @Autowired
    private SensitiveLogService sensitiveLogService;

    /**
     * 记录敏感操作日志（异步）
     */
    public void log(HttpServletRequest request, Long userId, String operationType, 
                   String loginMethod, UserSensitiveLog.OperationResult result, 
                   String failureReason, Long startTimeMs) {
        UserSensitiveLog log = createLog(request, userId, operationType, loginMethod, 
                                        result, failureReason, startTimeMs);
        sensitiveLogService.logAsync(log);
    }

    /**
     * 记录敏感操作日志（同步）
     */
    public void logSync(HttpServletRequest request, Long userId, String operationType, 
                       String loginMethod, UserSensitiveLog.OperationResult result, 
                       String failureReason, Long startTimeMs) {
        UserSensitiveLog log = createLog(request, userId, operationType, loginMethod, 
                                        result, failureReason, startTimeMs);
        sensitiveLogService.logSync(log);
    }

    /**
     * 创建日志对象
     */
    private UserSensitiveLog createLog(HttpServletRequest request, Long userId, 
                                      String operationType, String loginMethod,
                                      UserSensitiveLog.OperationResult result, 
                                      String failureReason, Long startTimeMs) {
        UserSensitiveLog log = new UserSensitiveLog();
        log.setUserId(userId);
        log.setOperationType(operationType);
        log.setLoginMethod(loginMethod);
        log.setResult(result);
        log.setFailureReason(failureReason);

        // 获取IP和User-Agent
        String ip = IpUtil.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        log.setIpAddress(ip != null ? ip : "unknown");
        log.setUserAgent(userAgent);

        // 计算耗时
        if (startTimeMs != null) {
            long duration = System.currentTimeMillis() - startTimeMs;
            log.setDurationMs((int) duration);
        }

        return log;
    }

    /**
     * 记录注册操作
     */
    public void logRegister(HttpServletRequest request, Long userId, boolean success, 
                           String failureReason, Long startTimeMs) {
        log(request, userId, "REGISTER", null, 
            success ? UserSensitiveLog.OperationResult.SUCCESS : UserSensitiveLog.OperationResult.FAILURE,
            failureReason, startTimeMs);
    }

    /**
     * 记录登录操作
     */
    public void logLogin(HttpServletRequest request, Long userId, String loginMethod, 
                        boolean success, String failureReason, Long startTimeMs) {
        log(request, userId, "LOGIN", loginMethod,
            success ? UserSensitiveLog.OperationResult.SUCCESS : UserSensitiveLog.OperationResult.FAILURE,
            failureReason, startTimeMs);
    }

    /**
     * 记录敏感操作认证
     */
    public void logSensitiveVerify(HttpServletRequest request, Long userId, boolean success, 
                                  String failureReason, Long startTimeMs) {
        log(request, userId, "SENSITIVE_VERIFY", null,
            success ? UserSensitiveLog.OperationResult.SUCCESS : UserSensitiveLog.OperationResult.FAILURE,
            failureReason, startTimeMs);
    }

    /**
     * 记录修改密码操作
     */
    public void logChangePassword(HttpServletRequest request, Long userId, boolean success, 
                                 String failureReason, Long startTimeMs) {
        log(request, userId, "CHANGE_PASSWORD", null,
            success ? UserSensitiveLog.OperationResult.SUCCESS : UserSensitiveLog.OperationResult.FAILURE,
            failureReason, startTimeMs);
    }

    /**
     * 记录修改邮箱操作
     */
    public void logChangeEmail(HttpServletRequest request, Long userId, boolean success, 
                              String failureReason, Long startTimeMs) {
        log(request, userId, "CHANGE_EMAIL", null,
            success ? UserSensitiveLog.OperationResult.SUCCESS : UserSensitiveLog.OperationResult.FAILURE,
            failureReason, startTimeMs);
    }

    /**
     * 记录新增Passkey操作
     */
    public void logAddPasskey(HttpServletRequest request, Long userId, boolean success, 
                             String failureReason, Long startTimeMs) {
        log(request, userId, "ADD_PASSKEY", null,
            success ? UserSensitiveLog.OperationResult.SUCCESS : UserSensitiveLog.OperationResult.FAILURE,
            failureReason, startTimeMs);
    }

    /**
     * 记录删除Passkey操作
     */
    public void logDeletePasskey(HttpServletRequest request, Long userId, boolean success, 
                                String failureReason, Long startTimeMs) {
        log(request, userId, "DELETE_PASSKEY", null,
            success ? UserSensitiveLog.OperationResult.SUCCESS : UserSensitiveLog.OperationResult.FAILURE,
            failureReason, startTimeMs);
    }

    /**
     * 记录启用TOTP操作
     */
    public void logEnableTotp(HttpServletRequest request, Long userId, boolean success, 
                             String failureReason, Long startTimeMs) {
        log(request, userId, "ENABLE_TOTP", null,
            success ? UserSensitiveLog.OperationResult.SUCCESS : UserSensitiveLog.OperationResult.FAILURE,
            failureReason, startTimeMs);
    }

    /**
     * 记录禁用TOTP操作
     */
    public void logDisableTotp(HttpServletRequest request, Long userId, boolean success, 
                              String failureReason, Long startTimeMs) {
        log(request, userId, "DISABLE_TOTP", null,
            success ? UserSensitiveLog.OperationResult.SUCCESS : UserSensitiveLog.OperationResult.FAILURE,
            failureReason, startTimeMs);
    }
}
