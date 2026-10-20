package cn.ksuser.api.controller;

import cn.ksuser.api.entity.User;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.result.ApiResultCode;
import cn.ksuser.api.service.NativeAuthorizationService;
import cn.ksuser.api.service.RateLimitService;
import cn.ksuser.api.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NativeAuthorizationControllerTest {
    private final NativeAuthorizationService service = mock(NativeAuthorizationService.class);
    private final UserService users = mock(UserService.class);
    private final RateLimitService rateLimit = mock(RateLimitService.class);
    private final User user = new User();
    private final UsernamePasswordAuthenticationToken principal = UsernamePasswordAuthenticationToken.authenticated("native-user", "unused", List.of());
    private MockMvc mvc;

    @BeforeEach void setup() {
        user.setId(7L); user.setUuid("native-user");
        when(users.findByUuid(user.getUuid())).thenReturn(Optional.of(user));
        when(rateLimit.getClientIp(any())).thenReturn("192.0.2.8");
        mvc = MockMvcBuilders.standaloneSetup(new NativeAuthorizationController(service, users, rateLimit)).build();
    }

    @Test void publicCreateUsesFrozenAuthorizationContractAndDoesNotCache() throws Exception {
        when(service.create(any(), eq("192.0.2.8"))).thenReturn(new NativeAuthorizationService.Created(
            "t".repeat(32), "s".repeat(32), "https://auth.ksuser.cn/app/sdk-authorize?ticket=" + "t".repeat(32),
            "https://auth.ksuser.cn/app/sdk-authorize/web?ticket=" + "t".repeat(32), 300));
        mvc.perform(post("/auth/native-authorization/create").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mode":"oauth","platform":"ios","authorization":{"clientId":"client","redirectUri":"https://client.example/callback","responseType":"code",
                    "state":"state","codeChallenge":"challenge","codeChallengeMethod":"S256"}}
                    """))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.data.ticket").value("t".repeat(32)))
            .andExpect(jsonPath("$.data.cancelSecret").value("s".repeat(32)))
            .andExpect(jsonPath("$.data.expiresInSeconds").value(300));
        verify(service).create(argThat(request -> "oauth".equals(request.mode())
            && "client".equals(request.authorization().getClientId()) && "challenge".equals(request.authorization().getCodeChallenge())), eq("192.0.2.8"));
        verifyNoInteractions(users);
    }

    @Test void decideResolvesPassportUserAndReturnsOnlyTheRegisteredCallback() throws Exception {
        when(service.decide(any(), eq(user))).thenReturn(new NativeAuthorizationService.Return("https://client.example/callback?code=once&state=random", true));
        mvc.perform(post("/auth/native-authorization/decide").principal(principal).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ticket\":\"ticket\",\"approve\":true,\"grantMode\":\"TIME_LIMITED\",\"grantTtlSeconds\":3600}"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.data.completed").value(true))
            .andExpect(jsonPath("$.data.returnUrl").value("https://client.example/callback?code=once&state=random"));
        verify(service).decide(argThat(input -> input.approve() && input.grantTtlSeconds() == 3600), eq(user));
    }

    @Test void contextRequiresAnActualAuthenticatedUser() throws Exception {
        mvc.perform(get("/auth/native-authorization/context").param("ticket", "ticket"))
            .andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
        when(users.findByUuid(user.getUuid())).thenReturn(Optional.empty());
        mvc.perform(get("/auth/native-authorization/context").principal(principal).param("ticket", "ticket"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void cancelReturnsStatusWithoutAnyAuthorizationCode() throws Exception {
        when(service.cancel(any())).thenReturn(new NativeAuthorizationService.Cancelled("decided"));
        mvc.perform(post("/auth/native-authorization/cancel").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ticket\":\"ticket\",\"cancelSecret\":\"private-secret\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("decided"))
            .andExpect(jsonPath("$.data.returnUrl").doesNotExist()).andExpect(jsonPath("$.data.code").doesNotExist());
        verifyNoInteractions(users);
    }

    @Test void rolloutAndProtocolErrorsStayStructuredAndUncached() throws Exception {
        when(service.create(any(), any())).thenThrow(new Oauth2Exception(HttpStatus.SERVICE_UNAVAILABLE,
            "temporarily_unavailable", ApiResultCode.NATIVE_AUTHORIZATION_UNAVAILABLE));
        mvc.perform(post("/auth/native-authorization/create").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isServiceUnavailable()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.errorCode").value("NATIVE_AUTHORIZATION_UNAVAILABLE"));
    }
}
