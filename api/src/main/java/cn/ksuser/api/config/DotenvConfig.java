package cn.ksuser.api.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * .env 文件加载器配置
 * 在应用启动时按 KSUSER_ENV 加载项目根目录的 .env.<env> 文件
 */
@Configuration
public class DotenvConfig {

    /**
     * 静态初始化块：在任何 Bean 创建之前加载 .env 文件
     * 这样可以确保所有的环境变量都在 Spring 配置之前被设置
     */
    static {
        try {
            String envName = resolveEnvName();
            Path rootDir = resolveRootDir();
            Path envFile = rootDir.resolve(".env." + envName);

            Dotenv dotenv = Dotenv.configure()
                    .directory(rootDir.toString())
                    .filename(envFile.getFileName().toString())
                    .ignoreIfMissing()
                    .load();

            dotenv.entries().forEach(entry -> {
                String key = entry.getKey();
                String value = entry.getValue();

                if (System.getenv(key) == null && System.getProperty(key) == null) {
                    System.setProperty(key, value);
                }
            });

            System.out.println("[Ksuser Auth] Loaded env file: " + envFile);
        } catch (Exception e) {
            System.out.println("[Ksuser Auth] Failed to load env file: " + e.getMessage());
        }
    }

    private static String resolveEnvName() {
        String envName = System.getenv("KSUSER_ENV");
        if (envName == null || envName.isBlank()) {
            envName = System.getProperty("KSUSER_ENV");
        }
        if (envName == null || envName.isBlank()) {
            return "production";
        }
        return envName.trim();
    }

    private static Path resolveRootDir() {
        String explicitRoot = System.getenv("KSUSER_ROOT");
        if (explicitRoot == null || explicitRoot.isBlank()) {
            explicitRoot = System.getProperty("KSUSER_ROOT");
        }
        if (explicitRoot != null && !explicitRoot.isBlank()) {
            return Paths.get(explicitRoot).toAbsolutePath().normalize();
        }

        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isDirectory(current.resolve("auth"))
                && Files.isDirectory(current.resolve("api"))) {
                return current;
            }
            current = current.getParent();
        }
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }
}
