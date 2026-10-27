package cn.ksuser.api.controller;

import cn.ksuser.api.result.ApiResultCode;
import cn.ksuser.api.result.ApiFailures;
import cn.ksuser.api.result.BusinessArgumentException;
import cn.ksuser.api.result.BusinessStateException;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.dto.ApiResponse;
import cn.ksuser.api.entity.*;
import cn.ksuser.api.repository.*;
import cn.ksuser.api.service.*;
import cn.ksuser.api.util.*;
import jakarta.servlet.http.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/oauth/apple")
public class AppleAuthController {
    private final AppleAuthService apple;
    private final UserSettingsRepository settings;
    private final UserPasskeyRepository passkeys;
    private final UserRepository users;
    private final TotpService totp;
    private final MfaService mfa;
    private final RateLimitService rate;
    private final SensitiveOperationService sensitive;
    private final UserSessionService sessions;
    private final JwtUtil jwt;
    private final AppProperties properties;
    private final SensitiveLogUtil logs;
    private final AdaptiveRiskOrchestrationService adaptive;
    public AppleAuthController(AppleAuthService apple,UserSettingsRepository settings,UserPasskeyRepository passkeys,
        UserRepository users,TotpService totp,MfaService mfa,RateLimitService rate,SensitiveOperationService sensitive,
        UserSessionService sessions,JwtUtil jwt,AppProperties properties,SensitiveLogUtil logs,AdaptiveRiskOrchestrationService adaptive) {
        this.apple=apple;this.settings=settings;this.passkeys=passkeys;this.users=users;this.totp=totp;this.mfa=mfa;
        this.rate=rate;this.sensitive=sensitive;this.sessions=sessions;this.jwt=jwt;this.properties=properties;this.logs=logs;this.adaptive=adaptive;
    }
    public record ChallengeRequest(String purpose,String clientId) {}
    public record NotificationRequest(String payload) {}
    @PostMapping("/challenge")
    public ResponseEntity<ApiResponse<Object>> challenge(@RequestBody ChallengeRequest body,Authentication auth,HttpServletRequest request) {
        limit(request);return ok(ApiResultCode.APPLE_001, apple.challenge(body.purpose(),body.clientId(),context(auth,request)));
    }
    @PostMapping("/mobile-login")
    public ResponseEntity<ApiResponse<Object>> login(@RequestBody AppleAuthService.Credential body,Authentication auth,HttpServletRequest request,HttpServletResponse response) {
        limit(request);long start=System.currentTimeMillis();
        AppleAuthService.Verified verified=apple.verify(body,context(auth,request),false);
        if (verified.user()!=null) return issueLogin(verified.user(),request,response,start);
        Map<String, Object> pending = apple.pendingData(verified.identity());
        ApiResultCode result = Boolean.TRUE.equals(pending.get("emailConflict"))
            ? ApiResultCode.APPLE_BIND_EMAIL_CONFLICT : ApiResultCode.APPLE_BIND_REQUIRED;
        return ResponseEntity.status(202).body(new ApiResponse<>(202, result, pending));
    }
    @PostMapping("/register-pending")
    public ResponseEntity<ApiResponse<Object>> register(@RequestBody AppleAuthService.PendingRequest body,Authentication auth,HttpServletRequest request,HttpServletResponse response) {
        limit(request);long start=System.currentTimeMillis();
        User user=apple.register(body,context(auth,request));return issueLogin(user,request,response,start);
    }
    @PostMapping("/bind-pending")
    public ResponseEntity<ApiResponse<Object>> bind(@RequestBody AppleAuthService.PendingRequest body,Authentication auth,HttpServletRequest request) {
        limit(request);var context=context(auth,request);requireSensitive(context);
        apple.bind(body,context);sensitive.clearVerification(context.uuid());
        logs.log(request,user(context).getId(),"BIND_OAUTH","apple",UserSensitiveLog.OperationResult.SUCCESS,null,System.currentTimeMillis());
        return ok(ApiResultCode.APPLE_002, Map.of("bound",true));
    }
    @PostMapping("/sensitive-verify")
    public ResponseEntity<ApiResponse<Object>> verifySensitive(@RequestBody AppleAuthService.Credential body,Authentication auth,HttpServletRequest request) {
        limit(request);long start=System.currentTimeMillis();var context=context(auth,request);requireActive(context);
        AppleAuthService.Verified result=apple.verify(body,context,true);
        sensitive.markVerified(context.uuid(),context.ip());
        sessions.findActiveSessionById(context.sessionId()).ifPresent(session -> sessions.markStepUpVerified(session,"apple"));
        logs.logSensitiveVerify(request,result.user().getId(),"APPLE",true,null,start);
        return ok(ApiResultCode.APPLE_003, Map.of("verified",true,"remainingSeconds",900));
    }
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Object>> status(Authentication auth,HttpServletRequest request) {
        return ok(ApiResultCode.APPLE_004, apple.status(context(auth,request)));
    }
    @PostMapping("/unbind")
    public ResponseEntity<ApiResponse<Object>> unbind(Authentication auth,HttpServletRequest request) {
        limit(request);var context=context(auth,request);requireSensitive(context);
        User user=user(context);apple.unbind(context);sensitive.clearVerification(context.uuid());
        logs.log(request,user.getId(),"UNBIND_OAUTH","apple",UserSensitiveLog.OperationResult.SUCCESS,null,System.currentTimeMillis());
        return ok(ApiResultCode.APPLE_005, Map.of("bound",false));
    }
    @PostMapping("/notifications")
    public ResponseEntity<ApiResponse<Object>> notification(@RequestBody NotificationRequest body) {
        try {
            apple.notification(body.payload());
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // Includes transaction commit and afterCommit storage errors raised by the proxy.
            throw new BusinessStateException(ApiResultCode.APPLE_010, ex);
        }
        return ok(ApiResultCode.APPLE_006, null);
    }
    private ResponseEntity<ApiResponse<Object>> issueLogin(User user,HttpServletRequest request,HttpServletResponse response,long start) {
        List<String> methods=new ArrayList<>();if(totp.isTotpEnabled(user.getId())) methods.add("totp");
        if(!passkeys.findByUserId(user.getId()).isEmpty()) methods.add("passkey");methods.add("qr");
        UserSettings preference=settings.findByUserId(user.getId()).orElse(null);
        if(preference!=null && Boolean.TRUE.equals(preference.getMfaEnabled())) {
            String preferred=preference.getPreferredMfaMethod();methods.sort(Comparator.comparingInt(method -> method.equals(preferred)?0:1));
            String id=mfa.createChallenge(user.getId(),rate.getClientIp(request),rate.getClientUserAgent(request),"apple",new HashSet<>(methods));
            return ResponseEntity.status(201).body(new ApiResponse<>(201, ApiResultCode.AUTH_MFA_REQUIRED, Map.of("challengeId",id,"method",methods.get(0),"methods",methods,"provider","apple")));
        }
        String refresh=jwt.generateRefreshToken(user.getUuid());
        UserSession session=sessions.createSession(user,refresh,rate.getClientIp(request),rate.getClientUserAgent(request));
        String access=jwt.generateAccessToken(user.getUuid(),session.getId(),session.getSessionVersion()==null?0:session.getSessionVersion());
        response.addHeader("Set-Cookie",ResponseCookie.from("refreshToken",refresh).httpOnly(true).secure(!properties.isDebug())
            .path("/").maxAge(604800).sameSite("Strict").build().toString());
        logs.logLogin(request,user.getId(),"APPLE",true,null,start);
        return ok(ApiResultCode.APPLE_007, Map.of("accessToken",access,"user",user,"provider","apple"));
    }
    private AppleAuthService.Context context(Authentication auth,HttpServletRequest request) {
        String uuid=auth!=null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken) ? auth.getPrincipal().toString():null;
        Long sid=null;String header=request.getHeader("Authorization");
        if(uuid!=null && header!=null && header.startsWith("Bearer ")) sid=jwt.getSessionId(header.substring(7));
        return new AppleAuthService.Context(uuid,sid,rate.getClientIp(request),rate.getClientUserAgent(request));
    }
    private User user(AppleAuthService.Context context) {
        if(context.uuid()==null || context.sessionId()==null) throw new BusinessArgumentException(ApiResultCode.UNAUTHENTICATED);
        return users.findByUuid(context.uuid()).orElseThrow(() -> new BusinessArgumentException(ApiResultCode.APPLE_011));
    }
    private void requireActive(AppleAuthService.Context context) {
        User user=user(context);UserSession session=sessions.findActiveSessionById(context.sessionId()).orElseThrow(() -> new BusinessArgumentException(ApiResultCode.APPLE_012));
        if(!user.getId().equals(session.getUser().getId()) || adaptive.evaluateAndApply(user,session,context.ip(),context.userAgent(),"apple-auth").isSessionFrozen())
            throw new BusinessArgumentException(ApiResultCode.APPLE_013);
    }
    private void requireSensitive(AppleAuthService.Context context) {
        requireActive(context);
        UserSession session=sessions.findActiveSessionById(context.sessionId()).orElseThrow(SensitiveRequired::new);
        if(!sensitive.consumeVerification(context.uuid(),context.ip(),context.sessionId(),session.getSessionVersion(),"login-options"))
            throw new SensitiveRequired();
    }
    private void limit(HttpServletRequest request) {
        String ip=rate.getClientIp(request);
        if(!rate.isIpAllowed(ip,RateLimitService.TYPE_LOGIN)) throw new RateExceeded();
        rate.recordIpRequest(ip,RateLimitService.TYPE_LOGIN);
    }
    private static ResponseEntity<ApiResponse<Object>> ok(ApiResultCode message,Object data) {return ResponseEntity.ok(new ApiResponse<>(200, message, data));}
    private static class SensitiveRequired extends RuntimeException {}
    private static class RateExceeded extends RuntimeException {}
    @ExceptionHandler(SensitiveRequired.class)
    ResponseEntity<ApiResponse<Object>> needsSensitive() {return ResponseEntity.status(202).body(new ApiResponse<>(202, ApiResultCode.SENSITIVE_VERIFICATION_REQUIRED, Map.of("needVerification",true)));}
    @ExceptionHandler(RateExceeded.class)
    ResponseEntity<ApiResponse<Object>> rateExceeded() {return ResponseEntity.status(429).body(new ApiResponse<>(429, ApiResultCode.RATE_LIMITED));}
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiResponse<Object>> invalid(IllegalArgumentException ex) {return ResponseEntity.badRequest().body(new ApiResponse<>(400, ApiFailures.result(ex, ApiResultCode.BAD_REQUEST)));}
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiResponse<Object>> unavailable(IllegalStateException ex) {return ResponseEntity.status(503).body(new ApiResponse<>(503, ApiFailures.result(ex, ApiResultCode.APPLE_008)));}
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiResponse<Object>> conflict() {return ResponseEntity.status(409).body(new ApiResponse<>(409, ApiResultCode.APPLE_009));}
}
