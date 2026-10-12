package cn.ksuser.api.controller;

import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.repository.UserRepository;
import cn.ksuser.api.service.OfficialAuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OfficialAuthorizationControllerTest {
    @Test
    void basicClientAuthenticationDecodesFormComponentsWithoutMixingCredentialMethods() {
        OfficialAuthorizationService service = mock(OfficialAuthorizationService.class);
        OfficialAuthorizationController controller = new OfficialAuthorizationController(service, mock(UserRepository.class));
        String basic = "Basic " + Base64.getEncoder().encodeToString("my_client:secret%2Bvalue".getBytes(StandardCharsets.UTF_8));
        when(service.exchangeAuthorizationCode("authorization_code", "code", "my_client", "secret+value", "https://example.test/callback", "verifier", null))
            .thenReturn(Map.of("access_token", "opaque"));
        assertEquals("opaque", controller.token("authorization_code", "code", null, null, "https://example.test/callback", "verifier", basic, null).getBody().get("access_token"));
        assertThrows(Oauth2Exception.class, () -> controller.token("authorization_code", "code", "another_client", null, "https://example.test/callback", "verifier", basic, null));
        assertThrows(Oauth2Exception.class, () -> controller.token("authorization_code", "code", "my_client", "form-secret", "https://example.test/callback", "verifier", basic, null));
        assertThrows(Oauth2Exception.class, () -> controller.token("authorization_code", "code", null, null, "https://example.test/callback", "verifier", "Basic !!!", null));
    }

    @Test
    void userinfoUsesOnlyBearerHeaderAndDoesNotExposeJwtOrTokenInResponseEnvelope() {
        OfficialAuthorizationService service = mock(OfficialAuthorizationService.class);
        OfficialAuthorizationController controller = new OfficialAuthorizationController(service, mock(UserRepository.class));
        when(service.buildUserInfo("opaque", "https://example.test")).thenReturn(Map.of("sub", "real-uuid"));
        var response = controller.userinfo("Bearer opaque", "https://example.test");
        assertEquals(Map.of("sub", "real-uuid"), response.getBody()); assertEquals("no-store", response.getHeaders().getCacheControl());
        verify(service).buildUserInfo("opaque", "https://example.test");
    }

    @Test
    void oauthErrorsUseProtocolShapeAndNoStore() {
        OfficialAuthorizationController controller = new OfficialAuthorizationController(mock(OfficialAuthorizationService.class), mock(UserRepository.class));
        MockHttpServletRequest request = new MockHttpServletRequest(); request.setRequestURI("/official/token");
        Oauth2Exception failure = assertThrows(Oauth2Exception.class, () -> controller.token("authorization_code", "code", null, null,
            "https://example.test/callback", "verifier", "Bearer passport-token", null));
        var response = controller.oauthException(failure, request);
        assertEquals(401, response.getStatusCode().value()); assertEquals("no-store", response.getHeaders().getCacheControl());
        assertInstanceOf(Map.class, response.getBody()); assertEquals("invalid_client", ((Map<?, ?>)response.getBody()).get("error"));
    }
}
