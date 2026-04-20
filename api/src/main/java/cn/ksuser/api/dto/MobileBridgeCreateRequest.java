package cn.ksuser.api.dto;

public class MobileBridgeCreateRequest {
    private String returnUrl;
    private String clientNonce;

    public MobileBridgeCreateRequest() {
    }

    public MobileBridgeCreateRequest(String returnUrl, String clientNonce) {
        this.returnUrl = returnUrl;
        this.clientNonce = clientNonce;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    public String getClientNonce() {
        return clientNonce;
    }

    public void setClientNonce(String clientNonce) {
        this.clientNonce = clientNonce;
    }
}
