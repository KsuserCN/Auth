package cn.ksuser.api.dto;

public class MobileBridgeApproveResponse {
    private String challengeId;
    private String returnUrl;
    private String returnOrigin;
    private long expiresInSeconds;

    public MobileBridgeApproveResponse() {
    }

    public MobileBridgeApproveResponse(String challengeId, String returnUrl, String returnOrigin, long expiresInSeconds) {
        this.challengeId = challengeId;
        this.returnUrl = returnUrl;
        this.returnOrigin = returnOrigin;
        this.expiresInSeconds = expiresInSeconds;
    }

    public String getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(String challengeId) {
        this.challengeId = challengeId;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    public String getReturnOrigin() {
        return returnOrigin;
    }

    public void setReturnOrigin(String returnOrigin) {
        this.returnOrigin = returnOrigin;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(long expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }
}
