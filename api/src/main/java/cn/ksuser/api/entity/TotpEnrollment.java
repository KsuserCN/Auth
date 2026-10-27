package cn.ksuser.api.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/** An independently authorized, session-bound attempt to add a new TOTP credential. */
@Data
@NoArgsConstructor
@Entity
@Table(name = "totp_enrollments", indexes = {
    @Index(name = "idx_totp_enrollment_user", columnList = "user_id"),
    @Index(name = "idx_totp_enrollment_expiry", columnList = "expires_at")})
public class TotpEnrollment {
    @Id @Column(length = 32)
    private String id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "session_id", nullable = false)
    private Long sessionId;
    @Column(name = "session_version", nullable = false)
    private Integer sessionVersion;
    @Column(name = "pending_secret_ciphertext", columnDefinition = "VARBINARY(512)")
    private byte[] pendingSecretCiphertext;
    @Column(name = "recovery_codes_ciphertext", columnDefinition = "VARBINARY(512)")
    private byte[] recoveryCodesCiphertext;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    public void erasePendingSecrets() {
        pendingSecretCiphertext = null;
        recoveryCodesCiphertext = null;
    }
}
