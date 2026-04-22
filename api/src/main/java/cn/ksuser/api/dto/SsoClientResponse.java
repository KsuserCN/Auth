package cn.ksuser.api.dto;

import java.time.LocalDateTime;
import java.util.List;

public class SsoClientResponse {
    private String clientId;
    private String clientName;
    private String logoUrl;
    private List<String> redirectUris;
    private List<String> postLogoutRedirectUris;
    private List<String> scopes;
    private List<String> audiences;
    private boolean requirePkce;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public SsoClientResponse() {
    }

    public SsoClientResponse(String clientId, String clientName, String logoUrl, List<String> redirectUris,
                             List<String> postLogoutRedirectUris, List<String> scopes,
                             List<String> audiences, boolean requirePkce,
                             LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.clientId = clientId;
        this.clientName = clientName;
        this.logoUrl = logoUrl;
        this.redirectUris = redirectUris;
        this.postLogoutRedirectUris = postLogoutRedirectUris;
        this.scopes = scopes;
        this.audiences = audiences;
        this.requirePkce = requirePkce;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public List<String> getRedirectUris() {
        return redirectUris;
    }

    public void setRedirectUris(List<String> redirectUris) {
        this.redirectUris = redirectUris;
    }

    public List<String> getPostLogoutRedirectUris() {
        return postLogoutRedirectUris;
    }

    public void setPostLogoutRedirectUris(List<String> postLogoutRedirectUris) {
        this.postLogoutRedirectUris = postLogoutRedirectUris;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public void setScopes(List<String> scopes) {
        this.scopes = scopes;
    }

    public List<String> getAudiences() {
        return audiences;
    }

    public void setAudiences(List<String> audiences) {
        this.audiences = audiences;
    }

    public boolean isRequirePkce() {
        return requirePkce;
    }

    public void setRequirePkce(boolean requirePkce) {
        this.requirePkce = requirePkce;
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
