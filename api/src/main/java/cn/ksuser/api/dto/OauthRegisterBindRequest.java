package cn.ksuser.api.dto;

public class OauthRegisterBindRequest {
    private String openid;
    private String username;
    private String email;
    private String password;

    public OauthRegisterBindRequest() {}

    public String getOpenid() { return openid; }
    public void setOpenid(String openid) { this.openid = openid; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
