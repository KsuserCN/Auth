package cn.ksuser.api.dto;

public class Oauth2AuthorizeApproveResponse {
    private String redirectUrl;

    public Oauth2AuthorizeApproveResponse() {
    }

    public Oauth2AuthorizeApproveResponse(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }
}
