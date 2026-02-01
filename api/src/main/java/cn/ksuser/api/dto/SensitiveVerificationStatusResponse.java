package cn.ksuser.api.dto;

public class SensitiveVerificationStatusResponse {
    private boolean verified;
    private long remainingSeconds;

    public SensitiveVerificationStatusResponse() {
    }

    public SensitiveVerificationStatusResponse(boolean verified, long remainingSeconds) {
        this.verified = verified;
        this.remainingSeconds = remainingSeconds;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public long getRemainingSeconds() {
        return remainingSeconds;
    }

    public void setRemainingSeconds(long remainingSeconds) {
        this.remainingSeconds = remainingSeconds;
    }
}
