package cn.ksuser.api.service;

import cn.ksuser.api.exception.Oauth2Exception;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

public final class AuthorizationGrantPolicy {

    public static final String MODE_PERSISTENT = "PERSISTENT";
    public static final String MODE_ONE_TIME = "ONE_TIME";
    public static final String MODE_TIME_LIMITED = "TIME_LIMITED";
    public static final List<String> SUPPORTED_MODES = List.of(
        MODE_PERSISTENT,
        MODE_ONE_TIME,
        MODE_TIME_LIMITED
    );
    public static final int DEFAULT_TTL_SECONDS = 3600;
    public static final int MIN_TTL_SECONDS = 300;
    public static final int MAX_TTL_SECONDS = 30 * 24 * 60 * 60;

    private AuthorizationGrantPolicy() {
    }

    public static String normalizeGrantMode(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            return MODE_PERSISTENT;
        }
        String normalized = rawMode.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_MODES.contains(normalized)) {
            throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_request", "不支持的授权模式");
        }
        return normalized;
    }

    public static Integer resolveTtlSeconds(String grantMode, Integer requestedTtlSeconds) {
        if (!MODE_TIME_LIMITED.equals(grantMode)) {
            return null;
        }
        int ttlSeconds = requestedTtlSeconds == null ? DEFAULT_TTL_SECONDS : requestedTtlSeconds;
        if (ttlSeconds < MIN_TTL_SECONDS || ttlSeconds > MAX_TTL_SECONDS) {
            throw new Oauth2Exception(
                HttpStatus.BAD_REQUEST,
                "invalid_request",
                "限时授权有效期必须在 " + MIN_TTL_SECONDS + "-" + MAX_TTL_SECONDS + " 秒之间"
            );
        }
        return ttlSeconds;
    }

    public static LocalDateTime calculateExpiresAt(LocalDateTime now, String grantMode, Integer ttlSeconds) {
        if (!MODE_TIME_LIMITED.equals(grantMode) || ttlSeconds == null) {
            return null;
        }
        return now.plusSeconds(ttlSeconds.longValue());
    }

    public static boolean isReusable(String grantMode, LocalDateTime expiresAt) {
        String normalizedMode = normalizeGrantMode(grantMode);
        if (MODE_ONE_TIME.equals(normalizedMode)) {
            return false;
        }
        if (MODE_TIME_LIMITED.equals(normalizedMode)) {
            return expiresAt != null && expiresAt.isAfter(LocalDateTime.now());
        }
        return true;
    }
}
