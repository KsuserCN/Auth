package cn.ksuser.api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户 TOTP 配置实体（Time-based One-Time Password）
 * 用于双因素认证
 * 
 * 安全设计：
 * - secret_key_ciphertext：AES-GCM 加密的 TOTP 密钥（解密后是 Base32 编码）
 * - key_version：密钥版本，便于轮换
 * - pending_secret_ciphertext：待确认的秘密值（注册流程中使用，10 分钟过期后自动清理）
 * - confirmed_at：TOTP 首次确认启用的时间
 * - last_used_step：上次成功验证的时间步长，防止 TOTP 码重放
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_totp")
public class UserTotp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /**
     * TOTP 密钥（AES-GCM 加密存储）
     * 解密后是 Base32 编码的 32 字节随机数据
     * 注：不存明文，只存加密值
     */
    @Column(name = "secret_key_ciphertext", columnDefinition = "VARBINARY(512)")
    private byte[] secretKeyCiphertext;

    /**
     * 密钥版本号（便于密钥轮换）
     * 默认版本为 1
     */
    @Column(name = "key_version", nullable = false)
    private Integer keyVersion;

    /**
     * 是否启用 TOTP（0=禁用，1=启用）
     */
    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled;

    /**
     * 待确认的秘密值（注册流程中）
     * 用户在注册 TOTP 时，该字段临时保存加密的密钥
     * 用户确认后清空此字段
     */
    @Column(name = "pending_secret_ciphertext", columnDefinition = "VARBINARY(512)")
    private byte[] pendingSecretCiphertext;

    /**
     * 待确认秘密的过期时间
     * 建议设置为 10 分钟
     * 过期后应清理 pending_secret_ciphertext
     */
    @Column(name = "pending_expires_at")
    private LocalDateTime pendingExpiresAt;

    /**
     * TOTP 最终确认启用的时间
     * 从此时刻开始，TOTP 码才能被有效验证
     */
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    /**
     * 上次成功验证的时间步长
     * 计算公式：floor(Unix_timestamp / 30)
     * 用于防止 TOTP 码重放：若新 step <= last_used_step 则拒绝
     */
    @Column(name = "last_used_step")
    private Long lastUsedStep;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public UserTotp(Long userId, byte[] secretKeyCiphertext, Integer keyVersion) {
        this.userId = userId;
        this.secretKeyCiphertext = secretKeyCiphertext;
        this.keyVersion = keyVersion;
        this.isEnabled = false;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (keyVersion == null) {
            keyVersion = 1;
        }
        if (isEnabled == null) {
            isEnabled = false;
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 清理待确认的秘密值（确认后调用）
     */
    public void clearPendingSecret() {
        this.pendingSecretCiphertext = null;
        this.pendingExpiresAt = null;
    }

    /**
     * 检查待确认秘密是否过期
     */
    public boolean isPendingSecretExpired() {
        if (pendingExpiresAt == null) {
            return true;
        }
        return LocalDateTime.now().isAfter(pendingExpiresAt);
    }

    /**
     * 更新上次验证的时间步长
     */
    public void updateLastUsedStep(Long currentStep) {
        this.lastUsedStep = currentStep;
    }

    /**
     * 检查是否应该拒绝当前 TOTP 步长（防重放）
     */
    public boolean shouldRejectStep(Long currentStep) {
        if (lastUsedStep == null) {
            return false;
        }
        return currentStep <= lastUsedStep;
    }
}
