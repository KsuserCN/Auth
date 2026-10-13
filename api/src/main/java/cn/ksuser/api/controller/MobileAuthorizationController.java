package cn.ksuser.api.controller;

import cn.ksuser.api.result.ApiResultCode;

import cn.ksuser.api.dto.ApiResponse;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.service.MobileAuthorizationService;
import cn.ksuser.api.service.UserService;
import cn.ksuser.api.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth/mobile-authorization")
public class MobileAuthorizationController {
    private final MobileAuthorizationService service;
    private final UserService users;
    private final RateLimitService rateLimit;
    public MobileAuthorizationController(MobileAuthorizationService service, UserService users, RateLimitService rateLimit) {
        this.service = service; this.users = users; this.rateLimit = rateLimit;
    }
    @PostMapping("/create") public ResponseEntity<?> create(@RequestBody MobileAuthorizationService.CreateRequest input) { return ok(service.create(input)); }
    @PostMapping("/qr/create") public ResponseEntity<?> createQr(@RequestBody MobileAuthorizationService.CreateRequest input, HttpServletRequest request) {
        return ok(service.createQr(input, rateLimit.getClientIp(request), rateLimit.getClientUserAgent(request)));
    }
    @GetMapping("/qr/status") public ResponseEntity<?> qrStatus(@RequestParam String ticket, @RequestParam String secret) {
        return ok(service.qrStatus(new MobileAuthorizationService.BrowserRequest(ticket, secret)));
    }
    @GetMapping("/context") public ResponseEntity<?> context(@RequestParam String ticket, Authentication auth) { return ok(service.preview(ticket, user(auth))); }
    @PostMapping("/decide") public ResponseEntity<?> decide(@RequestBody MobileAuthorizationService.Decision input, Authentication auth) { return ok(service.decide(input, user(auth))); }
    @PostMapping("/consume") public ResponseEntity<?> consume(@RequestBody MobileAuthorizationService.BrowserRequest input) { return ok(service.consume(input)); }
    @PostMapping("/cancel") public ResponseEntity<?> cancel(@RequestBody MobileAuthorizationService.BrowserRequest input) { service.cancel(input); return ok(null); }
    private ResponseEntity<?> ok(Object value) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ApiResponse<>(200, ApiResultCode.REQUEST_SUCCESS, value)); }
    private User user(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) throw new Oauth2Exception(HttpStatus.UNAUTHORIZED, "invalid_token", ApiResultCode.UNAUTHENTICATED);
        return users.findByUuid(auth.getPrincipal().toString()).orElseThrow(() -> new Oauth2Exception(HttpStatus.UNAUTHORIZED, "invalid_token", ApiResultCode.AUTH_005));
    }
    @ExceptionHandler(Oauth2Exception.class) public ResponseEntity<?> error(Oauth2Exception e) { return ResponseEntity.status(e.getStatus()).cacheControl(CacheControl.noStore()).body(new ApiResponse<>(e.getStatus().value(), e.getResult())); }
}
