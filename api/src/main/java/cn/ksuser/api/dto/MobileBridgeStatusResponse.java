package cn.ksuser.api.dto;

public class MobileBridgeStatusResponse {
    private String status;
    private String transferCode;
    private String returnUrl;
    private String returnOrigin;
    private long expiresInSeconds;

    public MobileBridgeStatusResponse() {
    }

    public MobileBridgeStatusResponse(
            String status,
            String transferCode,
            String returnUrl,
            String returnOrigin,
            long expiresInSeconds) {
        this.status = status;
        this.transferCode = transferCode;
        this.returnUrl = returnUrl;
        this.returnOrigin = returnOrigin;
        this.expiresInSeconds = expiresInSeconds;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTransferCode() {
        return transferCode;
    }

    public void setTransferCode(String transferCode) {
        this.transferCode = transferCode;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    public String getReturnOrigin() {
        return returnOrigin;
    }

    public void setReturnOrigin(String returnOrigin) {
        this.returnOrigin = returnOrigin;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(long expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }
}
