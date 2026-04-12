package cn.ksuser.api.dto;

public class SessionTransferCreateRequest {
    private String target;
    private String purpose;

    public SessionTransferCreateRequest() {
    }

    public SessionTransferCreateRequest(String target) {
        this.target = target;
    }

    public SessionTransferCreateRequest(String target, String purpose) {
        this.target = target;
        this.purpose = purpose;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }
}
