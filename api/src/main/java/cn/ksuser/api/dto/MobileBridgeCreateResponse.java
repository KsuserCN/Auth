package cn.ksuser.api.dto;

public class MobileBridgeCreateResponse {
    private String challengeId;
    private String appLink;
    private long expiresInSeconds;

    public MobileBridgeCreateResponse() {
    }

    public MobileBridgeCreateResponse(String challengeId, String appLink, long expiresInSeconds) {
        this.challengeId = challengeId;
        this.appLink = appLink;
        this.expiresInSeconds = expiresInSeconds;
    }

    public String getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(String challengeId) {
        this.challengeId = challengeId;
    }

    public String getAppLink() {
        return appLink;
    }

    public void setAppLink(String appLink) {
        this.appLink = appLink;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(long expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }
}
