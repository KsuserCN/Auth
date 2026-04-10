package cn.ksuser.api.dto;

import java.util.List;

public class Oauth2AuthorizeContextResponse {
    private String clientId;
    private String appName;
    private String contactInfo;
    private String redirectUri;
    private List<String> requestedScopes;

    public Oauth2AuthorizeContextResponse() {
    }

    public Oauth2AuthorizeContextResponse(String clientId, String appName, String contactInfo,
                                          String redirectUri, List<String> requestedScopes) {
        this.clientId = clientId;
        this.appName = appName;
        this.contactInfo = contactInfo;
        this.redirectUri = redirectUri;
        this.requestedScopes = requestedScopes;
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
}
