package cn.ksuser.api.dto;

public class Oauth2AuthorizeRequest {
    private String clientId;
    private String redirectUri;
    private String responseType;
    private String scope;
    private String state;
    private String grantMode;
    private Integer grantTtlSeconds;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getResponseType() {
        return responseType;
    }

    public void setResponseType(String responseType) {
        this.responseType = responseType;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getGrantMode() {
        return grantMode;
    }

    public void setGrantMode(String grantMode) {
        this.grantMode = grantMode;
    }

    public Integer getGrantTtlSeconds() {
        return grantTtlSeconds;
    }

    public void setGrantTtlSeconds(Integer grantTtlSeconds) {
        this.grantTtlSeconds = grantTtlSeconds;
    }
}
