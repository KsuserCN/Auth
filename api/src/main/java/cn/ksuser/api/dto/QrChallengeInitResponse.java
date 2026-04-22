package cn.ksuser.api.dto;

public class QrChallengeInitResponse {
    private String challengeId;
    private String pollToken;
    private String qrText;
    private long expiresInSeconds;

    public QrChallengeInitResponse() {
    }

    public QrChallengeInitResponse(String challengeId, String pollToken, String qrText, long expiresInSeconds) {
        this.challengeId = challengeId;
        this.pollToken = pollToken;
        this.qrText = qrText;
        this.expiresInSeconds = expiresInSeconds;
    }

    public String getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(String challengeId) {
        this.challengeId = challengeId;
    }

    public String getPollToken() {
        return pollToken;
    }

    public void setPollToken(String pollToken) {
        this.pollToken = pollToken;
    }

    public String getQrText() {
        return qrText;
    }

    public void setQrText(String qrText) {
        this.qrText = qrText;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(long expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }
}
