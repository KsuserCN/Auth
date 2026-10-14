package cn.ksuser.api.service;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.result.ApiResult;
import cn.ksuser.api.result.ApiResultCode;
import cn.ksuser.api.result.PasswordRequirementResult;
import cn.ksuser.api.security.SecurityValidator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Shared checks for email/password registration, including registration through OAuth. */
@Service
public class RegistrationValidationService {
    private final AppProperties properties;
    private final SecurityValidator validator;
    private final VerificationCodeService codes;

    public RegistrationValidationService(AppProperties properties, SecurityValidator validator,
                                         VerificationCodeService codes) {
        this.properties = properties;
        this.validator = validator;
        this.codes = codes;
    }

    /** Returns a rejection, or null after validating and consuming the email verification code. */
    public Rejection validatePasswordRegistration(String username, String email, String password, String code) {
        if (username == null || username.isBlank()) {
            return reject(HttpStatus.BAD_REQUEST, ApiResultCode.AUTH_002, "Empty username");
        }
        if (email == null || email.isBlank()) {
            return reject(HttpStatus.BAD_REQUEST, ApiResultCode.AUTH_007, "Empty email");
        }
        if (!validator.isValidEmail(email)) {
            return reject(HttpStatus.BAD_REQUEST, ApiResultCode.AUTH_008, "Invalid email format");
        }
        if (password == null || password.isBlank()) {
            return reject(HttpStatus.BAD_REQUEST, ApiResultCode.AUTH_017, "Empty password");
        }
        if (code == null || code.isBlank()) {
            return reject(HttpStatus.BAD_REQUEST, ApiResultCode.AUTH_018, "Empty verification code");
        }
        if (!validator.isValidUsername(username)) {
            return reject(HttpStatus.BAD_REQUEST, ApiResultCode.AUTH_019, "Invalid username format");
        }
        if (!validator.isValidPasswordLength(password)) {
            return new Rejection(HttpStatus.BAD_REQUEST, ApiResultCode.AUTH_020.withParams(
                "min", properties.getPassword().getMinLength(), "max", properties.getPassword().getMaxLength()),
                "Invalid password length");
        }
        if (!validator.isStrongPassword(password)) {
            return reject(HttpStatus.BAD_REQUEST, PasswordRequirementResult.from(properties.getPassword()), "Weak password");
        }
        if (validator.isCommonWeakPassword(password)) {
            return reject(HttpStatus.BAD_REQUEST, ApiResultCode.AUTH_021, "Common weak password");
        }
        if (validator.possibleSqlInjection(username) || validator.possibleSqlInjection(email)) {
            return reject(HttpStatus.BAD_REQUEST, ApiResultCode.AUTH_022, "SQL injection detected");
        }

        int result = codes.verifyRegistrationCode(email, code);
        if (result == 0) return null;
        if (result == 3) {
            return reject(HttpStatus.TOO_MANY_REQUESTS, ApiResultCode.AUTH_010, "Verification code locked");
        }
        if (result == 4) {
            return reject(HttpStatus.BAD_REQUEST, ApiResultCode.AUTH_023, "No verification code sent");
        }
        if (result == 5) {
            return new Rejection(HttpStatus.BAD_REQUEST,
                ApiResultCode.AUTH_024.withParams("count", codes.getErrorCount(email)), "Email mismatch");
        }
        if (codes.isLocked(email)) {
            return reject(HttpStatus.TOO_MANY_REQUESTS, ApiResultCode.AUTH_010, "Verification code locked");
        }
        return new Rejection(HttpStatus.BAD_REQUEST,
            (result == 1 ? ApiResultCode.AUTH_026 : ApiResultCode.AUTH_027)
                .withParams("count", codes.getErrorCount(email)),
            result == 1 ? "Verification code expired" : "Invalid verification code");
    }

    private static Rejection reject(HttpStatus status, ApiResultCode code, String reason) {
        return new Rejection(status, ApiResult.of(code), reason);
    }

    public record Rejection(HttpStatus status, ApiResult result, String reason) {}
}
