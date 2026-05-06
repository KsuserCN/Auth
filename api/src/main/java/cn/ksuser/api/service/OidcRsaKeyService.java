package cn.ksuser.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OidcRsaKeyService {

    private static final String KEY_ID = "ksuser-oidc-rs256";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KeyPair keyPair;

    public OidcRsaKeyService(@Value("${app.oidc.rsa.private-key-pem:}") String privateKeySeed,
                             @Value("${jwt.secret}") String jwtSecret) {
        this.keyPair = generateKeyPair(privateKeySeed == null || privateKeySeed.isBlank() ? jwtSecret : privateKeySeed);
    }

    public PrivateKey getPrivateKey() {
        return keyPair.getPrivate();
    }

    public String getKeyId() {
        return KEY_ID;
    }

    public String getJwksJson() {
        try {
            return objectMapper.writeValueAsString(buildJwks());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize OIDC JWKS", ex);
        }
    }

    private Map<String, Object> buildJwks() {
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("kid", KEY_ID);
        jwk.put("alg", "RS256");
        jwk.put("n", base64Url(publicKey.getModulus()));
        jwk.put("e", base64Url(publicKey.getPublicExponent()));
        return Map.of("keys", List.of(jwk));
    }

    private KeyPair generateKeyPair(String seed) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
            random.setSeed(MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8)));
            generator.initialize(2048, random);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize OIDC RSA key pair", ex);
        }
    }

    private String base64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] stripped = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, stripped, 0, stripped.length);
            bytes = stripped;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
