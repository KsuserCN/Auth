package cn.ksuser.api.config;

import cn.ksuser.api.filter.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RequestMappingHandlerMapping handlerMapping;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, 
                          @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.handlerMapping = handlerMapping;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> {})
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/", "/auth/health", "/auth/health/",
                    "/auth/register", "/auth/register/", "/auth/login", "/auth/login/",
                    "/auth/login-with-code", "/auth/login-with-code/",
                    "/auth/refresh", "/auth/refresh/", "/auth/logout", "/auth/logout/",
                    "/auth/check-username", "/auth/check-username/", "/auth/send-code", "/auth/send-code/",
                    "/auth/passkey/authentication-options", "/auth/passkey/authentication-options/",
                    "/auth/passkey/authentication-verify", "/auth/passkey/authentication-verify/",
                    "/info/password-requirement", "/info/password-requirement/")
                .permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    // 检查路由是否存在
                    try {
                        HandlerExecutionChain handler = handlerMapping.getHandler(request);
                        if (handler == null) {
                            // 路由不存在，返回404
                            response.setStatus(404);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"code\":404,\"msg\":\"请求的资源不存在\"}");
                            return;
                        }
                    } catch (Exception e) {
                        // 如果检查过程出错，继续返回401
                    }
                    
                    // 路由存在但未认证，返回401
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"msg\":\"未登录或Token已过期\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(403);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":403,\"msg\":\"无权限\"}");
                })
            )
            .csrf(csrf -> csrf
                // 使用 Cookie CSRF Token Repository，但改为标准 Spring 配置
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                // CSRF Token 在 Cookie 中的名称
                .csrfTokenRequestHandler(new org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler())
                // 只为查询接口排除 CSRF 检查
                .ignoringRequestMatchers(
                    "/auth/check-username", "/auth/check-username/"
                )
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
