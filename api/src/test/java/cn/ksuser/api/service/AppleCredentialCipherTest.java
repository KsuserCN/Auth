package cn.ksuser.api.service;
import cn.ksuser.api.config.AppleAuthProperties;
import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
class AppleCredentialCipherTest {
    @Test void authenticatedEncryptionUsesRandomNonceAndRejectsTampering() {
        AppleAuthProperties properties=new AppleAuthProperties();properties.setCredentialEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        AppleCredentialCipher cipher=new AppleCredentialCipher(properties);
        String first=cipher.encrypt("private-refresh-token"),second=cipher.encrypt("private-refresh-token");
        assertNotEquals(first,second);assertFalse(first.contains("private-refresh-token"));assertEquals("private-refresh-token",cipher.decrypt(first));
        String[] parts=first.split("\\.");byte[] ciphertext=Base64.getDecoder().decode(parts[1]);ciphertext[0]^=1;
        assertThrows(IllegalStateException.class,() -> cipher.decrypt(parts[0]+"."+Base64.getEncoder().encodeToString(ciphertext)));
    }
    @Test void noPublicDefaultKeyIsAllowed() {
        AppleCredentialCipher cipher=new AppleCredentialCipher(new AppleAuthProperties());
        assertThrows(IllegalStateException.class,() -> cipher.encrypt("token"));
    }
}
