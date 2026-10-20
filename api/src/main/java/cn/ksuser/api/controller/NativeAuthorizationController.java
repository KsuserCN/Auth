package cn.ksuser.api.controller;

import cn.ksuser.api.dto.ApiResponse;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.result.ApiResultCode;
import cn.ksuser.api.service.NativeAuthorizationService;
import cn.ksuser.api.service.RateLimitService;
import cn.ksuser.api.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth/native-authorization")
public class NativeAuthorizationController {
    private final NativeAuthorizationService service;
    private final UserService users;
    private final RateLimitService rateLimit;

    public NativeAuthorizationController(NativeAuthorizationService service, UserService users, RateLimitService rateLimit) {
        this.service = service; this.users = users; this.rateLimit = rateLimit;
    }
    @PostMapping("/create") public ResponseEntity<?> create(@RequestBody NativeAuthorizationService.CreateRequest body, HttpServletRequest request) {
        return ok(service.create(body, rateLimit.getClientIp(request)));
    }
    @GetMapping("/context") public ResponseEntity<?> context(@RequestParam String ticket, Authentication auth) {
        return ok(service.preview(ticket, user(auth)));
    }
    @PostMapping("/decide") public ResponseEntity<?> decide(@RequestBody NativeAuthorizationService.Decision body, Authentication auth) {
        return ok(service.decide(body, user(auth)));
    }
    @PostMapping("/cancel") public ResponseEntity<?> cancel(@RequestBody NativeAuthorizationService.CancelRequest body) {
        return ok(service.cancel(body));
    }
    private ResponseEntity<?> ok(Object value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ApiResponse<>(200, ApiResultCode.REQUEST_SUCCESS, value));
    }
    private User user(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) throw unauthenticated();
        return users.findByUuid(auth.getPrincipal().toString()).orElseThrow(NativeAuthorizationController::unauthenticated);
    }
    private static Oauth2Exception unauthenticated() { return new Oauth2Exception(HttpStatus.UNAUTHORIZED, "login_required", ApiResultCode.UNAUTHENTICATED); }
    @ExceptionHandler(Oauth2Exception.class) public ResponseEntity<?> error(Oauth2Exception ex) {
        return ResponseEntity.status(ex.getStatus()).cacheControl(CacheControl.noStore()).body(new ApiResponse<>(ex.getStatus().value(), ex.getResult()));
    }
}
