package cn.ksuser.api.dto;

/**
 * MFA 登录时用于提交 TOTP 或恢复码的请求体
 */
public class MfaTotpVerifyRequest {
    private String challengeId;
    private String code;              // 6位 TOTP 动态码
    private String recoveryCode;      // 8位恢复码（仅字母）

    public MfaTotpVerifyRequest() {}

    public MfaTotpVerifyRequest(String challengeId, String code) {
        this.challengeId = challengeId;
        this.code = code;
    }

    public MfaTotpVerifyRequest(String challengeId, String code, String recoveryCode) {
        this.challengeId = challengeId;
        this.code = code;
        this.recoveryCode = recoveryCode;
    }

    public String getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(String challengeId) {
        this.challengeId = challengeId;
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
