package cn.ksuser.api.dto;

import java.time.LocalDateTime;
import java.util.List;

public class Oauth2AppCreateResponse extends Oauth2AppResponse {
    private String appSecret;

    public Oauth2AppCreateResponse() {
    }

    public Oauth2AppCreateResponse(String appId, String appName, String redirectUri, String contactInfo,
                                   List<String> scopes, LocalDateTime createdAt, LocalDateTime updatedAt,
                                   String appSecret) {
        super(appId, appName, redirectUri, contactInfo, scopes, createdAt, updatedAt);
        this.appSecret = appSecret;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }
}
