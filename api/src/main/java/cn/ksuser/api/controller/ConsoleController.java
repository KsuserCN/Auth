package cn.ksuser.api.controller;

import cn.ksuser.api.dto.ApiResponse;
import cn.ksuser.api.entity.ConsoleAuditLog;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.result.ApiResultCode;
import cn.ksuser.api.service.ConsoleAdminService;
import cn.ksuser.api.service.ConsoleSessionService;
import cn.ksuser.api.dto.QrChallengeInitResponse;
import cn.ksuser.api.service.RateLimitService;
import cn.ksuser.api.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/console")
public class ConsoleController {
    private final ConsoleSessionService sessions;
    private final ConsoleAdminService admin;
    private final RateLimitService rateLimit;
    @org.springframework.beans.factory.annotation.Autowired
    private cn.ksuser.api.service.UnionidApplicationService unionidApplications;
    public ConsoleController(ConsoleSessionService sessions,ConsoleAdminService admin,RateLimitService rateLimit) {
        this.sessions=sessions; this.admin=admin; this.rateLimit=rateLimit;
    }
    @GetMapping("/csrf-token")
    public ApiResponse<Map<String,String>> csrf(CsrfToken token) { return success(Map.of("token",token.getToken())); }
    @PostMapping("/auth/start")
    public ApiResponse<Map<String,String>> start(HttpServletRequest request,HttpServletResponse response) {
        sessions.cancelQrLogin(request,response);
        return success(sessions.startLogin(response));
    }
    @PostMapping("/auth/qr/init")
    public ApiResponse<QrChallengeInitResponse> qrInit(HttpServletRequest request,HttpServletResponse response) {
        return success(sessions.startQrLogin(request,response,clientIp(request),rateLimit.getClientUserAgent(request)));
    }
    @GetMapping("/auth/qr/status")
    public ApiResponse<ConsoleSessionService.QrLoginStatus> qrStatus(@RequestParam String challengeId,
        @RequestParam String pollToken,HttpServletRequest request) {
        return success(sessions.qrLoginStatus(challengeId,pollToken,request));
    }
    @PostMapping("/auth/qr/exchange")
    public ApiResponse<ConsoleSessionService.SessionView> qrExchange(@RequestBody QrLoginExchange body,
        HttpServletRequest request,HttpServletResponse response) {
        return success(sessions.exchangeQrLogin(body.challengeId(),body.pollToken(),request,response));
    }
    @DeleteMapping("/auth/qr")
    public ApiResponse<Void> qrCancel(@RequestParam(required=false) String challengeId,
        HttpServletRequest request,HttpServletResponse response) {
        sessions.cancelQrLogin(challengeId,request,response); return success(null);
    }
    @PostMapping("/auth/exchange")
    public ApiResponse<ConsoleSessionService.SessionView> exchange(@RequestBody LoginExchange body,HttpServletRequest request,HttpServletResponse response) {
        return success(sessions.exchangeLogin(body.code(),body.state(),request,response));
    }
    @GetMapping("/session")
    public ApiResponse<ConsoleSessionService.SessionView> session(Authentication auth) { return success(sessions.sessionView(principal(auth))); }
    @DeleteMapping("/session")
    public ApiResponse<Void> logout(Authentication auth,HttpServletResponse response) { sessions.logout(principal(auth),response); return success(null); }
    @PostMapping("/verification/start")
    public ApiResponse<Map<String,String>> verificationStart(Authentication auth) { return success(sessions.startVerification(principal(auth))); }
    @PostMapping("/verification/exchange")
    public ApiResponse<ConsoleSessionService.SessionView> verificationExchange(@RequestBody VerificationExchange body,Authentication auth) {
        return success(sessions.exchangeVerification(principal(auth),body.ticket(),body.state()));
    }
    @GetMapping("/users")
    public ApiResponse<ConsoleAdminService.PageView<ConsoleAdminService.UserView>> users(@RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="20") int size,@RequestParam(required=false) String search,@RequestParam(required=false) String role,
        @RequestParam(defaultValue="createdAt") String sort,@RequestParam(defaultValue="desc") String direction,
        @RequestParam(required=false) String verification) {
        return success(admin.listUsers(page,size,search,role,sort,direction,verification));
    }
    @GetMapping("/users/{uuid}")
    public ApiResponse<ConsoleAdminService.UserView> user(@PathVariable String uuid) { return success(admin.user(uuid)); }
    @PutMapping("/users/{uuid}/verification")
    public ApiResponse<ConsoleAdminService.UserView> verification(@PathVariable String uuid,
        @RequestBody ConsoleAdminService.VerificationRequest body,Authentication auth,HttpServletRequest request) {
        return success(admin.changeVerification(principal(auth),uuid,body,clientIp(request)));
    }
    @GetMapping("/users/{uuid}/applications")
    public ApiResponse<ConsoleAdminService.UserApplicationsView> userApplications(@PathVariable String uuid) {
        return success(admin.userApplications(uuid));
    }
    @PutMapping("/users/{uuid}/role")
    public ApiResponse<ConsoleAdminService.UserView> role(@PathVariable String uuid,@RequestBody RoleRequest body,Authentication auth,HttpServletRequest request) {
        return success(admin.changeRole(principal(auth),uuid,body.systemRole(),clientIp(request)));
    }
    @DeleteMapping("/users/{uuid}")
    public ApiResponse<Void> deleteUser(@PathVariable String uuid,Authentication auth,HttpServletRequest request) {
        admin.deleteUser(principal(auth),uuid,clientIp(request)); return success(null);
    }
    @GetMapping("/official-applications")
    public ApiResponse<List<ConsoleAdminService.ApplicationView>> apps() { return success(admin.listApplications()); }
    @PostMapping("/official-applications")
    public ApiResponse<ConsoleAdminService.ApplicationSecret> create(@RequestBody ConsoleAdminService.ApplicationRequest body,Authentication auth,HttpServletRequest request) {
        return success(admin.createApplication(principal(auth),body,clientIp(request)));
    }
    @PutMapping("/official-applications/{clientId}")
    public ApiResponse<ConsoleAdminService.ApplicationView> update(@PathVariable String clientId,@RequestBody ConsoleAdminService.ApplicationRequest body,Authentication auth,HttpServletRequest request) {
        return success(admin.updateApplication(principal(auth),clientId,body,clientIp(request)));
    }
    @DeleteMapping("/official-applications/{clientId}")
    public ApiResponse<Void> delete(@PathVariable String clientId,Authentication auth,HttpServletRequest request) {
        admin.deleteApplication(principal(auth),clientId,clientIp(request)); return success(null);
    }
    @PostMapping("/official-applications/{clientId}/rotate-secret")
    public ApiResponse<ConsoleAdminService.ApplicationSecret> rotate(@PathVariable String clientId,Authentication auth,HttpServletRequest request) {
        return success(admin.rotateSecret(principal(auth),clientId,clientIp(request)));
    }
    @GetMapping("/unionid-applications")
    public ApiResponse<ConsoleAdminService.PageView<cn.ksuser.api.service.UnionidApplicationService.View>> unionidApplications(
        @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
        @RequestParam(defaultValue="pending") String status) {
        return success(unionidApplications.list(page, size, status));
    }
    @PostMapping("/unionid-applications/{id}/review")
    public ApiResponse<cn.ksuser.api.service.UnionidApplicationService.View> reviewUnionid(@PathVariable Long id,
        @RequestBody cn.ksuser.api.service.UnionidApplicationService.Review body, Authentication auth, HttpServletRequest request) {
        return success(unionidApplications.review(principal(auth), id, body, clientIp(request)));
    }
    @GetMapping("/audit-logs")
    public ApiResponse<ConsoleAdminService.PageView<ConsoleAuditLog>> audits(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
        @RequestParam(defaultValue="createdAt") String sort,@RequestParam(defaultValue="desc") String direction) {
        return success(admin.listAudits(page,size,sort,direction));
    }
    private static String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        return IpUtil.isTrustedProxyIp(remote) ? IpUtil.getClientIp(request) : remote;
    }
    private static ConsoleSessionService.ConsolePrincipal principal(Authentication auth) {
        if (auth==null || !(auth.getPrincipal() instanceof ConsoleSessionService.ConsolePrincipal principal))
            throw new Oauth2Exception(HttpStatus.UNAUTHORIZED,"invalid_token",ApiResultCode.CONSOLE_SESSION_INVALID);
        return principal;
    }
    private static <T> ApiResponse<T> success(T value) { return new ApiResponse<>(200,ApiResultCode.REQUEST_SUCCESS,value); }
    public record LoginExchange(String code,String state) {}
    public record QrLoginExchange(String challengeId,String pollToken) {}
    public record VerificationExchange(String ticket,String state) {}
    public record RoleRequest(String systemRole) {}
}
