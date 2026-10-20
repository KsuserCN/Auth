package cn.ksuser.api.dto;

public class Oauth2AppUpdateRequest {

    private String applicationType;
    private MobileAppPlatforms platforms;
    public String getApplicationType() { return applicationType; }
    public void setApplicationType(String value) { applicationType = value; }
    public MobileAppPlatforms getPlatforms() { return platforms; }
    public void setPlatforms(MobileAppPlatforms value) { platforms = value; }
    private String appName;
    private String redirectUri;
    private String contactInfo;

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
}
