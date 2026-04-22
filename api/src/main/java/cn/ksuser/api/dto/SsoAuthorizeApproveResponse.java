package cn.ksuser.api.dto;

public class SsoAuthorizeApproveResponse {
    private String redirectUrl;

    public SsoAuthorizeApproveResponse() {
    }

    public SsoAuthorizeApproveResponse(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }
}
