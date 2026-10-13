package cn.ksuser.api.filter;

import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.result.ApiErrorResponses;
import cn.ksuser.api.result.ApiResultCode;
import cn.ksuser.api.service.ConsoleSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Replaces passport authentication on Console routes, so bearer credentials cannot bypass its session. */
@Component
public class ConsoleSessionFilter extends OncePerRequestFilter {
    private final ConsoleSessionService sessions;
    public ConsoleSessionFilter(ConsoleSessionService sessions) { this.sessions = sessions; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/console/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        SecurityContextHolder.clearContext();
        response.setHeader("Cache-Control", "no-store");
        String path = request.getRequestURI().substring(request.getContextPath().length()).replaceAll("/+$", "");
        if ("OPTIONS".equals(request.getMethod()) || path.equals("/console/auth/start")
            || path.equals("/console/auth/exchange") || path.equals("/console/csrf-token")
            || path.equals("/console/auth/qr/init") || path.equals("/console/auth/qr/status")
            || path.equals("/console/auth/qr/exchange") || path.equals("/console/auth/qr")) {
            chain.doFilter(request, response);
            return;
        }
        try {
            ConsoleSessionService.ConsolePrincipal principal = sessions.authenticate(request);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,
                null, List.of(new SimpleGrantedAuthority("CONSOLE_SUPER_ADMIN"))));
        } catch (Oauth2Exception ex) {
            ApiErrorResponses.write(request, response, ex.getStatus().value(), ex.getResult().errorCode());
            return;
        } catch (Exception ex) {
            ApiErrorResponses.write(request, response, 503, ApiResultCode.INTERNAL_ERROR);
            return;
        }
        chain.doFilter(request, response);
    }
}
