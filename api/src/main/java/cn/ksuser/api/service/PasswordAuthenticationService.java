package cn.ksuser.api.service;

import cn.ksuser.api.entity.User;
import cn.ksuser.api.result.ApiResultCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/** Applies one shared budget reservation before each password assertion. */
@Service
public class PasswordAuthenticationService {
    private final UserService users;
    private final RateLimitService limits;

    public PasswordAuthenticationService(UserService users, RateLimitService limits) {
        this.users = users;
        this.limits = limits;
    }

    public Optional<User> authenticate(String email, String password, String ip) {
        return authenticate(email, password, ip, null);
    }

    public Optional<User> authenticate(String email, String password, String ip, String pendingTokenQuotaKey) {
        if (email == null || email.isBlank() || email.length() > 320)
            throw new AuthenticationFailure(HttpStatus.BAD_REQUEST, ApiResultCode.AUTH_007);
        if (password == null || password.isBlank() || password.length() > 256)
            throw new AuthenticationFailure(HttpStatus.BAD_REQUEST, ApiResultCode.AUTH_017);
        String normalizedEmail = email.trim();
        User user = users.findByEmail(normalizedEmail).orElse(null);
        // Database-equivalent email spellings all use the same durable user identity.
        String accountKey = user == null ? "unknown:" + digest(normalizedEmail.toLowerCase(Locale.ROOT))
            : "user:" + user.getId();
        if (user != null && user.getId() == null)
            throw new AuthenticationFailure(HttpStatus.SERVICE_UNAVAILABLE, ApiResultCode.INTERNAL_ERROR);
        RateLimitService.PasswordAttemptLimit permit = limits.acquirePasswordAttempt(accountKey, ip, pendingTokenQuotaKey);
        if (permit == RateLimitService.PasswordAttemptLimit.LIMITED)
            throw new AuthenticationFailure(HttpStatus.TOO_MANY_REQUESTS, ApiResultCode.AUTH_035);
        if (permit != RateLimitService.PasswordAttemptLimit.ALLOWED)
            throw new AuthenticationFailure(HttpStatus.SERVICE_UNAVAILABLE, ApiResultCode.INTERNAL_ERROR);
        if (user == null || !user.getHasPassword() || !users.verifyPassword(password, user.getPasswordHash()))
            return Optional.empty();
        return Optional.of(user);
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public static final class AuthenticationFailure extends RuntimeException {
        private final HttpStatus status;
        private final ApiResultCode resultCode;
        public AuthenticationFailure(HttpStatus status, ApiResultCode resultCode) {
            super(resultCode.name());
            this.status = status;
            this.resultCode = resultCode;
        }
        public HttpStatus status() { return status; }
        public ApiResultCode resultCode() { return resultCode; }
    }
}
