package cn.ksuser.api.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Official registration is separate from developer OAuth and SSO registries. */
@Entity
@Table(name = "official_applications")
public class OfficialApplication {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", length = 128, nullable = false, unique = true)
    private String clientId;

    @com.fasterxml.jackson.annotation.JsonIgnore @Column(name = "client_secret_hash", length = 255)
    private String clientSecretHash;

    @Column(name = "client_name", length = 120, nullable = false)
    private String clientName;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "client_type", length = 32, nullable = false)
    private String clientType;

    @Column(name = "redirect_uris", columnDefinition = "TEXT", nullable = false)
    private String redirectUris;

    @Column(name = "scopes", length = 255, nullable = false)
    private String scopes;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "reserved", nullable = false)
    private Boolean reserved;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (isActive == null) isActive = true;
        if (reserved == null) reserved = false;
        if (scopes == null) scopes = "openid";
    }
    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public String getClientId() { return clientId; }
    public void setClientId(String value) { clientId = value; }
    public String getClientSecretHash() { return clientSecretHash; }
    public void setClientSecretHash(String value) { clientSecretHash = value; }
    public String getClientName() { return clientName; }
    public void setClientName(String value) { clientName = value; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String value) { logoUrl = value; }
    public String getClientType() { return clientType; }
    public void setClientType(String value) { clientType = value; }
    public String getRedirectUris() { return redirectUris; }
    public void setRedirectUris(String value) { redirectUris = value; }
    public String getScopes() { return scopes; }
    public void setScopes(String value) { scopes = value; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean value) { isActive = value; }
    public Boolean getReserved() { return reserved; }
    public void setReserved(Boolean value) { reserved = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
}
