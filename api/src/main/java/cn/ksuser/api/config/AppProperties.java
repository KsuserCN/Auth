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
    private final RateLimit rateLimit = new RateLimit();
    private final SensitiveOperation sensitiveOperation = new SensitiveOperation();
    private final Passkey passkey = new Passkey();

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

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public SensitiveOperation getSensitiveOperation() {
        return sensitiveOperation;
    }

    public Passkey getPasskey() {
        return passkey;
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
     * 速率限制配置
     */
    public static class RateLimit {
        private int sendCodeEmailPerMinute = 1;
        private int sendCodeEmailPerHour = 14;
        private int sendCodeIpPerMinute = 3;
        private int sendCodeIpPerHour = 14;

        private int loginEmailPerMinute = 5;
        private int loginEmailPerHour = 60;
        private int loginIpPerMinute = 10;
        private int loginIpPerHour = 120;

        public int getSendCodeEmailPerMinute() {
            return sendCodeEmailPerMinute;
        }

        public void setSendCodeEmailPerMinute(int sendCodeEmailPerMinute) {
            this.sendCodeEmailPerMinute = sendCodeEmailPerMinute;
        }

        public int getSendCodeEmailPerHour() {
            return sendCodeEmailPerHour;
        }

        public void setSendCodeEmailPerHour(int sendCodeEmailPerHour) {
            this.sendCodeEmailPerHour = sendCodeEmailPerHour;
        }

        public int getSendCodeIpPerMinute() {
            return sendCodeIpPerMinute;
        }

        public void setSendCodeIpPerMinute(int sendCodeIpPerMinute) {
            this.sendCodeIpPerMinute = sendCodeIpPerMinute;
        }

        public int getSendCodeIpPerHour() {
            return sendCodeIpPerHour;
        }

        public void setSendCodeIpPerHour(int sendCodeIpPerHour) {
            this.sendCodeIpPerHour = sendCodeIpPerHour;
        }

        public int getLoginEmailPerMinute() {
            return loginEmailPerMinute;
        }

        public void setLoginEmailPerMinute(int loginEmailPerMinute) {
            this.loginEmailPerMinute = loginEmailPerMinute;
        }

        public int getLoginEmailPerHour() {
            return loginEmailPerHour;
        }

        public void setLoginEmailPerHour(int loginEmailPerHour) {
            this.loginEmailPerHour = loginEmailPerHour;
        }

        public int getLoginIpPerMinute() {
            return loginIpPerMinute;
        }

        public void setLoginIpPerMinute(int loginIpPerMinute) {
            this.loginIpPerMinute = loginIpPerMinute;
        }

        public int getLoginIpPerHour() {
            return loginIpPerHour;
        }

        public void setLoginIpPerHour(int loginIpPerHour) {
            this.loginIpPerHour = loginIpPerHour;
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
     * Passkey (WebAuthn) 配置
     */
    public static class Passkey {
        private String rpName = "KSUser Auth API";
        private String rpId = "localhost";
        private String origin = "http://localhost:5173";
        private String attestation = "none";
        private String userVerification = "preferred";
        private String residentKey = "preferred";
        private long timeout = 300000; // 毫秒

        public String getRpName() {
            return rpName;
        }

        public void setRpName(String rpName) {
            this.rpName = rpName;
        }

        public String getRpId() {
            return rpId;
        }

        public void setRpId(String rpId) {
            this.rpId = rpId;
        }

        public String getOrigin() {
            return origin;
        }

        public void setOrigin(String origin) {
            this.origin = origin;
        }

        public String getAttestation() {
            return attestation;
        }

        public void setAttestation(String attestation) {
            this.attestation = attestation;
        }

        public String getUserVerification() {
            return userVerification;
        }

        public void setUserVerification(String userVerification) {
            this.userVerification = userVerification;
        }

        public String getResidentKey() {
            return residentKey;
        }

        public void setResidentKey(String residentKey) {
            this.residentKey = residentKey;
        }

        public long getTimeout() {
            return timeout;
        }

        public void setTimeout(long timeout) {
            this.timeout = timeout;
        }
    }

}
