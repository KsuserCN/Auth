package cn.ksuser.api.service;

import cn.ksuser.api.entity.TotpRecoveryCode;
import cn.ksuser.api.entity.UserTotp;
import cn.ksuser.api.repository.TotpRecoveryCodeRepository;
import cn.ksuser.api.repository.UserTotpRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

/**
 * TOTP（Time-based One-Time Password）服务
 * 用于双因素认证
 * 
 * 安全设计：
 * - 密钥使用 AES-GCM 加密存储（不存明文）
 * - 恢复码使用 SHA-256 哈希存储
 * - 防重放：记录上次验证的时间步长
 * - key_version：支持密钥轮换
 */
@Service
public class TotpService {

    private final UserTotpRepository userTotpRepository;
    private final TotpRecoveryCodeRepository recoveryCodeRepository;

    // TOTP 配置常数
    private static final int SECRET_LENGTH = 32; // 字节长度
    private static final int CODE_LENGTH = 6; // OTP 码长度
    private static final int TIME_INTERVAL = 30; // 时间步长（秒）
    private static final int RECOVERY_CODES_COUNT = 10; // 恢复码数量
    private static final int RECOVERY_CODE_LENGTH = 8; // 恢复码长度

    // AES-GCM 配置
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // 位数
    private static final int GCM_IV_LENGTH = 12; // 字节长度

    public TotpService(UserTotpRepository userTotpRepository,
                       TotpRecoveryCodeRepository recoveryCodeRepository) {
        this.userTotpRepository = userTotpRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
    }


