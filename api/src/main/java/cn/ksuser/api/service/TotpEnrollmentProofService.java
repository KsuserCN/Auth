package cn.ksuser.api.service;

import cn.ksuser.api.dto.PasskeyAuthenticationOptionsResponse;
import cn.ksuser.api.dto.TotpEnrollmentPasskeyVerifyRequest;
import cn.ksuser.api.dto.TotpEnrollmentVerifyRequest;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserSession;
import cn.ksuser.api.result.ApiResultCode;
import cn.ksuser.api.util.JwtUtil;
import cn.ksuser.api.util.SensitiveLogUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** TOTP enrollment can be authorized only by a new assertion from an existing credential. */
@Service
public class TotpEnrollmentProofService {
    private static final String PREFIX = "auth:totp:enrollment:v2:";
    private static final long PROOF_TTL_SECONDS = 300;
    static final DefaultRedisScript<Long> RESERVE_ATTEMPT = new DefaultRedisScript<>("""
        for i = 1, 2 do
            local stored = redis.call('GET', KEYS[i])
            local count = tonumber(stored or '0')
            if stored and redis.call('TTL', KEYS[i]) == -1 then redis.call('EXPIRE', KEYS[i], ARGV[i+2]) end
            if count >= tonumber(ARGV[i]) then return 0 end
        end
        for i = 1, 2 do
            local count = redis.call('INCR', KEYS[i])
            if count == 1 then redis.call('EXPIRE', KEYS[i], ARGV[i+2]) end
        end
        return 1
        """, Long.class);
    static final DefaultRedisScript<Long> CONSUME_BOUND_VALUE = new DefaultRedisScript<>("""
        if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
        redis.call('DEL', KEYS[1])
        return 1
        """, Long.class);

    private final UserService users;
    private final UserSessionService sessions;
    private final JwtUtil jwt;
    private final PasskeyService passkeys;
    private final VerificationCodeService codes;
    private final RateLimitService rateLimit;
    private final StringRedisTemplate redis;
    private final SensitiveLogUtil logs;
    private final SecureRandom random = new SecureRandom();

    public TotpEnrollmentProofService(UserService users, UserSessionService sessions, JwtUtil jwt,
                                      PasskeyService passkeys, VerificationCodeService codes,
                                      RateLimitService rateLimit, StringRedisTemplate redis, SensitiveLogUtil logs) {
        this.users = users; this.sessions = sessions; this.jwt = jwt; this.passkeys = passkeys;
        this.codes = codes; this.rateLimit = rateLimit; this.redis = redis; this.logs = logs;
    }

    public EnrollmentProof verify(TotpEnrollmentVerifyRequest input, Authentication authentication,
                                  HttpServletRequest request) {
        if (input == null) throw failure(HttpStatus.BAD_REQUEST, ApiResultCode.BAD_REQUEST);
        Context context = requireContext(authentication, request);
        String method = input.method() == null ? "" : input.method().trim().toLowerCase(Locale.ROOT);
        if (!"password".equals(method) && !"email-code".equals(method)) {
            throw failure(HttpStatus.BAD_REQUEST, ApiResultCode.AUTH_129);
        }
        if ("password".equals(method)) {
            if (input.password() == null || input.password().isBlank() || input.password().length() > 256)
                throw failure(HttpStatus.BAD_REQUEST, ApiResultCode.AUTH_017);
        } else if (input.code() == null || !input.code().trim().matches("[0-9]{6}")) {
            throw failure(HttpStatus.BAD_REQUEST, ApiResultCode.AUTH_VERIFICATION_CODE_INVALID);
        }
        reserveAttempt(context, false);
        long start = System.currentTimeMillis();
        boolean verified = "password".equals(method)
            ? context.user().getHasPassword() && users.verifyPassword(input.password(), context.user().getPasswordHash())
            : context.user().getEmail() != null && !context.user().getEmail().isBlank()
                && codes.verifyCodeForType(context.user().getEmail(), input.code().trim(),
                    rateLimit.getClientIp(request), "sensitive-verification") == 0;
        logs.logSensitiveVerify(request, context.user().getId(), method, verified,
            verified ? null : "totp_enrollment_credential_invalid", start);
        if (!verified) throw failure(HttpStatus.BAD_REQUEST,
            "password".equals(method) ? ApiResultCode.AUTH_130 : ApiResultCode.AUTH_VERIFICATION_CODE_INVALID);
        // A credential check may be expensive: revoke/version checks must run again before issuing authorization.
        context = requireContext(authentication, request);
        return issue(context);
    }

