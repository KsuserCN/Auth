package cn.ksuser.api.controller;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.result.ApiResultCode;
import cn.ksuser.api.service.Oauth2PlatformService;
import cn.ksuser.api.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class Oauth2PlatformControllerTest {
    private final Oauth2PlatformService oauth = mock(Oauth2PlatformService.class);
    private MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new Oauth2PlatformController(oauth, mock(UserService.class), new AppProperties())).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Basic Y2xpZW50OnNlY3JldA==", "Bearer passport-token", "", "Unknown value"})
    void tokenRejectsEveryAuthorizationHeaderInsteadOfIgnoringCredentials(String authorization) throws Exception {
        mvc.perform(post("/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Authorization", authorization).param("grant_type", "authorization_code")
                .param("client_id", "public-client").param("code", "code").param("code_verifier", "verifier")
                .param("redirect_uri", "https://client.example/callback"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("error").value("invalid_client"))
            .andExpect(header().string("Cache-Control", "no-store"));
        verifyNoInteractions(oauth);
    }

    @Test void tokenAcceptsPublicNoneAndConfidentialPostWithoutChangingTheirCredentials() throws Exception {
        when(oauth.exchangeAuthorizationCode("authorization_code", "public-code", "public-client", null, "https://client.example/callback", "verifier"))
            .thenReturn(Map.of("access_token", "public-token"));
        when(oauth.exchangeAuthorizationCode("authorization_code", "server-code", "server-client", "server-secret", "https://client.example/callback", null))
            .thenReturn(Map.of("access_token", "server-token"));
        mvc.perform(post("/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code").param("client_id", "public-client").param("code", "public-code")
                .param("code_verifier", "verifier").param("redirect_uri", "https://client.example/callback"))
            .andExpect(status().isOk()).andExpect(jsonPath("access_token").value("public-token"));
        mvc.perform(post("/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code").param("client_id", "server-client").param("code", "server-code")
                .param("client_secret", "server-secret").param("redirect_uri", "https://client.example/callback"))
            .andExpect(status().isOk()).andExpect(jsonPath("access_token").value("server-token"));
    }

    @Test void tokenRejectsMixedAuthenticationAndPreservesMissingSecretErrors() throws Exception {
        mvc.perform(post("/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Authorization", "Basic Y2xpZW50OnNlY3JldA==").param("client_secret", "form-secret"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("error").value("invalid_client"));
        verifyNoInteractions(oauth);
        when(oauth.exchangeAuthorizationCode("authorization_code", "code", "server-client", null, "https://client.example/callback", null))
            .thenThrow(new Oauth2Exception(HttpStatus.UNAUTHORIZED, "invalid_client", ApiResultCode.OAUTH2_015));
        mvc.perform(post("/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code").param("code", "code").param("client_id", "server-client")
                .param("redirect_uri", "https://client.example/callback"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("error").value("invalid_client"));
    }
}
