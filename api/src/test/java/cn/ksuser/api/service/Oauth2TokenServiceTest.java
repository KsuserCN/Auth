package cn.ksuser.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Oauth2TokenServiceTest {

    private Oauth2TokenService service;

    @BeforeEach
    void setUp() {
        service = new Oauth2TokenService();
        ReflectionTestUtils.setField(service, "secret", "ksuser-very-secret-key-2026-abc-oidc-test");
        ReflectionTestUtils.setField(service, "accessTokenExpirationSeconds", 7200L);
        ReflectionTestUtils.setField(service, "issuer", "http://localhost:8000");
    }

    @Test
    void shouldGenerateAndParseIdTokenWithScopedClaims() {
        String idToken = service.generateIdToken(
            "ksapp_demo",
            "oid_demo_subject",
            "nonce-demo",
            "openid profile email",
            "demo-user",
            "https://cdn.example.com/avatar.png",
            "demo@example.com"
        );

        Oauth2TokenService.ParsedOidcIdToken parsed = service.parseIdToken(idToken);

        assertNotNull(parsed);
        assertEquals("http://localhost:8000", parsed.issuer());
        assertEquals("ksapp_demo", parsed.audience());
        assertEquals("oid_demo_subject", parsed.subject());
        assertEquals("nonce-demo", parsed.nonce());
        assertEquals("demo-user", parsed.nickname());
        assertEquals("https://cdn.example.com/avatar.png", parsed.picture());
        assertEquals("demo@example.com", parsed.email());
        assertTrue(parsed.emailVerified());
        assertNotNull(parsed.expiresAt());
    }
}
