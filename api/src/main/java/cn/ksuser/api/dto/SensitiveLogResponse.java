package cn.ksuser.api.dto;

import cn.ksuser.api.entity.UserSensitiveLog;
import java.time.LocalDateTime;

public class SensitiveLogResponse {

    private Long id;
    private String operationType;
    private String loginMethod;
    private String ipAddress;
    private String ipLocation;
    private String browser;
    private String deviceType;
    private String result;
    private String failureReason;
    private Integer riskScore;
    private String actionTaken;
    private Boolean triggeredMultiErrorLock;
    private Boolean triggeredRateLimitLock;
    private Integer durationMs;
    private LocalDateTime createdAt;

    public SensitiveLogResponse() {
    }

    public SensitiveLogResponse(UserSensitiveLog log) {
        this.id = log.getId();
        this.operationType = log.getOperationType();
        this.loginMethod = log.getLoginMethod();
        this.ipAddress = log.getIpAddress();
        this.ipLocation = log.getIpLocation();
        this.browser = log.getBrowser();
        this.deviceType = log.getDeviceType();
        this.result = log.getResult().name();
        this.failureReason = log.getFailureReason();
        this.riskScore = log.getRiskScore();
        this.actionTaken = log.getActionTaken();
        this.triggeredMultiErrorLock = log.getTriggeredMultiErrorLock();
        this.triggeredRateLimitLock = log.getTriggeredRateLimitLock();
        this.durationMs = log.getDurationMs();
        this.createdAt = log.getCreatedAt();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
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
