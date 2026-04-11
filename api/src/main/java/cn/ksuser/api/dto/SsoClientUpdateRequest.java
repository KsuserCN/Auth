package cn.ksuser.api.dto;

import java.util.List;

public class SsoClientUpdateRequest {
    private String clientName;
    private List<String> redirectUris;
    private List<String> postLogoutRedirectUris;
    private List<String> scopes;
    private List<String> audiences;
    private Boolean requirePkce;

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public List<String> getRedirectUris() {
        return redirectUris;
    }

    public void setRedirectUris(List<String> redirectUris) {
        this.redirectUris = redirectUris;
    }

    public List<String> getPostLogoutRedirectUris() {
        return postLogoutRedirectUris;
    }

    public void setPostLogoutRedirectUris(List<String> postLogoutRedirectUris) {
        this.postLogoutRedirectUris = postLogoutRedirectUris;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public void setScopes(List<String> scopes) {
        this.scopes = scopes;
    }

    public List<String> getAudiences() {
        return audiences;
    }

    public void setAudiences(List<String> audiences) {
        this.audiences = audiences;
    }

    public Boolean getRequirePkce() {
        return requirePkce;
    }

    public void setRequirePkce(Boolean requirePkce) {
        this.requirePkce = requirePkce;
    }
}
