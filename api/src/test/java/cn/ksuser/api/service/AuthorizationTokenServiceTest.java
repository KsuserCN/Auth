package cn.ksuser.api.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertEquals("openid", parsed.userUuid());
        assertFalse(new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]),
            java.nio.charset.StandardCharsets.UTF_8).contains("user-uuid-demo"));
    }

    @Test
    void unapprovedApplicationTokenOmitsUnionidClaim() throws Exception {
        Oauth2TokenService service = new Oauth2TokenService();
        ReflectionTestUtils.setField(service, "secret", SECRET);
        ReflectionTestUtils.setField(service, "accessTokenExpirationSeconds", 7200L);
        String token = service.generateAccessToken("ksapp_new", 99L, 1L, "user-uuid-demo", "profile", "openid", null, 42L);
        var claims = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
            java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        assertFalse(claims.has("unionid"));
        assertEquals(null, service.parse(token).unionid());
    }

}
