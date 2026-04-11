package cn.ksuser.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path staticDir = Paths.get("static").toAbsolutePath().normalize();
        registry.addResourceHandler("/static/**")
            .addResourceLocations(staticDir.toUri().toString());
    }
}
