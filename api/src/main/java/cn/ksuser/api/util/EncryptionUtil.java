package cn.ksuser.api.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Base64;

/**
 * 加密工具类
 * 提供主加密密钥管理
 */
@Component
public class EncryptionUtil {
    
    /**
     * 主加密密钥（从配置文件或环境变量读取）
     * 需要在 application.properties 中配置：app.encryption.master-key
     * 
     * 重要：这是一个 32 字节（256 位）的密钥，用于 AES-256-GCM 加密
     * 格式：Base64 编码的密钥
     * 
     * 建议：使用强随机密钥生成
     * 生成方法：new SecureRandom().nextBytes(new byte[32])，然后 Base64 编码
     */
    @Value("${app.encryption.master-key:#{null}}")
    private String masterKeyBase64;
    
    /**
     * 获取主加密密钥的字节数组
     * @return 32 字节的加密密钥
     * @throws IllegalStateException 如果密钥未配置
     */
    public byte[] getMasterKey() {
        if (masterKeyBase64 == null || masterKeyBase64.isEmpty()) {
            throw new IllegalStateException(
                "主加密密钥未配置，请在 application.properties 中设置 app.encryption.master-key"
            );
        }
        
        try {
            byte[] decodedKey = Base64.getDecoder().decode(masterKeyBase64);
            
            // 验证密钥长度（必须是 256 位 = 32 字节）
            if (decodedKey.length != 32) {
                throw new IllegalStateException(
                    "主加密密钥长度必须是 32 字节，当前长度：" + decodedKey.length
                );
            }
            
            return decodedKey;
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                "主加密密钥必须是有效的 Base64 格式", e
            );
        }
    }
}
