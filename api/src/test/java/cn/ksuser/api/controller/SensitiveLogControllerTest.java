package cn.ksuser.api.controller;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.config.SecurityConfig;
import cn.ksuser.api.dto.SensitiveLogResponse;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserSession;
import cn.ksuser.api.filter.JwtAuthenticationFilter;
import cn.ksuser.api.service.SensitiveLogService;
import cn.ksuser.api.service.TokenBlacklistService;
import cn.ksuser.api.service.UserService;
import cn.ksuser.api.service.UserSessionService;
import cn.ksuser.api.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig(SensitiveLogControllerTest.Configuration.class)
@TestPropertySource(properties = {"jwt.secret=unused-test-secret", "jwt.access-token-expiration=300000", "jwt.refresh-token-expiration=600000"})
class SensitiveLogControllerTest {
    @Autowired private WebApplicationContext context;
    @Autowired private JwtUtil jwt;
    @Autowired private UserSessionService sessions;
    @Autowired private TokenBlacklistService blacklist;
    @MockitoBean private UserService users;
    @MockitoBean private SensitiveLogService logs;
    private MockMvc mvc;

    @BeforeEach void setUp() {
        reset(jwt, sessions, blacklist, users, logs);
        var user = new User(); user.setId(7L); user.setUuid("log-owner");
        var session = new UserSession(); session.setId(42L); session.setUser(user); session.setSessionVersion(3);
        when(jwt.getUuidFromToken("access-token")).thenReturn("log-owner");
        when(jwt.getTokenType("access-token")).thenReturn("access");
        when(jwt.getSessionId("access-token")).thenReturn(42L);
        when(jwt.getSessionVersion("access-token")).thenReturn(3);
        when(jwt.isTokenValid("access-token")).thenReturn(true);
        when(sessions.findActiveSessionById(42L)).thenReturn(Optional.of(session));
        when(users.findByUuid("log-owner")).thenReturn(Optional.of(user));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test void returnsOwnedNotificationTargetWithoutListFiltersOrPagination() throws Exception {
        var log = new SensitiveLogResponse(); log.setId(41L); log.setOperationType("LOGIN");
        when(logs.findLog(7L, 41L)).thenReturn(Optional.of(log));
        mvc.perform(get("/auth/sensitive-logs/41").header("Authorization", "Bearer access-token")
                .param("page", "2").param("result", "FAILURE").param("operationType", "CHANGE_PASSWORD"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(41))
                .andExpect(jsonPath("$.data.operationType").value("LOGIN"));
        verify(logs).findLog(7L, 41L);
        verifyNoMoreInteractions(logs);
    }

    @Test void missingAndOtherAccountRecordsReturnTheSameNotFoundResponse() throws Exception {
        for (long id : new long[]{99L, 100L}) {
            when(logs.findLog(7L, id)).thenReturn(Optional.empty());
            mvc.perform(get("/auth/sensitive-logs/" + id).header("Authorization", "Bearer access-token"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.data").doesNotExist());
            verify(logs).findLog(7L, id);
        }
    }

    @Test void absentInvalidAndRevokedSessionsCannotReadLogDetails() throws Exception {
        mvc.perform(get("/auth/sensitive-logs/41")).andExpect(status().isUnauthorized());
        mvc.perform(get("/auth/sensitive-logs/41").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
        when(sessions.findActiveSessionById(42L)).thenReturn(Optional.empty());
        mvc.perform(get("/auth/sensitive-logs/41").header("Authorization", "Bearer access-token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(logs);
    }

    @org.springframework.context.annotation.Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, JwtAuthenticationFilter.class, SensitiveLogController.class})
    static class Configuration {
        @Bean JwtUtil jwt() { return mock(JwtUtil.class); }
        @Bean UserSessionService sessions() { return mock(UserSessionService.class); }
        @Bean TokenBlacklistService blacklist() { return mock(TokenBlacklistService.class); }
        @Bean AppProperties properties() { return new AppProperties(); }
    }
}
