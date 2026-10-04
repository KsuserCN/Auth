package cn.ksuser.api.config;

import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserSession;
import cn.ksuser.api.filter.JwtAuthenticationFilter;
import cn.ksuser.api.service.TokenBlacklistService;
import cn.ksuser.api.service.UserSessionService;
import cn.ksuser.api.util.JwtUtil;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises the production security chain without SQL, Redis, or Apple requests. */
@SpringJUnitWebConfig(SecurityConfigAppleBindingTest.TestConfiguration.class)
@TestPropertySource(properties = {"jwt.secret=unused-test-secret", "jwt.access-token-expiration=300000", "jwt.refresh-token-expiration=600000"})
class SecurityConfigAppleBindingTest {
    @Autowired private WebApplicationContext context;
    @Autowired private JwtUtil jwt;
    @Autowired private UserSessionService sessions;
    @Autowired private TokenBlacklistService blacklist;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(jwt, sessions, blacklist);
        User user = new User();
        user.setUuid("binding-user");
        UserSession session = new UserSession();
        session.setId(42L);
        session.setUser(user);
        session.setSessionVersion(3);
        when(jwt.getUuidFromToken("access-token")).thenReturn(user.getUuid());
        when(jwt.getTokenType("access-token")).thenReturn("access");
        when(jwt.getSessionId("access-token")).thenReturn(session.getId());
        when(jwt.getSessionVersion("access-token")).thenReturn(session.getSessionVersion());
        when(jwt.isTokenValid("access-token")).thenReturn(true);
        when(sessions.findActiveSessionById(42L)).thenReturn(Optional.of(session));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void bindingRejectsMissingCsrfEvenWithValidBearer() throws Exception {
        mvc.perform(post("/oauth/apple/bind-pending")
                .header("Authorization", "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
        verifyNoInteractions(jwt, sessions);
    }

    @Test
    void bindingRejectsInvalidCsrfEvenWithValidBearer() throws Exception {
        mvc.perform(post("/oauth/apple/bind-pending").with(csrf().useInvalidToken())
                .header("Authorization", "Bearer access-token"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(jwt, sessions);
    }

    @Test
    void validBearerAndCsrfCanBind() throws Exception {
        mvc.perform(post("/oauth/apple/bind-pending").with(csrf())
                .header("Authorization", "Bearer access-token"))
            .andExpect(status().isOk());
    }

    @Test
    void missingBearerCannotBind() throws Exception {
        mvc.perform(post("/oauth/apple/bind-pending").with(csrf()))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void expiredBearerCannotBind() throws Exception {
        when(jwt.isTokenValid("access-token")).thenReturn(false);
        mvc.perform(post("/oauth/apple/bind-pending").with(csrf())
                .header("Authorization", "Bearer access-token"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void revokedBearerCannotBind() throws Exception {
        when(blacklist.isBlacklisted("access-token")).thenReturn(true);
        mvc.perform(post("/oauth/apple/bind-pending").with(csrf())
                .header("Authorization", "Bearer access-token"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void otherProtectedOperationsStillRequireCsrf() throws Exception {
        mvc.perform(post("/test/protected")
                .header("Authorization", "Bearer access-token"))
            .andExpect(status().is4xxClientError());
        mvc.perform(post("/test/protected").with(csrf())
                .header("Authorization", "Bearer access-token"))
            .andExpect(status().isOk());
    }

    @Test
    void errorDispatchDoesNotReplaceServerFailureWithLoginFailure() throws Exception {
        mvc.perform(post("/error").with(request -> { request.setDispatcherType(DispatcherType.ERROR); return request; })
                .requestAttr("jakarta.servlet.error.status_code", 500)
                .requestAttr("jakarta.servlet.error.request_uri", "/oauth/apple/bind-pending"))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(500));
    }

    @Test
    void errorDispatchDoesNotReplaceMalformedRequestWithLoginFailure() throws Exception {
        mvc.perform(post("/error").with(request -> { request.setDispatcherType(DispatcherType.ERROR); return request; })
                .requestAttr("jakarta.servlet.error.status_code", 400)
                .requestAttr("jakarta.servlet.error.request_uri", "/oauth/apple/bind-pending"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void directErrorRequestStillRequiresAuthentication() throws Exception {
        mvc.perform(post("/error").with(csrf()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void otherProviderBindingsStillRequireAuthentication() throws Exception {
        mvc.perform(post("/oauth/qq/bind-pending"))
            .andExpect(status().isUnauthorized());
    }

    @Configuration
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class TestConfiguration {
        @Bean JwtUtil jwt() { return mock(JwtUtil.class); }
        @Bean UserSessionService sessions() { return mock(UserSessionService.class); }
        @Bean TokenBlacklistService blacklist() { return mock(TokenBlacklistService.class); }
        @Bean AppProperties properties() { return new AppProperties(); }
        @Bean JwtAuthenticationFilter jwtFilter(JwtUtil jwt, UserSessionService sessions, TokenBlacklistService blacklist) {
            return new JwtAuthenticationFilter(jwt, sessions, blacklist);
        }
        @Bean BindingRoutes routes() { return new BindingRoutes(); }
    }

    @RestController
    static class BindingRoutes {
        @PostMapping({"/oauth/apple/bind-pending", "/oauth/qq/bind-pending", "/test/protected"})
        Map<String, Object> bind(Authentication auth) {
            return Map.of("uuid", auth.getPrincipal().toString());
        }

        @PostMapping("/error")
        ResponseEntity<Map<String, Object>> error(HttpServletRequest request) {
            Integer code = (Integer) request.getAttribute("jakarta.servlet.error.status_code");
            int status = code == null ? 500 : code;
            return ResponseEntity.status(status).body(Map.of("code", status));
        }
    }
}
