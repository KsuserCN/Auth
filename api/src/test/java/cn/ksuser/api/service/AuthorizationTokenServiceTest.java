package cn.ksuser.api.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AuthorizationTokenServiceTest {

    private static final String SECRET = "ksuser-very-secret-key-2026-abc-platform-test";

    @Test
    void oauth2AccessTokenKeepsAuthorizationId() {
        Oauth2TokenService service = new Oauth2TokenService();
        ReflectionTestUtils.setField(service, "secret", SECRET);
        ReflectionTestUtils.setField(service, "accessTokenExpirationSeconds", 7200L);

        String token = service.generateAccessToken(
            "ksapp_demo", 99L, 1L, "user-uuid-demo", "profile", "openid", "unionid", 42L
        );

        Oauth2TokenService.ParsedOauth2AccessToken parsed = service.parse(token);
        assertNotNull(parsed);
        assertEquals(42L, parsed.authorizationId());
        assertEquals("ksapp_demo", parsed.clientId());
    }

    @Test
    void ssoAccessTokenKeepsAuthorizationId() {
        SsoTokenService service = new SsoTokenService(
            new OidcRsaKeyService("test-oidc-rsa-key-seed", SECRET)
        );
        ReflectionTestUtils.setField(service, "secret", SECRET);
        ReflectionTestUtils.setField(service, "accessTokenExpirationSeconds", 7200L);

        String token = service.generateAccessToken(
            "ksuser_auth_1", 1L, "sub-demo", "openid profile", "ksuser-auth", 42L
        );

        SsoTokenService.ParsedSsoAccessToken parsed = service.parseAccessToken(token);
        assertNotNull(parsed);
        assertEquals(42L, parsed.authorizationId());
        assertEquals("ksuser_auth_1", parsed.clientId());
    }
}
