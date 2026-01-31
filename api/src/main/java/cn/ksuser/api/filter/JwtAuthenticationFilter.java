package cn.ksuser.api.filter;

import cn.ksuser.api.entity.UserSession;
import cn.ksuser.api.service.UserSessionService;
import cn.ksuser.api.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserSessionService userSessionService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserSessionService userSessionService) {
        this.jwtUtil = jwtUtil;
        this.userSessionService = userSessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        try {
            String token = extractTokenFromRequest(request);
            
            if (StringUtils.hasText(token)) {
                String uuid = jwtUtil.getUuidFromToken(token);
                String tokenType = jwtUtil.getTokenType(token);
                Long sessionId = jwtUtil.getSessionId(token);
                Integer sessionVersion = jwtUtil.getSessionVersion(token);

                if (StringUtils.hasText(uuid)
                    && jwtUtil.isTokenValid(token)
                    && "access".equals(tokenType)
                    && sessionId != null
                    && sessionVersion != null) {
                    UserSession session = userSessionService.findActiveSessionById(sessionId).orElse(null);
                    if (session != null
                        && sessionVersion.equals(session.getSessionVersion())
                        && uuid.equals(session.getUser().getUuid())) {
                        UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(uuid, null, new ArrayList<>());
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Cannot set user authentication", e);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从请求中提取 JWT Token
     * @param request HttpServletRequest
     * @return token
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
