package cn.ksuser.api.dto;

/**
 * MFA 登录时用于提交 TOTP 的请求体
 */
public class MfaTotpVerifyRequest {
    private String challengeId;
    private String code;

    public MfaTotpVerifyRequest() {}

    public MfaTotpVerifyRequest(String challengeId, String code) {
        this.challengeId = challengeId;
        this.code = code;
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
}
