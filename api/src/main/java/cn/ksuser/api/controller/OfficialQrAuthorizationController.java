package cn.ksuser.api.controller;

import cn.ksuser.api.dto.ApiResponse;
import cn.ksuser.api.dto.OfficialAuthorizeRequest;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.result.ApiResultCode;
import cn.ksuser.api.service.OfficialQrAuthorizationService;
import cn.ksuser.api.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Anonymous browser endpoints; all writes retain CSRF checks, approval requires an App session. */
@RestController
@RequestMapping("/official/qr")
public class OfficialQrAuthorizationController {
    private final OfficialQrAuthorizationService service;
    private final RateLimitService rateLimit;
    public OfficialQrAuthorizationController(OfficialQrAuthorizationService service, RateLimitService rateLimit) {
        this.service = service; this.rateLimit = rateLimit;
    }
    @PostMapping("/init")
    public ResponseEntity<?> init(@RequestBody OfficialAuthorizeRequest input, HttpServletRequest request) {
        return ok(service.create(input, rateLimit.getClientIp(request), rateLimit.getClientUserAgent(request)));
    }
    @GetMapping("/status")
    public ResponseEntity<?> status(@RequestParam String challengeId, @RequestParam String pollToken) {
        return ok(service.status(new OfficialQrAuthorizationService.BrowserRequest(challengeId, pollToken)));
    }
    @PostMapping("/exchange")
    public ResponseEntity<?> exchange(@RequestBody OfficialQrAuthorizationService.BrowserRequest input) { return ok(service.consume(input)); }
    @PostMapping("/cancel")
    public ResponseEntity<?> cancel(@RequestBody OfficialQrAuthorizationService.BrowserRequest input) { service.cancel(input); return ok(null); }
    @ExceptionHandler(Oauth2Exception.class)
    public ResponseEntity<?> error(Oauth2Exception e) {
        return ResponseEntity.status(e.getStatus()).cacheControl(CacheControl.noStore()).body(new ApiResponse<>(e.getStatus().value(), e.getResult()));
    }
    private ResponseEntity<?> ok(Object data) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ApiResponse<>(200, ApiResultCode.REQUEST_SUCCESS, data)); }
}
