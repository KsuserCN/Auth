package cn.ksuser.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

/**
 * CORS 跨域配置
 * 
 * 安全改进:
 * 1. 明确指定允许的 HTTP 方法，而非使用通配符
 * 2. 明确指定允许的请求头，而非使用通配符
 * 3. 正确配置 allowCredentials 和 exposedHeaders
 */
@Configuration
public class CorsConfig {

    private final AppProperties appProperties;

    public CorsConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        
        // 允许的源（明确指定）
        if (appProperties.isDebug()) {
            // 本地开发环境
            config.addAllowedOrigin("http://localhost:5173");
        } else {
            // 生产环境域名（根据实际情况修改）
            config.addAllowedOrigin("https://auth.ksuser.cn");
        }
        
        // 明确指定允许的 HTTP 方法（不使用通配符）
        config.setAllowedMethods(Arrays.asList(
            "GET",
            "POST",
            "PUT",
            "DELETE",
            "OPTIONS",
            "PATCH"
        ));
        
        // 明确指定允许的请求头（不使用通配符）
        config.setAllowedHeaders(Arrays.asList(
            "Content-Type",
            "Authorization",
            "X-Requested-With",
            "Accept",
            "Origin",
            "Access-Control-Request-Method",
            "Access-Control-Request-Headers",
            "X-XSRF-TOKEN",
            "X-CSRF-TOKEN"
        ));
        
        // 指定哪些响应头可以暴露给客户端
        config.setExposedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type"
        ));
        
        // 允许凭证（cookies、authorization等）
        config.setAllowCredentials(true);
        
        // 预检请求的缓存时间（秒）
        config.setMaxAge(3600L);
        
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
