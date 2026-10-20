package cn.ksuser.api.dto;

import java.time.LocalDateTime;
import java.util.List;

public class Oauth2AppResponse {

    private String applicationType;
    private MobileAppPlatforms platforms;
    public String getApplicationType() { return applicationType; }
    public void setApplicationType(String value) { applicationType = value; }
    public MobileAppPlatforms getPlatforms() { return platforms; }
    public void setPlatforms(MobileAppPlatforms value) { platforms = value; }

    private String unionidStatus;
    private String unionidReason;
    private String unionidReviewReason;
    public String getUnionidStatus() { return unionidStatus; }
    public void setUnionidStatus(String value) { unionidStatus = value; }
    public String getUnionidReason() { return unionidReason; }
    public void setUnionidReason(String value) { unionidReason = value; }
    public String getUnionidReviewReason() { return unionidReviewReason; }
    public void setUnionidReviewReason(String value) { unionidReviewReason = value; }
    private String appId;
    private String appName;
    private String logoUrl;
    private String redirectUri;
    private String contactInfo;
    private List<String> scopes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String clientType = "confidential";

    public String getClientType() {
        return clientType;
    }

    public void setClientType(String clientType) {
        this.clientType = clientType;
    }

    public Oauth2AppResponse() {
    }

    public Oauth2AppResponse(String appId, String appName, String logoUrl, String redirectUri, String contactInfo,
                             List<String> scopes, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.appId = appId;
        this.appName = appName;
        this.logoUrl = logoUrl;
        this.redirectUri = redirectUri;
        this.contactInfo = contactInfo;
        this.scopes = scopes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getContactInfo() {
        return contactInfo;
    }

    public void setContactInfo(String contactInfo) {
        this.contactInfo = contactInfo;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public void setScopes(List<String> scopes) {
        this.scopes = scopes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
