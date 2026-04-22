package cn.ksuser.api.dto;

/**
 * TOTP 禁用请求
 */
public class TotpDisableRequest {
    private String password;

    public TotpDisableRequest() {
    }

    public TotpDisableRequest(String password) {
        this.password = password;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
