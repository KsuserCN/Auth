package cn.ksuser.api.dto;

/**
 * TOTP 注册确认请求
 */
public class TotpRegistrationConfirmRequest {
    private String code;

    public TotpRegistrationConfirmRequest() {
    }

    public TotpRegistrationConfirmRequest(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
