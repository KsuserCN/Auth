package cn.ksuser.api.dto;

public class MfaChallengeResponse {
    private String challengeId;
    private String method; // e.g., "totp"

    public MfaChallengeResponse() {}

    public MfaChallengeResponse(String challengeId, String method) {
        this.challengeId = challengeId;
        this.method = method;
    }

    public String getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(String challengeId) {
        this.challengeId = challengeId;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }
}
