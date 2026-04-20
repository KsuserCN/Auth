package cn.ksuser.api.dto;

public class MobileBridgeApproveRequest {
    private String challengeId;

    public MobileBridgeApproveRequest() {
    }

    public MobileBridgeApproveRequest(String challengeId) {
        this.challengeId = challengeId;
    }

    public String getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(String challengeId) {
        this.challengeId = challengeId;
    }
}
