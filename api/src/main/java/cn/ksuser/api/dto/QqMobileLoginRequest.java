package cn.ksuser.api.dto;

public class QqMobileLoginRequest {
    private String appId;
    private String accessToken;
    private String openid;
    private String unionid;
    private String expiresIn;

    public QqMobileLoginRequest() {}

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getOpenid() { return openid; }
    public void setOpenid(String openid) { this.openid = openid; }

    public String getUnionid() { return unionid; }
    public void setUnionid(String unionid) { this.unionid = unionid; }

    public String getExpiresIn() { return expiresIn; }
    public void setExpiresIn(String expiresIn) { this.expiresIn = expiresIn; }
}
