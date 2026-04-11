package cn.ksuser.api.dto;

import java.time.LocalDateTime;
import java.util.List;

public class SsoAuthorizedClientResponse {
    private String clientId;
    private String clientName;
    private String logoUrl;
    private String redirectUri;
    private List<String> scopes;
    private LocalDateTime authorizedAt;
    private LocalDateTime lastAuthorizedAt;

    public SsoAuthorizedClientResponse() {
    }

    public SsoAuthorizedClientResponse(String clientId, String clientName, String logoUrl, String redirectUri,
                                       List<String> scopes, LocalDateTime authorizedAt,
                                       LocalDateTime lastAuthorizedAt) {
        this.clientId = clientId;
        this.clientName = clientName;
        this.logoUrl = logoUrl;
        this.redirectUri = redirectUri;
        this.scopes = scopes;
        this.authorizedAt = authorizedAt;
        this.lastAuthorizedAt = lastAuthorizedAt;
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
