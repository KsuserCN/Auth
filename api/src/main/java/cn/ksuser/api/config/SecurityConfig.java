package cn.ksuser.api.config;

import cn.ksuser.api.filter.JwtAuthenticationFilter;
import cn.ksuser.api.filter.ConsoleSessionFilter;
import jakarta.servlet.DispatcherType;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

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
    public SecurityFilterChain filterChain(HttpSecurity http, ObjectProvider<ConsoleSessionFilter> consoleFilters) throws Exception {
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
                // Preserve the original failure when the servlet container dispatches to /error.
                // Normal requests still require the endpoint's authentication and CSRF checks.
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/console/auth/start", "/console/auth/exchange", "/console/csrf-token",
                    "/console/auth/qr/init", "/console/auth/qr/status", "/console/auth/qr/exchange", "/console/auth/qr",
                    "/official/qr/init", "/official/qr/status", "/official/qr/exchange", "/official/qr/cancel",
                    "/official/authorize/context", "/official/token", "/official/userinfo",
                    "/official/jwks", "/official/.well-known/openid-configuration").permitAll()
                .requestMatchers("/console/**").hasAuthority("CONSOLE_SUPER_ADMIN")
                .requestMatchers("/", "/auth/health", "/auth/health/",
                    "/auth/csrf-token", "/auth/csrf-token/",
                    "/auth/register", "/auth/register/", "/auth/login", "/auth/login/",
                    "/auth/login-with-code", "/auth/login-with-code/",
                    "/auth/session-transfer/exchange", "/auth/session-transfer/exchange/",
                    "/auth/mobile-authorization/create", "/auth/mobile-authorization/create/",
                    "/auth/mobile-authorization/qr/create", "/auth/mobile-authorization/qr/create/",
                    "/auth/mobile-authorization/qr/status", "/auth/mobile-authorization/qr/status/",
                    "/auth/mobile-authorization/consume", "/auth/mobile-authorization/consume/",
                    "/auth/mobile-authorization/cancel", "/auth/mobile-authorization/cancel/",
                    "/auth/mobile-bridge/create", "/auth/mobile-bridge/create/",
                    "/auth/mobile-bridge/status", "/auth/mobile-bridge/status/",
                    "/auth/mobile-bridge/cancel", "/auth/mobile-bridge/cancel/",
                    "/auth/account-recovery/qr/init", "/auth/account-recovery/qr/init/",
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
                    "/official/token", "/official/token/",
                    "/oauth/qq/callback/login", "/oauth/qq/callback/login/",
                    "/oauth/qq/mobile-login", "/oauth/qq/mobile-login/",
                    "/oauth/apple/challenge", "/oauth/apple/challenge/",
                    "/oauth/apple/mobile-login", "/oauth/apple/mobile-login/",
                    "/oauth/apple/register-pending", "/oauth/apple/register-pending/",
                    "/oauth/apple/notifications", "/oauth/apple/notifications/",
                    "/oauth/qq/bind-existing", "/oauth/qq/bind-existing/",
                    "/oauth/qq/register-bind", "/oauth/qq/register-bind/",
                    "/oauth/github/bind-existing", "/oauth/github/bind-existing/",
                    "/oauth/github/register-bind", "/oauth/github/register-bind/",
                    "/oauth/github/callback/login", "/oauth/github/callback/login/",
                    "/oauth/microsoft/bind-existing", "/oauth/microsoft/bind-existing/",
                    "/oauth/microsoft/register-bind", "/oauth/microsoft/register-bind/",
                    "/oauth/microsoft/callback/login", "/oauth/microsoft/callback/login/",
                    "/oauth/google/bind-existing", "/oauth/google/bind-existing/",
                    "/oauth/google/register-bind", "/oauth/google/register-bind/",
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
                            cn.ksuser.api.result.ApiErrorResponses.write(request, response, 404, cn.ksuser.api.result.ApiResultCode.NOT_FOUND);
                            return;
                        }
                    } catch (Exception e) {
                        // 如果检查过程出错，继续返回401
                    }
                    
                    // 路由存在但未认证，返回401
                    cn.ksuser.api.result.ApiErrorResponses.write(request, response, 401, cn.ksuser.api.result.ApiResultCode.UNAUTHENTICATED);
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    cn.ksuser.api.result.ApiErrorResponses.write(request, response, 403,
                        accessDeniedException instanceof org.springframework.security.web.csrf.CsrfException
                            ? cn.ksuser.api.result.ApiResultCode.SECURITY_CSRF_INVALID : cn.ksuser.api.result.ApiResultCode.FORBIDDEN);
                })
            )
            .csrf(csrf -> csrf
                // 使用 Cookie CSRF Token Repository，但改为标准 Spring 配置
                .csrfTokenRepository(csrfTokenRepository)
                // CSRF Token 在 Cookie 中的名称
                .csrfTokenRequestHandler(new org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler())
                // 只为查询接口排除 CSRF 检查
                .ignoringRequestMatchers(
                    "/oauth/apple/notifications", "/oauth/apple/notifications/",
                    "/auth/check-username", "/auth/check-username/",
                    "/oauth/qq/callback/login", "/oauth/qq/callback/login/",
                    "/oauth/qq/mobile-login", "/oauth/qq/mobile-login/",
                    "/oauth/qq/callback/bind", "/oauth/qq/callback/bind/",
                    "/oauth/qq/callback/unbind", "/oauth/qq/callback/unbind/",
                    "/oauth/qq/bind-existing", "/oauth/qq/bind-existing/",
                    "/oauth/qq/register-bind", "/oauth/qq/register-bind/",
                    "/oauth/qq/bind-pending", "/oauth/qq/bind-pending/",
                    "/oauth/github/callback/login", "/oauth/github/callback/login/",
                    "/oauth/github/callback/bind", "/oauth/github/callback/bind/",
                    "/oauth/github/callback/unbind", "/oauth/github/callback/unbind/",
                    "/oauth/github/bind-existing", "/oauth/github/bind-existing/",
                    "/oauth/github/register-bind", "/oauth/github/register-bind/",
                    "/oauth/github/bind-pending", "/oauth/github/bind-pending/",
                    "/oauth/microsoft/callback/login", "/oauth/microsoft/callback/login/",
                    "/oauth/microsoft/callback/bind", "/oauth/microsoft/callback/bind/",
                    "/oauth/microsoft/callback/unbind", "/oauth/microsoft/callback/unbind/",
                    "/oauth/microsoft/unbind", "/oauth/microsoft/unbind/",
                    "/oauth/microsoft/bind-existing", "/oauth/microsoft/bind-existing/",
                    "/oauth/microsoft/register-bind", "/oauth/microsoft/register-bind/",
                    "/oauth/microsoft/bind-pending", "/oauth/microsoft/bind-pending/",
                    "/oauth/google/callback/login", "/oauth/google/callback/login/",
                    "/oauth/google/callback/bind", "/oauth/google/callback/bind/",
                    "/oauth/google/callback/unbind", "/oauth/google/callback/unbind/",
                    "/oauth/google/unbind", "/oauth/google/unbind/",
                    "/oauth/google/bind-existing", "/oauth/google/bind-existing/",
                    "/oauth/google/register-bind", "/oauth/google/register-bind/",
                    "/oauth/google/bind-pending", "/oauth/google/bind-pending/",
                    "/oauth2/token", "/oauth2/token/",
                    "/official/token", "/official/token/"
                )
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        ConsoleSessionFilter consoleFilter = consoleFilters.getIfAvailable();
        if (consoleFilter != null) {
            http.addFilterAfter(consoleFilter, JwtAuthenticationFilter.class);
        }
        return http.build();
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration() {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(jwtAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<ConsoleSessionFilter> consoleFilterRegistration(ObjectProvider<ConsoleSessionFilter> filters) {
        FilterRegistrationBean<ConsoleSessionFilter> registration = new FilterRegistrationBean<>();
        registration.setEnabled(false);
        ConsoleSessionFilter filter = filters.getIfAvailable();
        if (filter != null) registration.setFilter(filter);
        return registration;
    }
}
