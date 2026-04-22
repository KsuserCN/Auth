package cn.ksuser.api.dto;

public class SessionTransferResponse {
    private String transferCode;
    private long expiresInSeconds;

    public SessionTransferResponse() {
    }

    public SessionTransferResponse(String transferCode, long expiresInSeconds) {
        this.transferCode = transferCode;
        this.expiresInSeconds = expiresInSeconds;
    }

    public String getTransferCode() {
        return transferCode;
    }

    public void setTransferCode(String transferCode) {
        this.transferCode = transferCode;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(long expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }
}
