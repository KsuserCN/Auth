package cn.ksuser.api.dto;

import java.util.List;

public class MfaChallengeResponse {
    private String challengeId;
    private String method; // e.g., "totp"
    private List<String> methods; // e.g., ["totp", "passkey"]

    public MfaChallengeResponse() {}

    public MfaChallengeResponse(String challengeId, String method) {
        this.challengeId = challengeId;
        this.method = method;
        this.methods = method == null ? List.of() : List.of(method);
    }

    public MfaChallengeResponse(String challengeId, String method, List<String> methods) {
        this.challengeId = challengeId;
        this.method = method;
        this.methods = methods;
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

    public List<String> getMethods() {
        return methods;
    }

    public void setMethods(List<String> methods) {
        this.methods = methods;
    }
}
