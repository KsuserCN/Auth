package cn.ksuser.api.dto;

/**
 * TOTP 注册选项响应
 * 返回秘钥和二维码
 */
public class TotpRegistrationOptionsResponse {
    private String secret;
    private String qrCodeUrl;
    private String[] recoveryCodes;

    public TotpRegistrationOptionsResponse() {
    }

    public TotpRegistrationOptionsResponse(String secret, String qrCodeUrl, String[] recoveryCodes) {
        this.secret = secret;
        this.qrCodeUrl = qrCodeUrl;
        this.recoveryCodes = recoveryCodes;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getQrCodeUrl() {
        return qrCodeUrl;
    }

    public void setQrCodeUrl(String qrCodeUrl) {
        this.qrCodeUrl = qrCodeUrl;
    }

    public String[] getRecoveryCodes() {
        return recoveryCodes;
    }

    public void setRecoveryCodes(String[] recoveryCodes) {
        this.recoveryCodes = recoveryCodes;
    }
}
