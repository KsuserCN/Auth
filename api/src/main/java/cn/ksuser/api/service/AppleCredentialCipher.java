package cn.ksuser.api.service;

import cn.ksuser.api.config.AppleAuthProperties;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class AppleCredentialCipher {
    private final AppleAuthProperties properties;
    public AppleCredentialCipher(AppleAuthProperties properties) { this.properties = properties; }
    public String encrypt(String value) {
        try {
            byte[] nonce = new byte[12]; new SecureRandom().nextBytes(nonce);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce);
            return Base64.getEncoder().encodeToString(nonce) + "." + Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException("Apple 凭据加密失败", ex); }
    }
    public String decrypt(String value) {
        try {
            String[] parts = value.split("\\.", 2);
            Cipher cipher = cipher(Cipher.DECRYPT_MODE, Base64.getDecoder().decode(parts[0]));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
        } catch (Exception ex) { throw new IllegalStateException("Apple 凭据解密失败", ex); }
    }
    private byte[] masterKey() {
        byte[] bytes=Base64.getDecoder().decode(properties.getCredentialEncryptionKey());
        if (bytes.length!=32) throw new IllegalStateException("Apple 加密密钥必须为 32 字节");
        return bytes;
    }
    private Cipher cipher(int mode, byte[] nonce) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(masterKey(), "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD("ksuser.apple.v1".getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
}
