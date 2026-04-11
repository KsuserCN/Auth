package cn.ksuser.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "oidc_clients")
public class OidcClient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", length = 128, nullable = false, unique = true)
    private String clientId;

    @Column(name = "client_secret_hash", length = 255)
    private String clientSecretHash;

    @Column(name = "client_name", length = 120, nullable = false)
    private String clientName;

    @Column(name = "logo_url", length = 255)
    private String logoUrl;

    @Column(name = "client_type", length = 32, nullable = false)
    private String clientType = "confidential";

    @Column(name = "application_type", length = 32, nullable = false)
    private String applicationType = "web";

    @Column(name = "redirect_uris", columnDefinition = "TEXT", nullable = false)
    private String redirectUris;

    @Column(name = "post_logout_redirect_uris", columnDefinition = "TEXT", nullable = false)
    private String postLogoutRedirectUris = "";

    @Column(name = "scopes", length = 255, nullable = false)
    private String scopes = "openid profile email";

    @Column(name = "audiences", length = 255, nullable = false)
    private String audiences = "ksuser-auth";

    @Column(name = "require_pkce", nullable = false)
    private Boolean requirePkce = true;

    @Column(name = "is_first_party", nullable = false)
    private Boolean isFirstParty = true;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (clientType == null) {
            clientType = "confidential";
        }
        if (applicationType == null) {
            applicationType = "web";
        }
        if (postLogoutRedirectUris == null) {
            postLogoutRedirectUris = "";
        }
        if (scopes == null || scopes.isBlank()) {
            scopes = "openid profile email";
        }
        if (audiences == null || audiences.isBlank()) {
            audiences = "ksuser-auth";
        }
        if (requirePkce == null) {
            requirePkce = true;
        }
        if (isFirstParty == null) {
            isFirstParty = true;
        }
        if (isActive == null) {
            isActive = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecretHash() {
        return clientSecretHash;
    }

    public void setClientSecretHash(String clientSecretHash) {
        this.clientSecretHash = clientSecretHash;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getClientType() {
        return clientType;
    }

    public void setClientType(String clientType) {
        this.clientType = clientType;
    }

    public String getApplicationType() {
        return applicationType;
    }

    public void setApplicationType(String applicationType) {
        this.applicationType = applicationType;
    }

    public String getRedirectUris() {
        return redirectUris == null ? "" : redirectUris;
    }

    public void setRedirectUris(String redirectUris) {
        this.redirectUris = redirectUris;
    }

    public String getPostLogoutRedirectUris() {
        return postLogoutRedirectUris == null ? "" : postLogoutRedirectUris;
    }

    public void setPostLogoutRedirectUris(String postLogoutRedirectUris) {
        this.postLogoutRedirectUris = postLogoutRedirectUris;
    }

    public String getScopes() {
        return scopes == null ? "openid profile email" : scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    public String getAudiences() {
        return audiences == null ? "ksuser-auth" : audiences;
    }

    public void setAudiences(String audiences) {
        this.audiences = audiences;
    }

    public Boolean getRequirePkce() {
        return requirePkce;
    }

    public void setRequirePkce(Boolean requirePkce) {
        this.requirePkce = requirePkce;
    }

    public Boolean getIsFirstParty() {
        return isFirstParty;
    }

    public void setIsFirstParty(Boolean isFirstParty) {
        this.isFirstParty = isFirstParty;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
