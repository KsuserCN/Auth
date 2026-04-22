package cn.ksuser.api.dto;

public class SessionTransferExchangeRequest {
    private String transferCode;
    private String target;

    public SessionTransferExchangeRequest() {
    }

    public SessionTransferExchangeRequest(String transferCode, String target) {
        this.transferCode = transferCode;
        this.target = target;
    }

    public String getTransferCode() {
        return transferCode;
    }

    public void setTransferCode(String transferCode) {
        this.transferCode = transferCode;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }
}
