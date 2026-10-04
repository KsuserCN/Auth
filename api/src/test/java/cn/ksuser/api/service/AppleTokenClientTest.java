package cn.ksuser.api.service;

import cn.ksuser.api.config.AppleAuthProperties;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.security.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AppleTokenClientTest {
    private AppleTokenClient client;
    private KeyPair keys;
    @BeforeEach void setUp() throws Exception {
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);keys=generator.generateKeyPair();
        AppleAuthProperties properties=new AppleAuthProperties();properties.setClientIds(List.of("cn.ksuser.ios"));
        client=new AppleTokenClient(properties);
        ReflectionTestUtils.setField(client,"keys",Map.of("test-key",keys.getPublic()));
        ReflectionTestUtils.setField(client,"keysExpireAt",Instant.now().plusSeconds(3600).getEpochSecond());
        ReflectionTestUtils.setField(client,"lastKeysFetchAt",Instant.now().getEpochSecond());
    }
    private String token(String issuer,String audience,String nonce,Instant expires,PrivateKey signingKey) {
        return Jwts.builder().header().keyId("test-key").and().issuer(issuer).subject("apple-user")
            .audience().add(audience).and().claim("nonce",nonce).claim("email","hidden@privaterelay.appleid.com")
            .claim("email_verified","true").issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(expires)).signWith(signingKey,Jwts.SIG.RS256).compact();
    }
    @Test void acceptsValidSignatureAudienceIssuerNonceAndVerifiedHiddenEmail() {
        String token=token("https://appleid.apple.com","cn.ksuser.ios","challenge-nonce",Instant.now().plusSeconds(300),keys.getPrivate());
        AppleTokenClient.Identity identity=client.verifyIdentity(token,"cn.ksuser.ios","challenge-nonce");
        assertEquals("apple-user",identity.sub());assertEquals("hidden@privaterelay.appleid.com",identity.email());
    }
    @Test void rejectsWrongAudienceIssuerNonceAndExpiredToken() {
        assertThrows(IllegalArgumentException.class,() -> client.verifyIdentity(token("https://evil.example","cn.ksuser.ios","nonce",Instant.now().plusSeconds(300),keys.getPrivate()),"cn.ksuser.ios","nonce"));
        assertThrows(IllegalArgumentException.class,() -> client.verifyIdentity(token("https://appleid.apple.com","evil-app","nonce",Instant.now().plusSeconds(300),keys.getPrivate()),"cn.ksuser.ios","nonce"));
        assertThrows(IllegalArgumentException.class,() -> client.verifyIdentity(token("https://appleid.apple.com","cn.ksuser.ios","wrong",Instant.now().plusSeconds(300),keys.getPrivate()),"cn.ksuser.ios","nonce"));
        assertThrows(IllegalArgumentException.class,() -> client.verifyIdentity(token("https://appleid.apple.com","cn.ksuser.ios","nonce",Instant.now().minusSeconds(60),keys.getPrivate()),"cn.ksuser.ios","nonce"));
    }
    @Test void rejectsForgedSignatureAndUntrustedClientId() throws Exception {
        KeyPair other=KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String forged=token("https://appleid.apple.com","cn.ksuser.ios","nonce",Instant.now().plusSeconds(300),other.getPrivate());
        assertThrows(IllegalArgumentException.class,() -> client.verifyIdentity(forged,"cn.ksuser.ios","nonce"));
        assertThrows(IllegalArgumentException.class,() -> client.verifyIdentity(forged,"untrusted-app","nonce"));
    }
    @Test void rejectsAlgorithmConfusion() {
        javax.crypto.SecretKey key=Jwts.SIG.HS256.key().build();
        String token=Jwts.builder().header().keyId("test-key").and().issuer("https://appleid.apple.com")
            .audience().add("cn.ksuser.ios").and().subject("apple-user").claim("nonce","nonce")
            .expiration(Date.from(Instant.now().plusSeconds(300))).signWith(key).compact();
        assertThrows(IllegalArgumentException.class,() -> client.verifyIdentity(token,"cn.ksuser.ios","nonce"));
    }
}
