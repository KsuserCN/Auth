package cn.ksuser.api.dto;

public class OauthBindRequest {
    private String provider;
    private String bindToken;
    private String openid;
    private String email;
    private String password;

    public OauthBindRequest() {}

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getBindToken() { return bindToken; }
    public void setBindToken(String bindToken) { this.bindToken = bindToken; }

    public String getOpenid() { return openid; }
    public void setOpenid(String openid) { this.openid = openid; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
