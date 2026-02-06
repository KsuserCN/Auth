package cn.ksuser.api.dto;

/**
 * TOTP 验证请求
 */
public class TotpVerifyRequest {
    private String code;
    private String recoveryCode;

    public TotpVerifyRequest() {
    }

    public TotpVerifyRequest(String code, String recoveryCode) {
        this.code = code;
        this.recoveryCode = recoveryCode;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getRecoveryCode() {
        return recoveryCode;
    }

    public void setRecoveryCode(String recoveryCode) {
        this.recoveryCode = recoveryCode;
    }
}
