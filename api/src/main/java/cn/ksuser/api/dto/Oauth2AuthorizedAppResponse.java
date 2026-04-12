package cn.ksuser.api.dto;

import java.time.LocalDateTime;
import java.util.List;

public class Oauth2AuthorizedAppResponse {
    private String appId;
    private String appName;
    private String logoUrl;
    private String creatorName;
    private String creatorVerificationType;
    private String contactInfo;
    private String redirectUri;
    private List<String> scopes;
    private LocalDateTime authorizedAt;
    private LocalDateTime lastAuthorizedAt;

    public Oauth2AuthorizedAppResponse() {
    }

    public Oauth2AuthorizedAppResponse(String appId, String appName, String logoUrl, String contactInfo,
                                       String redirectUri, List<String> scopes, LocalDateTime authorizedAt,
                                       LocalDateTime lastAuthorizedAt) {
        this.appId = appId;
        this.appName = appName;
        this.logoUrl = logoUrl;
        this.contactInfo = contactInfo;
        this.redirectUri = redirectUri;
        this.scopes = scopes;
        this.authorizedAt = authorizedAt;
        this.lastAuthorizedAt = lastAuthorizedAt;
    }

    public Oauth2AuthorizedAppResponse(String appId, String appName, String logoUrl, String creatorName,
                                       String creatorVerificationType, String contactInfo, String redirectUri,
                                       List<String> scopes, LocalDateTime authorizedAt, LocalDateTime lastAuthorizedAt) {
        this.appId = appId;
        this.appName = appName;
        this.logoUrl = logoUrl;
        this.creatorName = creatorName;
        this.creatorVerificationType = creatorVerificationType;
        this.contactInfo = contactInfo;
        this.redirectUri = redirectUri;
        this.scopes = scopes;
        this.authorizedAt = authorizedAt;
        this.lastAuthorizedAt = lastAuthorizedAt;
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

    public String getCreatorName() {
        return creatorName;
    }

    public void setCreatorName(String creatorName) {
        this.creatorName = creatorName;
    }

    public String getCreatorVerificationType() {
        return creatorVerificationType;
    }

    public void setCreatorVerificationType(String creatorVerificationType) {
        this.creatorVerificationType = creatorVerificationType;
    }

    public String getContactInfo() {
        return contactInfo;
    }

    public void setContactInfo(String contactInfo) {
        this.contactInfo = contactInfo;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public void setScopes(List<String> scopes) {
        this.scopes = scopes;
    }

    public LocalDateTime getAuthorizedAt() {
        return authorizedAt;
    }

    public void setAuthorizedAt(LocalDateTime authorizedAt) {
        this.authorizedAt = authorizedAt;
    }

    public LocalDateTime getLastAuthorizedAt() {
        return lastAuthorizedAt;
    }

    public void setLastAuthorizedAt(LocalDateTime lastAuthorizedAt) {
        this.lastAuthorizedAt = lastAuthorizedAt;
    }
}
