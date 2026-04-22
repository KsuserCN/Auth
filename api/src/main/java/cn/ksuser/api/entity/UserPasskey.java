package cn.ksuser.api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户 Passkey 凭证实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_passkeys")
public class UserPasskey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "credential_id", nullable = false, columnDefinition = "VARBINARY(512)")
    private byte[] credentialId;

    @Column(name = "public_key_cose", nullable = false, columnDefinition = "VARBINARY(1024)")
    private byte[] publicKeyCose;

    @Column(name = "sign_count", nullable = false)
    private Long signCount;

    @Column(name = "transports", length = 255)
    private String transports;

    @Column(name = "aaguid", columnDefinition = "BINARY(16)")
    private byte[] aaguid;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
