package cn.ksuser.api.dto;

import java.util.ArrayList;
import java.util.List;

public class AdaptiveAuthStatusResponse {
    private Long sessionId;
    private int riskScore;
    private String riskLevel;
    private String policyDecision;
    private String policyVersion;
    private boolean trusted;
    private boolean requiresStepUp;
    private boolean sessionFrozen;
    private boolean sensitiveVerified;
    private long sensitiveVerificationRemainingSeconds;
    private long authAgeSeconds;
    private long idleSeconds;
    private String currentIp;
    private String currentLocation;
    private String sessionIp;
    private String sessionLocation;
    private String browser;
    private String deviceType;
    private boolean multiEndpointAlert;
    private String alertLevel;
    private String alertTitle;
    private String alertMessage;
    private long alertRemainingSeconds;
    private String recommendedAction;
    private List<String> reasons = new ArrayList<>();

    public AdaptiveAuthStatusResponse() {
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(int riskScore) {
        this.riskScore = riskScore;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getPolicyDecision() {
        return policyDecision;
    }

    public void setPolicyDecision(String policyDecision) {
        this.policyDecision = policyDecision;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public void setPolicyVersion(String policyVersion) {
        this.policyVersion = policyVersion;
    }

    public boolean isTrusted() {
        return trusted;
    }

    public void setTrusted(boolean trusted) {
        this.trusted = trusted;
    }

    public boolean isRequiresStepUp() {
        return requiresStepUp;
    }

    public void setRequiresStepUp(boolean requiresStepUp) {
        this.requiresStepUp = requiresStepUp;
    }

    public boolean isSessionFrozen() {
        return sessionFrozen;
    }

    public void setSessionFrozen(boolean sessionFrozen) {
        this.sessionFrozen = sessionFrozen;
    }

    public boolean isSensitiveVerified() {
        return sensitiveVerified;
    }

    public void setSensitiveVerified(boolean sensitiveVerified) {
        this.sensitiveVerified = sensitiveVerified;
    }

    public long getSensitiveVerificationRemainingSeconds() {
        return sensitiveVerificationRemainingSeconds;
    }

    public void setSensitiveVerificationRemainingSeconds(long sensitiveVerificationRemainingSeconds) {
        this.sensitiveVerificationRemainingSeconds = sensitiveVerificationRemainingSeconds;
    }

    public long getAuthAgeSeconds() {
        return authAgeSeconds;
    }

    public void setAuthAgeSeconds(long authAgeSeconds) {
        this.authAgeSeconds = authAgeSeconds;
    }

    public long getIdleSeconds() {
        return idleSeconds;
    }

    public void setIdleSeconds(long idleSeconds) {
        this.idleSeconds = idleSeconds;
    }

    public String getCurrentIp() {
        return currentIp;
    }

    public void setCurrentIp(String currentIp) {
        this.currentIp = currentIp;
    }

    public String getCurrentLocation() {
        return currentLocation;
    }

    public void setCurrentLocation(String currentLocation) {
        this.currentLocation = currentLocation;
    }

    public String getSessionIp() {
        return sessionIp;
    }

    public void setSessionIp(String sessionIp) {
        this.sessionIp = sessionIp;
    }

    public String getSessionLocation() {
        return sessionLocation;
    }

    public void setSessionLocation(String sessionLocation) {
        this.sessionLocation = sessionLocation;
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

    public boolean isMultiEndpointAlert() {
        return multiEndpointAlert;
    }

    public void setMultiEndpointAlert(boolean multiEndpointAlert) {
        this.multiEndpointAlert = multiEndpointAlert;
    }

    public String getAlertLevel() {
        return alertLevel;
    }

    public void setAlertLevel(String alertLevel) {
        this.alertLevel = alertLevel;
    }

    public String getAlertTitle() {
        return alertTitle;
    }

    public void setAlertTitle(String alertTitle) {
        this.alertTitle = alertTitle;
    }

    public String getAlertMessage() {
        return alertMessage;
    }

    public void setAlertMessage(String alertMessage) {
        this.alertMessage = alertMessage;
    }

    public long getAlertRemainingSeconds() {
        return alertRemainingSeconds;
    }

    public void setAlertRemainingSeconds(long alertRemainingSeconds) {
        this.alertRemainingSeconds = alertRemainingSeconds;
    }

    public String getRecommendedAction() {
        return recommendedAction;
    }

    public void setRecommendedAction(String recommendedAction) {
        this.recommendedAction = recommendedAction;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public void setReasons(List<String> reasons) {
        this.reasons = reasons;
    }
}
