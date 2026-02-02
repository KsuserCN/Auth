package cn.ksuser.api.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Configuration;

/**
 * .env 文件加载器配置
 * 在应用启动时加载项目根目录的 .env 文件
 */
@Configuration
public class DotenvConfig {

    /**
     * 静态初始化块：在任何 Bean 创建之前加载 .env 文件
     * 这样可以确保所有的环境变量都在 Spring 配置之前被设置
     */
    static {
        try {
            // 尝试从项目根目录加载 .env 文件
            Dotenv dotenv = Dotenv.configure()
                    .directory(".")  // 项目根目录
                    .ignoreIfMissing()  // 如果 .env 文件不存在，不报错
                    .load();

            // 将 .env 中的所有配置加载到系统环境变量
            // 这样 Spring Boot 的 ${VAR_NAME} 占位符就能读取这些值
            dotenv.entries().forEach(entry -> {
                String key = entry.getKey();
                String value = entry.getValue();

                // 只在环境变量中不存在时才设置
                // 这样命令行或系统环境变量可以覆盖 .env 文件
                if (System.getenv(key) == null) {
                    System.setProperty(key, value);
                }
            });

            System.out.println("[Ksuser Auth] .env 文件已加载成功");
        } catch (Exception e) {
            System.out.println("[Ksuser Auth] 未找到 .env 文件或加载失败: " + e.getMessage());
            System.out.println("[Ksuser Auth] 如需使用 .env 文件，请复制 .env.example 到 .env 并填写配置");
        }
    }
}
