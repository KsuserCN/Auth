package cn.ksuser.api.dto;

public class MobileBridgeCancelRequest {
    private String challengeId;

    public MobileBridgeCancelRequest() {
    }

    public MobileBridgeCancelRequest(String challengeId) {
        this.challengeId = challengeId;
    }

    public String getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(String challengeId) {
        this.challengeId = challengeId;
    }
}
