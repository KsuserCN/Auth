package cn.ksuser.api.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SensitiveLogLoginMethodUtil {

    private SensitiveLogLoginMethodUtil() {
    }

    public static List<String> parseTokens(String rawLoginMethod) {
        if (rawLoginMethod == null) {
            return List.of();
        }

        String raw = rawLoginMethod.trim();
        if (raw.isEmpty() || "null".equalsIgnoreCase(raw)) {
            return List.of();
        }

        if (raw.startsWith("[") && raw.endsWith("]")) {
            return splitTokens(raw.substring(1, raw.length() - 1));
        }

        if (raw.contains(",")) {
            return splitTokens(raw);
        }

        String token = normalizeToken(raw);
        if (token.endsWith("_MFA") && token.length() > 4) {
            return List.of(token.substring(0, token.length() - 4), "MFA");
        }

        return List.of(token);
    }

    private static List<String> splitTokens(String rawTokens) {
        String[] segments = rawTokens.split(",");
        List<String> tokens = new ArrayList<>();
        for (String segment : segments) {
            String token = normalizeToken(segment);
            if (!token.isEmpty() && !tokens.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static String normalizeToken(String rawToken) {
        String token = rawToken == null ? "" : rawToken.trim();
        if (token.isEmpty()) {
            return "";
        }

        String normalized = token.toUpperCase(Locale.ROOT)
            .replace('-', '_')
            .replace(' ', '_');

        return switch (normalized) {
            case "EMAIL" -> "EMAIL_CODE";
            case "WEIXIN" -> "WECHAT";
            default -> normalized;
        };
    }
}
