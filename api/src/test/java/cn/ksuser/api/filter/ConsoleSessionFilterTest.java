package cn.ksuser.api.filter;

import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.result.ApiResultCode;
import cn.ksuser.api.service.ConsoleSessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConsoleSessionFilterTest {
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    @Test void passportBearerCannotAuthenticateConsoleWithoutItsCookieSession() throws Exception {
        ConsoleSessionService sessions=mock(ConsoleSessionService.class);
        MockHttpServletRequest request=new MockHttpServletRequest("GET","/console/users");
        request.addHeader("Authorization","Bearer passport-token");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("passport-user",null,List.of()));
        when(sessions.authenticate(request)).thenThrow(new Oauth2Exception(HttpStatus.UNAUTHORIZED,"invalid_token",ApiResultCode.CONSOLE_SESSION_INVALID));
        MockHttpServletResponse response=new MockHttpServletResponse();
        AtomicBoolean invoked=new AtomicBoolean();
        new ConsoleSessionFilter(sessions).doFilter(request,response,(req,res)->invoked.set(true));
        assertEquals(401,response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertFalse(invoked.get());
    }
    @Test void onlyValidatedConsoleSessionCreatesConsoleAuthority() throws Exception {
        ConsoleSessionService sessions=mock(ConsoleSessionService.class);
        MockHttpServletRequest request=new MockHttpServletRequest("GET","/console/session");
        ConsoleSessionService.ConsolePrincipal principal=new ConsoleSessionService.ConsolePrincipal("id","uuid",7L,Long.MAX_VALUE,0);
        when(sessions.authenticate(request)).thenReturn(principal);
        new ConsoleSessionFilter(sessions).doFilter(request,new MockHttpServletResponse(),(req,res)-> {
            assertSame(principal,SecurityContextHolder.getContext().getAuthentication().getPrincipal());
            assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().anyMatch(value->value.getAuthority().equals("CONSOLE_SUPER_ADMIN")));
        });
    }
    @Test void publicLoginEndpointDoesNotCarryOverPassportAuthentication() throws Exception {
        ConsoleSessionService sessions=mock(ConsoleSessionService.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("passport-user",null,List.of()));
        new ConsoleSessionFilter(sessions).doFilter(new MockHttpServletRequest("POST","/console/auth/start"),new MockHttpServletResponse(),(req,res)->assertNull(SecurityContextHolder.getContext().getAuthentication()));
        verifyNoInteractions(sessions);
    }
}
