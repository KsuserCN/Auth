package cn.ksuser.api.controller;

import cn.ksuser.api.config.AppProperties;
import cn.ksuser.api.dto.*;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.service.Oauth2PlatformService;
import cn.ksuser.api.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/oauth2")
public class Oauth2PlatformController {
    private static final long LOGO_MAX_SIZE_BYTES = 3L * 1024 * 1024;
    private static final String LOGO_STORAGE_DIR = "static/oauth2-logos";

    private final Oauth2PlatformService oauth2PlatformService;
    private final UserService userService;
    private final AppProperties appProperties;

    public Oauth2PlatformController(Oauth2PlatformService oauth2PlatformService,
                                    UserService userService,
                                    AppProperties appProperties) {
        this.oauth2PlatformService = oauth2PlatformService;
        this.userService = userService;
        this.appProperties = appProperties;
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

    @PostMapping(value = "/apps/{appId}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Oauth2AppResponse>> uploadApplicationLogo(@PathVariable String appId,
                                                                                 @RequestPart("file") MultipartFile file,
                                                                                 Authentication authentication) {
        User user = requireUser(authentication);
        if (file == null || file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "请选择 Logo 文件"));
        }
        if (file.getSize() > LOGO_MAX_SIZE_BYTES) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "Logo 文件不能超过 3MB"));
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "仅支持图片文件"));
        }

        String extension = resolveFileExtension(contentType, file.getOriginalFilename());
        if (extension == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, "不支持的图片格式"));
        }

        try {
            Path logoDir = Paths.get(LOGO_STORAGE_DIR).toAbsolutePath().normalize();
            Files.createDirectories(logoDir);

            String filename = UUID.randomUUID().toString().replace("-", "") + extension;
            Path target = logoDir.resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            String logoUrl = getStaticHostPrefix() + "/static/oauth2-logos/" + filename;
            Oauth2AppResponse response = oauth2PlatformService.updateApplicationLogo(user, appId, logoUrl);
            return ResponseEntity.ok(new ApiResponse<>(200, "Logo 上传成功", response));
        } catch (IOException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "Logo 上传失败"));
        }
    }

    @GetMapping("/authorize/context")
    public ResponseEntity<ApiResponse<Oauth2AuthorizeContextResponse>> authorizeContext(
        @RequestParam(name = "client_id") String clientId,
        @RequestParam(name = "redirect_uri") String redirectUri,
        @RequestParam(name = "response_type") String responseType,
        @RequestParam(name = "scope", required = false) String scope,
        Authentication authentication) {
        User user = resolveUser(authentication);
        Oauth2AuthorizeContextResponse response = oauth2PlatformService.buildAuthorizeContext(
            user, clientId, redirectUri, responseType, scope
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

    @GetMapping("/authorizations")
    public ResponseEntity<ApiResponse<java.util.List<Oauth2AuthorizedAppResponse>>> listAuthorizations(
        Authentication authentication) {
        User user = requireUser(authentication);
        return ResponseEntity.ok(new ApiResponse<>(200, "获取成功", oauth2PlatformService.listAuthorizations(user)));
    }

    @DeleteMapping("/authorizations/{appId}")
    public ResponseEntity<ApiResponse<Void>> revokeAuthorization(@PathVariable String appId,
                                                                 Authentication authentication) {
        User user = requireUser(authentication);
        oauth2PlatformService.revokeAuthorization(user, appId);
        return ResponseEntity.ok(new ApiResponse<>(200, "撤销成功"));
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

    private User resolveUser(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        String uuid = authentication.getPrincipal().toString();
        return userService.findByUuid(uuid).orElse(null);
    }

    private String extractBearerToken(String authorizationHeader, String queryToken) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7).trim();
        }
        return queryToken;
    }

    private String getStaticHostPrefix() {
        return appProperties.isDebug() ? "http://localhost:8000" : "https://api.ksuser.cn";
    }

    private String resolveFileExtension(String contentType, String originalFilename) {
        String normalizedType = contentType.toLowerCase(Locale.ROOT);
        switch (normalizedType) {
            case "image/jpeg":
            case "image/jpg":
                return ".jpg";
            case "image/png":
                return ".png";
            case "image/webp":
                return ".webp";
            case "image/gif":
                return ".gif";
            default:
                break;
        }
        if (originalFilename == null) {
            return null;
        }
        int index = originalFilename.lastIndexOf('.');
        if (index < 0 || index == originalFilename.length() - 1) {
            return null;
        }
        String extension = originalFilename.substring(index).toLowerCase(Locale.ROOT);
        if (".jpg".equals(extension) || ".jpeg".equals(extension) || ".png".equals(extension)
            || ".webp".equals(extension) || ".gif".equals(extension)) {
            return ".jpeg".equals(extension) ? ".jpg" : extension;
        }
        return null;
    }
}
