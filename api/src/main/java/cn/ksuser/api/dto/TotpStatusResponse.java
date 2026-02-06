package cn.ksuser.api.dto;

/**
 * TOTP 状态响应
 */
public class TotpStatusResponse {
    private boolean enabled;
    private long recoveryCodesCount;

    public TotpStatusResponse() {
    }

    public TotpStatusResponse(boolean enabled, long recoveryCodesCount) {
        this.enabled = enabled;
        this.recoveryCodesCount = recoveryCodesCount;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getRecoveryCodesCount() {
        return recoveryCodesCount;
    }

    public void setRecoveryCodesCount(long recoveryCodesCount) {
        this.recoveryCodesCount = recoveryCodesCount;
    }
}
