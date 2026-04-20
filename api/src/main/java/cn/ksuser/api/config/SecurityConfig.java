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
    private final AppProperties appProperties;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, 
                          @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
                          AppProperties appProperties) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.handlerMapping = handlerMapping;
        this.appProperties = appProperties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieCustomizer((builder) -> {
            builder.path("/");
            builder.secure(!appProperties.isDebug());
            if (!appProperties.isDebug()) {
                builder.domain("ksuser.cn");
            }
        });

        http
            .cors(cors -> {})
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/", "/auth/health", "/auth/health/",
                    "/auth/csrf-token", "/auth/csrf-token/",
                    "/.well-known/openid-configuration",
                    "/auth/register", "/auth/register/", "/auth/login", "/auth/login/",
                    "/auth/login-with-code", "/auth/login-with-code/",
                    "/auth/session-transfer/exchange", "/auth/session-transfer/exchange/",
                    "/auth/account-recovery/status", "/auth/account-recovery/status/",
                    "/auth/account-recovery/complete", "/auth/account-recovery/complete/",
                    "/auth/qr/preview", "/auth/qr/preview/",
                    "/auth/qr/login/init", "/auth/qr/login/init/",
                    "/auth/qr/mfa/init", "/auth/qr/mfa/init/",
                    "/auth/qr/status", "/auth/qr/status/",
                    "/auth/refresh", "/auth/refresh/", "/auth/logout", "/auth/logout/",
                    "/auth/check-username", "/auth/check-username/", "/auth/send-code", "/auth/send-code/",
                    "/auth/passkey/authentication-options", "/auth/passkey/authentication-options/",
                    "/auth/passkey/authentication-verify", "/auth/passkey/authentication-verify/",
                    "/auth/passkey/mfa-verify", "/auth/passkey/mfa-verify/",
                    "/auth/totp/mfa-verify", "/auth/totp/mfa-verify/",
                    "/oauth2/token", "/oauth2/token/",
                    "/oauth2/userinfo", "/oauth2/userinfo/",
                    "/oauth2/authorize/context", "/oauth2/authorize/context/",
                    "/sso/token", "/sso/token/",
                    "/sso/userinfo", "/sso/userinfo/",
                    "/sso/authorize/context", "/sso/authorize/context/",
                    "/oauth/qq/callback/login", "/oauth/qq/callback/login/",
                    "/oauth/qq/bind-existing", "/oauth/qq/bind-existing/",
                    "/oauth/qq/register-bind", "/oauth/qq/register-bind/",
                    "/oauth/github/callback/login", "/oauth/github/callback/login/",
                    "/oauth/microsoft/callback/login", "/oauth/microsoft/callback/login/",
                    "/oauth/google/callback/login", "/oauth/google/callback/login/",
                    "/info/password-requirement", "/info/password-requirement/",
                    "/static/**")
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
                .csrfTokenRepository(csrfTokenRepository)
                // CSRF Token 在 Cookie 中的名称
                .csrfTokenRequestHandler(new org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler())
                // 只为查询接口排除 CSRF 检查
                .ignoringRequestMatchers(
                    "/auth/check-username", "/auth/check-username/",
                    "/oauth/qq/callback/login", "/oauth/qq/callback/login/",
                    "/oauth/qq/callback/bind", "/oauth/qq/callback/bind/",
                    "/oauth/qq/callback/unbind", "/oauth/qq/callback/unbind/",
                    "/oauth/qq/bind-existing", "/oauth/qq/bind-existing/",
                    "/oauth/qq/register-bind", "/oauth/qq/register-bind/",
                    "/oauth/github/callback/login", "/oauth/github/callback/login/",
                    "/oauth/github/callback/bind", "/oauth/github/callback/bind/",
                    "/oauth/github/callback/unbind", "/oauth/github/callback/unbind/",
                    "/oauth/microsoft/callback/login", "/oauth/microsoft/callback/login/",
                    "/oauth/microsoft/callback/bind", "/oauth/microsoft/callback/bind/",
                    "/oauth/microsoft/callback/unbind", "/oauth/microsoft/callback/unbind/",
                    "/oauth/microsoft/unbind", "/oauth/microsoft/unbind/",
                    "/oauth/google/callback/login", "/oauth/google/callback/login/",
                    "/oauth/google/callback/bind", "/oauth/google/callback/bind/",
                    "/oauth/google/callback/unbind", "/oauth/google/callback/unbind/",
                    "/oauth/google/unbind", "/oauth/google/unbind/",
                    "/oauth2/token", "/oauth2/token/",
                    "/sso/token", "/sso/token/"
                )
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
