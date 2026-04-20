package cn.ksuser.api.dto;

import cn.ksuser.api.service.AuthorizationGrantPolicy;

import java.time.LocalDateTime;
import java.util.List;

public class SsoAuthorizeContextResponse {
    private String clientId;
    private String clientName;
    private String logoUrl;
    private String redirectUri;
    private List<String> requestedScopes;
    private boolean alreadyAuthorized;
    private String existingGrantMode;
    private LocalDateTime existingGrantExpiresAt;

    public SsoAuthorizeContextResponse() {
    }

    public SsoAuthorizeContextResponse(String clientId, String clientName, String logoUrl, String redirectUri,
                                       List<String> requestedScopes, boolean alreadyAuthorized,
                                       String existingGrantMode, LocalDateTime existingGrantExpiresAt) {
        this.clientId = clientId;
        this.clientName = clientName;
        this.logoUrl = logoUrl;
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

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
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
