package cn.ksuser.api.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "oauth2_app_platforms", uniqueConstraints = @UniqueConstraint(columnNames = {"app_id", "platform"}))
public class Oauth2AppPlatform {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "app_id", length = 64, nullable = false) private String appId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_id", referencedColumnName = "app_id", insertable = false, updatable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Oauth2Application application;
    @Column(length = 16, nullable = false) private String platform;
    @Column(name = "is_configured", nullable = false) private boolean configured = true;
    @Column(name = "config_json", columnDefinition = "TEXT", nullable = false) private String configJson;
    @Column(name = "config_revision", length = 36, nullable = false) private String configRevision;
    @Column(name = "verification_status", length = 16, nullable = false) private String verificationStatus = "unverified";
    @Column(name = "verification_message", length = 255) private String verificationMessage;
    @Column(name = "verified_at") private LocalDateTime verifiedAt;
    @Column(name = "last_verification_attempt_at") private LocalDateTime lastVerificationAttemptAt;
    @Version @Column(nullable = false) private long version;

    public Long getId() { return id; }
    public String getAppId() { return appId; }
    public void setAppId(String value) { appId = value; }
    public String getPlatform() { return platform; }
    public void setPlatform(String value) { platform = value; }
    public boolean isConfigured() { return configured; }
    public void setConfigured(boolean value) { configured = value; }
    public String getConfigJson() { return configJson; }
    public void setConfigJson(String value) { configJson = value; }
    public String getConfigRevision() { return configRevision; }
    public void setConfigRevision(String value) { configRevision = value; }
    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String value) { verificationStatus = value; }
    public String getVerificationMessage() { return verificationMessage; }
    public void setVerificationMessage(String value) { verificationMessage = value; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime value) { verifiedAt = value; }
    public LocalDateTime getLastVerificationAttemptAt() { return lastVerificationAttemptAt; }
    public void setLastVerificationAttemptAt(LocalDateTime value) { lastVerificationAttemptAt = value; }
}
