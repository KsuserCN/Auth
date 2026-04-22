package cn.ksuser.api.dto;

import java.util.List;

public class QrChallengeStatusResponse {
    private String status;
    private Long expiresInSeconds;
    private String transferCode;
    private String recoveryCode;
    private String mfaChallengeId;
    private String method;
    private List<String> methods;
    private Boolean verified;

    public QrChallengeStatusResponse() {
    }

    public QrChallengeStatusResponse(String status, Long expiresInSeconds) {
        this.status = status;
        this.expiresInSeconds = expiresInSeconds;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(Long expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }

    public String getTransferCode() {
        return transferCode;
    }

    public void setTransferCode(String transferCode) {
        this.transferCode = transferCode;
    }

    public String getRecoveryCode() {
        return recoveryCode;
    }

    public void setRecoveryCode(String recoveryCode) {
        this.recoveryCode = recoveryCode;
    }

    public String getMfaChallengeId() {
        return mfaChallengeId;
    }

    public void setMfaChallengeId(String mfaChallengeId) {
        this.mfaChallengeId = mfaChallengeId;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public List<String> getMethods() {
        return methods;
    }

    public void setMethods(List<String> methods) {
        this.methods = methods;
    }

    public Boolean getVerified() {
        return verified;
    }

    public void setVerified(Boolean verified) {
        this.verified = verified;
    }
}
