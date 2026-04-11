package cn.ksuser.api.dto;

import java.util.List;

public class SsoAuthorizeContextResponse {
    private String clientId;
    private String clientName;
    private String logoUrl;
    private String redirectUri;
    private List<String> requestedScopes;

    public SsoAuthorizeContextResponse() {
    }

    public SsoAuthorizeContextResponse(String clientId, String clientName, String logoUrl, String redirectUri,
                                       List<String> requestedScopes) {
        this.clientId = clientId;
        this.clientName = clientName;
        this.logoUrl = logoUrl;
        this.redirectUri = redirectUri;
        this.requestedScopes = requestedScopes;
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
}
