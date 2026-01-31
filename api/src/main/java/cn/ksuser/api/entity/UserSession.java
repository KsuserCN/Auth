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

    public UserSession() {
    }

    public UserSession(User user, byte[] refreshTokenVerifier, String verifierAlgo, LocalDateTime expiresAt) {
        this.user = user;
        this.refreshTokenVerifier = refreshTokenVerifier;
        this.verifierAlgo = verifierAlgo;
        this.createdAt = LocalDateTime.now();
        this.expiresAt = expiresAt;
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
}
