package cn.ksuser.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 应用程序配置属性
 * 从 application.properties 中读取配置信息
 */
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private boolean debug = false;
    private final Password password = new Password();
    private final Token token = new Token();
    private final VerificationCode verificationCode = new VerificationCode();
    private final RateLimit rateLimit = new RateLimit();
    private final SensitiveOperation sensitiveOperation = new SensitiveOperation();
    private final Cors cors = new Cors();

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public Password getPassword() {
        return password;
    }

    public Token getToken() {
        return token;
    }

    public VerificationCode getVerificationCode() {
        return verificationCode;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public SensitiveOperation getSensitiveOperation() {
        return sensitiveOperation;
    }

    public Cors getCors() {
        return cors;
    }

    /**
     * 密码策略配置
     */
    public static class Password {
        private int minLength = 6;
        private int maxLength = 66;
        private boolean requireUppercase = true;
        private boolean requireLowercase = true;
        private boolean requireDigits = true;
        private boolean requireSpecialChars = false;

        public int getMinLength() {
            return minLength;
        }

        public void setMinLength(int minLength) {
            this.minLength = minLength;
        }

        public int getMaxLength() {
            return maxLength;
        }

        public void setMaxLength(int maxLength) {
            this.maxLength = maxLength;
        }

        public boolean isRequireUppercase() {
            return requireUppercase;
        }

        public void setRequireUppercase(boolean requireUppercase) {
            this.requireUppercase = requireUppercase;
        }

        public boolean isRequireLowercase() {
            return requireLowercase;
        }

        public void setRequireLowercase(boolean requireLowercase) {
            this.requireLowercase = requireLowercase;
        }

        public boolean isRequireDigits() {
            return requireDigits;
        }

        public void setRequireDigits(boolean requireDigits) {
            this.requireDigits = requireDigits;
        }

        public boolean isRequireSpecialChars() {
            return requireSpecialChars;
        }

        public void setRequireSpecialChars(boolean requireSpecialChars) {
            this.requireSpecialChars = requireSpecialChars;
        }
    }

    /**
     * Token 配置
     */
    public static class Token {
        private boolean blacklistEnabled = true;
        private boolean refreshRotationEnabled = true;

        public boolean isBlacklistEnabled() {
            return blacklistEnabled;
        }

        public void setBlacklistEnabled(boolean blacklistEnabled) {
            this.blacklistEnabled = blacklistEnabled;
        }

        public boolean isRefreshRotationEnabled() {
            return refreshRotationEnabled;
        }

        public void setRefreshRotationEnabled(boolean refreshRotationEnabled) {
            this.refreshRotationEnabled = refreshRotationEnabled;
        }
    }

    /**
     * 验证码配置
     */
    public static class VerificationCode {
        private int length = 6;
        private int expirationMinutes = 10;
        private int maxErrorCount = 5;
        private int lockDurationHours = 1;

        public int getLength() {
            return length;
        }

        public void setLength(int length) {
            this.length = length;
        }

        public int getExpirationMinutes() {
            return expirationMinutes;
        }

        public void setExpirationMinutes(int expirationMinutes) {
            this.expirationMinutes = expirationMinutes;
        }

        public int getMaxErrorCount() {
            return maxErrorCount;
        }

        public void setMaxErrorCount(int maxErrorCount) {
            this.maxErrorCount = maxErrorCount;
        }

        public int getLockDurationHours() {
            return lockDurationHours;
        }

        public void setLockDurationHours(int lockDurationHours) {
            this.lockDurationHours = lockDurationHours;
        }
    }

    /**
     * 速率限制配置
     */
    public static class RateLimit {
        private int emailPerMinute = 1;
        private int emailPerHour = 14;
        private int ipPerMinute = 3;
        private int ipPerHour = 14;

        public int getEmailPerMinute() {
            return emailPerMinute;
        }

        public void setEmailPerMinute(int emailPerMinute) {
            this.emailPerMinute = emailPerMinute;
        }

        public int getEmailPerHour() {
            return emailPerHour;
        }

        public void setEmailPerHour(int emailPerHour) {
            this.emailPerHour = emailPerHour;
        }

        public int getIpPerMinute() {
            return ipPerMinute;
        }

        public void setIpPerMinute(int ipPerMinute) {
            this.ipPerMinute = ipPerMinute;
        }

        public int getIpPerHour() {
            return ipPerHour;
        }

        public void setIpPerHour(int ipPerHour) {
            this.ipPerHour = ipPerHour;
        }
    }

    /**
     * 敏感操作配置
     */
    public static class SensitiveOperation {
        private int verificationDurationMinutes = 15;
        private boolean requireIpMatch = true;

        public int getVerificationDurationMinutes() {
            return verificationDurationMinutes;
        }

        public void setVerificationDurationMinutes(int verificationDurationMinutes) {
            this.verificationDurationMinutes = verificationDurationMinutes;
        }

        public boolean isRequireIpMatch() {
            return requireIpMatch;
        }

        public void setRequireIpMatch(boolean requireIpMatch) {
            this.requireIpMatch = requireIpMatch;
        }
    }

    /**
     * CORS 配置
     */
    public static class Cors {
        private boolean production = false;

        public boolean isProduction() {
            return production;
        }

        public void setProduction(boolean production) {
            this.production = production;
        }
    }
}
