package cn.ksuser.api.dto;

public class QrMfaInitRequest {
    private String mfaChallengeId;

    public QrMfaInitRequest() {
    }

    public QrMfaInitRequest(String mfaChallengeId) {
        this.mfaChallengeId = mfaChallengeId;
    }

    public String getMfaChallengeId() {
        return mfaChallengeId;
    }

    public void setMfaChallengeId(String mfaChallengeId) {
        this.mfaChallengeId = mfaChallengeId;
    }
}
