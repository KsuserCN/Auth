package cn.ksuser.api.service;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * 轻量级User-Agent解析服务
 * 用于解析客户端User-Agent字符串以提取浏览器和设备类型
 */
@Service
public class UserAgentParserService {

    private static final Logger logger = LoggerFactory.getLogger(UserAgentParserService.class);

    // 常见浏览器模式（优先匹配 Edge）
    private static final String[] BROWSER_PATTERNS = {
        "(?i)EdgA/([\\d.]+)",
        "(?i)EdgiOS/([\\d.]+)",
        "(?i)Edg/([\\d.]+)",
        "(?i)Edge/([\\d.]+)",
        "(?i)OPR/([\\d.]+)",
        "(?i)Chrome/([\\d.]+)",
        "(?i)Firefox/([\\d.]+)",
        "(?i)Safari/([\\d.]+)(?!.*Chrome)",
        "(?i)MSIE\\s([\\d.]+)",
        "(?i)Trident.*rv:([\\d.]+)"
    };

    private static final String[] BROWSER_NAMES = {
        "Microsoft Edge", "Microsoft Edge", "Microsoft Edge", "Microsoft Edge",
        "Opera", "Chrome", "Firefox", "Safari", "IE", "IE"
    };

    /**
     * 解析User-Agent字符串
     */
    public UserAgentInfo parse(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return new UserAgentInfo("Unknown", "Unknown");
        }

        try {
            String browser = parseBrowser(userAgent);
            String deviceType = parseDeviceType(userAgent);
            return new UserAgentInfo(browser, deviceType);
        } catch (Exception e) {
            logger.warn("Failed to parse User-Agent: {}", e.getMessage());
            return new UserAgentInfo("Unknown", "Unknown");
        }
    }

    /**
     * 从User-Agent提取浏览器信息
     */
    private String parseBrowser(String userAgent) {
        for (int i = 0; i < BROWSER_PATTERNS.length; i++) {
            Pattern pattern = Pattern.compile(BROWSER_PATTERNS[i]);
            Matcher matcher = pattern.matcher(userAgent);
            if (matcher.find()) {
                String version = matcher.group(1);
                String browserName = BROWSER_NAMES[i];
                return browserName + " " + version;
            }
        }
        return "Unknown";
    }

    /**
     * 从User-Agent提取设备类型（系统类型）
     */
    private String parseDeviceType(String userAgent) {
        String ua = userAgent.toLowerCase();
        if (ua.contains("bot") || ua.contains("spider") || ua.contains("crawler")) {
            return "Bot";
        }
        if (ua.contains("windows nt")) {
            return "Windows";
        }
        if (ua.contains("mac os x") || ua.contains("macintosh")) {
            return "Mac";
        }
        if (ua.contains("android")) {
            return "Android";
        }
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ios")) {
            return "iOS";
        }
        if (ua.contains("linux") || ua.contains("x11")) {
            return "Linux";
        }
        if (ua.contains("cros")) {
            return "ChromeOS";
        }
        return "Unknown";
    }

    /**
     * User-Agent解析结果
     */
    public static class UserAgentInfo {
        private final String browser;
        private final String deviceType;

        public UserAgentInfo(String browser, String deviceType) {
            this.browser = browser;
            this.deviceType = deviceType;
        }

        public String getBrowser() {
            return browser;
        }

        public String getDeviceType() {
            return deviceType;
        }

        @Override
        public String toString() {
            return "UserAgentInfo{" +
                    "browser='" + browser + '\'' +
                    ", deviceType='" + deviceType + '\'' +
                    '}';
        }
    }
}
