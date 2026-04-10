package cn.ksuser.api.controller;

import cn.ksuser.api.dto.*;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.service.Oauth2PlatformService;
import cn.ksuser.api.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/oauth2")
public class Oauth2PlatformController {

    private final Oauth2PlatformService oauth2PlatformService;
    private final UserService userService;

    public Oauth2PlatformController(Oauth2PlatformService oauth2PlatformService,
                                    UserService userService) {
        this.oauth2PlatformService = oauth2PlatformService;
        this.userService = userService;
    }

    @GetMapping("/apps")
    public ResponseEntity<ApiResponse<Oauth2AppsOverviewResponse>> listApplications(Authentication authentication) {
        User user = requireUser(authentication);
        return ResponseEntity.ok(new ApiResponse<>(200, "获取成功", oauth2PlatformService.listApplications(user)));
    }

    @PostMapping("/apps")
    public ResponseEntity<ApiResponse<Oauth2AppCreateResponse>> createApplication(
        @RequestBody Oauth2AppCreateRequest request,
        Authentication authentication) {
        User user = requireUser(authentication);
        Oauth2AppCreateResponse response = oauth2PlatformService.createApplication(user, request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new ApiResponse<>(201, "创建成功，请妥善保存 AppSecret", response));
    }

    @PutMapping("/apps/{appId}")
    public ResponseEntity<ApiResponse<Oauth2AppResponse>> updateApplication(@PathVariable String appId,
                                                                            @RequestBody Oauth2AppUpdateRequest request,
                                                                            Authentication authentication) {
        User user = requireUser(authentication);
        Oauth2AppResponse response = oauth2PlatformService.updateApplication(user, appId, request);
        return ResponseEntity.ok(new ApiResponse<>(200, "更新成功", response));
    }

    @DeleteMapping("/apps/{appId}")
    public ResponseEntity<ApiResponse<Void>> deleteApplication(@PathVariable String appId,
                                                               Authentication authentication) {
        User user = requireUser(authentication);
        oauth2PlatformService.deleteApplication(user, appId);
        return ResponseEntity.ok(new ApiResponse<>(200, "删除成功"));
    }

    @GetMapping("/authorize/context")
    public ResponseEntity<ApiResponse<Oauth2AuthorizeContextResponse>> authorizeContext(
        @RequestParam(name = "client_id") String clientId,
        @RequestParam(name = "redirect_uri") String redirectUri,
        @RequestParam(name = "response_type") String responseType,
        @RequestParam(name = "scope", required = false) String scope) {
        Oauth2AuthorizeContextResponse response = oauth2PlatformService.buildAuthorizeContext(
            clientId, redirectUri, responseType, scope
        );
        return ResponseEntity.ok(new ApiResponse<>(200, "获取成功", response));
    }

    @PostMapping("/authorize/approve")
    public ResponseEntity<ApiResponse<Oauth2AuthorizeApproveResponse>> approveAuthorization(
        @RequestBody Oauth2AuthorizeRequest request,
        Authentication authentication) {
        User user = requireUser(authentication);
        Oauth2AuthorizeApproveResponse response = oauth2PlatformService.approveAuthorization(user, request);
        return ResponseEntity.ok(new ApiResponse<>(200, "授权成功", response));
    }

    @PostMapping("/token")
    public ResponseEntity<Map<String, Object>> exchangeToken(
        @RequestParam(name = "grant_type", required = false) String grantType,
        @RequestParam(name = "code", required = false) String code,
        @RequestParam(name = "client_id", required = false) String clientId,
        @RequestParam(name = "client_secret", required = false) String clientSecret,
        @RequestParam(name = "redirect_uri", required = false) String redirectUri) {
        Map<String, Object> response = oauth2PlatformService.exchangeAuthorizationCode(
            grantType, code, clientId, clientSecret, redirectUri
        );
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.PRAGMA, "no-cache")
            .body(response);
    }

    @GetMapping("/userinfo")
    public ResponseEntity<Map<String, Object>> userInfo(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestParam(name = "access_token", required = false) String accessToken) {
        String token = extractBearerToken(authorization, accessToken);
        Map<String, Object> response = oauth2PlatformService.buildUserInfo(token);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.PRAGMA, "no-cache")
            .body(response);
    }

    @ExceptionHandler(Oauth2Exception.class)
    public ResponseEntity<?> handleOauth2Exception(Oauth2Exception ex, HttpServletRequest request) {
        if (isOauthProtocolRequest(request)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", ex.getError());
            body.put("error_description", ex.getDescription());
            return ResponseEntity.status(ex.getStatus()).body(body);
        }

        return ResponseEntity.status(ex.getStatus())
            .body(new ApiResponse<>(ex.getStatus().value(), ex.getDescription()));
    }

    private boolean isOauthProtocolRequest(HttpServletRequest request) {
        String path = request == null ? "" : request.getRequestURI();
        return path.endsWith("/oauth2/token")
            || path.endsWith("/oauth2/token/")
            || path.endsWith("/oauth2/userinfo")
            || path.endsWith("/oauth2/userinfo/");
    }

    private User requireUser(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new Oauth2Exception(HttpStatus.UNAUTHORIZED, "invalid_token", "未登录");
        }

        String uuid = authentication.getPrincipal().toString();
        return userService.findByUuid(uuid)
            .orElseThrow(() -> new Oauth2Exception(HttpStatus.UNAUTHORIZED, "invalid_token", "用户不存在"));
    }

    private String extractBearerToken(String authorizationHeader, String queryToken) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7).trim();
        }
        return queryToken;
    }
}
