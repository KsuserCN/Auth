package cn.ksuser.api.dto;

public class SessionTransferCreateRequest {
    private String target;

    public SessionTransferCreateRequest() {
    }

    public SessionTransferCreateRequest(String target) {
        this.target = target;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }
}
