package cn.ksuser.api.dto;

public class OauthBindRequest {
    private String openid;
    private String email;
    private String password;

    public OauthBindRequest() {}

    public String getOpenid() { return openid; }
    public void setOpenid(String openid) { this.openid = openid; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
