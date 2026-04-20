package cn.ksuser.api.dto;

import cn.ksuser.api.service.AuthorizationGrantPolicy;

import java.time.LocalDateTime;
import java.util.List;

public class Oauth2AuthorizeContextResponse {
    private String clientId;
    private String appName;
    private String logoUrl;
    private String contactInfo;
    private String redirectUri;
    private List<String> requestedScopes;
    private boolean alreadyAuthorized;
    private String existingGrantMode;
    private LocalDateTime existingGrantExpiresAt;

    public Oauth2AuthorizeContextResponse() {
    }

    public Oauth2AuthorizeContextResponse(String clientId, String appName, String logoUrl, String contactInfo,
                                          String redirectUri, List<String> requestedScopes,
                                          boolean alreadyAuthorized, String existingGrantMode,
                                          LocalDateTime existingGrantExpiresAt) {
        this.clientId = clientId;
        this.appName = appName;
        this.logoUrl = logoUrl;
        this.contactInfo = contactInfo;
        this.redirectUri = redirectUri;
        this.requestedScopes = requestedScopes;
        this.alreadyAuthorized = alreadyAuthorized;
        this.existingGrantMode = existingGrantMode == null || existingGrantMode.isBlank()
            ? AuthorizationGrantPolicy.MODE_PERSISTENT
            : existingGrantMode;
        this.existingGrantExpiresAt = existingGrantExpiresAt;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
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

    public List<String> getRequestedScopes() {
        return requestedScopes;
    }

    public void setRequestedScopes(List<String> requestedScopes) {
        this.requestedScopes = requestedScopes;
    }

    public boolean isAlreadyAuthorized() {
        return alreadyAuthorized;
    }

    public void setAlreadyAuthorized(boolean alreadyAuthorized) {
        this.alreadyAuthorized = alreadyAuthorized;
    }

    public String getExistingGrantMode() {
        return existingGrantMode == null || existingGrantMode.isBlank()
            ? AuthorizationGrantPolicy.MODE_PERSISTENT
            : existingGrantMode;
    }

    public void setExistingGrantMode(String existingGrantMode) {
        this.existingGrantMode = existingGrantMode;
    }

    public LocalDateTime getExistingGrantExpiresAt() {
        return existingGrantExpiresAt;
    }

    public void setExistingGrantExpiresAt(LocalDateTime existingGrantExpiresAt) {
        this.existingGrantExpiresAt = existingGrantExpiresAt;
    }
}
