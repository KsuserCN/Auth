package cn.ksuser.api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * TOTP 恢复码实体
 * 用户可以在丢失 TOTP 设备时，使用恢复码来恢复账户访问权限
 * 
 * 安全设计：
 * - code_hash：使用 SHA-256 哈希存储恢复码（10 个恢复码，每个 32 字节）
 * - used_at IS NULL：标记未使用的恢复码（比 is_used 字段更清晰）
 * - UNIQUE(user_id, code_hash)：防止重复的恢复码
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "totp_recovery_codes", uniqueConstraints = {
    @UniqueConstraint(name = "uk_recovery_code", columnNames = {"user_id", "code_hash"})
})
public class TotpRecoveryCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 恢复码的 SHA-256 哈希值
     * 原文恢复码长度为 8 个字符，哈希后为固定 32 字节
     * 数据库中只存哈希，不存原文
     */
    @Column(name = "code_hash", nullable = false, columnDefinition = "VARBINARY(32)")
    private byte[] codeHash;

    /**
     * 恢复码密文（AES-GCM 加密）
     * 用于需要显示原始恢复码的场景
     */
    @Column(name = "code_ciphertext", columnDefinition = "VARBINARY(256)")
    private byte[] codeCiphertext;

    /**
     * 恢复码使用时间
     * NULL 表示未使用，非 NULL 表示已使用及使用时间
     */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public TotpRecoveryCode(Long userId, byte[] codeHash, byte[] codeCiphertext) {
        this.userId = userId;
        this.codeHash = codeHash;
        this.codeCiphertext = codeCiphertext;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 标记恢复码为已使用
     */
    public void markAsUsed() {
        this.usedAt = LocalDateTime.now();
    }

    /**
     * 检查恢复码是否已被使用
     */
    public boolean isUsed() {
        return usedAt != null;
    }
}
