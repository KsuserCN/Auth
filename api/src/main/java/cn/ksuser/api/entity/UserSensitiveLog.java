package cn.ksuser.api.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_sensitive_logs")
public class UserSensitiveLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "operation_type", length = 50, nullable = false)
    private String operationType;

    @Column(name = "login_method", length = 50)
    private String loginMethod;

    @Column(name = "ip_address", length = 45, nullable = false)
    private String ipAddress;

    @Column(name = "ip_location", length = 255)
    private String ipLocation;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "browser", length = 50)
    private String browser;

    @Column(name = "device_type", length = 50)
    private String deviceType;

    @Column(name = "result", nullable = false)
    @Enumerated(EnumType.STRING)
    private OperationResult result;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(name = "action_taken", length = 50, nullable = false)
    private String actionTaken;

    @Column(name = "triggered_multi_error_lock", nullable = false)
    private Boolean triggeredMultiErrorLock;

    @Column(name = "triggered_rate_limit_lock", nullable = false)
    private Boolean triggeredRateLimitLock;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum OperationResult {
        SUCCESS, FAILURE
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (riskScore == null) {
            riskScore = 0;
        }
        if (actionTaken == null) {
            actionTaken = "ALLOW";
        }
        if (triggeredMultiErrorLock == null) {
            triggeredMultiErrorLock = false;
        }
        if (triggeredRateLimitLock == null) {
            triggeredRateLimitLock = false;
        }
    }

    // Constructors
    public UserSensitiveLog() {
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getLoginMethod() {
        return loginMethod;
    }

    public void setLoginMethod(String loginMethod) {
        this.loginMethod = loginMethod;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getIpLocation() {
        return ipLocation;
    }

    public void setIpLocation(String ipLocation) {
        this.ipLocation = ipLocation;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getBrowser() {
        return browser;
    }

    public void setBrowser(String browser) {
        this.browser = browser;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public OperationResult getResult() {
        return result;
    }

    public void setResult(OperationResult result) {
        this.result = result;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Integer getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(Integer riskScore) {
        this.riskScore = riskScore;
    }

    public String getActionTaken() {
        return actionTaken;
    }

    public void setActionTaken(String actionTaken) {
        this.actionTaken = actionTaken;
    }

    public Boolean getTriggeredMultiErrorLock() {
        return triggeredMultiErrorLock;
    }

    public void setTriggeredMultiErrorLock(Boolean triggeredMultiErrorLock) {
        this.triggeredMultiErrorLock = triggeredMultiErrorLock;
    }

    public Boolean getTriggeredRateLimitLock() {
        return triggeredRateLimitLock;
    }

    public void setTriggeredRateLimitLock(Boolean triggeredRateLimitLock) {
        this.triggeredRateLimitLock = triggeredRateLimitLock;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
        this.durationMs = durationMs;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
