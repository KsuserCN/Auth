package cn.ksuser.api.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_sessions")
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "refresh_token_verifier", nullable = false, unique = true, columnDefinition = "VARBINARY(255)")
    private byte[] refreshTokenVerifier;

    @Column(name = "verifier_algo", length = 16, nullable = false)
    private String verifierAlgo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "session_version", nullable = false)
    private Integer sessionVersion = 0;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "ip_location", length = 255)
    private String ipLocation;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "browser", length = 64)
    private String browser;

    @Column(name = "device_type", length = 32)
    private String deviceType;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    public UserSession() {
    }

    public UserSession(User user, byte[] refreshTokenVerifier, String verifierAlgo, LocalDateTime expiresAt) {
        this.user = user;
        this.refreshTokenVerifier = refreshTokenVerifier;
        this.verifierAlgo = verifierAlgo;
        this.createdAt = LocalDateTime.now();
        this.expiresAt = expiresAt;
        this.sessionVersion = 0;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public byte[] getRefreshTokenVerifier() {
        return refreshTokenVerifier;
    }

    public void setRefreshTokenVerifier(byte[] refreshTokenVerifier) {
        this.refreshTokenVerifier = refreshTokenVerifier;
    }

    public String getVerifierAlgo() {
        return verifierAlgo;
    }

    public void setVerifierAlgo(String verifierAlgo) {
        this.verifierAlgo = verifierAlgo;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public Integer getSessionVersion() {
        return sessionVersion;
    }

    public void setSessionVersion(Integer sessionVersion) {
        this.sessionVersion = sessionVersion;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getIpLocation() {
        return ipLocation;
    }

    public void setIpLocation(String ipLocation) {
        this.ipLocation = ipLocation;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getBrowser() {
        return browser;
    }

    public void setBrowser(String browser) {
        this.browser = browser;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(LocalDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
}