    public PasskeyAuthenticationOptionsResponse passkeyOptions(Authentication authentication, HttpServletRequest request) {
        Context context = requireContext(authentication, request);
        reserveAttempt(context, true);
        try {
            PasskeyAuthenticationOptionsResponse options = passkeys.generateSensitiveVerificationOptions();
            if (options == null || options.getChallengeId() == null || options.getChallengeId().isBlank())
                throw failure(HttpStatus.SERVICE_UNAVAILABLE, ApiResultCode.INTERNAL_ERROR);
            redis.opsForValue().set(passkeyKey(options.getChallengeId()), binding(context), Duration.ofSeconds(PROOF_TTL_SECONDS));
            return options;
        } catch (EnrollmentFailure ex) {
            throw ex;
        } catch (Exception ex) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, ApiResultCode.INTERNAL_ERROR);
        }
    }

    public EnrollmentProof verifyPasskey(TotpEnrollmentPasskeyVerifyRequest input, Authentication authentication,
                                         HttpServletRequest request) {
        if (input == null || input.passkeyChallengeId() == null || input.passkeyChallengeId().isBlank()
            || input.passkeyChallengeId().length() > 128 || input.credential() == null)
            throw failure(HttpStatus.BAD_REQUEST, ApiResultCode.BAD_REQUEST);
        Context context = requireContext(authentication, request);
        reserveAttempt(context, false);
        try {
            Long consumed = redis.execute(CONSUME_BOUND_VALUE,
                List.of(passkeyKey(input.passkeyChallengeId())), binding(context));
            if (!Long.valueOf(1).equals(consumed)) throw failure(HttpStatus.BAD_REQUEST, ApiResultCode.AUTH_152);
        } catch (DataAccessException ex) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, ApiResultCode.INTERNAL_ERROR);
        }
        long start = System.currentTimeMillis();
        try {
            passkeys.verifySensitiveOperation(context.user(), input.credential(), input.passkeyChallengeId());
        } catch (Exception ex) {
            logs.logSensitiveVerify(request, context.user().getId(), "passkey", false,
                "totp_enrollment_credential_invalid", start);
            throw failure(HttpStatus.BAD_REQUEST, ApiResultCode.AUTH_152);
        }
        logs.logSensitiveVerify(request, context.user().getId(), "passkey", true, null, start);
        return issue(requireContext(authentication, request));
    }

    public Context requireContext(Authentication authentication, HttpServletRequest request) {
        if (authentication == null || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken || request == null)
            throw failure(HttpStatus.UNAUTHORIZED, ApiResultCode.UNAUTHENTICATED);
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer "))
            throw failure(HttpStatus.UNAUTHORIZED, ApiResultCode.UNAUTHENTICATED);
        String token = header.substring(7);
        String uuid = authentication.getName();
        Long sid = jwt.getSessionId(token);
        Integer version = jwt.getSessionVersion(token);
        if (sid == null || version == null || !jwt.isTokenValid(token) || !"access".equals(jwt.getTokenType(token))
            || !uuid.equals(jwt.getUuidFromToken(token)))
            throw failure(HttpStatus.UNAUTHORIZED, ApiResultCode.UNAUTHENTICATED);
        UserSession session = sessions.findActiveSessionById(sid)
            .orElseThrow(() -> failure(HttpStatus.UNAUTHORIZED, ApiResultCode.UNAUTHENTICATED));
        if (session.getUser() == null || !uuid.equals(session.getUser().getUuid())
            || !version.equals(session.getSessionVersion()))
            throw failure(HttpStatus.UNAUTHORIZED, ApiResultCode.UNAUTHENTICATED);
        User user = users.findByUuid(uuid)
            .orElseThrow(() -> failure(HttpStatus.UNAUTHORIZED, ApiResultCode.UNAUTHENTICATED));
        return new Context(user, session);
    }

    /** Consume only the exact enrollment authorization; foreign proofs are neither accepted nor destroyed. */
    public boolean consume(String proofId, String uuid, Long sid, Integer version) {
        if (proofId == null || !proofId.matches("[A-Za-z0-9_-]{43}") || uuid == null || uuid.isBlank()
            || sid == null || version == null) return false;
        try {
            Long result = redis.execute(CONSUME_BOUND_VALUE, List.of(proofKey(proofId)), binding(uuid, sid, version));
            if (result == null) throw failure(HttpStatus.SERVICE_UNAVAILABLE, ApiResultCode.INTERNAL_ERROR);
            return result == 1;
        } catch (DataAccessException ex) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, ApiResultCode.INTERNAL_ERROR);
        }
    }

    private EnrollmentProof issue(Context context) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String proofId = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try {
            redis.opsForValue().set(proofKey(proofId), binding(context), Duration.ofSeconds(PROOF_TTL_SECONDS));
            return new EnrollmentProof(proofId, PROOF_TTL_SECONDS);
        } catch (DataAccessException ex) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, ApiResultCode.INTERNAL_ERROR);
        }
    }

    private void reserveAttempt(Context context, boolean options) {
        String account = digest(context.user().getUuid());
        String bucket = options ? "options:" : "verify:";
        try {
            Long allowed = redis.execute(RESERVE_ATTEMPT,
                List.of(PREFIX + bucket + "minute:" + account, PREFIX + bucket + "hour:" + account),
                options ? "10" : "5", options ? "60" : "30", "60", "3600");
            if (allowed == null) throw failure(HttpStatus.SERVICE_UNAVAILABLE, ApiResultCode.INTERNAL_ERROR);
            if (allowed != 1) throw failure(HttpStatus.TOO_MANY_REQUESTS, ApiResultCode.RATE_LIMITED);
        } catch (DataAccessException ex) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, ApiResultCode.INTERNAL_ERROR);
        }
    }

    private static String passkeyKey(String challengeId) { return PREFIX + "passkey:" + challengeId; }
    private static String proofKey(String proofId) { return PREFIX + "proof:" + proofId; }
    private static String binding(Context context) {
        return binding(context.user().getUuid(), context.session().getId(), context.session().getSessionVersion());
    }
    private static String binding(String uuid, Long sid, Integer version) {
        return "TOTP_ENROLL:" + digest(uuid) + ":" + sid + ":" + version;
    }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public record Context(User user, UserSession session) { }
    public record EnrollmentProof(String proofId, long expiresInSeconds) { }
    private static EnrollmentFailure failure(HttpStatus status, ApiResultCode code) { return new EnrollmentFailure(status, code); }
    public static final class EnrollmentFailure extends RuntimeException {
        private final HttpStatus status;
        private final ApiResultCode resultCode;
        public EnrollmentFailure(HttpStatus status, ApiResultCode resultCode) {
            super(resultCode.name()); this.status = status; this.resultCode = resultCode;
        }
        public HttpStatus status() { return status; }
        public ApiResultCode resultCode() { return resultCode; }
    }
}
