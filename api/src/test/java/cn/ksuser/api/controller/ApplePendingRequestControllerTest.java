package cn.ksuser.api.controller;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.config.AppleAuthProperties;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserSession;
import cn.ksuser.api.dto.AdaptiveAuthStatusResponse;
import cn.ksuser.api.repository.*;
import cn.ksuser.api.security.SecurityValidator;
import cn.ksuser.api.service.*;
import cn.ksuser.api.util.JwtUtil;
import cn.ksuser.api.util.SensitiveLogUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MVC Jackson 3 parsing and Apple service, with only storage/provider dependencies mocked. */
class ApplePendingRequestControllerTest {
    private static final String TICKET = "a".repeat(43);
    private static final String TERMS_MESSAGE = "请先同意服务协议与隐私政策";
    private static final AppleAuthService.Context CONTEXT =
        new AppleAuthService.Context("binding-user", 42L, "127.0.0.1", "test-ios");
    private final Map<String, String> pending = new HashMap<>();
    private UserRepository users;
    private UserOauthAccountRepository accounts;
    private MockMvc mvc;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.getAndDelete(anyString())).thenAnswer(invocation -> pending.remove(invocation.getArgument(0)));
        users = mock(UserRepository.class);
        accounts = mock(UserOauthAccountRepository.class);
        UserSettingsRepository settings = mock(UserSettingsRepository.class);
        UserPasskeyRepository passkeys = mock(UserPasskeyRepository.class);
        UserSessionService sessions = mock(UserSessionService.class);
        User user = new User(CONTEXT.uuid(), "existing-user", "user@example.invalid", "password-hash");
        user.setId(1L);
        UserSession session = new UserSession();
        session.setId(CONTEXT.sessionId());
        session.setUser(user);
        when(users.findByUuid(CONTEXT.uuid())).thenReturn(Optional.of(user));
        when(users.findByUuidForUpdate(CONTEXT.uuid())).thenReturn(Optional.of(user));
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User created = invocation.getArgument(0);
            created.setId(2L);
            return created;
        });
        when(sessions.findActiveSessionById(CONTEXT.sessionId())).thenReturn(Optional.of(session));
        when(sessions.createSession(any(), anyString(), anyString(), anyString())).thenReturn(session);
        AppProperties properties = new AppProperties();
        AppleAuthProperties appleProperties = new AppleAuthProperties();
        AppleAuthService apple = new AppleAuthService(redis, appleProperties, mock(AppleTokenClient.class),
            new AppleCredentialCipher(appleProperties), users, accounts, passkeys, settings,
            new SecurityValidator(properties), mock(AppleRevocationService.class), sessions);
        RateLimitService rate = mock(RateLimitService.class);
        when(rate.getClientIp(any())).thenReturn(CONTEXT.ip());
        when(rate.getClientUserAgent(any())).thenReturn(CONTEXT.userAgent());
        when(rate.isIpAllowed(CONTEXT.ip(), RateLimitService.TYPE_LOGIN)).thenReturn(true);
        SensitiveOperationService sensitive = mock(SensitiveOperationService.class);
        when(sensitive.isVerified(CONTEXT.uuid(), CONTEXT.ip())).thenReturn(true);
        JwtUtil jwt = mock(JwtUtil.class);
        when(jwt.getSessionId("access-token")).thenReturn(CONTEXT.sessionId());
        when(jwt.generateRefreshToken(anyString())).thenReturn("refresh-token");
        when(jwt.generateAccessToken(anyString(), anyLong(), anyInt())).thenReturn("new-access-token");
        AdaptiveRiskOrchestrationService adaptive = mock(AdaptiveRiskOrchestrationService.class);
        when(adaptive.evaluateAndApply(any(), any(), anyString(), anyString(), anyString()))
            .thenReturn(new AdaptiveAuthStatusResponse());
        AppleAuthController controller = new AppleAuthController(apple, settings, passkeys, users,
            mock(TotpService.class), mock(MfaService.class), rate, sensitive, sessions, jwt,
            properties, mock(SensitiveLogUtil.class), adaptive);
        // Spring Framework 7 / Boot 4 uses Jackson 3, which rejects missing primitive creator values.
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(new JacksonJsonHttpMessageConverter()).build();
    }

    private void ticket(String purpose) throws Exception {
        AppleAuthService.Pending identity = new AppleAuthService.Pending(purpose, "cn.ksuser.ios", "apple-user",
            null, "", "encrypted-credential", CONTEXT);
        pending.put("apple:pending:" + TICKET, new ObjectMapper().writeValueAsString(identity));
    }

    private void bindWith(String terms) throws Exception {
        ticket("bind");
        mvc.perform(post("/oauth/apple/bind-pending")
                .principal(new UsernamePasswordAuthenticationToken(CONTEXT.uuid(), null, List.of()))
                .header("Authorization", "Bearer access-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"oauthBindToken\":\"" + TICKET + "\"" + terms + "}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.bound").value(true));
        verify(accounts).saveAndFlush(argThat(account -> account.getUserId().equals(1L)
            && "apple".equals(account.getProvider())));
        verify(users, never()).saveAndFlush(any());
    }

    @Test void bindingDoesNotRequireRegistrationTermsField() throws Exception { bindWith(""); }
    @Test void bindingAllowsExplicitNullRegistrationTerms() throws Exception { bindWith(",\"acceptTerms\":null"); }
    @Test void bindingAllowsFalseRegistrationTerms() throws Exception { bindWith(",\"acceptTerms\":false"); }

    private void rejectsRegistrationTerms(String terms) throws Exception {
        ticket("login");
        mvc.perform(post("/oauth/apple/register-pending").contentType(MediaType.APPLICATION_JSON)
                .content("{\"oauthBindToken\":\"" + TICKET + "\"" + terms + "}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.msg").value(TERMS_MESSAGE));
        verify(users, never()).saveAndFlush(any());
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test void registrationRejectsMissingTerms() throws Exception { rejectsRegistrationTerms(""); }
    @Test void registrationRejectsNullTerms() throws Exception { rejectsRegistrationTerms(",\"acceptTerms\":null"); }
    @Test void registrationRejectsFalseTerms() throws Exception { rejectsRegistrationTerms(",\"acceptTerms\":false"); }

    @Test
    void registrationAcceptsExplicitTerms() throws Exception {
        ticket("login");
        mvc.perform(post("/oauth/apple/register-pending").contentType(MediaType.APPLICATION_JSON)
                .content("{\"oauthBindToken\":\"" + TICKET + "\",\"acceptTerms\":true}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.accessToken").value("new-access-token"));
        verify(users).saveAndFlush(any());
        verify(accounts).saveAndFlush(any());
    }
}
