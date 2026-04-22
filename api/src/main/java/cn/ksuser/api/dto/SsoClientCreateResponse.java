package cn.ksuser.api.dto;

import java.time.LocalDateTime;
import java.util.List;

public class SsoClientCreateResponse extends SsoClientResponse {
    private String clientSecret;

    public SsoClientCreateResponse() {
    }

    public SsoClientCreateResponse(String clientId, String clientName, String logoUrl, List<String> redirectUris,
                                   List<String> postLogoutRedirectUris, List<String> scopes,
                                   List<String> audiences, boolean requirePkce,
                                   LocalDateTime createdAt, LocalDateTime updatedAt,
                                   String clientSecret) {
        super(clientId, clientName, logoUrl, redirectUris, postLogoutRedirectUris, scopes, audiences,
            requirePkce, createdAt, updatedAt);
        this.clientSecret = clientSecret;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }
}
