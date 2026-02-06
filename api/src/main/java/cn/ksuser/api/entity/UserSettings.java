package cn.ksuser.api.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_settings")
public class UserSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "mfa_enabled", nullable = false)
    private Boolean mfaEnabled;

    @Column(name = "detect_unusual_login", nullable = false)
    private Boolean detectUnusualLogin;

    @Column(name = "notify_sensitive_action_email", nullable = false)
    private Boolean notifySensitiveActionEmail;

    @Column(name = "subscribe_news_email", nullable = false)
    private Boolean subscribeNewsEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public UserSettings() {
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

    public Boolean getMfaEnabled() {
        return mfaEnabled;
    }

    public void setMfaEnabled(Boolean mfaEnabled) {
        this.mfaEnabled = mfaEnabled;
    }

    public Boolean getDetectUnusualLogin() {
        return detectUnusualLogin;
    }

    public void setDetectUnusualLogin(Boolean detectUnusualLogin) {
        this.detectUnusualLogin = detectUnusualLogin;
    }

    public Boolean getNotifySensitiveActionEmail() {
        return notifySensitiveActionEmail;
    }

    public void setNotifySensitiveActionEmail(Boolean notifySensitiveActionEmail) {
        this.notifySensitiveActionEmail = notifySensitiveActionEmail;
    }

    public Boolean getSubscribeNewsEmail() {
        return subscribeNewsEmail;
    }

    public void setSubscribeNewsEmail(Boolean subscribeNewsEmail) {
        this.subscribeNewsEmail = subscribeNewsEmail;
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
