package cn.ksuser.api.security;

import cn.ksuser.api.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 输入验证和安全检查工具类
 */
@Component
public class SecurityValidator {

    // 用户名: 3-20个字符，仅包含字母、数字、下划线和连字符
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,20}$");

    // 邮箱: 标准邮箱格式
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[a-zA-Z]{2,}$"
    );

    // 密码强度: 至少包含大小写字母、数字和特殊字符
    private static final Pattern STRONG_PASSWORD_PATTERN = Pattern.compile(
        "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[a-zA-Z\\d@$!%*?&]{8,}$"
    );

    private final AppProperties appProperties;

    public SecurityValidator(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /**
     * 验证用户名格式
     * @param username 用户名
     * @return 是否有效
     */
    public boolean isValidUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        return USERNAME_PATTERN.matcher(username).matches();
    }

    /**
     * 验证邮箱格式
     * @param email 邮箱
     * @return 是否有效
     */
    public boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * 验证密码强度（根据配置的密码策略）
     * @param password 密码
     * @return 是否满足强度要求
     */
    public boolean isStrongPassword(String password) {
        if (password == null) {
            return false;
        }

        AppProperties.Password pwdConfig = appProperties.getPassword();
        
        // 检查长度
        if (password.length() < pwdConfig.getMinLength() || password.length() > pwdConfig.getMaxLength()) {
            return false;
        }

        // 检查大写字母
        if (pwdConfig.isRequireUppercase() && !password.matches(".*[A-Z].*")) {
            return false;
        }

        // 检查小写字母
        if (pwdConfig.isRequireLowercase() && !password.matches(".*[a-z].*")) {
            return false;
        }

        // 检查数字
        if (pwdConfig.isRequireDigits() && !password.matches(".*\\d.*")) {
            return false;
        }

        // 检查特殊字符
        if (pwdConfig.isRequireSpecialChars() && !password.matches(".*[@$!%*?&].*")) {
            return false;
        }

        return true;
    }

    /**
     * 验证密码长度（根据配置）
     * @param password 密码
     * @return 长度是否有效
     */
    public boolean isValidPasswordLength(String password) {
        if (password == null) {
            return false;
        }
        
        AppProperties.Password pwdConfig = appProperties.getPassword();
        int length = password.length();
        return length >= pwdConfig.getMinLength() && length <= pwdConfig.getMaxLength();
    }

    /**
     * 检查是否为常见弱密码
     * @param password 密码
     * @return 是否为弱密码
     */
    public boolean isCommonWeakPassword(String password) {
        String[] commonPasswords = {
            "password", "123456", "12345678", "qwerty", "abc123",
            "password123", "admin", "letmein", "welcome", "monkey"
        };

        String lowerPassword = password.toLowerCase();
        for (String common : commonPasswords) {
            if (lowerPassword.equals(common)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查字符串是否可能包含 SQL 注入
     * @param input 输入字符串
     * @return 是否可能包含 SQL 注入
     */
    public boolean possibleSqlInjection(String input) {
        if (input == null) {
            return false;
        }

        String upperInput = input.toUpperCase();

        // 关键字检测
        String[] sqlKeywords = {
            "SELECT", "INSERT", "UPDATE", "DELETE", "DROP", "UNION",
            "ALTER", "CREATE", "EXEC", "EXECUTE", "TRUNCATE", "MERGE",
            "SCRIPT", "JAVASCRIPT", "ONLOAD", "SLEEP", "BENCHMARK",
            "INFORMATION_SCHEMA", "LOAD_FILE", "INTO OUTFILE"
        };
        for (String keyword : sqlKeywords) {
            if (upperInput.contains(keyword)) {
                return true;
            }
        }

        // 关键符号/注释检测
        String[] sqlSymbols = {
            "--", "#", "/*", "*/", ";", "'", "\""
        };
        for (String symbol : sqlSymbols) {
            if (input.contains(symbol)) {
                return true;
            }
        }

        // 常见注入模式（大小写不敏感）
        String[] sqlPatterns = {
            ".*\\\\bOR\\\\b\\s+\\d+=\\d+.*",
            ".*\\\\bOR\\\\b\\s+'.*'='.*'.*",
            ".*\\\\bAND\\\\b\\s+\\d+=\\d+.*",
            ".*\\\\bUNION\\\\b\\s+\\\\bSELECT\\\\b.*",
            ".*\\\\bSELECT\\\\b.+\\\\bFROM\\\\b.*",
            ".*\\\\bINSERT\\\\b.+\\\\bINTO\\\\b.*",
            ".*\\\\bUPDATE\\\\b.+\\\\bSET\\\\b.*",
            ".*\\\\bDELETE\\\\b.+\\\\bFROM\\\\b.*"
        };
        for (String pattern : sqlPatterns) {
            if (upperInput.matches(pattern)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 防止时序攻击 - 常量时间比较
     * @param a 第一个值
     * @param b 第二个值
     * @return 是否相等
     */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }

        byte[] aBytes = a.getBytes();
        byte[] bBytes = b.getBytes();

        int result = 0;
        result |= aBytes.length ^ bBytes.length;

        for (int i = 0; i < Math.min(aBytes.length, bBytes.length); i++) {
            result |= aBytes[i] ^ bBytes[i];
        }

        return result == 0;
    }

    /**
     * 模拟常量时间延迟 - 用于防止时序攻击
     * @param baseDelayMs 基础延迟毫秒数
     * @throws InterruptedException 当线程被中断时
     */
    public static void constantTimeDelay(long baseDelayMs) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long elapsed = 0;

        while (elapsed < baseDelayMs) {
            elapsed = System.currentTimeMillis() - startTime;
            // 使用 volatile 变量防止编译器优化
            if (elapsed < baseDelayMs) {
                Thread.sleep(Math.min(1, baseDelayMs - elapsed));
            }
        }
    }
}
