package cn.ksuser.api.service;

import cn.ksuser.api.config.ApnsProperties;
import cn.ksuser.api.dto.PushDeviceRequest;
import cn.ksuser.api.entity.UserPushDevice;
import cn.ksuser.api.entity.UserSensitiveLog;
import cn.ksuser.api.entity.UserSession;
import cn.ksuser.api.repository.UserPushDeviceRepository;
import cn.ksuser.api.repository.UserSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
public class PushNotificationService {
    private static final Logger logger = LoggerFactory.getLogger(PushNotificationService.class);
    private final UserPushDeviceRepository devices;
    private final UserSessionRepository sessions;
    private final ApnsClient apns;
    private final ApnsProperties properties;

    public PushNotificationService(UserPushDeviceRepository devices, UserSessionRepository sessions,
                                   ApnsClient apns, ApnsProperties properties) {
        this.devices = devices; this.sessions = sessions; this.apns = apns; this.properties = properties;
    }

    @Transactional
    public void register(UserSession session, PushDeviceRequest request) {
        String token = request.deviceToken().toLowerCase(java.util.Locale.ROOT);
        var existing = devices.findByDeviceTokenAndEnvironment(token, request.environment());
        var previous = devices.findBySessionId(session.getId());
        if (previous.isPresent() && (existing.isEmpty() || !previous.get().getId().equals(existing.get().getId()))) {
            devices.delete(previous.get()); devices.flush();
        }
        var device = existing.orElseGet(UserPushDevice::new);
        // A token belongs to this installation's latest login, including after account switching.
        device.setSession(session); device.setDeviceToken(token);
        device.setEnvironment(request.environment()); device.setMode(request.mode());
        device.setUpdatedAt(LocalDateTime.now());
        devices.save(device);
    }

    @Transactional
    public void unregister(Long sessionId) { devices.deleteBySessionId(sessionId); }

    public boolean isAbnormal(UserSensitiveLog log) {
        return log.getResult() == UserSensitiveLog.OperationResult.FAILURE
            || Boolean.TRUE.equals(log.getTriggeredMultiErrorLock()) || Boolean.TRUE.equals(log.getTriggeredRateLimitLock())
            || (log.getRiskScore() != null && log.getRiskScore() >= properties.getAbnormalRiskThreshold())
            || (log.getActionTaken() != null && !"ALLOW".equalsIgnoreCase(log.getActionTaken()));
    }

    public boolean shouldNotify(UserPushDevice.Mode mode, UserSensitiveLog log) {
        String operation = log.getOperationType();
        if (mode == null || mode == UserPushDevice.Mode.OFF || log.getUserId() == null || operation == null) return false;
        boolean abnormal = isAbnormal(log);
        // Successful verification and policy housekeeping are intermediate events; failures still matter.
        if (!abnormal && (operation.endsWith("_MFA") || operation.startsWith("ADAPTIVE_") || "SENSITIVE_VERIFY".equals(operation))) return false;
        return mode == UserPushDevice.Mode.ALL || "LOGIN".equals(operation) || abnormal;
    }

    @Async
    public void notifySensitiveOperation(UserSensitiveLog log) {
        if (!properties.isEnabled() || log.getUserId() == null) return;
        try {
            for (var device : devices.findActiveDevices(log.getUserId(), LocalDateTime.now())) {
                if (!shouldNotify(device.getMode(), log)) continue;
                try {
                    // Check again immediately before delivery: logout/revocation can happen while a task is queued.
                    Optional<UserSession> active = sessions.findActiveSessionById(device.getSession().getId(), LocalDateTime.now());
                    if (active.isEmpty() || !log.getUserId().equals(active.get().getUser().getId())) continue;
                    var current = devices.findById(device.getId()).orElse(null);
                    if (current == null || !device.getVersion().equals(current.getVersion()) || !shouldNotify(current.getMode(), log)) continue;
                    var delivery = apns.send(device, isAbnormal(log) ? "账号安全提醒" : "账号操作提醒", notificationBody(log), String.valueOf(log.getId()));
                    if (delivery.invalidToken()) devices.deleteInvalidDevice(device.getId(), device.getVersion());
                    else if (!delivery.accepted()) logger.warn("APNs delivery rejected: deviceId={}, reason={}", device.getId(), delivery.reason());
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                catch (Exception e) { logger.warn("APNs delivery failed: deviceId={}, errorType={}", device.getId(), e.getClass().getSimpleName()); }
            }
        } catch (Exception e) { logger.warn("Unable to prepare APNs notifications: errorType={}", e.getClass().getSimpleName()); }
    }

    static String notificationBody(UserSensitiveLog log) {
        String operation = Map.ofEntries(
            Map.entry("LOGIN", "登录"), Map.entry("CHANGE_PASSWORD", "修改密码"), Map.entry("CHANGE_EMAIL", "修改邮箱"),
            Map.entry("ADD_PASSKEY", "添加 Passkey"), Map.entry("DELETE_PASSKEY", "删除 Passkey"),
            Map.entry("ENABLE_TOTP", "启用验证器"), Map.entry("DISABLE_TOTP", "停用验证器"),
            Map.entry("DELETE_ACCOUNT", "注销账号"), Map.entry("SENSITIVE_VERIFY", "敏感操作验证")
        ).getOrDefault(log.getOperationType(), "账号安全操作");
        String result = log.getResult() == UserSensitiveLog.OperationResult.FAILURE ? "未成功" : "已发生";
        return operation + result + "。请打开 Ksuser 安全查看操作日志；如非本人操作，请及时检查设备会话。";
    }

    @Scheduled(fixedDelayString = "${app.apns.cleanup-delay-ms:3600000}")
    public void cleanInactiveDevices() {
        try { devices.deleteInactiveDevices(LocalDateTime.now()); }
        catch (Exception e) { logger.warn("Unable to clean inactive push devices: errorType={}", e.getClass().getSimpleName()); }
    }
}
