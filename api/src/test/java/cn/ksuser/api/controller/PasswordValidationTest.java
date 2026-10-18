package cn.ksuser.api.controller;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.config.SecurityConfig;
import cn.ksuser.api.filter.JwtAuthenticationFilter;
import cn.ksuser.api.result.PasswordRequirementResult;
import cn.ksuser.api.security.SecurityValidator;
import cn.ksuser.api.service.RegistrationValidationService;
import cn.ksuser.api.service.TokenBlacklistService;
import cn.ksuser.api.service.UserSessionService;
import cn.ksuser.api.service.VerificationCodeService;
import cn.ksuser.api.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real registration policy and security chain, without external persistence. */
@SpringJUnitWebConfig(PasswordValidationTest.TestConfiguration.class)
class PasswordValidationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private AppProperties properties;
    @Autowired private SecurityValidator validator;
    @Autowired private VerificationCodeService codes;
    @Autowired private RegistrationValidationService validation;
    private MockMvc mvc;

    @BeforeEach void setUp() {
        reset(validator, codes);
        var password = properties.getPassword();
        password.setMinLength(8);
        password.setMaxLength(64);
        password.setRequireUppercase(true);
        password.setRequireLowercase(true);
        password.setRequireDigits(true);
        password.setRequireSpecialChars(false);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test void anonymousPreflightAcceptsPasswordWithoutConsumingEmailProof() throws Exception {
        mvc.perform(post("/info/validate-password").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body("  StrongPassword123!  ")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errorCode").value("REQUEST_SUCCESS"))
            .andExpect(jsonPath("$.data").doesNotExist())
            .andExpect(header().string("Cache-Control", "no-store"));
        verifyNoInteractions(codes);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "short1A", "alllowercase", "Password123", "pAsSwOrD123"})
    void preflightReturnsSameRejectionAsFinalRegistration(String password) throws Exception {
        var expected = validation.validatePasswordRegistration("valid_user", "user@example.com", password, "123456");
        assertNotNull(expected);
        mvc.perform(post("/info/validate-password").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(password)))
            .andExpect(status().is(expected.status().value()))
            .andExpect(jsonPath("$.errorCode").value(expected.result().errorCode().name()))
            .andExpect(header().string("Cache-Control", "no-store"));
        verifyNoInteractions(codes);
    }

    @Test void updatedServerWeakPasswordRuleAppliesToPreflightAndRegistration() throws Exception {
        String password = "PreviouslyAllowed123";
        mvc.perform(post("/info/validate-password").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(password)))
            .andExpect(status().isOk());
        doReturn(true).when(validator).isCommonWeakPassword(password);
        mvc.perform(post("/info/validate-password").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(password)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("AUTH_021"));
        var rejection = validation.validatePasswordRegistration("valid_user", "user@example.com", password, "123456");
        assertNotNull(rejection);
        assertEquals("AUTH_021", rejection.result().errorCode().name());
        verifyNoInteractions(codes);
    }

    @Test void updatedCharacterRequirementsAndLengthParamsAreReturned() throws Exception {
        properties.getPassword().setRequireSpecialChars(true);
        mvc.perform(post("/info/validate-password").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body("StrongPassword123")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value(PasswordRequirementResult.from(properties.getPassword()).name()));
        properties.getPassword().setMinLength(20);
        mvc.perform(post("/info/validate-password").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body("StrongPassword123!")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("AUTH_020"))
            .andExpect(jsonPath("$.params.min").value(20)).andExpect(jsonPath("$.params.max").value(64));
        verifyNoInteractions(codes);
    }

    @Test void preflightRequiresCsrfAndPost() throws Exception {
        mvc.perform(post("/info/validate-password")
                .contentType(MediaType.APPLICATION_JSON).content(body("StrongPassword123")))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("SECURITY_CSRF_INVALID"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/info/validate-password"))
            .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(validator, codes);
    }

    private String body(String password) throws Exception {
        return new ObjectMapper().writeValueAsString(password == null ? Map.of() : Map.of("password", password));
    }

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, InfoController.class, RegistrationValidationService.class})
    static class TestConfiguration {
        @Bean AppProperties properties() { return new AppProperties(); }
        @Bean SecurityValidator validator(AppProperties properties) { return spy(new SecurityValidator(properties)); }
        @Bean VerificationCodeService codes() { return mock(VerificationCodeService.class); }
        @Bean JwtAuthenticationFilter jwtFilter() {
            return new JwtAuthenticationFilter(mock(JwtUtil.class), mock(UserSessionService.class), mock(TokenBlacklistService.class));
        }
    }
}
