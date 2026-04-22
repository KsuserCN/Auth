package cn.ksuser.api.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

final class PkceUtil {

    private PkceUtil() {
    }

    static String toS256CodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute PKCE code challenge", e);
        }
    }
}
