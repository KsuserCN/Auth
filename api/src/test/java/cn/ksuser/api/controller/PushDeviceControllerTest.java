package cn.ksuser.api.controller;

import cn.ksuser.api.config.*;
import cn.ksuser.api.entity.*;
import cn.ksuser.api.filter.JwtAuthenticationFilter;
import cn.ksuser.api.service.*;
import cn.ksuser.api.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig(PushDeviceControllerTest.Configuration.class)
@TestPropertySource(properties = {"jwt.secret=unused-test-secret", "jwt.access-token-expiration=300000", "jwt.refresh-token-expiration=600000"})
class PushDeviceControllerTest {
    @Autowired private WebApplicationContext context;
    @Autowired private JwtUtil jwt;
    @Autowired private UserSessionService sessions;
    @Autowired private TokenBlacklistService blacklist;
    @Autowired private PushNotificationService push;
    private MockMvc mvc;
    private UserSession session;
    private final String body = "{\"deviceToken\":\"" + "a".repeat(64) + "\",\"environment\":\"SANDBOX\",\"mode\":\"ALL\"}";
    @BeforeEach void setUp() {
        reset(jwt, sessions, blacklist, push);
        var user = new User(); user.setUuid("push-user"); session = new UserSession(); session.setId(42L); session.setUser(user); session.setSessionVersion(3);
        when(jwt.getUuidFromToken("access-token")).thenReturn("push-user"); when(jwt.getTokenType("access-token")).thenReturn("access");
        when(jwt.getSessionId("access-token")).thenReturn(42L); when(jwt.getSessionVersion("access-token")).thenReturn(3);
        when(jwt.isTokenValid("access-token")).thenReturn(true); when(sessions.findActiveSessionById(42L)).thenReturn(Optional.of(session));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @Test void authenticatedRegistrationUsesServerSessionAndReportsProviderState() throws Exception {
        mvc.perform(post("/auth/push/device").with(csrf()).header("Authorization", "Bearer access-token")
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(false));
        verify(push).register(eq(session), any());
    }
    @Test void registrationAndDeletionRequireCsrfAndAuthentication() throws Exception {
        mvc.perform(post("/auth/push/device").header("Authorization", "Bearer access-token").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(delete("/auth/push/device").header("Authorization", "Bearer access-token")).andExpect(status().isForbidden());
        mvc.perform(post("/auth/push/device").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/auth/push/device").with(csrf())).andExpect(status().isUnauthorized());
        verifyNoInteractions(push);
    }
    @Test void revokedOrOtherAccountSessionCannotRegister() throws Exception {
        when(sessions.findActiveSessionById(42L)).thenReturn(Optional.empty());
        mvc.perform(post("/auth/push/device").with(csrf()).header("Authorization", "Bearer access-token")
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        when(sessions.findActiveSessionById(42L)).thenReturn(Optional.of(session)); session.getUser().setUuid("another-user");
        mvc.perform(post("/auth/push/device").with(csrf()).header("Authorization", "Bearer access-token")
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized()); verifyNoInteractions(push);
    }
    @Test void invalidTokensModesAndEnvironmentsAreRejected() throws Exception {
        for (String invalid : new String[]{body.replace("a".repeat(64), "not-a-token"), body.replace("SANDBOX", "arbitrary"), body.replace("ALL", "LOGIN"), body.replace("\"ALL\"", "null")}) {
            mvc.perform(post("/auth/push/device").with(csrf()).header("Authorization", "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(push);
    }
    @Test void unregisterDeletesOnlyCurrentSession() throws Exception {
        mvc.perform(delete("/auth/push/device").with(csrf()).header("Authorization", "Bearer access-token")).andExpect(status().isOk());
        verify(push).unregister(42L);
    }
    @org.springframework.context.annotation.Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, JwtAuthenticationFilter.class, PushDeviceController.class})
    static class Configuration {
        @Bean JwtUtil jwt() { return mock(JwtUtil.class); }
        @Bean UserSessionService sessions() { return mock(UserSessionService.class); }
        @Bean TokenBlacklistService blacklist() { return mock(TokenBlacklistService.class); }
        @Bean PushNotificationService push() { return mock(PushNotificationService.class); }
        @Bean ApnsProperties properties() { return new ApnsProperties(); }
        @Bean AppProperties appProperties() { return new AppProperties(); }
    }
}
