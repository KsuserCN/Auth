package cn.ksuser.api.config;

import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserSession;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.filter.ConsoleSessionFilter;
import cn.ksuser.api.filter.JwtAuthenticationFilter;
import cn.ksuser.api.result.ApiResultCode;
import cn.ksuser.api.service.ConsoleSessionService;
import cn.ksuser.api.service.TokenBlacklistService;
import cn.ksuser.api.service.UserSessionService;
import cn.ksuser.api.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real production filter ordering and authorization/CSRF rules, with persistence-free session checks. */
@SpringJUnitWebConfig(SecurityConfigConsoleTest.TestConfiguration.class)
@TestPropertySource(properties={"jwt.secret=unused-test-secret","jwt.access-token-expiration=300000","jwt.refresh-token-expiration=600000"})
class SecurityConfigConsoleTest {
    @Autowired private WebApplicationContext context;
    @Autowired private JwtUtil jwt;
    @Autowired private UserSessionService passportSessions;
    @Autowired private TokenBlacklistService blacklist;
    @Autowired private ConsoleSessionService consoleSessions;
    private MockMvc mvc;
    private static final Cookie CONSOLE_COOKIE=new Cookie("consoleSession","valid-console-cookie");
    private static final ConsoleSessionService.ConsolePrincipal PRINCIPAL=new ConsoleSessionService.ConsolePrincipal("console-id","super-uuid",7L,Long.MAX_VALUE,0);

