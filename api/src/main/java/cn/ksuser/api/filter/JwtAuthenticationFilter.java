package cn.ksuser.api.filter;

import cn.ksuser.api.entity.User;
import cn.ksuser.api.service.UserService;
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
    private final UserService userService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserService userService) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        try {
            String token = extractTokenFromRequest(request);
            
            if (StringUtils.hasText(token)) {
                String uuid = jwtUtil.getUuidFromToken(token);
                String tokenType = jwtUtil.getTokenType(token);
                Integer tokenVersion = jwtUtil.getTokenVersion(token);

                if (StringUtils.hasText(uuid)
                    && jwtUtil.isTokenValid(token)
                    && "access".equals(tokenType)
                    && tokenVersion != null) {
                    User user = userService.findByUuid(uuid).orElse(null);
                    if (user != null && tokenVersion.equals(user.getTokenVersion())) {
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
