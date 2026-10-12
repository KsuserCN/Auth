package cn.ksuser.api.controller;

import cn.ksuser.api.dto.ApiResponse;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.result.ApiResultCode;
import cn.ksuser.api.service.ConsoleSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** These routes use the existing passport session, while the proof is bound to one Console session. */
@RestController
@RequestMapping("/auth/console-verification")
public class ConsoleVerificationController {
    private final ConsoleSessionService sessions;
    public ConsoleVerificationController(ConsoleSessionService sessions) { this.sessions = sessions; }
    @GetMapping("/context")
    public ApiResponse<Map<String, Object>> context(@RequestParam("request_id") String requestId, Authentication auth) {
        return new ApiResponse<>(200, ApiResultCode.REQUEST_SUCCESS, sessions.verificationContext(requestId, uuid(auth)));
    }
    @PostMapping("/complete")
    public ApiResponse<Map<String, String>> complete(@RequestBody CompleteRequest body, Authentication auth) {
        return new ApiResponse<>(200, ApiResultCode.REQUEST_SUCCESS, sessions.completeVerification(body.requestId(), uuid(auth)));
    }
    private static String uuid(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof String uuid) || uuid.isBlank())
            throw new Oauth2Exception(HttpStatus.UNAUTHORIZED, "invalid_token", ApiResultCode.UNAUTHENTICATED);
        return uuid;
    }
    public record CompleteRequest(String requestId) {}
}
