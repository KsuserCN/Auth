package cn.ksuser.api.dto;

import java.util.List;

public class SensitiveVerificationStatusResponse {
    private boolean verified;
    private long remainingSeconds;
    private String preferredMethod;
    private List<String> methods;

    public SensitiveVerificationStatusResponse() {
    }

    public SensitiveVerificationStatusResponse(boolean verified, long remainingSeconds) {
        this.verified = verified;
        this.remainingSeconds = remainingSeconds;
        this.preferredMethod = "password";
        this.methods = List.of("password");
    }

    public SensitiveVerificationStatusResponse(boolean verified, long remainingSeconds,
                                               String preferredMethod, List<String> methods) {
        this.verified = verified;
        this.remainingSeconds = remainingSeconds;
        this.preferredMethod = preferredMethod;
        this.methods = methods;
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

    public String getPreferredMethod() {
        return preferredMethod;
    }

    public void setPreferredMethod(String preferredMethod) {
        this.preferredMethod = preferredMethod;
    }

    public List<String> getMethods() {
        return methods;
    }

    public void setMethods(List<String> methods) {
        this.methods = methods;
    }
}
