package cn.ksuser.api.entity;

import cn.ksuser.api.service.AuthorizationGrantPolicy;
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
@Table(name = "user_oauth2_authorizations")
public class UserOauth2Authorization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "app_id", length = 64, nullable = false)
    private String appId;

    @Column(name = "app_name", length = 100, nullable = false)
    private String appName;

    @Column(name = "logo_url", length = 255)
    private String logoUrl;

    @Column(name = "contact_info", length = 120, nullable = false)
    private String contactInfo;

    @Column(name = "redirect_uri", length = 500, nullable = false)
    private String redirectUri;

    @Column(name = "scopes", length = 255, nullable = false)
    private String scopes = "";

    @Column(name = "grant_mode", length = 32, nullable = false)
    private String grantMode = AuthorizationGrantPolicy.MODE_PERSISTENT;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "authorized_at", nullable = false)
    private LocalDateTime authorizedAt;

    @Column(name = "last_authorized_at", nullable = false)
    private LocalDateTime lastAuthorizedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (authorizedAt == null) {
            authorizedAt = now;
        }
        if (lastAuthorizedAt == null) {
            lastAuthorizedAt = authorizedAt;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (scopes == null) {
            scopes = "";
        }
        if (grantMode == null || grantMode.isBlank()) {
            grantMode = AuthorizationGrantPolicy.MODE_PERSISTENT;
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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getContactInfo() {
        return contactInfo;
    }

    public void setContactInfo(String contactInfo) {
        this.contactInfo = contactInfo;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getScopes() {
        return scopes == null ? "" : scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes == null ? "" : scopes;
    }

    public String getGrantMode() {
        return grantMode == null || grantMode.isBlank()
            ? AuthorizationGrantPolicy.MODE_PERSISTENT
            : grantMode;
    }

    public void setGrantMode(String grantMode) {
        this.grantMode = grantMode == null || grantMode.isBlank()
            ? AuthorizationGrantPolicy.MODE_PERSISTENT
            : grantMode;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getAuthorizedAt() {
        return authorizedAt;
    }

    public void setAuthorizedAt(LocalDateTime authorizedAt) {
        this.authorizedAt = authorizedAt;
    }

    public LocalDateTime getLastAuthorizedAt() {
        return lastAuthorizedAt;
    }

    public void setLastAuthorizedAt(LocalDateTime lastAuthorizedAt) {
        this.lastAuthorizedAt = lastAuthorizedAt;
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
