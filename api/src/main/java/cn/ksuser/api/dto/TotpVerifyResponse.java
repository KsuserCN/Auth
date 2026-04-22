package cn.ksuser.api.dto;

/**
 * TOTP 验证响应
 */
public class TotpVerifyResponse {
    private boolean success;
    private String message;

    public TotpVerifyResponse() {
    }

    public TotpVerifyResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