    /**
     * 使用 AES-GCM 加密数据
     * @param plaintext 明文（密钥）
     * @param aesKey AES 密钥
     * @return 加密数据（IV + 密文 + TAG）
     */
    private byte[] encryptAesGcm(byte[] plaintext, byte[] aesKey) throws Exception {
        SecureRandom random = new SecureRandom();
        byte[] iv = new byte[GCM_IV_LENGTH];
        random.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, 0, aesKey.length, "AES"), spec);
        
        byte[] ciphertext = cipher.doFinal(plaintext);
        
        // 返回 IV + 密文，总长度：12 + plaintext.length + 16(tag)
        ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
        buffer.put(iv);
        buffer.put(ciphertext);
        return buffer.array();
    }

    /**
     * Base32 编码（RFC 4648）
     */
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private String base32Encode(byte[] data) {
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bufferLength = 0;

        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bufferLength += 8;
            while (bufferLength >= 5) {
                bufferLength -= 5;
                result.append(BASE32_ALPHABET.charAt((buffer >> bufferLength) & 31));
            }
        }

        if (bufferLength > 0) {
            result.append(BASE32_ALPHABET.charAt((buffer << (5 - bufferLength)) & 31));
        }

        return result.toString();
    }
    /**
     * 使用 AES-GCM 解密数据
     * @param ciphertext 加密数据（IV + 密文 + TAG）
     * @param aesKey AES 密钥
     * @return 明文（密钥）
     */
    private byte[] decryptAesGcm(byte[] ciphertext, byte[] aesKey) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(ciphertext);
        byte[] iv = new byte[GCM_IV_LENGTH];
        buffer.get(iv);
        
        byte[] encryptedData = new byte[buffer.remaining()];
        buffer.get(encryptedData);

        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, 0, aesKey.length, "AES"), spec);
        
        return cipher.doFinal(encryptedData);
    }

    /**
     * SHA-256 哈希
     */
    private byte[] sha256Hash(byte[] input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(input);
    }

    /**
     * 生成新的 TOTP 密钥和恢复码
     * @param userId 用户 ID
     * @param masterEncryptionKey 主加密密钥（用于加密 TOTP 密钥）
     * @return 包含密钥、二维码 URL 和恢复码的数据
     */
    @Transactional
    public Map<String, Object> generateTotpSecret(Long userId, byte[] masterEncryptionKey) {
        try {
            // 生成随机密钥
            byte[] randomBytes = new byte[SECRET_LENGTH];
            new SecureRandom().nextBytes(randomBytes);
            
            // 编码为 Base32
            
            String secretKey = base32Encode(randomBytes);
            // AES-GCM 加密密钥
            byte[] encryptedSecretKeyCiphertext = encryptAesGcm(randomBytes, masterEncryptionKey);
            
            // 生成恢复码
            String[] recoveryCodes = generateRecoveryCodes(RECOVERY_CODES_COUNT);
            
            // 生成二维码 URL
            String qrCodeUrl = generateQrCodeUrl(userId, secretKey);
            
            Map<String, Object> result = new HashMap<>();
            result.put("secret", secretKey); // Base32 编码版本，用于二维码和手动输入
            result.put("qrCodeUrl", qrCodeUrl);
            result.put("recoveryCodes", recoveryCodes);
            result.put("encryptedSecret", Base64.getEncoder().encodeToString(encryptedSecretKeyCiphertext));
            
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate TOTP secret", e);
        }
    }

    /**
     * 验证 TOTP 码
     * @param userId 用户 ID
     * @param code TOTP 码
     * @param masterEncryptionKey 主加密密钥
     * @return 验证是否成功
     */
    @Transactional
    public boolean verifyTotpCode(Long userId, String code, byte[] masterEncryptionKey) {
        try {
            Optional<UserTotp> userTotpOpt = userTotpRepository.findByUserId(userId);
            if (userTotpOpt.isEmpty()) {
                return false;
            }

            UserTotp userTotp = userTotpOpt.get();
            if (!userTotp.getIsEnabled() || userTotp.getConfirmedAt() == null) {
                return false;
            }

            // 解密密钥
            byte[] decryptedSecret = decryptAesGcm(userTotp.getSecretKeyCiphertext(), masterEncryptionKey);
            
            // 计算当前时间步长
            long currentStep = System.currentTimeMillis() / 1000 / TIME_INTERVAL;
            
            // 防重放检查
            if (userTotp.shouldRejectStep(currentStep)) {
                return false;
            }

            // 检查码是否匹配（允许时间误差：前一个、当前、后一个）
            boolean codeMatches = false;
            for (int offset = -1; offset <= 1; offset++) {
                long step = currentStep + offset;
                if (verifyTotpCode(decryptedSecret, code, step)) {
                    // 验证成功，更新 last_used_step
                    userTotp.updateLastUsedStep(step);
                    userTotpRepository.save(userTotp);
                    codeMatches = true;
                    break;
                }
            }
            
            return codeMatches;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 验证 TOTP 码（内部方法）
     */
    private boolean verifyTotpCode(byte[] decodedSecret, String code, long timeInterval) throws Exception {
        String expectedCode = generateTotpCode(decodedSecret, timeInterval);
        return expectedCode.equals(code);
    }

    /**
     * 生成 TOTP 码
     */
    private String generateTotpCode(byte[] decodedSecret, long timeInterval) throws Exception {
        byte[] message = ByteBuffer.allocate(8).putLong(timeInterval).array();
        
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(decodedSecret, "HmacSHA1"));
        byte[] hash = mac.doFinal(message);
        
        int offset = hash[hash.length - 1] & 0x0f;
        int truncated = ByteBuffer.wrap(hash, offset, 4).getInt() & 0x7fffffff;
        int otpCode = truncated % (int) Math.pow(10, CODE_LENGTH);
        
        return String.format("%0" + CODE_LENGTH + "d", otpCode);
    }

    /**
     * 生成二维码 URL（Google Authenticator 格式）
     */
    private String generateQrCodeUrl(Long userId, String secretKey) {
        String label = String.format("KSUser:user%d", userId);
        String issuer = "KSUser";
        
        return String.format(
            "otpauth://totp/%s?secret=%s&issuer=%s",
            label, secretKey, issuer
        );
    }

    /**
     * 生成恢复码
     */
    private String[] generateRecoveryCodes(int count) {
        String[] codes = new String[count];
        SecureRandom random = new SecureRandom();
        
        for (int i = 0; i < count; i++) {
            StringBuilder code = new StringBuilder();
            for (int j = 0; j < RECOVERY_CODE_LENGTH; j++) {
                code.append(random.nextInt(10));
            }
            codes[i] = code.toString();
        }
        
        return codes;
    }

    /**
     * 验证恢复码
     * @param userId 用户 ID
     * @param recoveryCode 恢复码
     * @return 验证是否成功
     */
    @Transactional
    public boolean verifyRecoveryCode(Long userId, String recoveryCode) {
        try {
            if (recoveryCode == null || recoveryCode.isEmpty()) {
                return false;
            }

            // SHA-256 哈希恢复码
            byte[] codeHash = sha256Hash(recoveryCode.getBytes());
            
            Optional<TotpRecoveryCode> codeOpt = 
                recoveryCodeRepository.findByUserIdAndCodeHash(userId, codeHash);
            
            if (codeOpt.isEmpty()) {
                return false;
            }

            TotpRecoveryCode code = codeOpt.get();
            if (code.isUsed()) {
                return false;
            }

            // 标记为已使用
            code.markAsUsed();
            recoveryCodeRepository.save(code);
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 确认 TOTP 注册（注册流程的第二步）
     * @param userId 用户 ID
     * @param code TOTP 码
     * @param recoveryCodes 恢复码列表
     * @param masterEncryptionKey 主加密密钥
     * @return 注册是否成功
     */
    @Transactional
    public boolean confirmTotpRegistration(Long userId, String code, String[] recoveryCodes, 
                                          byte[] masterEncryptionKey) {
        try {
            Optional<UserTotp> existingOpt = userTotpRepository.findByUserId(userId);
            if (existingOpt.isEmpty()) {
                return false;
            }

            UserTotp userTotp = existingOpt.get();
            
            // 检查是否有待确认的密钥
            if (userTotp.getPendingSecretCiphertext() == null) {
                return false;
            }

            // 检查是否过期
            if (userTotp.isPendingSecretExpired()) {
                userTotp.clearPendingSecret();
                userTotpRepository.save(userTotp);
                return false;
            }

            // 使用待确认的密钥验证码
            byte[] decryptedSecret = decryptAesGcm(userTotp.getPendingSecretCiphertext(), masterEncryptionKey);
            
            // 计算当前时间步长
            long currentStep = System.currentTimeMillis() / 1000 / TIME_INTERVAL;
            
            // 检查码是否匹配（允许时间误差）
            boolean codeMatches = false;
            for (int offset = -1; offset <= 1; offset++) {
                long step = currentStep + offset;
                if (verifyTotpCode(decryptedSecret, code, step)) {
                    codeMatches = true;
                    break;
                }
            }

            if (!codeMatches) {
                return false;
            }

            // 将待确认的密钥移到正式密钥
            userTotp.setSecretKeyCiphertext(userTotp.getPendingSecretCiphertext());
            userTotp.setKeyVersion(1);
            userTotp.setIsEnabled(true);
            userTotp.setConfirmedAt(LocalDateTime.now());
            userTotp.clearPendingSecret();
            userTotp.setLastUsedStep(currentStep);
            userTotpRepository.save(userTotp);

            // 删除旧的恢复码
            recoveryCodeRepository.deleteByUserId(userId);

            // 保存新的恢复码（SHA-256 哈希 + AES-GCM 密文）
            for (String recoveryCode : recoveryCodes) {
                byte[] codeHash = sha256Hash(recoveryCode.getBytes());
                byte[] codeCiphertext = encryptAesGcm(recoveryCode.getBytes(), masterEncryptionKey);
                TotpRecoveryCode codeEntity = new TotpRecoveryCode(userId, codeHash, codeCiphertext);
                recoveryCodeRepository.save(codeEntity);
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 禁用 TOTP
     */
    @Transactional
    public boolean disableTotp(Long userId) {
        Optional<UserTotp> userTotpOpt = userTotpRepository.findByUserId(userId);
        if (userTotpOpt.isEmpty()) {
            return false;
        }

        UserTotp userTotp = userTotpOpt.get();
        userTotpRepository.delete(userTotp);
        
        // 删除所有恢复码
        recoveryCodeRepository.deleteByUserId(userId);
        
        return true;
    }

    /**
     * 获取 TOTP 状态
     */
    public Map<String, Object> getTotpStatus(Long userId) {
        Optional<UserTotp> userTotpOpt = userTotpRepository.findByUserId(userId);
        
        Map<String, Object> result = new HashMap<>();
        
        if (userTotpOpt.isEmpty()) {
            result.put("enabled", false);
            result.put("recoveryCodesCount", 0);
        } else {
            UserTotp userTotp = userTotpOpt.get();
            result.put("enabled", userTotp.getIsEnabled());
            long count = recoveryCodeRepository.countByUserIdAndUnused(userId);
            result.put("recoveryCodesCount", count);
        }
        
        return result;
    }

    /**
     * 生成新的恢复码（当用户消耗了旧的恢复码时）
     */
    @Transactional
    public String[] regenerateRecoveryCodes(Long userId, byte[] masterEncryptionKey) {
        try {
            // 删除旧的恢复码
            recoveryCodeRepository.deleteByUserId(userId);

            // 生成新的恢复码
            String[] newCodes = generateRecoveryCodes(RECOVERY_CODES_COUNT);

            // 保存新的恢复码（SHA-256 哈希 + AES-GCM 密文）
            for (String code : newCodes) {
                byte[] codeHash = sha256Hash(code.getBytes());
                byte[] codeCiphertext = encryptAesGcm(code.getBytes(), masterEncryptionKey);
                TotpRecoveryCode codeEntity = new TotpRecoveryCode(userId, codeHash, codeCiphertext);
                recoveryCodeRepository.save(codeEntity);
            }

            return newCodes;
        } catch (Exception e) {
            throw new RuntimeException("Failed to regenerate recovery codes", e);
        }
    }

    /**
     * 获取用户的恢复码（用于显示，仅显示剩余未使用的）
     */
    public List<String> getRecoveryCodes(Long userId, byte[] masterEncryptionKey) {
        try {
            List<TotpRecoveryCode> codes = recoveryCodeRepository.findByUserIdAndUnusedOrderByCreatedAtAsc(userId);
            List<String> result = new ArrayList<>();
            
            for (TotpRecoveryCode code : codes) {
                byte[] ciphertext = code.getCodeCiphertext();
                if (ciphertext == null || ciphertext.length == 0) {
                    continue;
                }
                byte[] plaintext = decryptAesGcm(ciphertext, masterEncryptionKey);
                result.add(new String(plaintext));
            }
            
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt recovery codes", e);
        }
    }

    /**
     * 检查用户是否启用了 TOTP
     */
    public boolean isTotpEnabled(Long userId) {
        return userTotpRepository.existsByUserIdAndIsEnabledTrue(userId);
    }
}
