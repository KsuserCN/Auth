package cn.ksuser.api.dto;

import java.util.List;

public class Oauth2AppCreateRequest {

    private String applicationType;
    private MobileAppPlatforms platforms;
    public String getApplicationType() { return applicationType; }
    public void setApplicationType(String value) { applicationType = value; }
    public MobileAppPlatforms getPlatforms() { return platforms; }
    public void setPlatforms(MobileAppPlatforms value) { platforms = value; }
    private String appName;
    private String redirectUri;
    private String contactInfo;
    private List<String> scopes;
    private String clientType;

    public String getClientType() {
        return clientType;
    }

    public void setClientType(String clientType) {
        this.clientType = clientType;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
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
}
