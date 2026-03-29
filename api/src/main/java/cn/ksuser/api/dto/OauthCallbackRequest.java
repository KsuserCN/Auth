package cn.ksuser.api.dto;

public class OauthCallbackRequest {
    private String code;
    private String redirectUri;
    private String state;
    private String codeVerifier;

    public OauthCallbackRequest() {}

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getCodeVerifier() { return codeVerifier; }
    public void setCodeVerifier(String codeVerifier) { this.codeVerifier = codeVerifier; }
}
    