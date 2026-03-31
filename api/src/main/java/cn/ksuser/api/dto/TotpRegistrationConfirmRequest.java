package cn.ksuser.api.dto;

/**
 * TOTP 注册确认请求
 */
public class TotpRegistrationConfirmRequest {
    private String code;
    private String[] recoveryCodes;

    public TotpRegistrationConfirmRequest() {
    }

    public TotpRegistrationConfirmRequest(String code) {
        this.code = code;
    }

    public TotpRegistrationConfirmRequest(String code, String[] recoveryCodes) {
        this.code = code;
        this.recoveryCodes = recoveryCodes;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String[] getRecoveryCodes() {
        return recoveryCodes;
    }

    public void setRecoveryCodes(String[] recoveryCodes) {
        this.recoveryCodes = recoveryCodes;
    }
}
