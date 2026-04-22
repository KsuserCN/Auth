package cn.ksuser.api.dto;

import cn.ksuser.api.entity.UserSession;

import java.time.LocalDateTime;

public class SessionInfoResponse {

    private Long id;
    private String ipAddress;
    private String ipLocation;
    private String userAgent;
    private String browser;
    private String deviceType;
    private LocalDateTime createdAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private Boolean online;
    private Boolean current;

    public SessionInfoResponse() {
    }

    public SessionInfoResponse(UserSession session, boolean online, boolean current) {
        this.id = session.getId();
        this.ipAddress = session.getIpAddress();
        this.ipLocation = session.getIpLocation();
        this.userAgent = session.getUserAgent();
        this.browser = session.getBrowser();
        this.deviceType = session.getDeviceType();
        this.createdAt = session.getCreatedAt();
        this.lastSeenAt = session.getLastSeenAt();
        this.expiresAt = session.getExpiresAt();
        this.revokedAt = session.getRevokedAt();
        this.online = online;
        this.current = current;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(LocalDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public Boolean getOnline() {
        return online;
    }

    public void setOnline(Boolean online) {
        this.online = online;
    }

    public Boolean getCurrent() {
        return current;
    }

    public void setCurrent(Boolean current) {
        this.current = current;
    }
}
