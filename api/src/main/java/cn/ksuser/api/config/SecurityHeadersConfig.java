package cn.ksuser.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
// import org.springframework.http.HttpHeaders;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 安全 HTTP Header 配置
 * 添加防护 Header 以提高应用安全性
 */
@Configuration
public class SecurityHeadersConfig {

    private final AppProperties appProperties;

    public SecurityHeadersConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /**
     * 自定义安全 Header 过滤器
     */
    @Bean
    public OncePerRequestFilter securityHeadersFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain
            ) throws ServletException, IOException {

                // Debug 模式下关闭安全 Header（本地开发）
                if (appProperties.isDebug()) {
                    filterChain.doFilter(request, response);
                    return;
                }
                
                // X-Content-Type-Options: 防止 MIME 类型嗅探
                response.setHeader("X-Content-Type-Options", "nosniff");
                
                // X-Frame-Options: 防止点击劫持
                response.setHeader("X-Frame-Options", "DENY");
                
                // X-XSS-Protection: 启用浏览器 XSS 防护
                response.setHeader("X-XSS-Protection", "1; mode=block");
                
                // Strict-Transport-Security: 强制 HTTPS
                response.setHeader("Strict-Transport-Security", 
                    "max-age=31536000; includeSubDomains; preload");
                
                // Content-Security-Policy: 防止 XSS 和其他注入攻击
                response.setHeader("Content-Security-Policy",
                    "default-src 'self'; " +
                    "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
                    "style-src 'self' 'unsafe-inline'; " +
                    "img-src 'self' data: https:; " +
                    "font-src 'self'; " +
                    "connect-src 'self'; " +
                    "frame-ancestors 'none'; " +
                    "base-uri 'self'; " +
                    "form-action 'self'");
                
                // Referrer-Policy: 控制 Referer 头
                response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
                
                // Permissions-Policy: 控制浏览器特性访问
                response.setHeader("Permissions-Policy",
                    "geolocation=(), " +
                    "microphone=(), " +
                    "camera=(), " +
                    "payment=(), " +
                    "usb=(), " +
                    "magnetometer=(), " +
                    "gyroscope=(), " +
                    "accelerometer=()");
                
                // Cache-Control: 防止敏感信息被缓存
                if (request.getRequestURI().contains("/auth/")) {
                    response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
                    response.setHeader("Pragma", "no-cache");
                    response.setHeader("Expires", "0");
                }
                
                // 移除服务器信息
                response.setHeader("Server", "");
                
                filterChain.doFilter(request, response);
            }
        };
    }

    /**
     * 无缓存 Header 写入器 - 用于 Spring Security 配置
     */
    @Bean
    public StaticHeadersWriter noCacheHeaderWriter() {
        return new StaticHeadersWriter(
            "Cache-Control", "no-cache, no-store, must-revalidate",
            "Pragma", "no-cache",
            "Expires", "0"
        );
    }

    /**
     * XSS 防护 Header 写入器
     */
    @Bean
    public StaticHeadersWriter xssProtectionHeaderWriter() {
        return new StaticHeadersWriter(
            "X-XSS-Protection", "1; mode=block"
        );
    }

    /**
     * 点击劫持防护 Header 写入器
     */
    @Bean
    public StaticHeadersWriter clickJackingProtectionHeaderWriter() {
        return new StaticHeadersWriter(
            "X-Frame-Options", "DENY"
        );
    }

    /**
     * 内容类型防护 Header 写入器
     */
    @Bean
    public StaticHeadersWriter contentTypeProtectionHeaderWriter() {
        return new StaticHeadersWriter(
            "X-Content-Type-Options", "nosniff"
        );
    }
}
