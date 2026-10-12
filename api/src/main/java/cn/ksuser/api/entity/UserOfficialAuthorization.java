package cn.ksuser.api.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_official_authorizations", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "client_id"}))
public class UserOfficialAuthorization {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "client_id", length = 128, nullable = false)
    private String clientId;

    @Column(name = "scopes", length = 255, nullable = false)
    private String scopes;

    @Column(name = "redirect_uri", length = 2048, nullable = false)
    private String redirectUri;

    @Column(name = "authorized_at", nullable = false)
    private LocalDateTime authorizedAt;

    @Column(name = "last_authorized_at", nullable = false)
    private LocalDateTime lastAuthorizedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (authorizedAt == null) authorizedAt = now;
        if (lastAuthorizedAt == null) lastAuthorizedAt = now;
    }
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { userId = value; }
    public String getClientId() { return clientId; }
    public void setClientId(String value) { clientId = value; }
    public String getScopes() { return scopes; }
    public void setScopes(String value) { scopes = value; }
    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String value) { redirectUri = value; }
    public LocalDateTime getAuthorizedAt() { return authorizedAt; }
    public void setAuthorizedAt(LocalDateTime value) { authorizedAt = value; }
    public LocalDateTime getLastAuthorizedAt() { return lastAuthorizedAt; }
    public void setLastAuthorizedAt(LocalDateTime value) { lastAuthorizedAt = value; }
}