    @BeforeEach
    void setup() {
        reset(jwt,passportSessions,blacklist,consoleSessions);
        User user=new User();user.setUuid("passport-user");
        UserSession session=new UserSession();session.setId(42L);session.setUser(user);session.setSessionVersion(3);
        when(jwt.getUuidFromToken("passport-token")).thenReturn(user.getUuid());
        when(jwt.getTokenType("passport-token")).thenReturn("access");
        when(jwt.getSessionId("passport-token")).thenReturn(42L);
        when(jwt.getSessionVersion("passport-token")).thenReturn(3);
        when(jwt.isTokenValid("passport-token")).thenReturn(true);
        when(passportSessions.findActiveSessionById(42L)).thenReturn(Optional.of(session));
        when(consoleSessions.authenticate(any())).thenAnswer(call->{
            jakarta.servlet.http.HttpServletRequest request=call.getArgument(0);
            Cookie[] cookies=request.getCookies();
            if (cookies!=null) for (Cookie cookie:cookies) {
                if ("consoleSession".equals(cookie.getName())) {
                    if ("valid-console-cookie".equals(cookie.getValue())) return PRINCIPAL;
                    if ("demoted-console-cookie".equals(cookie.getValue())) throw new Oauth2Exception(HttpStatus.FORBIDDEN,"access_denied",ApiResultCode.CONSOLE_ACCESS_DENIED);
                }
            }
            throw new Oauth2Exception(HttpStatus.UNAUTHORIZED,"invalid_token",ApiResultCode.CONSOLE_SESSION_INVALID);
        });
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void passportAndOfficialBearerTokensCannotAccessConsole() throws Exception {
        for (String token:List.of("passport-token","ofa_other-application-token")) {
            mvc.perform(get("/console/session").header("Authorization","Bearer "+token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("CONSOLE_SESSION_INVALID"));
        }
        verify(passportSessions).findActiveSessionById(42L); // Passport really authenticated before Console cleared it.
    }

    @Test
    void validConsoleCookieUsesConsoleIdentityEvenWithAnotherPassportBearer() throws Exception {
        mvc.perform(get("/console/session").cookie(CONSOLE_COOKIE).header("Authorization","Bearer passport-token"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.uuid").value("super-uuid"))
            .andExpect(jsonPath("$.authority").value("CONSOLE_SUPER_ADMIN"));
    }

    @Test
    void consoleCookieCannotAuthenticatePassportAndVerificationRoutes() throws Exception {
        for (String path:List.of("/auth/test-profile","/auth/console-verification/context")) {
            mvc.perform(get(path).cookie(CONSOLE_COOKIE)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/auth/console-verification/complete").cookie(CONSOLE_COOKIE).with(csrf()))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(consoleSessions);
        mvc.perform(get("/auth/test-profile").cookie(CONSOLE_COOKIE).header("Authorization","Bearer passport-token"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.uuid").value("passport-user"));
    }

    @Test
    void demotedOrRevokedSessionFailsBeforeControllerRuns() throws Exception {
        mvc.perform(get("/console/session").cookie(new Cookie("consoleSession","demoted-console-cookie")))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("CONSOLE_ACCESS_DENIED"));
        mvc.perform(get("/console/session").cookie(new Cookie("consoleSession","expired-console-cookie")))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("CONSOLE_SESSION_INVALID"));
    }

    @Test
    void everyConsoleWriteIncludingPublicLoginAndStepUpRequiresCsrf() throws Exception {
        List<WriteRoute> writes=List.of(
            new WriteRoute(HttpMethod.POST,"/console/auth/start"),
            new WriteRoute(HttpMethod.POST,"/console/auth/exchange"),
            new WriteRoute(HttpMethod.POST,"/console/auth/qr/init"),
            new WriteRoute(HttpMethod.POST,"/console/auth/qr/exchange"),
            new WriteRoute(HttpMethod.DELETE,"/console/auth/qr"),
            new WriteRoute(HttpMethod.DELETE,"/console/session"),
            new WriteRoute(HttpMethod.POST,"/console/verification/start"),
            new WriteRoute(HttpMethod.POST,"/console/verification/exchange"),
            new WriteRoute(HttpMethod.PUT,"/console/users/user-uuid/role"),
            new WriteRoute(HttpMethod.PUT,"/console/users/user-uuid/verification"),
            new WriteRoute(HttpMethod.POST,"/console/official-applications"),
            new WriteRoute(HttpMethod.PUT,"/console/official-applications/client"),
            new WriteRoute(HttpMethod.DELETE,"/console/official-applications/client"),
            new WriteRoute(HttpMethod.POST,"/console/official-applications/client/rotate-secret"));
        for (WriteRoute write:writes) {
            mvc.perform(write.request().cookie(CONSOLE_COOKIE)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("SECURITY_CSRF_INVALID"));
            mvc.perform(write.request().cookie(CONSOLE_COOKIE).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("SECURITY_CSRF_INVALID"));
            mvc.perform(write.request().cookie(CONSOLE_COOKIE).with(csrf())).andExpect(status().isOk());
        }
    }

    @Test
    void qrBootstrapAndStatusArePublicButNeverInheritPassportIdentity() throws Exception {
        for (String path:List.of("/console/auth/qr/init","/console/auth/qr/exchange")) {
            mvc.perform(post(path).with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.passportAuthenticationInherited").value(false));
            mvc.perform(post(path).with(csrf()).header("Authorization","Bearer passport-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.passportAuthenticationInherited").value(false));
        }
        mvc.perform(get("/console/auth/qr/status")).andExpect(status().isOk())
            .andExpect(jsonPath("$.passportAuthenticationInherited").value(false));
        mvc.perform(delete("/console/auth/qr").with(csrf())).andExpect(status().isOk());
        verifyNoInteractions(consoleSessions);
        mvc.perform(post("/console/auth/qr/unknown").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void officialQrBrowserEndpointsArePublicButWritesKeepCsrfAndMobileDecisionsRequirePassport() throws Exception {
        for (String path:List.of("/official/qr/init","/official/qr/exchange","/official/qr/cancel","/auth/mobile-authorization/qr/create","/auth/mobile-authorization/consume","/auth/mobile-authorization/cancel")) {
            mvc.perform(post(path)).andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("SECURITY_CSRF_INVALID"));
            mvc.perform(post(path).with(csrf())).andExpect(status().isOk());
        }
        mvc.perform(get("/official/qr/status")).andExpect(status().isOk());
        mvc.perform(get("/auth/mobile-authorization/qr/status")).andExpect(status().isOk());
        for (String path:List.of("/auth/qr/official/approve","/auth/qr/official/reject","/auth/mobile-authorization/decide")) {
            mvc.perform(post(path).with(csrf())).andExpect(status().isUnauthorized());
            mvc.perform(post(path).header("Authorization","Bearer passport-token")).andExpect(status().isForbidden());
            mvc.perform(post(path).header("Authorization","Bearer passport-token").with(csrf())).andExpect(status().isOk());
        }
    }

    @Test
    void nativeProofCompletionNeedsPassportAndCsrfAndPublicLoginNeverInheritsPassport() throws Exception {
        for (String path:List.of("/auth/console-verification/complete","/auth/verify-sensitive","/auth/passkey/sensitive-verification-verify")) {
            mvc.perform(post(path).header("Authorization","Bearer passport-token"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("SECURITY_CSRF_INVALID"));
            mvc.perform(post(path).header("Authorization","Bearer passport-token").with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.uuid").value("passport-user"));
        }
        mvc.perform(post("/console/auth/start").header("Authorization","Bearer passport-token").with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.passportAuthenticationInherited").value(false));
    }

    @Test
    void officialTokenProtocolIsCsrfExemptButConsentStillRequiresCsrfAndPassport() throws Exception {
        mvc.perform(post("/official/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type","authorization_code").param("client_id","official-client"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.protocolReached").value(true));
        mvc.perform(post("/official/authorize/approve").header("Authorization","Bearer passport-token"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("SECURITY_CSRF_INVALID"));
        mvc.perform(post("/official/authorize/approve").with(csrf()))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/official/authorize/approve").header("Authorization","Bearer passport-token").with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.uuid").value("passport-user"));
    }

    @Test
    void nativeCapabilitiesArePublicButConsentRequiresPassportAndCsrf() throws Exception {
        for (String path : List.of("/auth/native-authorization/create", "/auth/native-authorization/cancel")) {
            mvc.perform(post(path)).andExpect(status().isOk());
        }
        mvc.perform(get("/auth/native-authorization/context")).andExpect(status().isUnauthorized());
        mvc.perform(get("/auth/native-authorization/context").cookie(CONSOLE_COOKIE)).andExpect(status().isUnauthorized());
        mvc.perform(get("/auth/native-authorization/context").header("Authorization", "Bearer passport-token"))
            .andExpect(status().isOk());
        mvc.perform(post("/auth/native-authorization/decide").header("Authorization", "Bearer passport-token"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("SECURITY_CSRF_INVALID"));
        mvc.perform(post("/auth/native-authorization/decide").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/native-authorization/decide").with(csrf()).header("Authorization", "Bearer passport-token"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.uuid").value("passport-user"));
        mvc.perform(post("/auth/native-authorization/consume").with(csrf())).andExpect(status().isNotFound());
    }

    @Configuration @EnableWebMvc @Import(SecurityConfig.class)
    static class TestConfiguration {
        @Bean JwtUtil jwt() { return mock(JwtUtil.class); }
        @Bean UserSessionService passportSessions() { return mock(UserSessionService.class); }
        @Bean TokenBlacklistService blacklist() { return mock(TokenBlacklistService.class); }
        @Bean ConsoleSessionService consoleSessions() { return mock(ConsoleSessionService.class); }
        @Bean AppProperties properties() { AppProperties properties=new AppProperties();properties.setDebug(true);return properties; }
        @Bean JwtAuthenticationFilter jwtFilter(JwtUtil jwt,UserSessionService sessions,TokenBlacklistService blacklist) { return new JwtAuthenticationFilter(jwt,sessions,blacklist); }
        @Bean ConsoleSessionFilter consoleFilter(ConsoleSessionService sessions) { return new ConsoleSessionFilter(sessions); }
        @Bean TestRoutes routes() { return new TestRoutes(); }
    }
    @RestController
    static class TestRoutes {
        @GetMapping("/console/session")
        Map<String,Object> console(Authentication auth) {
            ConsoleSessionService.ConsolePrincipal principal=(ConsoleSessionService.ConsolePrincipal)auth.getPrincipal();
            return Map.of("uuid",principal.userUuid(),"authority",auth.getAuthorities().iterator().next().getAuthority());
        }
        @RequestMapping(value={"/console/auth/start","/console/auth/exchange","/console/auth/qr/init","/console/auth/qr/exchange"},method=RequestMethod.POST)
        Map<String,Object> publicLogin(Authentication auth) { return Map.of("passportAuthenticationInherited",auth!=null); }
        @RequestMapping(value={"/console/auth/qr/status","/console/auth/qr"},method={RequestMethod.GET,RequestMethod.DELETE})
        Map<String,Object> publicQr(Authentication auth) { return Map.of("passportAuthenticationInherited",auth!=null); }
        @RequestMapping(value={"/official/qr/init","/official/qr/status","/official/qr/exchange","/official/qr/cancel","/auth/mobile-authorization/qr/create","/auth/mobile-authorization/qr/status","/auth/mobile-authorization/consume","/auth/mobile-authorization/cancel"},method={RequestMethod.GET,RequestMethod.POST})
        Map<String,Object> officialQr() { return Map.of("protocolReached",true); }
        @RequestMapping(value={"/console/session","/console/verification/start","/console/verification/exchange",
            "/console/users/{uuid}/role","/console/users/{uuid}/verification","/console/official-applications","/console/official-applications/{clientId}",
            "/console/official-applications/{clientId}/rotate-secret"},method={RequestMethod.POST,RequestMethod.PUT,RequestMethod.DELETE})
        Map<String,Object> write() { return Map.of("accepted",true); }
        @GetMapping({"/auth/test-profile","/auth/console-verification/context","/auth/native-authorization/context"})
        Map<String,Object> passport(Authentication auth) { return Map.of("uuid",auth.getPrincipal().toString()); }
        @PostMapping({"/auth/console-verification/complete","/auth/verify-sensitive","/auth/passkey/sensitive-verification-verify","/auth/qr/official/approve","/auth/qr/official/reject","/auth/mobile-authorization/decide"})
        Map<String,Object> complete(Authentication auth) { return passport(auth); }
        @PostMapping({"/auth/native-authorization/create", "/auth/native-authorization/cancel"})
        Map<String,Object> nativeCapability() { return Map.of("accepted", true); }
        @PostMapping("/auth/native-authorization/decide")
        Map<String,Object> nativeConsent(Authentication auth) { return passport(auth); }
        @PostMapping("/official/token")
        Map<String,Object> token() { return Map.of("protocolReached",true); }
        @PostMapping("/official/authorize/approve")
        Map<String,Object> approve(Authentication auth) { return passport(auth); }
    }
    private record WriteRoute(HttpMethod method,String path) {
        MockHttpServletRequestBuilder request() { return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(method,path).contentType(MediaType.APPLICATION_JSON).content("{}"); }
    }
}
